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
    GENRE_FR,
    SPECIES_FIXUPS,
    UNKNOWN_ESPECE_FORMS,
    VERNACULAR_OVERRIDES,
    apply_species_fixups,
    construct_vernacular,
    disambiguate_vernaculars,
    extract_nv_from_summary,
    first_p1843,
    genre_fr,
    is_unknown_species,
    pick_vernacular_from_redirects,
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

    def test_unknown_genre_mapped_in_genre_fr(self):
        # S6 : `Tilia sp.` retombe sur le nom français court du genre, sans
        # suffixe « (espèce indéterminée) ». Le `nc` du CSV n'est pas consulté.
        self.assertEqual(
            construct_vernacular("Tilia", "sp.", nc="Tilleul", is_unknown=True),
            "Tilleul",
        )

    def test_unknown_genre_not_mapped_falls_back_to_latin(self):
        # S6 : genre absent de `GENRE_FR` → binôme genre latin nu (candidat à
        # compléter dans la table, surfacé dans le rapport HTML).
        self.assertEqual(
            construct_vernacular("Genrefictif", "sp.", nc=None, is_unknown=True),
            "Genrefictif",
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
        self.aliases_cache_dir = self.tmp_path / ".wikipedia-aliases-cache"
        self.species_index_out = self.tmp_path / "species-index.json"
        self.trace_out = self.tmp_path / "_trace" / "vernacular-source.json"

        self.patches = [
            mock.patch.object(build_dataset, "WIKIDATA_CACHE_DIR", self.cache_dir),
            mock.patch.object(build_dataset, "WIKI_ALIASES_CACHE_DIR", self.aliases_cache_dir),
            mock.patch.object(build_dataset, "OUT_SPECIES_INDEX", self.species_index_out),
            mock.patch.object(build_dataset, "OUT_VERNACULAR_TRACE", self.trace_out),
        ]
        for p in self.patches:
            p.start()

    def tearDown(self):
        for p in self.patches:
            p.stop()
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
        # Pistacia : pas de nc, fallback construit binôme nu.
        self.assertEqual(counters["nv_via_construit_binom"], 1)
        self.assertEqual(counters["nv_via_construit_nc_unique"], 0)
        self.assertEqual(counters["nv_via_construit_nc_disamb"], 0)
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

    def test_non_specifie_active_raises(self):
        # Régression du drop : une entrée 'Non spécifié' reçoit des arbres
        # (count > 0). C'est ce qu'il faut détecter.
        entries = [
            self._entry(0, "Quercus", "robur", "Chêne pédonculé"),
            self._entry(1, "Non spécifié", "sp.", "Espèce indéterminée"),
        ]
        with self.assertRaisesRegex(AssertionError, "Non spécifié.*active"):
            build_dataset.verify_species_invariants(
                pre_sk_set=frozenset({0, 1}),
                pre_wp_present_by_sk={},
                entries=entries,
                count_by_sk={0: 100, 1: 42},  # le zombie reçoit 42 arbres → raise
                non_specifie_count=811,
                construit_high_count=[],
                cache_dir=self.cache_dir,
            )

    def test_non_specifie_zombie_silent(self):
        # Cas nominal : entrée 'Non spécifié' zombie (count = 0) préservée pour
        # rétrocompat des captures users → pas de raise. Le compteur CSV brut
        # (baseline ~811) n'est plus un signal et est ignoré.
        entries = [
            self._entry(0, "Quercus", "robur", "Chêne pédonculé"),
            self._entry(1, "Non spécifié", "sp.", "Espèce indéterminée"),
        ]
        try:
            build_dataset.verify_species_invariants(
                pre_sk_set=frozenset({0, 1}),
                pre_wp_present_by_sk={},
                entries=entries,
                count_by_sk={0: 100, 1: 0},  # zombie sans arbre actif
                non_specifie_count=811,
                construit_high_count=[],
                cache_dir=self.cache_dir,
            )
        except AssertionError as exc:
            self.fail(f"raise inattendu sur zombie 'Non spécifié' : {exc}")

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


class GenreFrTest(unittest.TestCase):
    """S6 : table des noms français de genres pour rendre les zombies lisibles."""

    def test_known_genre_returns_french_name(self):
        self.assertEqual(genre_fr("Quercus"), "Chêne")
        self.assertEqual(genre_fr("Tilia"), "Tilleul")
        self.assertEqual(genre_fr("Aesculus"), "Marronnier")

    def test_unknown_genre_returns_none(self):
        self.assertIsNone(genre_fr("Genrefictif"))

    def test_strips_whitespace(self):
        self.assertEqual(genre_fr("  Quercus  "), "Chêne")

    def test_empty_returns_none(self):
        self.assertIsNone(genre_fr(""))

    def test_unknown_only_genres_are_mapped(self):
        # Les 3 genres only-unknown listés au S8 doivent avoir un nom FR pour
        # éviter d'afficher du latin sur leur seule fiche disponible.
        self.assertIsNotNone(genre_fr("Genista"))
        self.assertIsNotNone(genre_fr("Vitex"))
        self.assertIsNotNone(genre_fr("Ziziphus"))

    def test_table_has_top_paris_genres(self):
        # Sentinel : si la table régresse, les espèces les plus visibles
        # repassent en latin nu en UI.
        for g in ("Platanus", "Aesculus", "Tilia", "Acer", "Quercus", "Fraxinus"):
            self.assertIn(g, GENRE_FR, f"{g} doit rester dans GENRE_FR")


class PickVernacularFromRedirectsTest(unittest.TestCase):
    """S6 : sélection d'un nom français parmi les redirections Wikipédia FR."""

    def test_picks_french_name_over_binomial(self):
        # `Quercus robur` : Wikipédia FR titre l'article scientifiquement,
        # plusieurs noms communs y redirigent → on prend le plus court (le
        # binôme `Quercus pedunculata`, synonyme taxonomique, est exclu).
        result = pick_vernacular_from_redirects(
            ["Chêne pédonculé", "Chêne rouvre", "Quercus pedunculata"],
            genre="Quercus",
            espece="robur",
        )
        self.assertEqual(result, "Chêne rouvre")  # 12 chars < 15

    def test_alphabetical_tiebreak_on_equal_length(self):
        # À longueur égale, ordre alphabétique pour reproductibilité entre runs.
        result = pick_vernacular_from_redirects(
            ["Bouleau blanc", "Bouleau verge"],
            genre="Betula", espece="pendula",
        )
        self.assertEqual(result, "Bouleau blanc")

    def test_skips_binomial_redirect(self):
        # Synonyme taxonomique pur : binôme latin → ignoré.
        result = pick_vernacular_from_redirects(
            ["Quercus pedunculata"], genre="Quercus", espece="robur",
        )
        self.assertIsNone(result)

    def test_skips_genus_only(self):
        # Un seul mot capitalisé latin = nom de genre, pas un nom commun.
        result = pick_vernacular_from_redirects(
            ["Quercus"], genre="Quercus", espece="robur",
        )
        self.assertIsNone(result)

    def test_skips_disambiguation_pages(self):
        result = pick_vernacular_from_redirects(
            ["Chêne (homonymie)", "Chêne (genre)"],
            genre="Quercus", espece="robur",
        )
        self.assertIsNone(result)

    def test_skips_namespaced_pages(self):
        # Préfixes `Catégorie:`, `Discussion:` etc. sont des pages techniques.
        result = pick_vernacular_from_redirects(
            ["Catégorie:Chêne", "Discussion:Quercus robur"],
            genre="Quercus", espece="robur",
        )
        self.assertIsNone(result)

    def test_empty_returns_none(self):
        self.assertIsNone(pick_vernacular_from_redirects(
            [], genre="Quercus", espece="robur",
        ))

    def test_handles_empty_strings_in_input(self):
        result = pick_vernacular_from_redirects(
            ["", "  ", "Chêne pédonculé"],
            genre="Quercus", espece="robur",
        )
        self.assertEqual(result, "Chêne pédonculé")


class ComputeVernacularFrTitleFilterAndRedirectTest(unittest.TestCase):
    """S6 : la cascade rejette frTitle == binôme nu et tente les redirects."""

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.tmp_path = Path(self.tmp.name)
        self.cache_dir = self.tmp_path / ".wikidata-cache"
        self.cache_dir.mkdir()
        self.aliases_cache_dir = self.tmp_path / ".wikipedia-aliases-cache"
        self.species_index_out = self.tmp_path / "species-index.json"
        self.trace_out = self.tmp_path / "_trace" / "vernacular-source.json"

        self.patches = [
            mock.patch.object(build_dataset, "WIKIDATA_CACHE_DIR", self.cache_dir),
            mock.patch.object(build_dataset, "WIKI_ALIASES_CACHE_DIR", self.aliases_cache_dir),
            mock.patch.object(build_dataset, "OUT_SPECIES_INDEX", self.species_index_out),
            mock.patch.object(build_dataset, "OUT_VERNACULAR_TRACE", self.trace_out),
        ]
        for p in self.patches:
            p.start()

    def tearDown(self):
        for p in self.patches:
            p.stop()
        self.tmp.cleanup()

    def _write_cache(self, sk: int, content: dict) -> None:
        (self.cache_dir / f"{sk}.json").write_text(
            json.dumps(content), encoding="utf-8",
        )

    def test_frtitle_equal_to_binomial_falls_through_to_redirect(self):
        # Cas réel pré-S6 : Wikipédia FR titre `Aria edulis` scientifiquement,
        # le frTitle == binôme. La cascade doit rejeter et tenter les redirects.
        species_index = {("Aria", "edulis"): 0}
        self._write_cache(0, {
            "qid": "Q123",
            "wp": "Aria_edulis",
            "summary": "...",
            "vernacularNames": [],
        })

        with mock.patch.object(
            build_dataset, "fetch_redirect_vernacular",
            return_value=("Alisier blanc", ["Alisier blanc", "Sorbus aria"]),
        ) as mock_fetch:
            entries, counters, _construit = build_dataset.compute_vernacular_and_pokedex(
                species_index=species_index,
                nom_commun_by_sk={0: {"Alisier": 5}},
                count_by_sk={0: 30},
            )
        mock_fetch.assert_called_once()
        self.assertEqual(entries[0]["nv"], "Alisier blanc")
        self.assertEqual(counters["nv_via_frtitle"], 0)
        self.assertEqual(counters["nv_via_redirect"], 1)

    def test_frtitle_not_equal_to_binomial_is_used(self):
        # Cas où frTitle est un vrai nom français (cascade Sprint 2 inchangée).
        species_index = {("Tilia", "cordata"): 0}
        self._write_cache(0, {
            "qid": "Q156726",
            "wp": "Tilleul_à_petites_feuilles",
            "summary": "...",
            "vernacularNames": [],
        })

        entries, counters, _construit = build_dataset.compute_vernacular_and_pokedex(
            species_index=species_index,
            nom_commun_by_sk={0: {"Tilleul": 1}},
            count_by_sk={0: 100},
        )
        self.assertEqual(entries[0]["nv"], "Tilleul à petites feuilles")
        self.assertEqual(counters["nv_via_frtitle"], 1)
        self.assertEqual(counters["nv_via_redirect"], 0)

    def test_redirect_returns_none_falls_back_to_construct(self):
        # frTitle rejeté + aucun redirect valide → tombe sur construct (binôme).
        species_index = {("Aria", "edulis"): 0}
        self._write_cache(0, {
            "qid": "Q123",
            "wp": "Aria_edulis",
            "summary": "...",
            "vernacularNames": [],
        })

        with mock.patch.object(
            build_dataset, "fetch_redirect_vernacular",
            return_value=(None, []),
        ):
            entries, counters, _construit = build_dataset.compute_vernacular_and_pokedex(
                species_index=species_index,
                nom_commun_by_sk={},
                count_by_sk={0: 30},
            )
        self.assertEqual(entries[0]["nv"], "Aria edulis")
        self.assertEqual(counters["nv_via_redirect"], 0)
        # Aria edulis : pas de nc, fallback construit binôme nu.
        self.assertEqual(counters["nv_via_construit_binom"], 1)

    def test_unknown_species_traced_as_genre_fr(self):
        # Zombie `(Tilia, sp.)` : pas de wp/p1843 → tombe sur construct, mais
        # comme `genre_fr("Tilia")` existe, la trace dit `genre_fr` (et pas
        # `construct`) — utile pour distinguer dans le rapport HTML.
        species_index = {("Tilia", "sp."): 0}
        self._write_cache(0, {"miss": True})

        entries, counters, _construit = build_dataset.compute_vernacular_and_pokedex(
            species_index=species_index,
            nom_commun_by_sk={},
            count_by_sk={0: 5},
        )
        self.assertEqual(entries[0]["nv"], "Tilleul")
        self.assertTrue(entries[0].get("u"))
        self.assertEqual(counters["nv_via_genre_fr"], 1)
        self.assertEqual(counters["nv_via_construit_binom"], 0)

    def test_writes_trace_sidecar(self):
        species_index = {
            ("Quercus", "robur"): 0,
            ("Tilia", "sp."): 1,
        }
        self._write_cache(0, {
            "qid": "Q1", "wp": "Chêne_pédonculé", "vernacularNames": ["Chêne pédonculé"],
        })
        self._write_cache(1, {"miss": True})
        build_dataset.compute_vernacular_and_pokedex(
            species_index=species_index,
            nom_commun_by_sk={0: {"Chêne": 1}},
            count_by_sk={0: 1500, 1: 5},
        )
        trace = json.loads(self.trace_out.read_text(encoding="utf-8"))
        by_sk = {t["sk"]: t for t in trace}
        self.assertEqual(by_sk[0]["source"], "p1843")
        self.assertEqual(by_sk[1]["source"], "genre_fr")


class VerifyRedundantNvRaisesTest(unittest.TestCase):
    """S6 : invariant 5, raise sur `{g} {e} ({g} {e})` (typique post-désambiguation)."""

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.cache_dir = Path(self.tmp.name)

    def tearDown(self):
        self.tmp.cleanup()

    def test_redundant_nv_raises(self):
        entries = [
            {"i": 0, "g": "Aria", "e": "edulis", "nv": "Aria edulis (Aria edulis)"},
        ]
        with self.assertRaisesRegex(AssertionError, "redondant"):
            build_dataset.verify_species_invariants(
                pre_sk_set=frozenset({0}),
                pre_wp_present_by_sk={},
                entries=entries,
                count_by_sk={0: 30},
                non_specifie_count=0,
                construit_high_count=[],
                cache_dir=self.cache_dir,
            )

    def test_normal_disambiguation_does_not_raise(self):
        # Nom commun vrai + binôme suffix : pas une redondance.
        entries = [
            {"i": 0, "g": "Quercus", "e": "robur", "nv": "Chêne (Quercus robur)"},
            {"i": 1, "g": "Quercus", "e": "petraea", "nv": "Chêne (Quercus petraea)"},
        ]
        try:
            build_dataset.verify_species_invariants(
                pre_sk_set=frozenset({0, 1}),
                pre_wp_present_by_sk={},
                entries=entries,
                count_by_sk={0: 100, 1: 100},
                non_specifie_count=0,
                construit_high_count=[],
                cache_dir=self.cache_dir,
            )
        except AssertionError as exc:
            self.fail(f"raise inattendu sur disambiguation normale : {exc}")


class ExtractNvFromSummaryTest(unittest.TestCase):
    """S6 v2 : extraction du nom français à partir de l'incipit Wikipédia."""

    def test_with_article_le(self):
        s = "Le marronnier commun, marronnier d'Inde ou marronnier blanc (Aesculus hippocastanum L.) est un arbre à fleurs."
        self.assertEqual(
            extract_nv_from_summary(s, "Aesculus", "hippocastanum"),
            "Marronnier commun",
        )

    def test_with_article_apostrophe(self):
        s = "L'érable plane (Acer platanoides) est une espèce d'arbres caducifoliés."
        self.assertEqual(
            extract_nv_from_summary(s, "Acer", "platanoides"),
            "Érable plane",
        )

    def test_without_article(self):
        s = "Bouleau verruqueux (Betula pendula) est un arbre de la famille des Betulaceae."
        self.assertEqual(
            extract_nv_from_summary(s, "Betula", "pendula"),
            "Bouleau verruqueux",
        )

    def test_starts_with_binomial_returns_none(self):
        # Article qui démarre par le binôme : pas de nom français exploitable.
        s = "Aria edulis est une espèce d'arbres de la famille des Rosaceae."
        self.assertIsNone(
            extract_nv_from_summary(s, "Aria", "edulis"),
        )

    def test_too_long_returns_none(self):
        # Phrase qui ne contient pas de séparateur tôt → match énorme, rejet.
        s = "Voici une longue introduction sans virgule ni parenthèse précoce dépassant largement les cinquante caractères tolérés en sortie"
        self.assertIsNone(
            extract_nv_from_summary(s, "Genre", "espece"),
        )

    def test_empty_returns_none(self):
        self.assertIsNone(extract_nv_from_summary("", "Genre", "espece"))
        self.assertIsNone(extract_nv_from_summary(None, "Genre", "espece"))

    def test_collapses_whitespace(self):
        s = "Le  chêne   pédonculé  ,  Quercus robur, est un arbre."
        self.assertEqual(
            extract_nv_from_summary(s, "Quercus", "robur"),
            "Chêne pédonculé",
        )


class ComputeVernacularSummaryExtractionTest(unittest.TestCase):
    """S6 v2 : la cascade prend `summary_extract` quand frTitle/redirects échouent."""

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.tmp_path = Path(self.tmp.name)
        self.cache_dir = self.tmp_path / ".wikidata-cache"
        self.cache_dir.mkdir()
        self.aliases_cache_dir = self.tmp_path / ".wikipedia-aliases-cache"
        self.species_index_out = self.tmp_path / "species-index.json"
        self.trace_out = self.tmp_path / "_trace" / "vernacular-source.json"
        self.patches = [
            mock.patch.object(build_dataset, "WIKIDATA_CACHE_DIR", self.cache_dir),
            mock.patch.object(build_dataset, "WIKI_ALIASES_CACHE_DIR", self.aliases_cache_dir),
            mock.patch.object(build_dataset, "OUT_SPECIES_INDEX", self.species_index_out),
            mock.patch.object(build_dataset, "OUT_VERNACULAR_TRACE", self.trace_out),
        ]
        for p in self.patches:
            p.start()

    def tearDown(self):
        for p in self.patches:
            p.stop()
        self.tmp.cleanup()

    def _write_cache(self, sk: int, content: dict) -> None:
        (self.cache_dir / f"{sk}.json").write_text(
            json.dumps(content), encoding="utf-8",
        )

    def test_cascade_uses_summary_extract_when_other_steps_fail(self):
        species_index = {("Aria", "edulis"): 0}
        # frTitle == binôme → rejeté ; pas de p1843 ; redirects mockés vides ;
        # summary contient un nom français exploitable.
        self._write_cache(0, {
            "qid": "Q123",
            "wp": "Aria_edulis",
            "summary": "L'alisier blanc (Aria edulis) est un petit arbre de la famille des Rosaceae.",
            "vernacularNames": [],
        })
        with mock.patch.object(
            build_dataset, "fetch_redirect_vernacular",
            return_value=(None, []),
        ):
            entries, counters, _construit = build_dataset.compute_vernacular_and_pokedex(
                species_index=species_index,
                nom_commun_by_sk={},
                count_by_sk={0: 30},
            )
        self.assertEqual(entries[0]["nv"], "Alisier blanc")
        self.assertEqual(counters["nv_via_summary_extract"], 1)
        self.assertEqual(counters["nv_via_construit_binom"], 0)
        # Trace doit refléter la source choisie.
        trace = json.loads(self.trace_out.read_text(encoding="utf-8"))
        self.assertEqual(trace[0]["source"], "summary_extract")
        self.assertEqual(trace[0]["summary_match"], "Alisier blanc")

    def test_summary_extract_skipped_when_redirect_already_succeeded(self):
        species_index = {("Aria", "edulis"): 0}
        self._write_cache(0, {
            "qid": "Q123",
            "wp": "Aria_edulis",
            "summary": "L'autre nom (Aria edulis) est…",
            "vernacularNames": [],
        })
        with mock.patch.object(
            build_dataset, "fetch_redirect_vernacular",
            return_value=("Alisier blanc", ["Alisier blanc"]),
        ):
            entries, counters, _construit = build_dataset.compute_vernacular_and_pokedex(
                species_index=species_index,
                nom_commun_by_sk={},
                count_by_sk={0: 30},
            )
        self.assertEqual(entries[0]["nv"], "Alisier blanc")
        self.assertEqual(counters["nv_via_redirect"], 1)
        self.assertEqual(counters["nv_via_summary_extract"], 0)


class ConstructNcUniqueTest(unittest.TestCase):
    """S6 v2 : `construct` sub-source — nc unique → nv nu, nc partagé → parenthèse."""

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.tmp_path = Path(self.tmp.name)
        self.cache_dir = self.tmp_path / ".wikidata-cache"
        self.cache_dir.mkdir()
        self.aliases_cache_dir = self.tmp_path / ".wikipedia-aliases-cache"
        self.species_index_out = self.tmp_path / "species-index.json"
        self.trace_out = self.tmp_path / "_trace" / "vernacular-source.json"
        self.patches = [
            mock.patch.object(build_dataset, "WIKIDATA_CACHE_DIR", self.cache_dir),
            mock.patch.object(build_dataset, "WIKI_ALIASES_CACHE_DIR", self.aliases_cache_dir),
            mock.patch.object(build_dataset, "OUT_SPECIES_INDEX", self.species_index_out),
            mock.patch.object(build_dataset, "OUT_VERNACULAR_TRACE", self.trace_out),
        ]
        for p in self.patches:
            p.start()

    def tearDown(self):
        for p in self.patches:
            p.stop()
        self.tmp.cleanup()

    def _write_cache(self, sk: int, content: dict) -> None:
        (self.cache_dir / f"{sk}.json").write_text(
            json.dumps(content), encoding="utf-8",
        )

    def test_nc_unique_used_bare(self):
        # « Orme de Samarie » présent sur 1 seul sk → nv = "Orme de Samarie"
        # nu, sans la parenthèse `(P. trifoliata)`.
        species_index = {("Ptelea", "trifoliata"): 0}
        self._write_cache(0, {"miss": True})
        entries, counters, _ = build_dataset.compute_vernacular_and_pokedex(
            species_index=species_index,
            nom_commun_by_sk={0: {"Orme de Samarie": 5}},
            count_by_sk={0: 12},
        )
        self.assertEqual(entries[0]["nv"], "Orme de Samarie")
        self.assertEqual(counters["nv_via_construit_nc_unique"], 1)
        self.assertEqual(counters["nv_via_construit_nc_disamb"], 0)

    def test_nc_shared_falls_back_to_parenthesis(self):
        # 2 sks partagent « Érable » → la branche nc_disamb avec « Érable
        # (A. campestre) » et « Érable (A. monspessulanum) ».
        species_index = {
            ("Acer", "campestre"): 0,
            ("Acer", "monspessulanum"): 1,
        }
        self._write_cache(0, {"miss": True})
        self._write_cache(1, {"miss": True})
        entries, counters, _ = build_dataset.compute_vernacular_and_pokedex(
            species_index=species_index,
            nom_commun_by_sk={0: {"Érable": 5}, 1: {"Érable": 3}},
            count_by_sk={0: 100, 1: 50},
        )
        nvs = sorted(e["nv"] for e in entries)
        self.assertEqual(nvs, ["Érable (A. campestre)", "Érable (A. monspessulanum)"])
        self.assertEqual(counters["nv_via_construit_nc_disamb"], 2)
        self.assertEqual(counters["nv_via_construit_nc_unique"], 0)

    def test_no_nc_falls_back_to_binom(self):
        species_index = {("Pistacia", "palaestina"): 0}
        self._write_cache(0, {"miss": True})
        entries, counters, _ = build_dataset.compute_vernacular_and_pokedex(
            species_index=species_index,
            nom_commun_by_sk={},  # pas de nc
            count_by_sk={0: 5},
        )
        self.assertEqual(entries[0]["nv"], "Pistacia palaestina")
        self.assertEqual(counters["nv_via_construit_binom"], 1)


class DisambiguateVernacularsZombieFormatTest(unittest.TestCase):
    """S6 v2 : suffixe court `(Genre)` pour les zombies en collision."""

    def test_one_identified_one_zombie_keeps_identified_pure(self):
        entries = [
            {"i": 0, "g": "Prunus", "e": "avium", "nv": "Prunier"},
            {"i": 1, "g": "Prunus", "e": "sp.", "nv": "Prunier", "u": True},
        ]
        changed = disambiguate_vernaculars(entries)
        self.assertEqual(changed, 1)
        by_sk = {e["i"]: e for e in entries}
        self.assertEqual(by_sk[0]["nv"], "Prunier")  # identifié garde
        self.assertEqual(by_sk[1]["nv"], "Prunier (Prunus)")  # zombie suffixé court

    def test_two_zombies_both_suffixed_short(self):
        entries = [
            {"i": 0, "g": "Sophora", "e": "sp.", "nv": "Sophora", "u": True},
            {"i": 1, "g": "Styphnolobium", "e": "sp.", "nv": "Sophora", "u": True},
        ]
        changed = disambiguate_vernaculars(entries)
        self.assertEqual(changed, 2)
        nvs = sorted(e["nv"] for e in entries)
        self.assertEqual(nvs, ["Sophora (Sophora)", "Sophora (Styphnolobium)"])

    def test_two_identifieds_keep_long_suffix(self):
        # Comportement historique inchangé : 2 identifiées suffixées par binôme.
        entries = [
            {"i": 0, "g": "Quercus", "e": "robur", "nv": "Chêne"},
            {"i": 1, "g": "Quercus", "e": "petraea", "nv": "Chêne"},
        ]
        changed = disambiguate_vernaculars(entries)
        self.assertEqual(changed, 2)
        nvs = sorted(e["nv"] for e in entries)
        self.assertEqual(nvs, ["Chêne (Quercus petraea)", "Chêne (Quercus robur)"])

    def test_no_collision_no_change(self):
        entries = [
            {"i": 0, "g": "Quercus", "e": "robur", "nv": "Chêne pédonculé"},
            {"i": 1, "g": "Tilia", "e": "sp.", "nv": "Tilleul", "u": True},
        ]
        changed = disambiguate_vernaculars(entries)
        self.assertEqual(changed, 0)

    def test_two_zombies_same_genre_use_binom_suffix(self):
        # Cas réel : `(Malus, sp.)` + `(Malus, n. sp.)` historique tous deux
        # zombies → le suffixe court `(Malus)` produirait une collision.
        # On retombe sur le binôme complet pour assurer l'unicité.
        entries = [
            {"i": 0, "g": "Malus", "e": "sp.", "nv": "Pommier", "u": True},
            {"i": 1, "g": "Malus", "e": "n. sp.", "nv": "Pommier", "u": True},
        ]
        changed = disambiguate_vernaculars(entries)
        self.assertEqual(changed, 2)
        nvs = sorted(e["nv"] for e in entries)
        self.assertEqual(nvs, ["Pommier (Malus n. sp.)", "Pommier (Malus sp.)"])


if __name__ == "__main__":
    unittest.main()
