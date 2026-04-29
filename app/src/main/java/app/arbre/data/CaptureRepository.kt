package app.arbre.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * État du jeu côté capture : qui a découvert quoi.
 *
 * Toutes les écritures Room de l'app passent par ici. Les Flows ré-émettent
 * automatiquement à chaque INSERT, ce qui propage la bascule grise → verte
 * jusqu'à l'expression `circleColor` de la layer MapLibre.
 */
class CaptureRepository(private val dao: CaptureDao) {

    fun capturedSpeciesIndices(): Flow<Set<Int>> =
        dao.capturedSpeciesIndices().map { it.toSet() }

    fun capturedRemarquableIds(): Flow<Set<Long>> =
        dao.capturedRemarquableIds().map { it.toSet() }

    fun capturesPourArbre(arbreId: Long): Flow<List<Capture>> =
        dao.capturesPourArbre(arbreId).map { rows -> rows.map(CaptureEntity::toCapture) }

    fun toutesLesCaptures(): Flow<List<Capture>> =
        dao.toutesLesCaptures().map { rows -> rows.map(CaptureEntity::toCapture) }

    fun capturesRemarquables(): Flow<List<Capture>> =
        dao.capturesRemarquables().map { rows -> rows.map(CaptureEntity::toCapture) }

    fun firstCaptureTimestamp(): Flow<Long?> = dao.firstCaptureTimestamp()

    fun captureCount(): Flow<Int> = dao.captureCount()

    suspend fun insertCapture(
        arbreId: Long,
        speciesIndex: Int,
        remarquable: Boolean,
        latitudeDevice: Double,
        longitudeDevice: Double,
        photoPath: String,
        timestamp: Long = System.currentTimeMillis(),
    ): Long = dao.insert(
        CaptureEntity(
            arbreId = arbreId,
            speciesIndex = speciesIndex,
            remarquable = remarquable,
            timestamp = timestamp,
            latitudeDevice = latitudeDevice,
            longitudeDevice = longitudeDevice,
            photoPath = photoPath,
            season = Season.fromTimestamp(timestamp),
        )
    )
}
