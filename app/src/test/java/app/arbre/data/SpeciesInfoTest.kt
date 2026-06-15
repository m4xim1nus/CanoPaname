package app.arbre.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests JVM du parsing du bloc `ess` (attributs Ville de Paris) via la fonction
 * `internal parseSpeciesAttributes`, sans dépendance Android (`Context.assets`).
 * Le contrat de format est figé par S1 (`tools/build_dataset.py`).
 */
class SpeciesInfoTest {

    @Test fun `parses a full ess block`() {
        val ess = JSONObject(
            """{
                "port":"Ovoïde",
                "feuillage":"Caduc",
                "taille":"Grand",
                "indigenat":"Exotique",
                "origine":"Sud-est de l'Europe (Balkans)",
                "fleurs":true,
                "expo":["soleil","mi-ombre"],
                "eau":["sol frais"],
                "sites":["place","espaces verts","cimetières"]
            }"""
        )
        val attrs = parseSpeciesAttributes(ess)!!
        assertEquals("Ovoïde", attrs.port)
        assertEquals("Caduc", attrs.feuillage)
        assertEquals("Grand", attrs.taille)
        assertEquals("Exotique", attrs.indigenat)
        assertEquals("Sud-est de l'Europe (Balkans)", attrs.origine)
        assertEquals(true, attrs.fleurs)
        assertEquals(listOf("soleil", "mi-ombre"), attrs.exposition)
        assertEquals(listOf("sol frais"), attrs.besoinsEau)
        assertEquals(listOf("place", "espaces verts", "cimetières"), attrs.sitePlantation)
    }

    @Test fun `absent ess block yields null attributes`() {
        // `optJSONObject("ess")` renvoie null quand la clé manque (longue traîne).
        assertNull(parseSpeciesAttributes(null))
    }

    @Test fun `tolerates a partial ess block`() {
        // Champs vides omis à la source : ici seuls port + taille présents.
        val ess = JSONObject("""{"port":"Etalé","taille":"Moyen"}""")
        val attrs = parseSpeciesAttributes(ess)!!
        assertEquals("Etalé", attrs.port)
        assertEquals("Moyen", attrs.taille)
        assertNull(attrs.feuillage)
        assertNull(attrs.indigenat)
        assertNull(attrs.origine)
        assertNull(attrs.fleurs)
        assertTrue(attrs.exposition.isEmpty())
        assertTrue(attrs.besoinsEau.isEmpty())
        assertTrue(attrs.sitePlantation.isEmpty())
    }

    @Test fun `distinguishes fleurs false from absent fleurs`() {
        val withFalse = parseSpeciesAttributes(JSONObject("""{"fleurs":false}"""))!!
        assertEquals(false, withFalse.fleurs)
        val withoutKey = parseSpeciesAttributes(JSONObject("""{"port":"Conique"}"""))!!
        assertNull(withoutKey.fleurs)
    }
}
