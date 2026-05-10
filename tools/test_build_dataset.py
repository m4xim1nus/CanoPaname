"""Tests offline des helpers purs de `build_dataset.py`.

Cible Sprint 1 du cycle Catalogue : valider sans tourner le build complet
(pas de CSV ni de cache Wikipedia/Wikidata requis).

Run :
    python3 -m unittest tools.test_build_dataset
ou :
    cd tools && python3 -m unittest test_build_dataset
"""

from __future__ import annotations

import unittest

from build_dataset import (
    SPECIES_FIXUPS,
    UNKNOWN_ESPECE_FORMS,
    apply_species_fixups,
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


if __name__ == "__main__":
    unittest.main()
