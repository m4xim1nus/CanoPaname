package app.arbre.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.map

/**
 * Événement émis après chaque INSERT capture réussi. Permet aux écrans de
 * déclencher une animation de climax (halo + scale sur le point, célébration
 * silhouette) sans repasser par un signal d'horodatage Room.
 *
 * `isFirstOfSpecies` : true si c'est la 1re capture de l'espèce, *toutes
 * saisons confondues*. Calculé en queryant Room juste avant l'INSERT.
 */
data class CaptureEvent(
    val arbreId: Long,
    val speciesIndex: Int,
    val remarquable: Boolean,
    val isFirstOfSpecies: Boolean,
    val latitudeDevice: Double,
    val longitudeDevice: Double,
)

/**
 * État du jeu côté capture : qui a découvert quoi.
 *
 * Toutes les écritures Room de l'app passent par ici. Les Flows ré-émettent
 * automatiquement à chaque INSERT, ce qui propage la bascule grise → verte
 * jusqu'à l'expression `circleColor` de la layer MapLibre.
 */
class CaptureRepository(private val dao: CaptureDao) {

    private val _captureConfirmed = MutableSharedFlow<CaptureEvent>(extraBufferCapacity = 4)
    val captureConfirmed: SharedFlow<CaptureEvent> = _captureConfirmed

    fun capturedSpeciesIndices(): Flow<Set<Int>> =
        dao.capturedSpeciesIndices().map { it.toSet() }

    /** Set scopé sur une saison (toutes années cumulées). */
    fun capturedSpeciesIndices(season: Season): Flow<Set<Int>> =
        dao.capturedSpeciesIndicesForSeason(season.storedValue).map { it.toSet() }

    fun capturedRemarquableIds(): Flow<Set<Long>> =
        dao.capturedRemarquableIds().map { it.toSet() }

    /** Set scopé sur une saison (toutes années cumulées). */
    fun capturedRemarquableIds(season: Season): Flow<Set<Long>> =
        dao.capturedRemarquableIdsForSeason(season.storedValue).map { it.toSet() }

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
    ): Long {
        val isFirstOfSpecies = !remarquable && !dao.speciesAlreadyCaptured(speciesIndex)
        val rowId = dao.insert(
            CaptureEntity(
                arbreId = arbreId,
                speciesIndex = speciesIndex,
                remarquable = remarquable,
                timestamp = timestamp,
                latitudeDevice = latitudeDevice,
                longitudeDevice = longitudeDevice,
                photoPath = photoPath,
                season = Season.fromTimestamp(timestamp).storedValue,
            )
        )
        _captureConfirmed.tryEmit(
            CaptureEvent(
                arbreId = arbreId,
                speciesIndex = speciesIndex,
                remarquable = remarquable,
                isFirstOfSpecies = isFirstOfSpecies,
                latitudeDevice = latitudeDevice,
                longitudeDevice = longitudeDevice,
            )
        )
        return rowId
    }
}
