package app.arbre.backup

import app.arbre.data.CaptureEntity
import java.io.File

/**
 * Format de l'archive d'export/import.
 *
 * Versionné via [CURRENT_SCHEMA_VERSION] dans `meta.json` :
 * - import `schemaVersion > CURRENT_SCHEMA_VERSION` → refusé (`SCHEMA_TOO_NEW`).
 * - import `schemaVersion <= CURRENT_SCHEMA_VERSION` → accepté.
 *
 * Bumper [CURRENT_SCHEMA_VERSION] chaque fois que le format devient
 * incompatible (champ retiré, sémantique changée).
 */
const val CURRENT_SCHEMA_VERSION: Int = 1

const val META_JSON: String = "meta.json"
const val CAPTURES_JSON: String = "captures.json"
const val PHOTOS_DIR: String = "photos/"

data class BackupMeta(
    val appVersionCode: Int,
    val appVersionName: String,
    val schemaVersion: Int,
    val captureCount: Int,
    val exportedAt: Long,
)

/**
 * Représentation portable d'une capture. On exclut volontairement le `id`
 * Room autoincrement — il n'a aucun sens d'un device à l'autre, et le ré-
 * insérer comme primary key bloquerait les imports multi-source.
 *
 * `photoFilename` est le basename UUID (ex. `8f3b...uuid.jpg`) — on
 * reconstruit le chemin absolu à l'import via
 * `getExternalFilesDir(null)/captures/<filename>`.
 */
data class CaptureExport(
    val arbreId: Long,
    val speciesIndex: Int,
    val remarquable: Boolean,
    val timestamp: Long,
    val latitudeDevice: Double,
    val longitudeDevice: Double,
    val photoFilename: String,
    val season: Int,
)

fun CaptureEntity.toExport(): CaptureExport = CaptureExport(
    arbreId = arbreId,
    speciesIndex = speciesIndex,
    remarquable = remarquable,
    timestamp = timestamp,
    latitudeDevice = latitudeDevice,
    longitudeDevice = longitudeDevice,
    photoFilename = File(photoPath).name,
    season = season,
)

fun CaptureExport.toEntity(capturesDir: File): CaptureEntity = CaptureEntity(
    id = 0L,
    arbreId = arbreId,
    speciesIndex = speciesIndex,
    remarquable = remarquable,
    timestamp = timestamp,
    latitudeDevice = latitudeDevice,
    longitudeDevice = longitudeDevice,
    photoPath = File(capturesDir, photoFilename).absolutePath,
    season = season,
)
