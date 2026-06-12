package app.arbre.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DistanceToRadiusFracTest {

    @Test
    fun bornes() {
        assertEquals(0f, distanceToRadiusFrac(25f), 1e-6f)
        assertEquals(1f, distanceToRadiusFrac(2_000f), 1e-6f)
    }

    @Test
    fun sous_le_seuil_et_au_dela_du_bord_capes() {
        assertEquals(0f, distanceToRadiusFrac(5f), 1e-6f)
        assertEquals(0f, distanceToRadiusFrac(0f), 1e-6f)
        assertEquals(1f, distanceToRadiusFrac(10_000f), 1e-6f)
    }

    @Test
    fun valeurs_anneaux() {
        // ~105 m → anneau 1 (0.33), ~450 m → anneau 2 (0.66).
        assertEquals(0.33f, distanceToRadiusFrac(106f), 0.01f)
        assertEquals(0.66f, distanceToRadiusFrac(452f), 0.01f)
    }

    @Test
    fun monotone_croissante() {
        val distances = listOf(25f, 50f, 100f, 250f, 500f, 1_000f, 2_000f)
        distances.zipWithNext().forEach { (a, b) ->
            assertTrue(
                "frac($a) < frac($b) attendu",
                distanceToRadiusFrac(a) < distanceToRadiusFrac(b),
            )
        }
    }
}
