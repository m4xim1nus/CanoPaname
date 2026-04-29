package app.arbre.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Source de vérité des arbres parisiens.
 *
 * Backed par une base Room peuplée depuis l'asset `databases/arbres-paris.db`
 * (généré hors-app par `tools/build_dataset.py`).
 */
class ArbreRepository(private val dao: ArbreDao) {

    fun arbresDansBbox(
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double,
        limit: Int = DEFAULT_BBOX_LIMIT,
    ): Flow<List<Arbre>> = dao
        .arbresDansBbox(minLat, maxLat, minLon, maxLon, limit)
        .map { rows -> rows.map(ArbreEntity::toArbre) }

    suspend fun arbreParId(id: Long): Arbre? = dao.arbreParId(id)?.toArbre()

    /** Batch lookup : utile pour évaluer les badges qui dépendent des
     *  caractéristiques des arbres capturés (hauteur, circ, arrondissement). */
    suspend fun arbresParIds(ids: Collection<Long>): Map<Long, Arbre> {
        if (ids.isEmpty()) return emptyMap()
        return dao.arbresParIds(ids.toList())
            .associate { it.id to it.toArbre() }
    }

    suspend fun arbresRemarquables(): List<Arbre> =
        dao.arbresRemarquables().map(ArbreEntity::toArbre)

    suspend fun compterParEspece(genre: String, espece: String): Int =
        dao.compterParEspece(genre, espece)

    suspend fun unArbreParEspece(genre: String, espece: String): Arbre? =
        dao.unArbreParEspece(genre, espece)?.toArbre()

    companion object {
        // Plafond pour éviter de déverser une bbox trop large dans la carte.
        // À z14+ sur Paris une bbox visible contient typiquement < 2000 arbres.
        const val DEFAULT_BBOX_LIMIT = 5000
    }
}
