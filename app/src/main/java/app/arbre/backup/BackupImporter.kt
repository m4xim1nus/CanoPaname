package app.arbre.backup

import android.content.Context
import android.net.Uri
import android.util.Log
import app.arbre.data.CaptureDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

private const val TAG = "BackupImporter"

// Caps anti-zipbomb. Calibrés pour la cible family & friends : un export
// réaliste pèse ~30 captures × ~500 KB photos ≈ 15 Mo. Les seuils sont
// volontairement larges (~30× la taille typique) pour ne jamais frustrer un
// utilisateur légitime tout en bornant l'allocation mémoire d'un zip hostile.
internal const val MAX_ENTRY_BYTES = 10L * 1024 * 1024
internal const val MAX_TOTAL_BYTES = 500L * 1024 * 1024
internal const val MAX_ENTRY_COUNT = 10_000

// Magic JPEG SOI + start of next marker. Suffit à rejeter un .jpg renommé
// arbitraire ; pas une validation complète (un fichier corrompu post-magic
// passera, mais BitmapFactory s'en chargera au décodage).
private val JPEG_MAGIC = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())

private class BackupTooLargeException(message: String) : IOException(message)

enum class ImportError {
    CORRUPT_ZIP,
    META_MISSING,
    CAPTURES_MISSING,
    SCHEMA_TOO_NEW,
    TOO_LARGE,
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
    var entryCount = 0
    var totalBytes = 0L
    try {
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entryCount++
                if (entryCount > MAX_ENTRY_COUNT) {
                    throw BackupTooLargeException("Backup contient > $MAX_ENTRY_COUNT entrées")
                }
                val name = entry.name
                if (entry.isDirectory) {
                    zip.closeEntry()
                    continue
                }
                if (isPathSuspicious(name)) {
                    zip.closeEntry()
                    continue
                }
                when {
                    name == META_JSON -> {
                        val bytes = zip.readBytesCapped(MAX_ENTRY_BYTES)
                        totalBytes = checkTotal(totalBytes + bytes.size)
                        meta = parseMeta(bytes)
                    }
                    name == CAPTURES_JSON -> {
                        val bytes = zip.readBytesCapped(MAX_ENTRY_BYTES)
                        totalBytes = checkTotal(totalBytes + bytes.size)
                        capturesJson = String(bytes)
                    }
                    name.startsWith(PHOTOS_DIR) -> {
                        val basename = name.removePrefix(PHOTOS_DIR)
                        if (basename.isNotEmpty() && !basename.contains('/')) {
                            val bytes = zip.readBytesCapped(MAX_ENTRY_BYTES)
                            totalBytes = checkTotal(totalBytes + bytes.size)
                            if (hasJpegMagic(bytes)) {
                                photoBytes[basename] = bytes
                            }
                            // sinon : skip silencieux, la capture sera comptée
                            // photosMissing si elle référence cette photo.
                        }
                    }
                }
                zip.closeEntry()
            }
        }
    } catch (e: BackupTooLargeException) {
        Log.e(TAG, "Backup trop volumineux", e)
        return@withContext ImportResult.Failure(ImportError.TOO_LARGE)
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

    if (entryCount == 0) {
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

private fun ZipInputStream.readBytesCapped(maxBytes: Long): ByteArray {
    val out = ByteArrayOutputStream()
    val buf = ByteArray(8192)
    var total = 0L
    while (true) {
        val n = read(buf)
        if (n < 0) break
        total += n.toLong()
        if (total > maxBytes) {
            throw BackupTooLargeException("Entrée dépasse $maxBytes octets")
        }
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}

private fun checkTotal(running: Long): Long {
    if (running > MAX_TOTAL_BYTES) {
        throw BackupTooLargeException("Backup dépasse $MAX_TOTAL_BYTES octets cumulés")
    }
    return running
}

// Refus des noms d'entrée qui essaient de sortir du dossier d'extraction
// (path traversal). Le filtre `!basename.contains('/')` côté boucle ne couvre
// que les sous-dossiers UNIX ; il faut aussi rejeter les `\` Windows-style et
// les `..` de tous types.
private fun isPathSuspicious(name: String): Boolean =
    name.contains('\\') || name.contains("..") || name.startsWith('/')

private fun hasJpegMagic(bytes: ByteArray): Boolean =
    bytes.size >= 3 &&
        bytes[0] == JPEG_MAGIC[0] &&
        bytes[1] == JPEG_MAGIC[1] &&
        bytes[2] == JPEG_MAGIC[2]

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
