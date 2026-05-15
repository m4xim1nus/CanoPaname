package app.arbre.ui.map

import app.arbre.data.ArrKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParseArrQueryTest {

    @Test
    fun chiffres_nus_dans_la_plage() {
        assertEquals(ArrKey.Paris(1), parseArrQuery("1"))
        assertEquals(ArrKey.Paris(1), parseArrQuery("01"))
        assertEquals(ArrKey.Paris(20), parseArrQuery("20"))
        assertNull(parseArrQuery("21"))
        assertNull(parseArrQuery("0"))
    }

    @Test
    fun ordinaux_abreges() {
        assertEquals(ArrKey.Paris(1), parseArrQuery("1er"))
        assertEquals(ArrKey.Paris(2), parseArrQuery("2e"))
        assertEquals(ArrKey.Paris(4), parseArrQuery("4eme"))
        assertEquals(ArrKey.Paris(4), parseArrQuery("4ème"))
    }

    @Test
    fun codes_postaux() {
        assertEquals(ArrKey.Paris(1), parseArrQuery("75001"))
        assertEquals(ArrKey.Paris(20), parseArrQuery("75020"))
        assertNull(parseArrQuery("75021"))
        assertNull(parseArrQuery("75000"))
        assertNull(parseArrQuery("75100"))
    }

    @Test
    fun ordinaux_francais_litteraux() {
        assertEquals(ArrKey.Paris(1), parseArrQuery("premier"))
        assertEquals(ArrKey.Paris(2), parseArrQuery("deuxième"))
        assertEquals(ArrKey.Paris(2), parseArrQuery("second"))
        assertEquals(ArrKey.Paris(20), parseArrQuery("vingtieme"))
    }

    @Test
    fun vincennes_toutes_casses() {
        assertEquals(ArrKey.BoisVincennes, parseArrQuery("VINCENNES"))
        assertEquals(ArrKey.BoisVincennes, parseArrQuery("Bois de Vincennes"))
        assertEquals(ArrKey.BoisVincennes, parseArrQuery("vincennes"))
    }

    @Test
    fun boulogne_toutes_casses() {
        assertEquals(ArrKey.BoisBoulogne, parseArrQuery("Boulogne"))
        assertEquals(ArrKey.BoisBoulogne, parseArrQuery("bois de boulogne"))
    }

    @Test
    fun cas_negatifs() {
        assertNull(parseArrQuery(""))
        assertNull(parseArrQuery("   "))
        assertNull(parseArrQuery("xyz"))
        assertNull(parseArrQuery("chêne"))
    }

    @Test
    fun casse_mixte_avec_accents() {
        assertEquals(ArrKey.Paris(2), parseArrQuery("DEUXIÈME"))
        assertEquals(ArrKey.Paris(11), parseArrQuery("11E"))
    }
}
