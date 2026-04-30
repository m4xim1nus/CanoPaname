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
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

private const val TAG = "BackupImporter"

enum class ImportError {
    CORRUPT_ZIP,
    META_MISSING,
    CAPTURES_MISSING,
    SCHEMA_TOO_NEW,
    IO_ERROR,
}

sealed class ImportResult {
    data class Success(
        val imported: Int,
        val skipped: Int,
        val photosMissing: Int,
    ) : ImportResult()

    data class Failure(val reason: ImportError) : ImportResult()
}

/**
 * Lit un zip d'export et applique les captures inconnues à la DB locale.
 *
 * Idempotence : dédup sur `(arbreId, timestamp)`. Un import partiel (zip
 * tronqué, IO error en cours) laisse les captures déjà ingérées —
 * la dédup garantit qu'une nouvelle tentative reprend là où on s'est
 * arrêté.
 *
 * Photo absente du zip mais capture présente dans `captures.json` :
 * la capture est insérée quand même (compteur `photosMissing`). On ne
 * veut pas perdre l'historique pour une photo manquante.
 *
 * Le zip est lu en deux passes mémoire :
 * 1. `meta.json` + `captures.json` parsés
 * 2. photos copiées vers `getExternalFilesDir(null)/captures/`
 *
 * Compromis simple — ~30 captures × 500 KB ≈ 15 Mo en mémoire, OK sur
 * Android. Si les volumes explosent, refactor en streaming-only.
 */
class BackupImporter(
    private val context: Context,
    private val captureDao: CaptureDao,
) {
    suspend fun import(source: Uri): ImportResult = withContext(Dispatchers.IO) {
        val inStream = context.contentResolver.openInputStream(source)
            ?: return@withContext ImportResult.Failure(ImportError.IO_ERROR)
        val photosDir = File(context.getExternalFilesDir(null), "captures").apply { mkdirs() }
        importStream(inStream, photosDir, captureDao)
    }
}

/**
 * Cœur logique de l'import, isolé de `Context` / `Uri` pour permettre des
 * tests JVM purs. Top-level `internal` pour rester appelable depuis le
 * dossier `test/` sans instance de [BackupImporter].
 */
internal suspend fun importStream(
    input: InputStream,
    photosDir: File,
    captureDao: CaptureDao,
): ImportResult = withContext(Dispatchers.IO) {
    var meta: BackupMeta? = null
    var capturesJson: String? = null
    val photoBytes = HashMap<String, ByteArray>()
    try {
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                if (entry.isDirectory) {
                    zip.closeEntry()
                    continue
                }
                when {
                    name == META_JSON -> meta = parseMeta(zip.readBytes())
                    name == CAPTURES_JSON -> capturesJson = String(zip.readBytes())
                    name.startsWith(PHOTOS_DIR) -> {
                        val basename = name.removePrefix(PHOTOS_DIR)
                        if (basename.isNotEmpty() && !basename.contains('/')) {
                            photoBytes[basename] = zip.readBytes()
                        }
                    }
                }
                zip.closeEntry()
            }
        }
    } catch (e: ZipException) {
        Log.e(TAG, "Zip corrompu", e)
        return@withContext ImportResult.Failure(ImportError.CORRUPT_ZIP)
    } catch (e: IOException) {
        Log.e(TAG, "IO error en import", e)
        return@withContext ImportResult.Failure(ImportError.IO_ERROR)
    } catch (t: Throwable) {
        Log.e(TAG, "Échec lecture zip", t)
        return@withContext ImportResult.Failure(ImportError.CORRUPT_ZIP)
    }

    val resolvedMeta = meta ?: return@withContext ImportResult.Failure(ImportError.META_MISSING)
    val resolvedCaptures = capturesJson
        ?: return@withContext ImportResult.Failure(ImportError.CAPTURES_MISSING)
    if (resolvedMeta.schemaVersion > CURRENT_SCHEMA_VERSION) {
        return@withContext ImportResult.Failure(ImportError.SCHEMA_TOO_NEW)
    }

    val captures = try {
        parseCaptures(resolvedCaptures)
    } catch (t: Throwable) {
        Log.e(TAG, "captures.json illisible", t)
        return@withContext ImportResult.Failure(ImportError.CAPTURES_MISSING)
    }

    var imported = 0
    var skipped = 0
    var photosMissing = 0

    for (capture in captures) {
        if (captureDao.captureExists(capture.arbreId, capture.timestamp)) {
            skipped++
            continue
        }
        val bytes = photoBytes[capture.photoFilename]
        if (bytes != null) {
            runCatching {
                File(photosDir, capture.photoFilename).writeBytes(bytes)
            }.onFailure {
                Log.w(TAG, "Échec écriture photo ${capture.photoFilename}", it)
                photosMissing++
            }
        } else {
            photosMissing++
        }
        captureDao.insert(capture.toEntity(photosDir))
        imported++
    }

    ImportResult.Success(imported = imported, skipped = skipped, photosMissing = photosMissing)
}

private fun parseMeta(bytes: ByteArray): BackupMeta {
    val o = JSONObject(String(bytes))
    return BackupMeta(
        appVersionCode = o.optInt("appVersionCode"),
        appVersionName = o.optString("appVersionName"),
        schemaVersion = o.getInt("schemaVersion"),
        captureCount = o.optInt("captureCount"),
        exportedAt = o.optLong("exportedAt"),
    )
}

private fun parseCaptures(json: String): List<CaptureExport> {
    val arr = JSONArray(json)
    return buildList(arr.length()) {
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            add(
                CaptureExport(
                    arbreId = o.getLong("arbreId"),
                    speciesIndex = o.getInt("speciesIndex"),
                    remarquable = o.getBoolean("remarquable"),
                    timestamp = o.getLong("timestamp"),
                    latitudeDevice = o.getDouble("latitudeDevice"),
                    longitudeDevice = o.getDouble("longitudeDevice"),
                    photoFilename = o.getString("photoFilename"),
                    season = o.getInt("season"),
                )
            )
        }
    }
}
