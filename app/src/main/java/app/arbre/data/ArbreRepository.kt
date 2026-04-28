package app.arbre.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Source de vérité des arbres.
 *
 * Pour le MVP cette implémentation renvoie un échantillon en mémoire.
 * L'étape suivante remplace ceci par une base SQLite (Room + R*Tree)
 * peuplée depuis l'OpenData Paris au premier lancement.
 */
class ArbreRepository {

    fun arbresDansBbox(
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double,
    ): Flow<List<Arbre>> = flowOf(
        SAMPLE.filter {
            it.latitude in minLat..maxLat && it.longitude in minLon..maxLon
        }
    )

    fun arbreParId(id: Long): Arbre? = SAMPLE.firstOrNull { it.id == id }

    companion object {
        // Échantillon — sera remplacé par le dataset complet.
        val SAMPLE = listOf(
            Arbre(1, "Platanus", "x acerifolia", null, 35, 420, true,
                "Quai de la Tournelle, 75005", 48.8513, 2.3530),
            Arbre(2, "Aesculus", "hippocastanum", null, 18, 180, false,
                "Jardin du Luxembourg, 75006", 48.8462, 2.3372),
            Arbre(3, "Quercus", "robur", null, 22, 240, true,
                "Square René Viviani, 75005", 48.8523, 2.3473),
        )
    }
}
