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

// Caps anti-zipbomb. Un export réaliste pèse ~30 captures × ~500 KB ≈ 15 Mo —
// seuils ~30× au-dessus pour n'embêter aucun utilisateur légitime tout en
// bornant l'allocation mémoire d'un zip hostile.
internal const val MAX_ENTRY_BYTES = 10L * 1024 * 1024
internal const val MAX_TOTAL_BYTES = 500L * 1024 * 1024
internal const val MAX_ENTRY_COUNT = 10_000

// Magic JPEG SOI — suffit à rejeter un .jpg renommé. Pas une validation
// complète, BitmapFactory rattrapera la corruption au décodage.
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
 * Importe un zip d'export. Idempotent par dédup `(arbreId, timestamp)` :
 * un import partiel (zip tronqué, IO error) laisse les captures déjà
 * ingérées et une seconde tentative reprend là où on s'est arrêté.
 *
 * Photo absente mais capture présente : on insère quand même (compteur
 * `photosMissing` remonté à l'UI), on ne perd pas l'historique pour une
 * photo manquante.
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

private class ZipAccumulator {
    var meta: BackupMeta? = null
    var capturesJson: String? = null
    val photoBytes = HashMap<String, ByteArray>()
    var totalBytes = 0L
    var entryCount = 0
}

private fun ZipInputStream.handleEntry(name: String, acc: ZipAccumulator) {
    when {
        name == META_JSON -> {
            val bytes = readBytesCapped(MAX_ENTRY_BYTES)
            acc.totalBytes = checkTotal(acc.totalBytes + bytes.size)
            acc.meta = parseMeta(bytes)
        }
        name == CAPTURES_JSON -> {
            val bytes = readBytesCapped(MAX_ENTRY_BYTES)
            acc.totalBytes = checkTotal(acc.totalBytes + bytes.size)
            acc.capturesJson = String(bytes)
        }
        name.startsWith(PHOTOS_DIR) -> handlePhotoEntry(name, acc)
    }
}

private fun ZipInputStream.handlePhotoEntry(name: String, acc: ZipAccumulator) {
    val basename = name.removePrefix(PHOTOS_DIR)
    if (basename.isEmpty() || basename.contains('/')) return
    val bytes = readBytesCapped(MAX_ENTRY_BYTES)
    acc.totalBytes = checkTotal(acc.totalBytes + bytes.size)
    // Photo non-JPEG : skip silencieux ; comptée photosMissing si une
    // capture s'y réfère.
    if (hasJpegMagic(bytes)) acc.photoBytes[basename] = bytes
}

private fun extractZip(input: InputStream): ZipAccumulator {
    val acc = ZipAccumulator()
    ZipInputStream(input.buffered()).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            acc.entryCount++
            if (acc.entryCount > MAX_ENTRY_COUNT) {
                throw BackupTooLargeException("Backup contient > $MAX_ENTRY_COUNT entrées")
            }
            val name = entry.name
            if (!entry.isDirectory && !isPathSuspicious(name)) {
                zip.handleEntry(name, acc)
            }
            zip.closeEntry()
        }
    }
    return acc
}

internal suspend fun importStream(
    input: InputStream,
    photosDir: File,
    captureDao: CaptureDao,
): ImportResult = withContext(Dispatchers.IO) {
    val parsed = try {
        extractZip(input)
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

    if (parsed.entryCount == 0) {
        return@withContext ImportResult.Failure(ImportError.CORRUPT_ZIP)
    }
    val resolvedMeta = parsed.meta
        ?: return@withContext ImportResult.Failure(ImportError.META_MISSING)
    val resolvedCaptures = parsed.capturesJson
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
    val photoBytes: Map<String, ByteArray> = parsed.photoBytes

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
        captureDao.insert(capture.toEntity())
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

// Path traversal : couvre les `\` Windows-style, les `..` de tous types et
// les paths absolus, en plus du filtre basename.
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
