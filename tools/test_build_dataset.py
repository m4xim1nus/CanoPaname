"""Tests offline des helpers purs de `build_dataset.py`.

Couvre Sprint 1 (drops/fixups/normalisation) + Sprint 2 (cascade nv,
désambiguation, Pokédex). Aucun appel réseau ni dépendance CSV.

Run :
    python3 -m unittest tools.test_build_dataset
ou :
    cd tools && python3 -m unittest test_build_dataset
"""

from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path
from unittest import mock

import build_dataset
from build_dataset import (
    SPECIES_FIXUPS,
    UNKNOWN_ESPECE_FORMS,
    VERNACULAR_OVERRIDES,
    apply_species_fixups,
    construct_vernacular,
    disambiguate_vernaculars,
    first_p1843,
    is_unknown_species,
)


class ApplySpeciesFixupsTest(unittest.TestCase):
    def test_remap_olea_europea(self):
        self.assertEqual(
            apply_species_fixups("Olea", "europea"),
            ("Olea", "europaea"),
        )

    def test_noop_canonical_pair(self):
        self.assertEqual(
            apply_species_fixups("Quercus", "robur"),
            ("Quercus", "robur"),
        )

    def test_noop_unknown_pair(self):
        self.assertEqual(
            apply_species_fixups("Genre", "fictif"),
            ("Genre", "fictif"),
        )

    def test_canonical_form_is_idempotent(self):
        self.assertEqual(
            apply_species_fixups("Olea", "europaea"),
            ("Olea", "europaea"),
        )

    def test_fixups_table_has_at_least_olea(self):
        # Sentinel : si on regression la table on perd les 33 oliviers.
        self.assertIn(("Olea", "europea"), SPECIES_FIXUPS)


class IsUnknownSpeciesTest(unittest.TestCase):
    def test_sp_is_unknown(self):
        self.assertTrue(is_unknown_species("Tilia", "sp."))

    def test_n_sp_is_unknown(self):
        self.assertTrue(is_unknown_species("Acer", "n. sp."))

    def test_n_sp_case_insensitive(self):
        self.assertTrue(is_unknown_species("Acer", "N. SP."))

    def test_sp_with_whitespace(self):
        self.assertTrue(is_unknown_species("Tilia", "  sp.  "))

    def test_non_specifie_genre_is_unknown(self):
        # Les rows `(Non spécifié, *)` sont droppées à l'ingestion mais leurs
        # entrées d'index pré-existantes restent comme zombies — taggées `u`.
        self.assertTrue(is_unknown_species("Non spécifié", "americana"))
        self.assertTrue(is_unknown_species("Non spécifié", "sp."))
        self.assertTrue(is_unknown_species("Non spécifié", "n. sp."))

    def test_canonical_species_is_known(self):
        self.assertFalse(is_unknown_species("Quercus", "robur"))
        self.assertFalse(is_unknown_species("Platanus", "x hispanica"))
        self.assertFalse(is_unknown_species("Olea", "europaea"))

    def test_unknown_forms_set_is_minimal(self):
        # Si on étend cette liste, `species-index.json` repassera des entrées
        # known en unknown au prochain build — décision à prendre consciemment.
        self.assertEqual(UNKNOWN_ESPECE_FORMS, frozenset({"sp.", "n. sp."}))


class FirstP1843Test(unittest.TestCase):
    def test_empty_returns_none(self):
        self.assertIsNone(first_p1843([]))

    def test_only_blanks_returns_none(self):
        self.assertIsNone(first_p1843(["", "  ", None]))  # type: ignore[list-item]

    def test_single_value(self):
        self.assertEqual(first_p1843(["Chêne pédonculé"]), "Chêne pédonculé")

    def test_alphabetical_pick(self):
        # Ordre déterministe quel que soit l'ordre Wikidata.
        self.assertEqual(
            first_p1843(["Chêne rouvre", "Chêne pédonculé"]),
            "Chêne pédonculé",
        )
        self.assertEqual(
            first_p1843(["Chêne pédonculé", "Chêne rouvre"]),
            "Chêne pédonculé",
        )

    def test_strips_whitespace(self):
        self.assertEqual(first_p1843(["  Tilleul  "]), "Tilleul")

    def test_dedup_in_caller_responsibility(self):
        # first_p1843 ne dédupe pas — c'est resolve_via_wikidata qui le fait.
        self.assertEqual(first_p1843(["Tilleul", "Tilleul"]), "Tilleul")


class ConstructVernacularTest(unittest.TestCase):
    def test_identified_with_nc(self):
        self.assertEqual(
            construct_vernacular("Quercus", "robur", nc="Chêne", is_unknown=False),
            "Chêne (Q. robur)",
        )

    def test_identified_without_nc(self):
        self.assertEqual(
            construct_vernacular("Pistacia", "palaestina", nc=None, is_unknown=False),
            "Pistacia palaestina",
        )

    def test_hybrid_strips_x(self):
        self.assertEqual(
            construct_vernacular("Platanus", "× hispanica", nc="Platane", is_unknown=False),
            "Platane (P. hispanica)",
        )
        self.assertEqual(
            construct_vernacular("Platanus", "x hispanica", nc="Platane", is_unknown=False),
            "Platane (P. hispanica)",
        )

    def test_unknown_with_nc(self):
        self.assertEqual(
            construct_vernacular("Tilia", "sp.", nc="Tilleul", is_unknown=True),
            "Tilleul (espèce indéterminée)",
        )

    def test_unknown_without_nc_uses_genre(self):
        self.assertEqual(
            construct_vernacular("Pistacia", "sp.", nc=None, is_unknown=True),
            "Pistacia (espèce indéterminée)",
        )

    def test_unknown_non_specifie_zombie(self):
        # Zombie `(Non spécifié, sp.)` : pas de nc utile, pas de genre utile.
        self.assertEqual(
            construct_vernacular("Non spécifié", "sp.", nc="Non spécifié", is_unknown=True),
            "Espèce indéterminée",
        )
        self.assertEqual(
            construct_vernacular("Non spécifié", "americana", nc=None, is_unknown=True),
            "Espèce indéterminée",
        )


class DisambiguateVernacularsTest(unittest.TestCase):
    def test_no_collision_no_change(self):
        entries = [
            {"i": 0, "g": "Quercus", "e": "robur", "nv": "Chêne pédonculé"},
            {"i": 1, "g": "Tilia", "e": "cordata", "nv": "Tilleul à petites feuilles"},
        ]
        changed = disambiguate_vernaculars(entries)
        self.assertEqual(changed, 0)
        self.assertEqual(entries[0]["nv"], "Chêne pédonculé")
        self.assertEqual(entries[1]["nv"], "Tilleul à petites feuilles")

    def test_two_collisions_suffixed(self):
        entries = [
            {"i": 0, "g": "Quercus", "e": "robur", "nv": "Chêne"},
            {"i": 1, "g": "Quercus", "e": "petraea", "nv": "Chêne"},
        ]
        changed = disambiguate_vernaculars(entries)
        self.assertEqual(changed, 2)
        self.assertEqual(entries[0]["nv"], "Chêne (Quercus robur)")
        self.assertEqual(entries[1]["nv"], "Chêne (Quercus petraea)")

    def test_three_collisions_all_suffixed(self):
        entries = [
            {"i": 0, "g": "Acer", "e": "platanoides", "nv": "Érable"},
            {"i": 1, "g": "Acer", "e": "pseudoplatanus", "nv": "Érable"},
            {"i": 2, "g": "Acer", "e": "campestre", "nv": "Érable"},
        ]
        changed = disambiguate_vernaculars(entries)
        self.assertEqual(changed, 3)
        self.assertEqual(
            sorted(e["nv"] for e in entries),
            [
                "Érable (Acer campestre)",
                "Érable (Acer platanoides)",
                "Érable (Acer pseudoplatanus)",
            ],
        )

    def test_uniqueness_after_disambiguation(self):
        # Les paires (g, e) sont uniques par construction → suffixe unique.
        entries = [
            {"i": 0, "g": "Quercus", "e": "robur", "nv": "Chêne"},
            {"i": 1, "g": "Quercus", "e": "petraea", "nv": "Chêne"},
            {"i": 2, "g": "Tilia", "e": "cordata", "nv": "Tilleul"},
        ]
        disambiguate_vernaculars(entries)
        nvs = [e["nv"] for e in entries]
        self.assertEqual(len(set(nvs)), len(nvs))


class ComputeVernacularAndPokedexTest(unittest.TestCase):
    """Test d'intégration de la cascade complète + désambig + Pokédex.

    Pas de réseau : on fixture les caches Wikidata sur disque temporaire.
    """

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.tmp_path = Path(self.tmp.name)
        self.cache_dir = self.tmp_path / ".wikidata-cache"
        self.cache_dir.mkdir()
        self.species_index_out = self.tmp_path / "species-index.json"

        self.cache_patch = mock.patch.object(
            build_dataset, "WIKIDATA_CACHE_DIR", self.cache_dir
        )
        self.out_patch = mock.patch.object(
            build_dataset, "OUT_SPECIES_INDEX", self.species_index_out
        )
        self.cache_patch.start()
        self.out_patch.start()

    def tearDown(self):
        self.cache_patch.stop()
        self.out_patch.stop()
        self.tmp.cleanup()

    def _write_cache(self, sk: int, content: dict) -> None:
        (self.cache_dir / f"{sk}.json").write_text(json.dumps(content), encoding="utf-8")

    def test_cascade_overrides_p1843_frtitle_construit(self):
        # 4 espèces couvrant chaque branche de la cascade.
        species_index = {
            ("Quercus", "robur"): 0,        # via P1843
            ("Tilia", "cordata"): 1,        # via frTitle
            ("Pistacia", "palaestina"): 2,  # construit (no nc, no cache)
            ("Override", "test"): 3,        # via override manuel
        }
        # Cache Quercus robur : a P1843
        self._write_cache(0, {
            "qid": "Q165145",
            "wp": "Chêne_pédonculé",
            "summary": "...",
            "vernacularNames": ["Chêne rouvre", "Chêne pédonculé"],
        })
        # Cache Tilia cordata : pas de P1843 mais frTitle dispo
        self._write_cache(1, {
            "qid": "Q156726",
            "wp": "Tilleul_à_petites_feuilles",
            "summary": "...",
            "vernacularNames": [],
        })
        # Cache Pistacia : miss → fallback construit
        self._write_cache(2, {"miss": True})
        # Cache override : on laisse vide pour vérifier override gagnant.
        self._write_cache(3, {"miss": True})

        nom_commun_by_sk: dict[int, dict[str, int]] = {
            0: {"Chêne": 100},
            1: {"Tilleul": 50},
            # 2: pas de nc → fallback construit nu
            # 3: pas de nc → ignoré car override
        }
        count_by_sk = {0: 1500, 1: 4000, 2: 5, 3: 10}

        with mock.patch.dict(
            VERNACULAR_OVERRIDES,
            {("Override", "test"): "Override Manual"},
            clear=False,
        ):
            entries, counters, _construit = build_dataset.compute_vernacular_and_pokedex(
                species_index=species_index,
                nom_commun_by_sk=nom_commun_by_sk,
                count_by_sk=count_by_sk,
            )

        by_sk = {e["i"]: e for e in entries}
        # Cascade par espèce.
        self.assertEqual(by_sk[0]["nv"], "Chêne pédonculé")  # P1843 1re alphabétique
        self.assertEqual(by_sk[1]["nv"], "Tilleul à petites feuilles")  # frTitle
        self.assertEqual(by_sk[2]["nv"], "Pistacia palaestina")  # construit
        self.assertEqual(by_sk[3]["nv"], "Override Manual")  # override

        # Compteurs.
        self.assertEqual(counters["nv_via_overrides"], 1)
        self.assertEqual(counters["nv_via_p1843"], 1)
        self.assertEqual(counters["nv_via_frtitle"], 1)
        self.assertEqual(counters["nv_via_construit"], 1)
        self.assertEqual(counters["nv_disambiguations"], 0)

        # n attribué à toutes (toutes identifiées avec count > 0).
        self.assertEqual(by_sk[0]["n"], 1)
        self.assertEqual(by_sk[1]["n"], 2)
        self.assertEqual(by_sk[2]["n"], 3)
        self.assertEqual(by_sk[3]["n"], 4)
        self.assertEqual(counters["pokedex_count"], 4)

    def test_pokedex_skips_unknown_and_zombies(self):
        species_index = {
            ("Quercus", "robur"): 0,        # identifiée, count > 0 → n=1
            ("Tilia", "sp."): 1,            # u: True → pas de n
            ("Olea", "europea"): 2,         # zombie count = 0 → pas de n
            ("Acer", "campestre"): 3,       # identifiée, count > 0 → n=2
        }
        for sk in [0, 1, 2, 3]:
            self._write_cache(sk, {"miss": True})  # tous fallback construit
        nom_commun_by_sk = {
            0: {"Chêne": 1},
            1: {"Tilleul": 1},
            3: {"Érable": 1},
        }
        count_by_sk = {0: 1500, 1: 50, 2: 0, 3: 200}

        entries, counters, _construit = build_dataset.compute_vernacular_and_pokedex(
            species_index=species_index,
            nom_commun_by_sk=nom_commun_by_sk,
            count_by_sk=count_by_sk,
        )

        by_sk = {e["i"]: e for e in entries}
        self.assertEqual(by_sk[0]["n"], 1)
        self.assertNotIn("n", by_sk[1])  # u: true
        self.assertNotIn("n", by_sk[2])  # count = 0 zombie
        self.assertEqual(by_sk[3]["n"], 2)
        self.assertEqual(counters["pokedex_count"], 2)

        # Tags u préservés.
        self.assertTrue(by_sk[1].get("u"))
        self.assertNotIn("u", by_sk[0])

    def test_disambiguation_triggers_and_asserts_unique(self):
        # Deux espèces sans nc, fallback construit → forcément distincts
        # (binôme inclus). Pour forcer une collision, on utilise des overrides
        # identiques.
        species_index = {
            ("Quercus", "robur"): 0,
            ("Quercus", "petraea"): 1,
        }
        for sk in [0, 1]:
            self._write_cache(sk, {"miss": True})
        nom_commun_by_sk = {0: {"Chêne": 1}, 1: {"Chêne": 1}}
        count_by_sk = {0: 100, 1: 100}

        with mock.patch.dict(
            VERNACULAR_OVERRIDES,
            {
                ("Quercus", "robur"): "Chêne",
                ("Quercus", "petraea"): "Chêne",
            },
            clear=False,
        ):
            entries, counters, _construit = build_dataset.compute_vernacular_and_pokedex(
                species_index=species_index,
                nom_commun_by_sk=nom_commun_by_sk,
                count_by_sk=count_by_sk,
            )

        nvs = sorted(e["nv"] for e in entries)
        self.assertEqual(nvs, ["Chêne (Quercus petraea)", "Chêne (Quercus robur)"])
        self.assertEqual(counters["nv_disambiguations"], 2)
        # Assert d'unicité passé.
        self.assertEqual(len(set(nvs)), len(nvs))

    def test_writes_species_index_json(self):
        species_index = {("Quercus", "robur"): 0}
        self._write_cache(0, {
            "qid": "Q165145",
            "wp": "Chêne_pédonculé",
            "summary": "...",
            "vernacularNames": ["Chêne pédonculé"],
        })
        nom_commun_by_sk = {0: {"Chêne": 1}}
        count_by_sk = {0: 1500}

        build_dataset.compute_vernacular_and_pokedex(
            species_index=species_index,
            nom_commun_by_sk=nom_commun_by_sk,
            count_by_sk=count_by_sk,
        )

        written = json.loads(self.species_index_out.read_text(encoding="utf-8"))
        self.assertEqual(len(written), 1)
        e = written[0]
        self.assertEqual(e["i"], 0)
        self.assertEqual(e["g"], "Quercus")
        self.assertEqual(e["e"], "robur")
        self.assertEqual(e["nc"], "Chêne")
        self.assertEqual(e["nv"], "Chêne pédonculé")
        self.assertEqual(e["n"], 1)
        self.assertNotIn("u", e)


class VerifySpeciesInvariantsTest(unittest.TestCase):
    """Tests offline des 5 sanity checks (cf. ROADMAP cycle Catalogue ligne 20).

    Pas de tempfile pour les fixtures de base — on construit `entries` à la
    main. Un répertoire temporaire est utilisé uniquement pour les tests qui
    doivent inspecter `.wikidata-cache/` (check #2 perte WP).
    """

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.cache_dir = Path(self.tmp.name)

    def tearDown(self):
        self.tmp.cleanup()

    def _entry(self, sk: int, g: str, e: str, nv: str, **extra) -> dict:
        out = {"i": sk, "g": g, "e": e, "nv": nv}
        out.update(extra)
        return out

    def _write_cache(self, sk: int, content: dict) -> None:
        (self.cache_dir / f"{sk}.json").write_text(
            json.dumps(content), encoding="utf-8"
        )

    def test_empty_pre_state_no_raise(self):
        # Premier build : aucun fichier persisté, baseline vide. Aucune
        # vérification ne peut tomber, mais on s'assure que la fonction passe
        # sans raise et sans warn.
        entries = [self._entry(0, "Quercus", "robur", "Chêne pédonculé")]
        try:
            build_dataset.verify_species_invariants(
                pre_sk_set=frozenset(),
                pre_wp_present_by_sk={},
                entries=entries,
                count_by_sk={0: 1500},
                non_specifie_count=0,
                construit_high_count=[],
                cache_dir=self.cache_dir,
            )
        except AssertionError as exc:
            self.fail(f"raise inattendu sur état vide : {exc}")

    def test_sk_disappeared_raises(self):
        entries = [self._entry(0, "Quercus", "robur", "Chêne pédonculé")]
        with self.assertRaisesRegex(AssertionError, "sk disparus"):
            build_dataset.verify_species_invariants(
                pre_sk_set=frozenset({0, 1, 2}),
                pre_wp_present_by_sk={},
                entries=entries,
                count_by_sk={0: 1500},
                non_specifie_count=0,
                construit_high_count=[],
                cache_dir=self.cache_dir,
            )

    def test_wp_loss_high_count_raises(self):
        # sk=0 avait wp pré-build, count > 100, et le cache courant est miss.
        self._write_cache(0, {"miss": True})
        entries = [self._entry(0, "Quercus", "robur", "Chêne pédonculé")]
        with self.assertRaisesRegex(AssertionError, "Wikipedia FR perdue"):
            build_dataset.verify_species_invariants(
                pre_sk_set=frozenset({0}),
                pre_wp_present_by_sk={0: True},
                entries=entries,
                count_by_sk={0: 1500},
                non_specifie_count=0,
                construit_high_count=[],
                cache_dir=self.cache_dir,
            )

    def test_wp_loss_low_count_silent(self):
        # Même scénario que ci-dessus mais count = 50 → sous le seuil, pas de raise.
        self._write_cache(0, {"miss": True})
        entries = [self._entry(0, "Genre", "raresp", "Genre raresp")]
        try:
            build_dataset.verify_species_invariants(
                pre_sk_set=frozenset({0}),
                pre_wp_present_by_sk={0: True},
                entries=entries,
                count_by_sk={0: 50},
                non_specifie_count=0,
                construit_high_count=[],
                cache_dir=self.cache_dir,
            )
        except AssertionError as exc:
            self.fail(f"raise inattendu sur count = 50 : {exc}")

    def test_wp_present_after_no_raise(self):
        # WP présente au pré-build ET au post-build (cache valide avec wp) → OK.
        self._write_cache(0, {"qid": "Q1", "wp": "Quercus_robur"})
        entries = [self._entry(0, "Quercus", "robur", "Chêne pédonculé")]
        try:
            build_dataset.verify_species_invariants(
                pre_sk_set=frozenset({0}),
                pre_wp_present_by_sk={0: True},
                entries=entries,
                count_by_sk={0: 5000},
                non_specifie_count=0,
                construit_high_count=[],
                cache_dir=self.cache_dir,
            )
        except AssertionError as exc:
            self.fail(f"raise inattendu sur wp présent : {exc}")

    def test_non_specifie_threshold_raises(self):
        entries = [self._entry(0, "Quercus", "robur", "Chêne pédonculé")]
        with self.assertRaisesRegex(AssertionError, "Non spécifié"):
            build_dataset.verify_species_invariants(
                pre_sk_set=frozenset({0}),
                pre_wp_present_by_sk={},
                entries=entries,
                count_by_sk={0: 100},
                non_specifie_count=51,
                construit_high_count=[],
                cache_dir=self.cache_dir,
            )

    def test_non_specifie_under_threshold_silent(self):
        entries = [self._entry(0, "Quercus", "robur", "Chêne pédonculé")]
        try:
            build_dataset.verify_species_invariants(
                pre_sk_set=frozenset({0}),
                pre_wp_present_by_sk={},
                entries=entries,
                count_by_sk={0: 100},
                non_specifie_count=50,
                construit_high_count=[],
                cache_dir=self.cache_dir,
            )
        except AssertionError as exc:
            self.fail(f"raise inattendu à seuil = 50 : {exc}")

    def test_nv_non_unique_raises(self):
        entries = [
            self._entry(0, "Quercus", "robur", "Chêne"),
            self._entry(1, "Quercus", "petraea", "Chêne"),
        ]
        with self.assertRaisesRegex(AssertionError, "nv non-unique"):
            build_dataset.verify_species_invariants(
                pre_sk_set=frozenset({0, 1}),
                pre_wp_present_by_sk={},
                entries=entries,
                count_by_sk={0: 100, 1: 100},
                non_specifie_count=0,
                construit_high_count=[],
                cache_dir=self.cache_dir,
            )

    def test_construit_high_count_warns(self):
        import io
        from contextlib import redirect_stderr

        entries = [
            self._entry(0, "Quercus", "rubra", "Quercus rubra"),
            self._entry(1, "Acer", "negundo", "Acer negundo"),
        ]
        construit = [("Quercus", "rubra", 4500), ("Acer", "negundo", 2300)]
        buf = io.StringIO()
        with redirect_stderr(buf):
            build_dataset.verify_species_invariants(
                pre_sk_set=frozenset({0, 1}),
                pre_wp_present_by_sk={},
                entries=entries,
                count_by_sk={0: 4500, 1: 2300},
                non_specifie_count=0,
                construit_high_count=construit,
                cache_dir=self.cache_dir,
            )
        out = buf.getvalue()
        self.assertIn("[warn]", out)
        self.assertIn("Quercus rubra", out)
        self.assertIn("Acer negundo", out)
        self.assertIn("4500", out)

    def test_all_invariants_pass(self):
        # Cas nominal : tous les sk préservés, aucune WP perdue, Non spécifié
        # sous seuil, nv uniques, pas de candidats construits → silencieux.
        self._write_cache(0, {"qid": "Q1", "wp": "Quercus_robur"})
        entries = [
            self._entry(0, "Quercus", "robur", "Chêne pédonculé"),
            self._entry(1, "Tilia", "cordata", "Tilleul à petites feuilles"),
        ]
        import io
        from contextlib import redirect_stderr
        buf = io.StringIO()
        with redirect_stderr(buf):
            build_dataset.verify_species_invariants(
                pre_sk_set=frozenset({0, 1}),
                pre_wp_present_by_sk={0: True, 1: False},
                entries=entries,
                count_by_sk={0: 5000, 1: 4000},
                non_specifie_count=10,
                construit_high_count=[],
                cache_dir=self.cache_dir,
            )
        self.assertEqual(buf.getvalue(), "")


class LoadPreBuildStateTest(unittest.TestCase):
    """Tests offline du snapshot pré-build (lecture des assets persistés)."""

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.tmp_path = Path(self.tmp.name)
        self.species_index_path = self.tmp_path / "species-index.json"
        self.species_info_path = self.tmp_path / "species-info.json"

    def tearDown(self):
        self.tmp.cleanup()

    def test_both_files_absent_returns_empty(self):
        sk_set, wp_by_sk = build_dataset.load_pre_build_state(
            self.species_index_path, self.species_info_path,
        )
        self.assertEqual(sk_set, frozenset())
        self.assertEqual(wp_by_sk, {})

    def test_reads_sk_and_wp_marker(self):
        self.species_index_path.write_text(
            json.dumps([
                {"i": 0, "g": "Quercus", "e": "robur", "nv": "Chêne"},
                {"i": 1, "g": "Tilia", "e": "cordata", "nv": "Tilleul"},
                {"i": 2, "g": "Genre", "e": "raresp", "nv": "Genre raresp"},
            ]), encoding="utf-8",
        )
        # `wp` natif présent ssi page WP résolue. La sémantique « clé absente
        # = pas de WP » est verrouillée ici — un futur refactor qui écrirait
        # `"wp": null` casserait silencieusement le check #1.
        self.species_info_path.write_text(
            json.dumps([
                {"i": 0, "wp": "Quercus_robur", "qid": "Q1"},
                {"i": 1, "wp": "Tilia_cordata", "qid": "Q2"},
                {"i": 2, "qid": None},
            ]), encoding="utf-8",
        )
        sk_set, wp_by_sk = build_dataset.load_pre_build_state(
            self.species_index_path, self.species_info_path,
        )
        self.assertEqual(sk_set, frozenset({0, 1, 2}))
        self.assertEqual(wp_by_sk, {0: True, 1: True, 2: False})

    def test_corrupt_index_returns_empty_sk_set(self):
        self.species_index_path.write_text("not valid json{", encoding="utf-8")
        sk_set, wp_by_sk = build_dataset.load_pre_build_state(
            self.species_index_path, self.species_info_path,
        )
        self.assertEqual(sk_set, frozenset())
        self.assertEqual(wp_by_sk, {})


if __name__ == "__main__":
    unittest.main()
