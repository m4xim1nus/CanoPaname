package app.arbre.backup

import android.content.Context
import android.net.Uri
import android.util.Log
import app.arbre.data.CaptureDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

sealed class ExportResult {
    data class Success(val captureCount: Int) : ExportResult()
    data class Failure(val cause: Throwable) : ExportResult()
}

/**
 * Génère un zip `arbres-export-YYYYMMDD.zip` contenant `meta.json`,
 * `captures.json` et un répertoire `photos/`. L'écriture passe par un
 * `Uri` SAF — l'utilisateur a choisi la destination via
 * `ACTION_CREATE_DOCUMENT` (Drive, Files, Documents…).
 *
 * Photo manquante sur disque : la capture est exportée sans son fichier
 * (cohérent avec la philosophie additive, l'import remontera un compteur
 * `photosMissing`).
 */
class BackupExporter(
    private val context: Context,
    private val captureDao: CaptureDao,
) {
    suspend fun export(target: Uri): ExportResult = withContext(Dispatchers.IO) {
        try {
            val captures = captureDao.allCapturesSnapshot()
            val pkg = context.packageManager.getPackageInfo(context.packageName, 0)
            val meta = BackupMeta(
                appVersionCode = pkg.longVersionCode.toInt(),
                appVersionName = pkg.versionName ?: "",
                schemaVersion = CURRENT_SCHEMA_VERSION,
                captureCount = captures.size,
                exportedAt = System.currentTimeMillis(),
            )
            val out = context.contentResolver.openOutputStream(target, "w")
                ?: return@withContext ExportResult.Failure(
                    IllegalStateException("openOutputStream returned null")
                )
            ZipOutputStream(out.buffered()).use { zip ->
                zip.putNextEntry(ZipEntry(META_JSON))
                zip.write(meta.toJson().toString(2).toByteArray())
                zip.closeEntry()

                zip.putNextEntry(ZipEntry(CAPTURES_JSON))
                val arr = JSONArray()
                for (capture in captures) {
                    arr.put(capture.toExport().toJson())
                }
                zip.write(arr.toString().toByteArray())
                zip.closeEntry()

                for (capture in captures) {
                    val photoFile = File(capture.photoPath)
                    if (!photoFile.exists() || photoFile.length() == 0L) {
                        Log.w(TAG, "Photo absente pour capture id=${capture.id} (${capture.photoPath})")
                        continue
                    }
                    zip.putNextEntry(ZipEntry(PHOTOS_DIR + photoFile.name))
                    photoFile.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            ExportResult.Success(captures.size)
        } catch (t: Throwable) {
            Log.e(TAG, "Export a échoué", t)
            ExportResult.Failure(t)
        }
    }

    private companion object {
        const val TAG = "BackupExporter"
    }
}

private fun BackupMeta.toJson(): JSONObject = JSONObject().apply {
    put("appVersionCode", appVersionCode)
    put("appVersionName", appVersionName)
    put("schemaVersion", schemaVersion)
    put("captureCount", captureCount)
    put("exportedAt", exportedAt)
}

private fun CaptureExport.toJson(): JSONObject = JSONObject().apply {
    put("arbreId", arbreId)
    put("speciesIndex", speciesIndex)
    put("remarquable", remarquable)
    put("timestamp", timestamp)
    put("latitudeDevice", latitudeDevice)
    put("longitudeDevice", longitudeDevice)
    put("photoFilename", photoFilename)
    put("season", season)
}
