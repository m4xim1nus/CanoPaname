package app.arbre.data

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests JVM unitaires de `SpeciesIndex` + `SpeciesEntry` sans dépendance
 * Android (pas de `Context.assets`). On construit les fixtures inline via
 * `JSONArray` puis on bypasse la fabrique de chargement en passant
 * directement les `SpeciesEntry` au constructeur public.
 *
 * Couvre la lecture tolérante des champs Catalogue (`nv`, `n`, `u`) sur
 * format legacy + format new + mixed, plus l'auto-débloquage genre-based.
 */
class SpeciesIndexTest {

    private fun entry(
        index: Int,
        genre: String,
        espece: String,
        nomCommun: String? = null,
        nv: String? = null,
        pokedexNumber: Int? = null,
        unknownSpecies: Boolean = false,
    ) = SpeciesEntry(
        index = index,
        genre = genre,
        espece = espece,
        nomCommun = nomCommun,
        nv = nv,
        pokedexNumber = pokedexNumber,
        unknownSpecies = unknownSpecies,
    )

    // ---------- Lecture tolérante / fallbacks displayNomCommun ----------

    @Test fun `legacy format without new fields falls back to nomCommun then binomial`() {
        val withNc = entry(0, "Aesculus", "hippocastanum", nomCommun = "Marronnier")
        val noNc = entry(1, "Pistacia", "palaestina")
        assertEquals("Marronnier", withNc.displayNomCommun)
        assertEquals("Pistacia palaestina", noNc.displayNomCommun)
        assertEquals("Aesculus hippocastanum", withNc.displayName)
        assertNull(withNc.nv)
        assertNull(withNc.pokedexNumber)
        assertFalse(withNc.unknownSpecies)
    }

    @Test fun `new format with nv prefers nv for displayNomCommun`() {
        val e = entry(
            index = 0,
            genre = "Quercus",
            espece = "robur",
            nomCommun = "Chêne",
            nv = "Chêne pédonculé",
            pokedexNumber = 12,
        )
        assertEquals("Chêne pédonculé", e.displayNomCommun)
        assertEquals("Quercus robur", e.displayName)
        assertEquals(12, e.pokedexNumber)
        assertFalse(e.unknownSpecies)
    }

    @Test fun `unknown species flag default false when absent`() {
        val e = entry(0, "Quercus", "robur")
        assertFalse(e.unknownSpecies)
    }

    @Test fun `unknown species flag true on sp entries`() {
        val e = entry(
            index = 99,
            genre = "Tilia",
            espece = "sp.",
            nv = "Tilleul (espèce indéterminée)",
            unknownSpecies = true,
        )
        assertTrue(e.unknownSpecies)
        assertNull(e.pokedexNumber)
        assertEquals("Tilleul (espèce indéterminée)", e.displayNomCommun)
    }

    // ---------- isDiscovered : auto-débloquage genre-based ----------

    @Test fun `isDiscovered returns true on direct sk capture`() {
        val idx = SpeciesIndex(listOf(
            entry(0, "Quercus", "robur"),
            entry(1, "Tilia", "cordata"),
        ))
        assertTrue(idx.isDiscovered(0, setOf(0)))
        assertFalse(idx.isDiscovered(1, setOf(0)))
    }

    @Test fun `isDiscovered returns true on sibling genre capture for unknown sk`() {
        // Tilia sp. (sk=99, u: true) débloqué dès qu'un Tilia X est capturé.
        val idx = SpeciesIndex(listOf(
            entry(0, "Tilia", "cordata"),
            entry(1, "Tilia", "platyphyllos"),
            entry(99, "Tilia", "sp.", unknownSpecies = true),
        ))
        // Aucune capture → pas découvert.
        assertFalse(idx.isDiscovered(99, emptySet()))
        // Capture d'un frère → débloqué.
        assertTrue(idx.isDiscovered(99, setOf(0)))
        assertTrue(idx.isDiscovered(99, setOf(1)))
        // Capture directe du sp. → débloqué aussi (path direct).
        assertTrue(idx.isDiscovered(99, setOf(99)))
    }

    @Test fun `isDiscovered does not auto-unlock identified species via sibling`() {
        // Q. petraea ne se débloque PAS si Q. robur est capturé. Le mécanisme
        // est strictement réservé aux unknownSpecies.
        val idx = SpeciesIndex(listOf(
            entry(0, "Quercus", "robur"),
            entry(1, "Quercus", "petraea"),
        ))
        assertFalse(idx.isDiscovered(1, setOf(0)))
    }

    @Test fun `isDiscovered handles unknown sk gracefully`() {
        val idx = SpeciesIndex(listOf(entry(0, "Quercus", "robur")))
        // sk inconnu → false, pas de crash.
        assertFalse(idx.isDiscovered(42, setOf(0)))
        assertFalse(idx.isDiscovered(42, emptySet()))
    }

    @Test fun `genreOf returns genre for known sk and null otherwise`() {
        val idx = SpeciesIndex(listOf(entry(0, "Quercus", "robur")))
        assertEquals("Quercus", idx.genreOf(0))
        assertNull(idx.genreOf(42))
    }

    @Test fun `unknownSks set contains only u-flagged entries`() {
        val idx = SpeciesIndex(listOf(
            entry(0, "Quercus", "robur"),
            entry(99, "Tilia", "sp.", unknownSpecies = true),
            entry(98, "Quercus", "sp.", unknownSpecies = true),
        ))
        assertEquals(setOf(98, 99), idx.unknownSks)
    }

    // ---------- Parsing JSON tolérant ----------

    @Test fun `JSONArray legacy entry parses without new fields`() {
        // Reproduit le shape réel du species-index.json actuel.
        val arr = JSONArray("""[{"i":0,"g":"Aesculus","e":"hippocastanum","nc":"Marronnier"}]""")
        val o = arr.getJSONObject(0)
        // Reproduit le bloc de lecture de `SpeciesIndex.load` à la main —
        // confirme que `optBoolean` et `optInt` ne lèvent pas sur clés absentes.
        val entry = SpeciesEntry(
            index = o.getInt("i"),
            genre = o.getString("g"),
            espece = o.getString("e"),
            nomCommun = if (o.has("nc") && !o.isNull("nc")) o.optString("nc").takeIf { it.isNotEmpty() } else null,
            nv = if (o.has("nv") && !o.isNull("nv")) o.optString("nv").takeIf { it.isNotEmpty() } else null,
            pokedexNumber = if (o.has("n") && !o.isNull("n")) o.optInt("n").takeIf { it > 0 } else null,
            unknownSpecies = o.optBoolean("u", false),
        )
        assertEquals(0, entry.index)
        assertEquals("Marronnier", entry.nomCommun)
        assertNull(entry.nv)
        assertNull(entry.pokedexNumber)
        assertFalse(entry.unknownSpecies)
        assertEquals("Marronnier", entry.displayNomCommun)
    }

    @Test fun `JSONArray new format entry parses all new fields`() {
        val arr = JSONArray(
            """[{"i":0,"g":"Quercus","e":"robur","nc":"Chêne","nv":"Chêne pédonculé","n":12}]"""
        )
        val o = arr.getJSONObject(0)
        val entry = SpeciesEntry(
            index = o.getInt("i"),
            genre = o.getString("g"),
            espece = o.getString("e"),
            nomCommun = if (o.has("nc") && !o.isNull("nc")) o.optString("nc").takeIf { it.isNotEmpty() } else null,
            nv = if (o.has("nv") && !o.isNull("nv")) o.optString("nv").takeIf { it.isNotEmpty() } else null,
            pokedexNumber = if (o.has("n") && !o.isNull("n")) o.optInt("n").takeIf { it > 0 } else null,
            unknownSpecies = o.optBoolean("u", false),
        )
        assertEquals("Chêne pédonculé", entry.nv)
        assertEquals(12, entry.pokedexNumber)
        assertEquals("Chêne pédonculé", entry.displayNomCommun)
    }

    @Test fun `JSONArray unknownSpecies entry parses u flag`() {
        val arr = JSONArray(
            """[{"i":99,"g":"Tilia","e":"sp.","nv":"Tilleul (espèce indéterminée)","u":true}]"""
        )
        val o = arr.getJSONObject(0)
        val entry = SpeciesEntry(
            index = o.getInt("i"),
            genre = o.getString("g"),
            espece = o.getString("e"),
            nomCommun = if (o.has("nc") && !o.isNull("nc")) o.optString("nc").takeIf { it.isNotEmpty() } else null,
            nv = if (o.has("nv") && !o.isNull("nv")) o.optString("nv").takeIf { it.isNotEmpty() } else null,
            pokedexNumber = if (o.has("n") && !o.isNull("n")) o.optInt("n").takeIf { it > 0 } else null,
            unknownSpecies = o.optBoolean("u", false),
        )
        assertTrue(entry.unknownSpecies)
        assertEquals("Tilleul (espèce indéterminée)", entry.displayNomCommun)
        assertNull(entry.pokedexNumber)
    }

    // ---------- Hooks chapitres genre (cycle Catalogue, sprint 7) ----------

    @Test fun `genres returns alphabetical list excluding only-unknown genres`() {
        // Vitex est only-unknown (uniquement une entrée sp.) → exclu.
        // Acer et Quercus ont chacun ≥ 1 entrée identifiée → présents.
        val idx = SpeciesIndex(listOf(
            entry(0, "Quercus", "robur"),
            entry(1, "Quercus", "petraea"),
            entry(2, "Acer", "platanoides"),
            entry(98, "Quercus", "sp.", unknownSpecies = true),
            entry(99, "Vitex", "sp.", unknownSpecies = true),
        ))
        assertEquals(listOf("Acer", "Quercus"), idx.genres())
    }

    @Test fun `genreCount excludes sp entries`() {
        val idx = SpeciesIndex(listOf(
            entry(0, "Quercus", "robur"),
            entry(1, "Quercus", "petraea"),
            entry(98, "Quercus", "sp.", unknownSpecies = true),
        ))
        assertEquals(2, idx.genreCount("Quercus"))
        assertEquals(0, idx.genreCount("Unknown"))
    }

    @Test fun `capturedCountInGenre returns identified-only intersection`() {
        // 2 identifiées + 1 sp. ; on capture 1 identifiée + le sp. → 1.
        val idx = SpeciesIndex(listOf(
            entry(0, "Quercus", "robur"),
            entry(1, "Quercus", "petraea"),
            entry(98, "Quercus", "sp.", unknownSpecies = true),
        ))
        assertEquals(1, idx.capturedCountInGenre("Quercus", setOf(0, 98)))
        assertEquals(2, idx.capturedCountInGenre("Quercus", setOf(0, 1)))
    }

    @Test fun `capturedCountInGenre returns zero for empty capturedSks`() {
        val idx = SpeciesIndex(listOf(
            entry(0, "Quercus", "robur"),
            entry(1, "Quercus", "petraea"),
        ))
        assertEquals(0, idx.capturedCountInGenre("Quercus", emptySet()))
        assertEquals(0, idx.capturedCountInGenre("Unknown", setOf(0)))
    }

    @Test fun `indexOf round-trip works`() {
        val idx = SpeciesIndex(listOf(
            entry(0, "Quercus", "robur"),
            entry(1, "Tilia", "cordata"),
        ))
        assertEquals(0, idx.indexOf("Quercus", "robur"))
        assertEquals(1, idx.indexOf("Tilia", "cordata"))
        assertNull(idx.indexOf("Acer", "negundo"))
        assertNotNull(idx.get(0))
        assertNull(idx.get(42))
    }

    // ---------- allGenres : routage des fiches genre (S8) ----------

    @Test fun `allGenres includes only-unknown genres but excludes Non specifie`() {
        val idx = SpeciesIndex(listOf(
            entry(0, "Quercus", "robur"),
            entry(1, "Acer", "platanoides"),
            // Genista est only-unknown → INCLUS dans allGenres (S8) mais
            // exclu de genres() (qui ne renvoie que les genres avec ≥ 1
            // espèce identifiée).
            entry(98, "Genista", "sp.", unknownSpecies = true),
            // Non spécifié est un cas dégénéré → toujours exclu d'allGenres.
            entry(99, "Non spécifié", "sp.", unknownSpecies = true),
        ))
        assertEquals(listOf("Acer", "Genista", "Quercus"), idx.allGenres())
        assertEquals(listOf("Acer", "Quercus"), idx.genres())
    }

    @Test fun `allGenres returns alphabetical case-insensitive order`() {
        val idx = SpeciesIndex(listOf(
            entry(0, "tilia", "cordata"),
            entry(1, "Acer", "negundo"),
            entry(2, "Quercus", "robur"),
        ))
        // Tri case-insensitive : « Acer », « Quercus », « tilia ».
        assertEquals(listOf("Acer", "Quercus", "tilia"), idx.allGenres())
    }

    @Test fun `allGenres is empty when only Non specifie present`() {
        val idx = SpeciesIndex(listOf(
            entry(99, "Non spécifié", "sp.", unknownSpecies = true),
        ))
        assertTrue(idx.allGenres().isEmpty())
    }
}
