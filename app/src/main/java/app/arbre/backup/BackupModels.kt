package app.arbre.backup

import app.arbre.data.CaptureEntity

/**
 * Versionnage du format archive. Bumper [CURRENT_SCHEMA_VERSION] à chaque
 * incompatibilité (champ retiré, sémantique changée). Import accepté ssi
 * `schemaVersion <= CURRENT_SCHEMA_VERSION`.
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
 * Capture portable. Pas de `id` Room — autoincrement, n'a aucun sens d'un
 * device à l'autre. Depuis v3, `photoFilename == Capture.photoPath` (basename),
 * mapping 1:1.
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
    photoFilename = photoPath,
    season = season,
)

fun CaptureExport.toEntity(): CaptureEntity = CaptureEntity(
    id = 0L,
    arbreId = arbreId,
    speciesIndex = speciesIndex,
    remarquable = remarquable,
    timestamp = timestamp,
    latitudeDevice = latitudeDevice,
    longitudeDevice = longitudeDevice,
    photoPath = photoFilename,
    season = season,
)
