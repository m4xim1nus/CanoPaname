package app.arbre.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
                "sites":["place","espaces verts","cimetières"],
                "flor":24,
                "fruct":768,
                "atouts":["Mellifère","Résistant à la sécheresse"],
                "limites":["Sensible au vent"]
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
        assertEquals(24, attrs.floraison)
        assertEquals(768, attrs.fructification)
        assertEquals(listOf("Mellifère", "Résistant à la sécheresse"), attrs.atouts)
        assertEquals(listOf("Sensible au vent"), attrs.limites)
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
        // Clés saisonnalité absentes : scalaires null, listes vides.
        assertNull(attrs.floraison)
        assertNull(attrs.fructification)
        assertTrue(attrs.atouts.isEmpty())
        assertTrue(attrs.limites.isEmpty())
    }

    @Test fun `distinguishes fleurs false from absent fleurs`() {
        val withFalse = parseSpeciesAttributes(JSONObject("""{"fleurs":false}"""))!!
        assertEquals(false, withFalse.fleurs)
        val withoutKey = parseSpeciesAttributes(JSONObject("""{"port":"Conique"}"""))!!
        assertNull(withoutKey.fleurs)
    }

    @Test fun `neutralises out-of-range bitfields`() {
        // Borne basse exclue (0 = aucun mois → traité comme absent).
        val zero = parseSpeciesAttributes(JSONObject("""{"flor":0,"fruct":0}"""))!!
        assertNull(zero.floraison)
        assertNull(zero.fructification)
        // Au-delà de 12 bits (0xFFF = 4095) → null.
        val over = parseSpeciesAttributes(JSONObject("""{"flor":8192,"fruct":8192}"""))!!
        assertNull(over.floraison)
        assertNull(over.fructification)
        // Bornes valides conservées.
        val edges = parseSpeciesAttributes(JSONObject("""{"flor":1,"fruct":4095}"""))!!
        assertEquals(1, edges.floraison)
        assertEquals(4095, edges.fructification)
    }

    @Test fun `isMonthInBitfield reads bit 0 as january`() {
        // flor = 1 → seul janvier (bit 0) actif.
        assertTrue(isMonthInBitfield(1, 1))
        assertFalse(isMonthInBitfield(1, 2))
        // flor = 2048 → seul décembre (bit 11) actif.
        assertTrue(isMonthInBitfield(2048, 12))
        assertFalse(isMonthInBitfield(2048, 11))
    }

    @Test fun `isMonthInBitfield handles multi-month bitfield`() {
        // flor = 24 = 0b0001_1000 → bits 3 et 4 = avril + mai.
        assertTrue(isMonthInBitfield(24, 4))
        assertTrue(isMonthInBitfield(24, 5))
        assertFalse(isMonthInBitfield(24, 3))
        assertFalse(isMonthInBitfield(24, 6))
    }
}
