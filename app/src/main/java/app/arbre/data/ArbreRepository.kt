package app.arbre.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Source de vérité des arbres. Backed par Room sur l'asset
 * `databases/arbres-paris.db` (généré hors-app par `tools/build_dataset.py`).
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

    /** Batch lookup pour les badges qui dépendent des caractéristiques des
     *  arbres capturés (hauteur, circ, arrondissement).
     */
    suspend fun arbresParIds(ids: Collection<Long>): Map<Long, Arbre> {
        if (ids.isEmpty()) return emptyMap()
        return dao.arbresParIds(ids.toList())
            .associate { it.id to it.toArbre() }
    }

    suspend fun arbresRemarquables(): List<Arbre> =
        dao.arbresRemarquables().map(ArbreEntity::toArbre)

    suspend fun compterParEspece(genre: String, espece: String): Int =
        dao.compterParEspece(genre, espece)

    /**
     * Cohérent avec la coloration carte : un arbre non-remarquable « se déverrouille »
     * dès que son espèce est capturée ; un remarquable se déverrouille uniquement
     * via sa propre capture. Pas de double-comptage.
     */
    suspend fun nombreArbresDecouverts(
        capturedSk: Set<Int>,
        capturedRemarquableIds: Set<Long>,
        speciesIndex: SpeciesIndex,
    ): Int {
        if (capturedSk.isEmpty() && capturedRemarquableIds.isEmpty()) return 0
        var count = capturedRemarquableIds.size
        for (sk in capturedSk) {
            val entry = speciesIndex.get(sk) ?: continue
            count += dao.compterArbresOrdinairesParEspece(entry.genre, entry.espece)
        }
        return count
    }

    suspend fun unArbreParEspece(genre: String, espece: String): Arbre? =
        dao.unArbreParEspece(genre, espece)?.toArbre()

    companion object {
        // Plafond bbox — z14+ sur Paris contient typiquement < 2000 arbres.
        const val DEFAULT_BBOX_LIMIT = 5000
    }
}
