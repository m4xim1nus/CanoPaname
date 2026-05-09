package app.arbre.data

import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.map

/**
 * Émis après chaque INSERT réussi pour déclencher l'animation climax sans
 * repasser par un signal d'horodatage Room. `isFirstOfSpecies` est calculé
 * AVANT l'insert et reste valide *toutes saisons confondues*.
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
 * Toutes les écritures captures passent par ici. Les Flows Room ré-émettent
 * à chaque INSERT, ce qui propage la bascule gris → vert jusqu'à l'expression
 * `circleColor` de la layer MapLibre.
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

    /**
     * Suppression d'une capture : DELETE row d'abord (cascade Flow immédiate),
     * puis fichier disque en best-effort. Une photo orpheline ne casse rien
     * (aucun écran ne liste le filesystem) ; à l'inverse, un fichier supprimé
     * sous une row vivante laisserait un thumbnail cassé.
     */
    suspend fun deleteCapture(capture: Capture, photoFile: File): Boolean {
        dao.deleteById(capture.id)
        return runCatching { !photoFile.exists() || photoFile.delete() }
            .getOrDefault(false)
    }
}
