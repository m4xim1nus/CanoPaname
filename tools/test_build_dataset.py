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
from io import BytesIO
from pathlib import Path
from types import SimpleNamespace
from unittest import mock

import build_dataset
import essence_pdf
from essence_pdf import (
    _PH_PLACEHOLDER_DHASH_MAX_DIST,
    _PH_PLACEHOLDER_DHASHES,
    _bits_from_flags,
    _chains,
    _clean,
    _dhash,
    _extract_bullets,
    _group_lines_x1,
    _hamming,
    _is_placeholder_dhash,
    _join_paragraph_lines,
    _validate_bullets,
    select_photos,
)
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
from build_dataset import _photo_manifest_entries, _render_credits_md
from essence_pdf import encode_raw_to_webp
from species_photos_cascade import (
    FallbackPhoto,
    _find_inat_taxon_id,
    build_fallback_manifest_entry,
    clean_artist_html,
    commons_license_key,
    filename_from_p18_value,
    inat_license_key,
    parse_commons_imageinfo,
    pick_inat_photo,
    taxon_name_matches,
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
        # `fleur n. sp.` / `fruit n. sp.` : marqueurs OpenData des cultivars
        # Prunus génériques (décoratifs / fruitiers), ajoutés au cycle Catalogue
        # pour les rabattre sur l'entrée `Prunus sp.` plutôt que de polluer le
        # catalogue identifié.
        self.assertEqual(
            UNKNOWN_ESPECE_FORMS,
            frozenset({"sp.", "n. sp.", "fleur n. sp.", "fruit n. sp."}),
        )


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

        # n attribué à toutes (toutes identifiées, count > 0), numérotées par
        # count décroissant (puis sk croissant) : sk1 (4000) → 1, sk0 (1500) → 2,
        # sk3 (10) → 3, sk2 (5) → 4.
        self.assertEqual(by_sk[1]["n"], 1)
        self.assertEqual(by_sk[0]["n"], 2)
        self.assertEqual(by_sk[3]["n"], 3)
        self.assertEqual(by_sk[2]["n"], 4)
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


class SplashTipsTest(unittest.TestCase):
    """Invariants sur la banque de tips : source statique + payload généré."""

    PLACEHOLDERS = {"speciesCount", "remarquableCount", "daysSinceFirst"}

    def test_supported_placeholders_in_sync(self):
        self.assertEqual(build_dataset.SUPPORTED_PLACEHOLDERS, self.PLACEHOLDERS)

    def test_static_intro_is_ten_without_requires(self):
        with build_dataset.STATIC_SPLASH_TIPS.open(encoding="utf-8") as f:
            static = json.load(f)
        intro = static["intro"]
        self.assertEqual(len(intro), 10)
        for t in intro:
            self.assertFalse(t.get("requires"), t["id"])
        # Placeholders connus uniquement, partout dans le statique.
        for t in intro + static["tips"]:
            for ph in build_dataset.PLACEHOLDER_RE.findall(t.get("text", "")):
                self.assertIn(ph, self.PLACEHOLDERS, (t["id"], ph))
            for req in t.get("requires", []):
                self.assertIn(req, self.PLACEHOLDERS, (t["id"], req))

    def test_static_ids_unique(self):
        with build_dataset.STATIC_SPLASH_TIPS.open(encoding="utf-8") as f:
            static = json.load(f)
        ids = [t["id"] for t in static["intro"]] + [t["id"] for t in static["tips"]]
        self.assertEqual(len(ids), len(set(ids)), "id en doublon dans le statique")

    def test_generated_payload_invariants(self):
        out = build_dataset.OUT_SPLASH_TIPS
        if not out.exists():
            self.skipTest("splash-tips.json non généré (lancer build_dataset.py)")
        with out.open(encoding="utf-8") as f:
            payload = json.load(f)
        tips_by_id = {t["id"]: t for t in payload["tips"]}
        # ids uniques
        self.assertEqual(len(payload["tips"]), len(tips_by_id))
        # intro : 10 ids, tous présents dans tips, aucun avec requires
        self.assertEqual(len(payload["intro"]), 10)
        for tid in payload["intro"]:
            self.assertIn(tid, tips_by_id, tid)
            self.assertFalse(tips_by_id[tid].get("requires"), tid)
        # placeholders : uniquement ceux supportés (dataset déjà résolus côté Python)
        for t in payload["tips"]:
            for ph in build_dataset.PLACEHOLDER_RE.findall(t["text"]):
                self.assertIn(ph, self.PLACEHOLDERS, (t["id"], ph))
            for req in t.get("requires", []):
                self.assertIn(req, self.PLACEHOLDERS, (t["id"], req))


class BitsFromFlagsTest(unittest.TestCase):
    """Contrat bitfield : bit 0 = janvier … bit 11 = décembre."""

    def test_empty_is_zero(self):
        self.assertEqual(_bits_from_flags([]), 0)

    def test_all_false_is_zero(self):
        self.assertEqual(_bits_from_flags([False] * 12), 0)

    def test_january_only(self):
        flags = [True] + [False] * 11
        self.assertEqual(_bits_from_flags(flags), 1)

    def test_december_only(self):
        flags = [False] * 11 + [True]
        self.assertEqual(_bits_from_flags(flags), 2048)

    def test_all_twelve(self):
        self.assertEqual(_bits_from_flags([True] * 12), 4095)


class ExtractBulletsTest(unittest.TestCase):
    """Regroupement pur des puces (x0, y0, texte) → liste de puces."""

    def test_single_bullet(self):
        items = [(10.0, 100.0, "•"), (20.0, 100.0, "Résistant à la sécheresse")]
        self.assertEqual(
            _extract_bullets(items), ["Résistant à la sécheresse"]
        )

    def test_continuation_multiline(self):
        # 2e ligne (y ~110) sans puce = continuation rattachée à la 1re.
        items = [
            (10.0, 100.0, "•"),
            (20.6, 100.6, "Bonnes capacités de régulation"),
            (20.0, 110.0, "du climat local"),
        ]
        self.assertEqual(
            _extract_bullets(items),
            ["Bonnes capacités de régulation du climat local"],
        )

    def test_two_bullets(self):
        items = [
            (10.0, 100.0, "•"),
            (20.0, 100.0, "Première puce"),
            (10.0, 120.0, "•"),
            (20.0, 120.0, "Deuxième puce"),
        ]
        self.assertEqual(
            _extract_bullets(items), ["Première puce", "Deuxième puce"]
        )


class ValidateBulletsTest(unittest.TestCase):
    """Bornes de validation : 1-8 puces, chacune 3-300 caractères."""

    def test_valid_passes(self):
        bullets = ["Puce une", "Puce deux"]
        self.assertEqual(_validate_bullets(bullets), (bullets, None))

    def test_empty_rejected(self):
        result, warn = _validate_bullets([])
        self.assertEqual(result, [])
        self.assertIsNotNone(warn)

    def test_too_many_rejected(self):
        result, warn = _validate_bullets([f"puce {i}" for i in range(9)])
        self.assertEqual(result, [])
        self.assertIsNotNone(warn)

    def test_seven_bullets_pass(self):
        # Fraxinus excelsior a 7 limites réelles — ne doit pas être rejeté.
        bullets = [f"limite numéro {i}" for i in range(7)]
        self.assertEqual(_validate_bullets(bullets), (bullets, None))

    def test_bullet_too_short_rejected(self):
        result, warn = _validate_bullets(["ok longueur", "ab"])
        self.assertEqual(result, [])
        self.assertIsNotNone(warn)

    def test_bullet_too_long_rejected(self):
        result, warn = _validate_bullets(["x" * 301])
        self.assertEqual(result, [])
        self.assertIsNotNone(warn)


class CleanOrdinalsTest(unittest.TestCase):
    """Ordinaux en exposant : l'exposant est un span PDF séparé, le join insère
    un espace parasite. `_clean` recolle « N e/er/ère/re » (borne de mot en fin)."""

    def test_ordinal_siecle(self):
        self.assertEqual(_clean("Dès le 12 e siècle"), "Dès le 12e siècle")

    def test_ordinal_arrondissement(self):
        # « (14 e) » (arrondissement) → « (14e) ». _clean recolle aussi les
        # parenthèses ; l'ordinal doit passer malgré la « ) » qui suit.
        self.assertEqual(_clean("au Parc Montsouris (14 e)"),
                         "au Parc Montsouris (14e)")

    def test_ordinal_premier(self):
        self.assertEqual(_clean("le 1 er arbre"), "le 1er arbre")

    def test_no_recollage_euros(self):
        # « 3 euros » ne doit PAS devenir « 3euros » (pas de borne après « e »).
        self.assertEqual(_clean("coûte 3 euros"), "coûte 3 euros")

    def test_no_recollage_especes(self):
        self.assertEqual(_clean("plus de 5 espèces"), "plus de 5 espèces")


class GroupLinesX1Test(unittest.TestCase):
    """Regroupement en lignes avec bord droit (x1 max) pour la détection de fin
    de paragraphe."""

    def test_single_line_two_spans(self):
        items = [(10.0, 100.0, 50.0, "Bonjour"),
                 (55.0, 100.5, 120.0, "monde")]
        self.assertEqual(_group_lines_x1(items), [("Bonjour monde", 120.0)])

    def test_two_lines_keep_max_x1(self):
        items = [(10.0, 100.0, 300.0, "premiere ligne"),
                 (10.0, 112.0, 140.0, "fin")]
        self.assertEqual(
            _group_lines_x1(items),
            [("premiere ligne", 300.0), ("fin", 140.0)],
        )


class JoinParagraphLinesTest(unittest.TestCase):
    """Recollage géométrique : point inséré UNIQUEMENT aux fins de paragraphe
    (ligne précédente en retrait sous la marge justifiée), jamais aux césures
    intra-phrase."""

    def test_point_at_paragraph_boundary(self):
        # Ligne 0 wrappée (touche la marge 300), ligne 1 courte = fin de para →
        # point avant la ligne 2 qui démarre en majuscule.
        lines = [("premier paragraphe wrappe", 300.0),
                 ("fin du premier", 120.0),
                 ("Second paragraphe ici", 300.0)]
        self.assertEqual(
            _join_paragraph_lines(lines),
            "premier paragraphe wrappe fin du premier. Second paragraphe ici",
        )

    def test_no_point_on_intra_phrase_cesure(self):
        # Ligne 0 touche la marge (300) = wrappée intra-phrase : pas de point
        # même si la ligne 1 démarre par un nom propre en majuscule.
        lines = [("il passe par la place de la", 300.0),
                 ("Concorde puis rentre", 250.0)]
        self.assertEqual(
            _join_paragraph_lines(lines),
            "il passe par la place de la Concorde puis rentre",
        )

    def test_no_double_point_when_already_punctuated(self):
        # Fin de paragraphe déjà ponctuée → simple espace, pas de point ajouté.
        lines = [("Fin de la phrase.", 120.0),
                 ("Nouvelle phrase ici", 300.0)]
        self.assertEqual(
            _join_paragraph_lines(lines),
            "Fin de la phrase. Nouvelle phrase ici",
        )

    def test_no_point_when_next_lowercase(self):
        # Fin de paragraphe non ponctuée mais suite en minuscule → pas de point.
        lines = [("premier bloc de texte", 300.0),
                 ("fin courte", 120.0),
                 ("suite en minuscule", 300.0)]
        self.assertEqual(
            _join_paragraph_lines(lines),
            "premier bloc de texte fin courte suite en minuscule",
        )

    def test_ginkgo_runon_full_pipeline(self):
        # Cas Ginkgo réel : deux paragraphes fusionnés sans point, réparés par
        # _join_paragraph_lines puis nettoyés par _clean (recollage ponctuation).
        lines = [("plantés isolés dans les", 300.0),
                 ("parcs", 90.0),
                 ("Essence rare à Paris, elle", 300.0),
                 ("est utilisée en alignement.", 200.0)]
        self.assertEqual(
            _clean(_join_paragraph_lines(lines)),
            "plantés isolés dans les parcs. Essence rare à Paris, "
            "elle est utilisée en alignement.",
        )


def _img(xref, bbox, width, height, nbytes, colvar=1000.0):
    """Fabrique une entrée d'inventaire synthétique (dims/octets NATIFS, bbox pt).

    Miroir du format produit par `_page0_inventory` : bbox en points PDF,
    `width`/`height` en pixels natifs, `nbytes` en octets bruts de l'image,
    `colvar` = variance couleur (garde anti-aplat ; défaut 1000 = vraie photo,
    bien au-dessus de `_PH_MIN_COLVAR` = 30).
    """
    return {
        "xref": xref,
        "bbox": bbox,
        "width": width,
        "height": height,
        "nbytes": nbytes,
        "colvar": colvar,
    }


# Page A4 de référence : 595 pt de large (seuil pleine largeur = 0.9 * 595 =
# 535.5 pt). La colonne droite d'une fiche essence vit vers x ≈ 340-460 pt.
_PAGE_W = 595.0


class SelectPhotosTest(unittest.TestCase):
    """Sélection PURE principale/détails depuis un inventaire page 0 synthétique.

    Géométries inspirées du corpus réel : principale ~464×620 px colonne droite,
    bandeaux/fonds pleine largeur, logos d'en-tête/pied, fragments recollables.
    Aucun fitz/Pillow — dicts natifs uniquement.
    """

    def test_principale_par_aire_bbox(self):
        # Bandeau pleine largeur + logo en-tête + logo pied + grande image
        # colonne droite → principale = grande image (seule candidate), 0 détail.
        inventory = [
            # Bandeau décoratif pleine largeur (555 pt >= 535.5) → exclu.
            _img(1, (20.0, 30.0, 575.0, 120.0), 1000, 160, 90_000),
            # Logo d'en-tête (y0 < 70) → exclu.
            _img(2, (30.0, 20.0, 90.0, 60.0), 200, 200, 4_000),
            # Logo de pied (y1 > 745) → exclu.
            _img(3, (30.0, 750.0, 90.0, 790.0), 200, 200, 3_000),
            # Grande image colonne droite → principale.
            _img(4, (340.0, 200.0, 460.0, 360.0), 464, 620, 90_000),
        ]
        principal, details = select_photos(inventory, _PAGE_W)
        self.assertIsNotNone(principal)
        self.assertEqual(principal["role"], "principal")
        self.assertEqual(principal["xrefs"], (4,))
        self.assertEqual(details, [])

    def test_principale_gros_plan_vs_portrait(self):
        # Scénario MEDIUM : un gros plan écorce/feuille très lourd en octets mais
        # de petite bbox, face à un portrait d'arbre de grande bbox mais plus
        # léger. La principale doit être le PORTRAIT (aire de bbox), pas le gros
        # plan — sinon la fiche affiche une texture au lieu de la silhouette.
        inventory = [
            # Gros plan (octets max, petite bbox 150×100 pt = 15 000 pt²,
            # downscale 900/150 = 6 → passe le filtre bande déco).
            _img(90, (380.0, 300.0, 530.0, 400.0), 900, 1000, 200_000),
            # Portrait d'arbre (grande bbox 120×300 pt = 36 000 pt², plus léger).
            _img(91, (340.0, 200.0, 460.0, 500.0), 464, 1100, 120_000),
        ]
        principal, details = select_photos(inventory, _PAGE_W)
        self.assertEqual(principal["xrefs"], (91,))       # portrait, pas gros plan
        self.assertEqual([d["xrefs"] for d in details], [(90,)])

    def test_aplat_decoratif_ne_rafle_pas_la_principale(self):
        # Cas Cedrus atlantica (sk 99) : un panneau bleu uni (grande bbox, aire
        # native énorme, mais variance couleur ~0) recouvre le vrai portrait,
        # de bbox quasi identique. Sans garde, l'aplat rafle la principale par
        # aire de bbox. Le garde anti-aplat (colvar < _PH_MIN_COLVAR) l'écarte
        # → principale = portrait, aplat ABSENT partout (ni principale, ni détail).
        inventory = [
            # Aplat décoratif bleu uni : bbox (légèrement) la plus grande.
            _img(41, (385.0, 115.0, 555.6, 342.5), 711, 948, 19_780, colvar=0.6),
            # Vrai portrait du cèdre, même colonne, bbox quasi identique.
            _img(42, (385.1, 115.3, 555.4, 342.4), 464, 619, 66_231, colvar=1220.0),
            # Petit détail (feuillage), colonne gauche.
            _img(43, (289.3, 219.1, 381.6, 342.1), 234, 312, 12_879, colvar=4419.0),
        ]
        principal, details = select_photos(inventory, _PAGE_W)
        self.assertEqual(principal["xrefs"], (42,))       # portrait, pas l'aplat
        all_xrefs = {principal["xrefs"]} | {d["xrefs"] for d in details}
        self.assertNotIn((41,), all_xrefs)                # aplat nulle part
        self.assertEqual([d["xrefs"] for d in details], [(43,)])

    def test_aplat_decoratif_ne_devient_pas_detail(self):
        # Un aplat plus petit que la principale ne doit pas non plus fuir en
        # détail : le garde s'applique à TOUTE candidate, pas seulement la 1re.
        inventory = [
            _img(50, (340.0, 100.0, 460.0, 260.0), 464, 620, 90_000),  # principale
            _img(51, (480.0, 300.0, 540.0, 380.0), 700, 900, 18_000, colvar=1.0),
        ]
        principal, details = select_photos(inventory, _PAGE_W)
        self.assertEqual(principal["xrefs"], (50,))
        self.assertEqual(details, [])  # l'aplat 51 écarté, pas relégué en détail

    def test_tie_break_octets_a_aire_egale(self):
        # Deux candidates de MÊME aire de bbox : départage par octets natifs
        # décroissants (déterminisme, garde le fichier le mieux résolu).
        inventory = [
            _img(1, (340.0, 100.0, 460.0, 260.0), 464, 620, 60_000),
            _img(2, (100.0, 100.0, 220.0, 260.0), 464, 620, 80_000),
        ]
        principal, _details = select_photos(inventory, _PAGE_W)
        self.assertEqual(principal["xrefs"], (2,))  # même bbox, octets max

    def test_fond_pleine_page_exclu_ancien_template(self):
        # Fond pleine page (ancien template) : plus gros octets natifs de tous,
        # mais exclu par le filtre pleine largeur AVANT le choix par octets.
        inventory = [
            _img(1, (0.0, 0.0, 595.0, 842.0), 2480, 3508, 500_000),
            _img(2, (340.0, 200.0, 460.0, 360.0), 464, 620, 85_000),
        ]
        principal, details = select_photos(inventory, _PAGE_W)
        self.assertIsNotNone(principal)
        self.assertEqual(principal["xrefs"], (2,))  # pas le fond malgré ses octets
        self.assertEqual(details, [])

    def test_recollage_big_plus_bande_fine(self):
        # « big + bande fine » même largeur contiguë → 1 groupe : hauteur sommée,
        # octets sommés, xrefs ordonnés haut→bas.
        inventory = [
            _img(10, (340.0, 100.0, 440.0, 300.0), 656, 752, 60_000),
            _img(11, (340.0, 300.0, 440.0, 312.0), 656, 47, 5_000),
        ]
        principal, details = select_photos(inventory, _PAGE_W)
        self.assertIsNotNone(principal)
        self.assertEqual(principal["xrefs"], (10, 11))
        self.assertEqual(principal["height"], 752 + 47)
        self.assertEqual(principal["nbytes"], 60_000 + 5_000)
        self.assertEqual(details, [])

    def test_recollage_quatre_tranches_egales(self):
        # 4 tranches horizontales égales même largeur → 1 groupe recollé.
        inventory = [
            _img(20, (340.0, 200.0, 420.0, 220.0), 322, 86, 15_000),
            _img(21, (340.0, 220.0, 420.0, 240.0), 322, 86, 15_000),
            _img(22, (340.0, 240.0, 420.0, 260.0), 322, 86, 15_000),
            _img(23, (340.0, 260.0, 420.0, 280.0), 322, 86, 15_000),
        ]
        principal, details = select_photos(inventory, _PAGE_W)
        self.assertIsNotNone(principal)
        self.assertEqual(principal["xrefs"], (20, 21, 22, 23))
        self.assertEqual(principal["height"], 86 * 4)
        self.assertEqual(details, [])

    def test_non_fusion_deux_details_portrait(self):
        # Cas Celtis : deux détails portrait distincts empilés même largeur
        # (aspect < 1.8, donc 2 « non-tranches ») → NON fusionnés, 2 groupes.
        inventory = [
            # Principale colonne droite (octets max).
            _img(30, (340.0, 100.0, 460.0, 260.0), 464, 620, 90_000),
            # Feuille (portrait) puis écorce (portrait), empilées, même largeur.
            _img(31, (480.0, 300.0, 540.0, 380.0), 300, 400, 30_000),
            _img(32, (480.0, 380.0, 540.0, 460.0), 300, 400, 28_000),
        ]
        principal, details = select_photos(inventory, _PAGE_W)
        self.assertEqual(principal["xrefs"], (30,))
        self.assertEqual(len(details), 2)  # non fusionnés
        self.assertEqual([d["xrefs"] for d in details], [(31,), (32,)])

    def test_rejet_bande_deco_downscale_haut(self):
        # Bande décorative : 2362 px natifs squeezés dans ~68 pt → downscale
        # 34.7 > 10 → rejetée (ne fuit pas en détail).
        inventory = [
            _img(40, (100.0, 400.0, 168.0, 420.0), 2362, 100, 40_000),
            _img(41, (340.0, 100.0, 460.0, 260.0), 464, 620, 80_000),
        ]
        principal, details = select_photos(inventory, _PAGE_W)
        self.assertEqual(principal["xrefs"], (41,))
        self.assertEqual(details, [])

    def test_rejet_vignette_etiree_downscale_bas(self):
        # Vignette 2×2 px étirée sur ~225 pt → downscale 0.009 < 1.0
        # (_PH_DS_MIN) → rejetée.
        inventory = [
            _img(50, (100.0, 300.0, 325.0, 400.0), 2, 2, 500),
            _img(51, (340.0, 100.0, 460.0, 260.0), 464, 620, 80_000),
        ]
        principal, details = select_photos(inventory, _PAGE_W)
        self.assertEqual(principal["xrefs"], (51,))
        self.assertEqual(details, [])

    def test_rejet_entete_et_pied(self):
        # Logo d'en-tête (y0 < _PH_Y_TOP = 25) et logo de pied (y1 > 745) rejetés :
        # sans ces filtres ils passeraient en détails.
        inventory = [
            _img(60, (30.0, 10.0, 130.0, 22.0), 200, 90, 4_000),   # y0 < 25
            _img(61, (30.0, 750.0, 130.0, 800.0), 200, 90, 3_000),  # y1 > 745
            _img(62, (340.0, 100.0, 460.0, 260.0), 464, 620, 70_000),
        ]
        principal, details = select_photos(inventory, _PAGE_W)
        self.assertEqual(principal["xrefs"], (62,))
        self.assertEqual(details, [])

    def test_portrait_haut_place_passe_logo_rejete(self):
        # Régression défaut 2 : _PH_Y_TOP abaissé de 70 à 25. Un vrai portrait
        # colonne-droite haut placé (y0=30, comme Platanus orientalis 30.1 /
        # occidentalis 32.9 / Prunus avium 60.4) doit PASSER — à 70 il était
        # rejeté et la fiche affichait un gros plan feuille/fleur à la place.
        # Un logo d'en-tête (y0=10) reste rejeté.
        inventory = [
            _img(1, (30.0, 10.0, 90.0, 22.0), 200, 231, 4_000),      # logo, y0<25
            _img(2, (340.0, 30.0, 460.0, 190.0), 464, 620, 90_000),  # portrait, y0=30
        ]
        principal, details = select_photos(inventory, _PAGE_W)
        self.assertIsNotNone(principal)
        self.assertEqual(principal["xrefs"], (2,))  # portrait retenu
        self.assertEqual(details, [])               # logo écarté (ni principale ni détail)

    def test_bande_haute_reservee_a_la_principale(self):
        # Cas Populus tremula 'Erecta' : la bande 25-70 pt est admise pour la
        # principale (portraits haut placés) mais PAS pour les détails — un
        # schéma de « port » stylisé y0=46 (colvar élevée, grande bbox mais
        # non-principale) ne doit pas fuiter comme faux détail (_PH_Y_TOP_DETAIL).
        inventory = [
            _img(1, (340.0, 100.0, 460.0, 260.0), 464, 620, 90_000),  # portrait
            _img(2, (250.0, 46.0, 330.0, 140.0), 320, 380, 30_000),   # schéma port, y0=46
            _img(3, (480.0, 300.0, 540.0, 380.0), 300, 200, 20_000),  # vrai détail
        ]
        principal, details = select_photos(inventory, _PAGE_W)
        self.assertEqual(principal["xrefs"], (1,))
        self.assertEqual([d["xrefs"] for d in details], [(3,)])  # schéma exclu

    def test_details_tronques_a_trois_ordonnes(self):
        # 5 détails candidats → tronqués à 3, ordonnés par y0 croissant.
        inventory = [
            _img(70, (340.0, 80.0, 460.0, 240.0), 464, 620, 100_000),  # principale
            _img(71, (480.0, 500.0, 540.0, 580.0), 300, 200, 20_000),
            _img(72, (480.0, 300.0, 540.0, 380.0), 300, 200, 20_000),
            _img(73, (480.0, 400.0, 540.0, 480.0), 300, 200, 20_000),
            _img(74, (480.0, 600.0, 540.0, 680.0), 300, 200, 20_000),
            _img(75, (480.0, 200.0, 540.0, 280.0), 300, 200, 20_000),
        ]
        principal, details = select_photos(inventory, _PAGE_W)
        self.assertEqual(principal["xrefs"], (70,))
        self.assertEqual(len(details), 3)
        # Tri par y0 : 75 (200), 72 (300), 73 (400) — les deux plus bas coupés.
        self.assertEqual([d["xrefs"][0] for d in details], [75, 72, 73])
        for d in details:
            self.assertEqual(d["role"], "detail")

    def test_aucune_candidate_assez_grande(self):
        # Aucune image d'aire native >= 70 000 px → (None, []).
        inventory = [
            _img(80, (340.0, 100.0, 460.0, 200.0), 200, 200, 30_000),  # 40 000 px
            _img(81, (340.0, 300.0, 460.0, 400.0), 250, 250, 35_000),  # 62 500 px
        ]
        self.assertEqual(select_photos(inventory, _PAGE_W), (None, []))


class ChainsTest(unittest.TestCase):
    """Bornes du chaînage vertical `_chains` (empilement de fragments, S9).

    `_chains(a, b)` : `b` (juste sous `a`) empile-t-il ? Même largeur native,
    bords x alignés (< `_PH_XEPS`), écart vertical bord-à-bord ∈ [-2, `_PH_GAP`].
    """

    @staticmethod
    def _seg(x0, y0, x1, y1, width=656):
        # Fragment horizontal : bbox en points, `width` natif (aligné pour chaîner).
        return {"xref": 1, "bbox": (x0, y0, x1, y1), "width": width,
                "height": 40, "nbytes": 5_000}

    def test_chaine_contigu(self):
        # Contigus (gap = 0) → chaînent.
        a = self._seg(340.0, 100.0, 440.0, 300.0)
        b = self._seg(340.0, 300.0, 440.0, 312.0)
        self.assertTrue(_chains(a, b))

    def test_gap_dans_borne(self):
        # Gap de 10 pt (< _PH_GAP = 12) → chaînent.
        a = self._seg(340.0, 100.0, 440.0, 300.0)
        b = self._seg(340.0, 310.0, 440.0, 322.0)
        self.assertTrue(_chains(a, b))

    def test_gap_trop_grand_ne_chaine_pas(self):
        # Gap vertical de 20 pt (> _PH_GAP = 12) → NE chaîne PAS.
        a = self._seg(340.0, 100.0, 440.0, 300.0)
        b = self._seg(340.0, 320.0, 440.0, 340.0)
        self.assertFalse(_chains(a, b))

    def test_chevauchement_trop_fort_ne_chaine_pas(self):
        # Chevauchement de 5 pt (gap = -5 < -2) → NE chaîne PAS (images distinctes
        # qui se recouvrent, pas des tranches contiguës).
        a = self._seg(340.0, 100.0, 440.0, 300.0)
        b = self._seg(340.0, 295.0, 440.0, 315.0)
        self.assertFalse(_chains(a, b))

    def test_largeur_differente_ne_chaine_pas(self):
        a = self._seg(340.0, 100.0, 440.0, 300.0, width=656)
        b = self._seg(340.0, 300.0, 440.0, 312.0, width=322)
        self.assertFalse(_chains(a, b))

    def test_x_desaligne_ne_chaine_pas(self):
        # Bords x décalés de 5 pt (>= _PH_XEPS = 2.5) → NE chaîne PAS.
        a = self._seg(340.0, 100.0, 440.0, 300.0)
        b = self._seg(345.0, 300.0, 445.0, 312.0)
        self.assertFalse(_chains(a, b))


class DhashTest(unittest.TestCase):
    """dHash perceptuel 8×8 + distance de Hamming — fonctions PURES (S9).

    Testées sur des matrices de niveaux de gris synthétiques (72 = 8×9 valeurs),
    sans Pillow : `_dhash` compare des pixels horizontalement adjacents
    (gauche > droite → 1), `_hamming` compte les bits différents.
    """

    def test_hamming_valeurs_connues(self):
        self.assertEqual(_hamming(0, 0), 0)
        self.assertEqual(_hamming(0b1011, 0b1110), 2)
        self.assertEqual(_hamming(0, (1 << 64) - 1), 64)

    def test_dhash_uniforme_est_zero(self):
        # Image uniforme : aucun pixel n'est > son voisin → 64 bits à 0.
        gray = [128] * (8 * 9)
        self.assertEqual(_dhash(gray), 0)

    def test_dhash_gradient_croissant_est_zero(self):
        # Rangées strictement croissantes (gauche < droite partout) → tous les
        # bits à 0 (le bit vaut 1 seulement si gauche > droite).
        gray = [c for _row in range(8) for c in range(9)]
        self.assertEqual(_dhash(gray), 0)

    def test_dhash_gradient_decroissant_est_tout_a_un(self):
        # Rangées strictement décroissantes (gauche > droite partout) → 64 bits à 1.
        gray = [8 - c for _row in range(8) for c in range(9)]
        self.assertEqual(_dhash(gray), (1 << 64) - 1)

    def test_dhash_distingue_bruit(self):
        # Deux motifs différents produisent des hashes différents (non triviaux).
        uniforme = _dhash([100] * (8 * 9))
        alterne = _dhash([(200 if c % 2 else 50) for _r in range(8) for c in range(9)])
        self.assertNotEqual(uniforme, alterne)


class PlaceholderDhashTest(unittest.TestCase):
    """Contrat du filtre placeholder « Photos à venir » (S9, dHash perceptuel).

    Le filtrage lui-même vit dans `_page0_inventory` (couche IMPURE : il faut les
    octets natifs de `extract_image` pour décoder et hasher l'image). La logique
    de décision `_is_placeholder_dhash`, elle, est PURE et testable directement :
    une référence exacte est un placeholder, une image quelconque (Hamming >> 8)
    ne l'est pas, `None` (décodage raté) non plus.
    """

    def test_deux_references_grandes_connues(self):
        # Les 2 variantes « grande » 1182×1004 mesurées au build.
        self.assertIn(544520902464865219, _PH_PLACEHOLDER_DHASHES)
        self.assertIn(544520902397756359, _PH_PLACEHOLDER_DHASHES)
        self.assertEqual(len(_PH_PLACEHOLDER_DHASHES), 2)

    def test_reference_exacte_est_placeholder(self):
        for ref in _PH_PLACEHOLDER_DHASHES:
            self.assertTrue(_is_placeholder_dhash(ref))

    def test_proche_sous_seuil_est_placeholder(self):
        # Une variante ré-encodée à Hamming = seuil est encore rattrapée.
        ref = _PH_PLACEHOLDER_DHASHES[0]
        near = ref ^ ((1 << _PH_PLACEHOLDER_DHASH_MAX_DIST) - 1)  # exactement seuil bits
        self.assertEqual(_hamming(ref, near), _PH_PLACEHOLDER_DHASH_MAX_DIST)
        self.assertTrue(_is_placeholder_dhash(near))

    def test_au_dela_du_seuil_nest_pas_placeholder(self):
        # Une vraie photo (Hamming 21 sur le corpus, ici seuil+1) n'est pas rejetée.
        ref = _PH_PLACEHOLDER_DHASHES[0]
        far = ref ^ ((1 << (_PH_PLACEHOLDER_DHASH_MAX_DIST + 1)) - 1)
        self.assertEqual(_hamming(ref, far), _PH_PLACEHOLDER_DHASH_MAX_DIST + 1)
        self.assertFalse(_is_placeholder_dhash(far))

    def test_none_nest_pas_placeholder(self):
        # Décodage raté → jamais écarté (« ne jamais inventer »).
        self.assertFalse(_is_placeholder_dhash(None))


class PhotoManifestEntriesTest(unittest.TestCase):
    """Construction PURE des entrées manifest `{f,r,src,lic,by,u}` (S9)."""

    @staticmethod
    def _photos(*roles):
        return [SimpleNamespace(role=r) for r in roles]

    def test_principale_puis_details(self):
        entries = _photo_manifest_entries(
            42, self._photos("principal", "detail", "detail"),
            "https://opendata.paris.fr/fiche.pdf")
        self.assertEqual(entries, [
            {"f": "42-0.webp", "r": "p", "src": "paris", "lic": "odbl-1.0",
             "by": "Ville de Paris", "u": "https://opendata.paris.fr/fiche.pdf"},
            {"f": "42-1.webp", "r": "d", "src": "paris", "lic": "odbl-1.0",
             "by": "Ville de Paris", "u": "https://opendata.paris.fr/fiche.pdf"},
            {"f": "42-2.webp", "r": "d", "src": "paris", "lic": "odbl-1.0",
             "by": "Ville de Paris", "u": "https://opendata.paris.fr/fiche.pdf"},
        ])

    def test_nommage_indexe_par_sk(self):
        entries = _photo_manifest_entries(
            7, self._photos("principal", "detail"), "https://x/f.pdf")
        self.assertEqual([e["f"] for e in entries], ["7-0.webp", "7-1.webp"])
        # n=0 = principale ("p"), la suite = détails ("d").
        self.assertEqual([e["r"] for e in entries], ["p", "d"])

    def test_principale_seule(self):
        entries = _photo_manifest_entries(
            100, self._photos("principal"), "https://x/f.pdf")
        self.assertEqual(len(entries), 1)
        self.assertEqual(entries[0]["f"], "100-0.webp")
        self.assertEqual(entries[0]["r"], "p")

    def test_url_propagee(self):
        url = "https://opendata.paris.fr/essences/tilia.pdf"
        entries = _photo_manifest_entries(3, self._photos("principal"), url)
        self.assertTrue(all(e["u"] == url for e in entries))


class TestEssenceOverrides(unittest.TestCase):
    """Fusion des overrides manuels (fiches à PDF rasterisé) — 100 % pur."""

    def _rasterized(self):
        """EssenceExtras d'une fiche rasterisée : flor/fruct OK, texte None."""
        return essence_pdf.EssenceExtras(
            flor=8, fruct=48,
            warnings=[
                "à retenir: ancres Atouts/Limites introuvables",
                "descriptif: PDF rasterisé (heading absent), "
                "champs textuels non extraits",
            ],
        )

    def _override(self):
        return {
            "nom_latin": "Betula nigra",
            "fam": "Bétulacées",
            "haut": "20 m",
            "env": "15 m",
            "croiss": "Rapide",
            "long": "Moyenne (100 à 200 ans)",
            "iddesc": {
                "ecorce": "Écorce brun-rouge en plaques",
                "feuillage": "Feuilles caduques lobées",
                "floraison": "Chatons jaune clair",
                "fructification": "Cône",
            },
            "paris": "Très rare à Paris.",
            "svc": {"climat": "Ombrage moyen.", "eau": "Bonne.", "biodiv": "Insectes."},
            "atouts": ["Régule l'eau", "Résiste au froid"],
            "limites": ["Sensible à la sécheresse"],
        }

    def test_remplit_les_champs_none(self):
        extras = self._rasterized()
        self.assertTrue(essence_pdf.merge_override(extras, self._override()))
        self.assertEqual(extras.fam, "Bétulacées")
        self.assertEqual(extras.haut, "20 m")
        self.assertEqual(extras.env, "15 m")
        self.assertEqual(extras.croiss, "Rapide")
        self.assertEqual(extras.long, "Moyenne (100 à 200 ans)")
        self.assertEqual(extras.paris, "Très rare à Paris.")
        self.assertEqual(set(extras.iddesc), {"ecorce", "feuillage", "floraison", "fructification"})
        self.assertEqual(set(extras.svc), {"climat", "eau", "biodiv"})
        self.assertEqual(extras.atouts, ["Régule l'eau", "Résiste au froid"])
        self.assertEqual(extras.limites, ["Sensible à la sécheresse"])

    def test_flor_fruct_preserves(self):
        # L'override ne porte pas de calendrier : le fallback couleur reste.
        extras = self._rasterized()
        essence_pdf.merge_override(extras, self._override())
        self.assertEqual(extras.flor, 8)
        self.assertEqual(extras.fruct, 48)

    def test_n_ecrase_jamais_une_valeur_existante(self):
        # Fiche entièrement extraite : l'override ne doit RIEN faire.
        extras = essence_pdf.EssenceExtras(
            flor=1, fruct=2,
            fam="DéjàLà", haut="99 m", env="88 m", croiss="Vive",
            long="courte", paris="Paris extrait",
            iddesc={"ecorce": "existante"},
            svc={"climat": "existant"},
            atouts=["atout extrait"], limites=["limite extraite"],
        )
        ov = self._override()
        # Rien à compléter → retourne False, tout inchangé.
        self.assertFalse(essence_pdf.merge_override(extras, ov))
        self.assertEqual(extras.fam, "DéjàLà")
        self.assertEqual(extras.haut, "99 m")
        self.assertEqual(extras.iddesc, {"ecorce": "existante"})
        self.assertEqual(extras.svc, {"climat": "existant"})
        self.assertEqual(extras.atouts, ["atout extrait"])
        self.assertEqual(extras.limites, ["limite extraite"])

    def test_completion_partielle(self):
        # fam extrait mais paris manquant : remplit paris, garde fam.
        extras = essence_pdf.EssenceExtras(flor=1, fruct=2, fam="Extraite")
        self.assertTrue(essence_pdf.merge_override(extras, self._override()))
        self.assertEqual(extras.fam, "Extraite")        # intouché
        self.assertEqual(extras.paris, "Très rare à Paris.")  # complété

    def test_valeurs_normalisees_par_clean(self):
        # La transcription manuelle reproduit la source rasterisée, espaces
        # d'exposant compris : le merge applique la même normalisation
        # `_clean` que les proses extraites, sur str, dict et listes.
        extras = self._rasterized()
        ov = self._override()
        ov["paris"] = "Présent au Parc Montsouris (14 e). Rare à Paris ."
        ov["svc"] = {"climat": "Depuis le 19 e siècle."}
        ov["atouts"] = ["Planté dès le 1 er âge"]
        essence_pdf.merge_override(extras, ov)
        self.assertEqual(extras.paris, "Présent au Parc Montsouris (14e). Rare à Paris.")
        self.assertEqual(extras.svc["climat"], "Depuis le 19e siècle.")
        self.assertEqual(extras.atouts, ["Planté dès le 1er âge"])

    def test_ignore_meta_et_nom_latin(self):
        extras = self._rasterized()
        ov = self._override()
        ov["_meta"] = {"description": "ne doit pas fuiter"}
        essence_pdf.merge_override(extras, ov)
        # nom_latin/_meta ne créent pas d'attribut ni ne fuitent nulle part.
        self.assertFalse(hasattr(extras, "nom_latin"))
        self.assertFalse(hasattr(extras, "_meta"))

    def test_warning_requalifie_et_note_provenance(self):
        extras = self._rasterized()
        essence_pdf.merge_override(extras, self._override())
        # Note de provenance en tête.
        self.assertEqual(extras.warnings[0], essence_pdf._OVERRIDE_PROVENANCE_WARNING)
        joined = " ".join(extras.warnings)
        # Warnings obsolètes retirés.
        self.assertNotIn("champs textuels non extraits", joined)
        self.assertNotIn("ancres Atouts/Limites introuvables", joined)
        self.assertNotIn("ancien template", joined)

    def test_requalifie_ancien_template_residuel(self):
        # Ceinture+bretelles : une mention « ancien template » subsistante est
        # requalifiée (et non supprimée) si elle ne relève pas des 2 obsolètes.
        extras = essence_pdf.EssenceExtras(
            flor=1, fruct=2,
            warnings=["divers: ancien template détecté ailleurs"],
        )
        essence_pdf.merge_override(extras, self._override())
        self.assertIn("divers: PDF rasterisé détecté ailleurs", extras.warnings)

    def test_apply_pdf_id_inconnu_ne_crashe_pas(self):
        corpus = {"connu": self._rasterized()}
        overrides = {
            "_meta": {"x": 1},
            "connu": self._override(),
            "inconnu-xyz": self._override(),
        }
        # pdf_id inconnu ignoré, pas d'exception.
        essence_pdf.apply_essence_overrides(corpus, overrides)
        self.assertEqual(corpus["connu"].fam, "Bétulacées")

    def test_apply_fichier_absent_noop(self):
        corpus = {"connu": self._rasterized()}
        with mock.patch.object(
            essence_pdf, "ESSENCE_OVERRIDES_PATH",
            Path("/nonexistent/essence-overrides.json"),
        ):
            essence_pdf.apply_essence_overrides(corpus)  # overrides=None → lit disque
        # Rien complété.
        self.assertIsNone(corpus["connu"].fam)

    def test_override_reel_du_disque_bien_forme(self):
        # Le fichier versionné a bien 6 entrées, chacune complète.
        with essence_pdf.ESSENCE_OVERRIDES_PATH.open(encoding="utf-8") as f:
            data = json.load(f)
        self.assertIn("_meta", data)
        entries = {k: v for k, v in data.items() if k != "_meta"}
        self.assertEqual(len(entries), 6)
        required = {"nom_latin", "fam", "haut", "env", "croiss", "long",
                    "iddesc", "paris", "svc", "atouts", "limites"}
        for pid, v in entries.items():
            self.assertEqual(required, set(v), f"clés incomplètes pour {pid}")
            self.assertEqual({"ecorce", "feuillage", "floraison", "fructification"}, set(v["iddesc"]))
            self.assertEqual({"climat", "eau", "biodiv"}, set(v["svc"]))


class EssenceTaxonSynonymsTest(unittest.TestCase):
    """Remappage synonymie/graphie des clés taxon des fiches-essences.

    `ESSENCE_TAXON_SYNONYMS` doit être appliquée par `_build_essences_index` à
    la clé issue de `_parse_essence_taxon`, pour que le taxon canonique du
    species-index soit vu de façon cohérente par tout le pipeline. Test pur.
    """

    @staticmethod
    def _rec(nom_latin, pdf_id):
        return {
            "nom_latin": nom_latin,
            "nom_fichier_pdf_associe": {
                "url": f"https://example/{pdf_id}",
                "id": pdf_id,
                "filename": f"{pdf_id}.pdf",
            },
        }

    def test_gymnocladus_dioicus_remappe_vers_dioica(self):
        idx = build_dataset._build_essences_index(
            [self._rec("Gymnocladus dioicus", "p1")]
        )
        self.assertIn(("Gymnocladus", "dioica"), idx)
        self.assertNotIn(("Gymnocladus", "dioicus"), idx)

    def test_sinomalus_sieboldii_remappe_vers_malus_toringo(self):
        idx = build_dataset._build_essences_index(
            [self._rec("Sinomalus sieboldii 'Brouwers Beauty'", "p2")]
        )
        self.assertIn(("Malus", "toringo"), idx)
        self.assertNotIn(("Sinomalus", "sieboldii"), idx)

    def test_taxons_non_synonymes_intacts(self):
        idx = build_dataset._build_essences_index([
            self._rec("Acer platanoides", "p3"),
            self._rec("Quercus robur 'Fastigiata'", "p4"),
        ])
        self.assertIn(("Acer", "platanoides"), idx)
        # Le cultivar est réduit à l'espèce nue, pas remappé par synonymie.
        self.assertIn(("Quercus", "robur"), idx)

    def test_table_sens_fiche_vers_canonique(self):
        # La table mappe (graphie fiche) → (taxon canonique species-index).
        self.assertEqual(
            build_dataset.ESSENCE_TAXON_SYNONYMS[("Gymnocladus", "dioicus")],
            ("Gymnocladus", "dioica"),
        )
        self.assertEqual(
            build_dataset.ESSENCE_TAXON_SYNONYMS[("Sinomalus", "sieboldii")],
            ("Malus", "toringo"),
        )


class CommonsLicenseKeyTest(unittest.TestCase):
    """Filtre licence Commons : CC0 / PD / CC-BY* acceptés, -SA/-NC/-ND rejetés."""

    def test_accepted(self):
        self.assertEqual(commons_license_key("cc0"), "cc0")
        self.assertEqual(commons_license_key("pd"), "pd")
        self.assertEqual(commons_license_key("public domain"), "pd")
        self.assertEqual(commons_license_key("cc-by-4.0"), "cc-by")
        self.assertEqual(commons_license_key("CC BY 3.0"), "cc-by")

    def test_rejected(self):
        for bad in ("CC BY-SA 3.0", "cc-by-nc", "cc-by-nd", "", None):
            self.assertIsNone(commons_license_key(bad))


class InatLicenseKeyTest(unittest.TestCase):
    """Filtre licence iNat strict : cc0 / cc-by seuls acceptés."""

    def test_accepted(self):
        self.assertEqual(inat_license_key("cc0"), "cc0")
        self.assertEqual(inat_license_key("cc-by"), "cc-by")
        self.assertEqual(inat_license_key("CC-BY"), "cc-by")

    def test_rejected(self):
        for bad in ("cc-by-nc", "cc-by-sa", "cc-by-nd", None,
                    "all rights reserved"):
            self.assertIsNone(inat_license_key(bad))


class CleanArtistHtmlTest(unittest.TestCase):
    """Nettoyage HTML de l'attribution Commons → texte plat ou None."""

    def test_strips_tags(self):
        self.assertEqual(
            clean_artist_html('<a href="x">Jane <b>Doe</b></a>'), "Jane Doe"
        )

    def test_unescapes_entities(self):
        self.assertEqual(clean_artist_html("Jean&nbsp;&amp; Marie"), "Jean & Marie")

    def test_empty_returns_none(self):
        self.assertIsNone(clean_artist_html(""))
        self.assertIsNone(clean_artist_html("<span></span>"))
        self.assertIsNone(clean_artist_html(None))


class ParseCommonsImageinfoTest(unittest.TestCase):
    """Parsing d'une réponse MediaWiki imageinfo (formatversion=2)."""

    def _api(self, extmeta, *, thumburl="https://commons/thumb.jpg"):
        return {
            "query": {
                "pages": [
                    {
                        "imageinfo": [
                            {
                                "thumburl": thumburl,
                                "descriptionurl": "https://commons/File:X.jpg",
                                "extmetadata": extmeta,
                            }
                        ]
                    }
                ]
            }
        }

    def test_valid(self):
        api = self._api({
            "License": {"value": "cc-by-4.0"},
            "Artist": {"value": '<a href="x">Jane Doe</a>'},
        })
        meta = parse_commons_imageinfo(api)
        self.assertEqual(meta["license_key"], "cc-by")
        self.assertEqual(meta["artist"], "Jane Doe")
        self.assertEqual(meta["page_url"], "https://commons/File:X.jpg")
        self.assertEqual(meta["download_url"], "https://commons/thumb.jpg")

    def test_license_slug_preferred(self):
        # `License` machine-slug préféré au `LicenseShortName` humain.
        api = self._api({
            "License": {"value": "cc0"},
            "LicenseShortName": {"value": "CC0"},
        })
        self.assertEqual(parse_commons_imageinfo(api)["license_key"], "cc0")

    def test_page_absent(self):
        self.assertIsNone(parse_commons_imageinfo({"query": {"pages": []}}))

    def test_imageinfo_empty(self):
        api = {"query": {"pages": [{"title": "File:X.jpg"}]}}
        self.assertIsNone(parse_commons_imageinfo(api))


class TaxonNameMatchesTest(unittest.TestCase):
    """Matching binomial : genre ET espèce, insensible casse et `×`/`x`."""

    def test_exact(self):
        self.assertTrue(taxon_name_matches("Quercus robur", "Quercus robur"))

    def test_case_insensitive(self):
        self.assertTrue(taxon_name_matches("quercus ROBUR", "Quercus robur"))

    def test_hybrid_marker(self):
        self.assertTrue(
            taxon_name_matches("Platanus x hispanica", "Platanus × hispanica")
        )

    def test_genus_differs(self):
        self.assertFalse(taxon_name_matches("Quercus robur", "Fagus robur"))

    def test_species_differs(self):
        self.assertFalse(taxon_name_matches("Quercus robur", "Quercus ilex"))


class FilenameFromP18ValueTest(unittest.TestCase):
    """Décodage d'une valeur P18 (URL Special:FilePath) → nom de fichier."""

    def test_decode(self):
        url = ("http://commons.wikimedia.org/wiki/Special:FilePath/"
               "Quercus%20robur%20-%20K%C3%B6hler.jpg")
        self.assertEqual(
            filename_from_p18_value(url), "Quercus robur - Köhler.jpg"
        )

    def test_underscores_to_spaces(self):
        url = ("http://commons.wikimedia.org/wiki/Special:FilePath/"
               "Acer_platanoides.jpg")
        self.assertEqual(filename_from_p18_value(url), "Acer platanoides.jpg")

    def test_empty(self):
        self.assertIsNone(filename_from_p18_value(""))


class PickInatPhotoTest(unittest.TestCase):
    """Choix de la 1re photo licence-valide du 1er taxon concordant."""

    def _photo(self, license_code, pid=42):
        return {
            "id": pid,
            "license_code": license_code,
            "attribution": "(c) Jane Doe, some rights reserved",
            "medium_url": "https://inat/photos/42/medium.jpg",
        }

    def test_match_cc0(self):
        api = {"results": [
            {"name": "Quercus robur", "default_photo": self._photo("cc0")}
        ]}
        pick = pick_inat_photo(api, "Quercus robur")
        self.assertIsNotNone(pick)
        self.assertEqual(pick["license_key"], "cc0")
        self.assertEqual(pick["page_url"], "https://www.inaturalist.org/photos/42")
        self.assertEqual(pick["image_url"], "https://inat/photos/42/medium.jpg")
        self.assertTrue(pick["by"])

    def test_match_cc_by_nc_rejected(self):
        api = {"results": [
            {"name": "Quercus robur", "default_photo": self._photo("cc-by-nc")}
        ]}
        self.assertIsNone(pick_inat_photo(api, "Quercus robur"))

    def test_first_non_matching_skipped(self):
        api = {"results": [
            {"name": "Fagus sylvatica", "default_photo": self._photo("cc0", pid=1)},
            {"name": "Quercus robur", "default_photo": self._photo("cc-by", pid=2)},
        ]}
        pick = pick_inat_photo(api, "Quercus robur")
        self.assertIsNotNone(pick)
        self.assertEqual(pick["license_key"], "cc-by")
        self.assertEqual(pick["page_url"], "https://www.inaturalist.org/photos/2")

    def test_taxon_photos_fallback_when_default_restricted(self):
        # Cas réel : `default_photo` réservée, une `taxon_photos` est CC0
        # (la fiche détail iNat expose taxon_photos, pas la recherche).
        api = {"results": [{
            "name": "Zelkova serrata",
            "default_photo": self._photo(None, pid=1),
            "taxon_photos": [
                {"photo": self._photo("cc-by-nc", pid=2)},
                {"photo": self._photo("cc0", pid=3)},
            ],
        }]}
        pick = pick_inat_photo(api, "Zelkova serrata")
        self.assertIsNotNone(pick)
        self.assertEqual(pick["license_key"], "cc0")
        self.assertEqual(pick["page_url"], "https://www.inaturalist.org/photos/3")


class FindInatTaxonIdTest(unittest.TestCase):
    """Id du 1er résultat de recherche concordant (anti-faux-positif)."""

    def test_matching_id(self):
        api = {"results": [
            {"name": "Acer platanoides", "id": 55},
            {"name": "Zelkova serrata", "id": 129055},
        ]}
        self.assertEqual(_find_inat_taxon_id(api, "Zelkova serrata"), 129055)

    def test_no_match(self):
        api = {"results": [{"name": "Acer platanoides", "id": 55}]}
        self.assertIsNone(_find_inat_taxon_id(api, "Zelkova serrata"))

    def test_empty(self):
        self.assertIsNone(_find_inat_taxon_id({"results": []}, "Zelkova serrata"))


class BuildFallbackManifestEntryTest(unittest.TestCase):
    """Forme exacte de l'entrée manifest d'un trou comblé."""

    def test_shape(self):
        photo = FallbackPhoto(
            webp=b"RIFF", lic="cc-by", src="inaturalist",
            by="Jane Doe", u="https://www.inaturalist.org/photos/42",
        )
        self.assertEqual(
            build_fallback_manifest_entry(7, photo),
            {
                "f": "7-0.webp",
                "r": "p",
                "src": "inaturalist",
                "lic": "cc-by",
                "by": "Jane Doe",
                "u": "https://www.inaturalist.org/photos/42",
            },
        )


class EncodeRawToWebpTest(unittest.TestCase):
    """Encodage WebP d'octets bruts : format WEBP, long-edge borné par le cap."""

    def test_png_to_webp(self):
        try:
            from PIL import Image
        except ImportError:  # pragma: no cover - dépend de l'install
            self.skipTest("Pillow absent")
        buf = BytesIO()
        Image.new("RGB", (120, 40), (10, 120, 60)).save(buf, format="PNG")
        webp = encode_raw_to_webp(buf.getvalue(), cap=64, quality=78)
        self.assertIsNotNone(webp)
        self.assertEqual(webp[8:12], b"WEBP")
        out = Image.open(BytesIO(webp))
        self.assertLessEqual(max(out.width, out.height), 64)

    def test_garbage_returns_none(self):
        self.assertIsNone(encode_raw_to_webp(b"not an image", cap=800, quality=78))


class RenderCreditsMdTest(unittest.TestCase):
    """Rendu Markdown PURE de CREDITS.md : sections, tri, stats, fallback nom."""

    def _manifest(self):
        return {
            "meta": {
                "licenses": {
                    "odbl-1.0": {"name": "Open Database License v1.0",
                                 "url": "https://odbl.example/1-0/"},
                    "cc0": {"name": "CC0 1.0", "url": "https://cc0.example/"},
                    "cc-by": {"name": "CC BY", "url": "https://ccby.example/"},
                },
                "sources": {
                    "paris": {"name": "Ville de Paris — Guide des essences 2024",
                              "authors": "J.E. Michaut, B. Morlon, B. Serres"},
                    "wikimedia-commons": {"name": "Wikimedia Commons",
                                          "authors": ""},
                    "inaturalist": {"name": "iNaturalist", "authors": ""},
                },
            },
            "photos": {
                # Paris : 1 principale + 1 détail (bloc collectif, compté = 2).
                "0": [
                    {"f": "0-0.webp", "r": "p", "src": "paris",
                     "lic": "odbl-1.0", "by": "Ville de Paris", "u": "http://p/0"},
                    {"f": "0-1.webp", "r": "d", "src": "paris",
                     "lic": "odbl-1.0", "by": "Ville de Paris", "u": "http://p/0"},
                ],
                # Wikimedia : nom résolu via name_by_sk.
                "5": [
                    {"f": "5-0.webp", "r": "p", "src": "wikimedia-commons",
                     "lic": "cc0", "by": "葉子", "u": "http://commons/5"},
                ],
                # iNaturalist : sk sans nom → fallback binôme injecté par appelant.
                "9": [
                    {"f": "9-0.webp", "r": "p", "src": "inaturalist",
                     "lic": "cc-by", "by": "Jane Doe", "u": "http://inat/9"},
                ],
            },
        }

    def test_sections_stats_and_tri(self):
        names = {0: "Platane commun", 5: "Aulne glutineux", 9: "Tilia sp."}
        md = _render_credits_md(self._manifest(), names)
        # Stats calculées (3 espèces, 4 photos).
        self.assertIn("3 espèces illustrées · 4 photos de référence.", md)
        # En-tête + avertissement.
        self.assertTrue(md.startswith("# Crédits photos\n"))
        self.assertIn("ne pas éditer à la main", md)
        # Bloc collectif Paris : compte + auteurs + licence nom+url.
        self.assertIn(
            "## Ville de Paris — Guide des essences 2024 (2 photos)", md)
        self.assertIn("J.E. Michaut, B. Morlon, B. Serres", md)
        self.assertIn(
            "[Open Database License v1.0](https://odbl.example/1-0/)", md)
        # Sections ligne-par-ligne.
        self.assertIn("## Wikimedia Commons (1 photos)", md)
        self.assertIn(
            "- Aulne glutineux — 葉子 · CC0 1.0 — http://commons/5", md)
        self.assertIn("## iNaturalist (1 photos)", md)
        self.assertIn(
            "- Tilia sp. — Jane Doe · CC BY — http://inat/9", md)
        # Terminaison par newline, ordre Paris < Wikimedia < iNaturalist.
        self.assertTrue(md.endswith("\n"))
        self.assertLess(md.index("## Ville de Paris"),
                        md.index("## Wikimedia Commons"))
        self.assertLess(md.index("## Wikimedia Commons"),
                        md.index("## iNaturalist"))

    def test_fallback_nom_sk_inconnu(self):
        # sk absent de name_by_sk → fallback `sk N` (ne casse pas le rendu).
        md = _render_credits_md(self._manifest(), {})
        self.assertIn("- sk 5 — 葉子 · CC0 1.0 — http://commons/5", md)


if __name__ == "__main__":
    unittest.main()
