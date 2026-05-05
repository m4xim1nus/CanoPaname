package app.arbre.backup

import app.arbre.data.CaptureDao
import app.arbre.data.CaptureEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupImporterTest {

    @get:Rule val tempFolder = TemporaryFolder()

    private lateinit var photosDir: File
    private lateinit var dao: FakeCaptureDao

    @Before fun setUp() {
        photosDir = tempFolder.newFolder("captures")
        dao = FakeCaptureDao()
    }

    // ---------- Cas nominal ----------

    @Test fun `importStream succeeds on a well-formed zip with two captures`() = runBlocking {
        val zip = buildZip(
            meta = META_V1,
            captures = capturesJson(
                CaptureRecord(1L, 1000L, "a.jpg"),
                CaptureRecord(2L, 2000L, "b.jpg"),
            ),
            photos = mapOf(
                "a.jpg" to jpegBytes("AAA"),
                "b.jpg" to jpegBytes("BBB"),
            ),
        )
        val result = importStream(ByteArrayInputStream(zip), photosDir, dao)

        assertEquals(ImportResult.Success(imported = 2, skipped = 0, photosMissing = 0), result)
        assertEquals(2, dao.inserts.size)
        assertArrayEquals(jpegBytes("AAA"), File(photosDir, "a.jpg").readBytes())
        assertArrayEquals(jpegBytes("BBB"), File(photosDir, "b.jpg").readBytes())
    }

    // ---------- Idempotence : roundtrip = 2nd import = 0 inséré ----------

    @Test fun `importStream is idempotent across two imports of the same zip`() = runBlocking {
        val zip = buildZip(
            meta = META_V1,
            captures = capturesJson(CaptureRecord(1L, 1000L, "a.jpg")),
            photos = mapOf("a.jpg" to jpegBytes("AAA")),
        )

        val first = importStream(ByteArrayInputStream(zip), photosDir, dao)
        assertEquals(ImportResult.Success(imported = 1, skipped = 0, photosMissing = 0), first)

        val second = importStream(ByteArrayInputStream(zip), photosDir, dao)
        assertEquals(ImportResult.Success(imported = 0, skipped = 1, photosMissing = 0), second)
        assertEquals(1, dao.inserts.size)
    }

    // ---------- Photo manquante ----------

    @Test fun `importStream still inserts capture when photo is missing from zip`() = runBlocking {
        // captures.json référence a.jpg mais aucune entry photos/a.jpg dans le zip.
        val zip = buildZip(
            meta = META_V1,
            captures = capturesJson(CaptureRecord(1L, 1000L, "a.jpg")),
            photos = emptyMap(),
        )
        val result = importStream(ByteArrayInputStream(zip), photosDir, dao)

        assertEquals(ImportResult.Success(imported = 1, skipped = 0, photosMissing = 1), result)
        assertEquals(1, dao.inserts.size)
        assertFalse(File(photosDir, "a.jpg").exists())
    }

    // ---------- Refus dur sur schemaVersion future ----------

    @Test fun `importStream refuses zip with schemaVersion above current`() = runBlocking {
        val zip = buildZip(
            meta = """{"schemaVersion": 99, "appVersionCode": 7, "appVersionName": "9.9.9", "captureCount": 1, "exportedAt": 0}""",
            captures = capturesJson(CaptureRecord(1L, 1000L, "a.jpg")),
            photos = mapOf("a.jpg" to jpegBytes("AAA")),
        )
        val result = importStream(ByteArrayInputStream(zip), photosDir, dao)

        assertEquals(ImportResult.Failure(ImportError.SCHEMA_TOO_NEW), result)
        assertTrue(dao.inserts.isEmpty())
        assertFalse(File(photosDir, "a.jpg").exists())
    }

    // ---------- Zip corrompu ----------

    @Test fun `importStream returns CORRUPT_ZIP when input is not a zip`() = runBlocking {
        // Bytes qui ne sont pas un zip valide.
        val garbage = byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07)
        val result = importStream(ByteArrayInputStream(garbage), photosDir, dao)

        assertEquals(ImportResult.Failure(ImportError.CORRUPT_ZIP), result)
        assertTrue(dao.inserts.isEmpty())
    }

    // ---------- Meta absent ----------

    @Test fun `importStream returns META_MISSING when meta_json is absent`() = runBlocking {
        val zip = buildZip(
            meta = null,
            captures = capturesJson(CaptureRecord(1L, 1000L, "a.jpg")),
            photos = mapOf("a.jpg" to jpegBytes("AAA")),
        )
        val result = importStream(ByteArrayInputStream(zip), photosDir, dao)
        assertEquals(ImportResult.Failure(ImportError.META_MISSING), result)
    }

    // ---------- Captures absent ----------

    @Test fun `importStream returns CAPTURES_MISSING when captures_json is absent`() = runBlocking {
        val zip = buildZip(
            meta = META_V1,
            captures = null,
            photos = emptyMap(),
        )
        val result = importStream(ByteArrayInputStream(zip), photosDir, dao)
        assertEquals(ImportResult.Failure(ImportError.CAPTURES_MISSING), result)
    }

    // ---------- Hardening anti-zipbomb / path traversal / non-JPEG ----------

    @Test fun `importStream rejects zip whose photo entry exceeds MAX_ENTRY_BYTES`() = runBlocking {
        val oversize = jpegBytes("X").copyOf((MAX_ENTRY_BYTES + 1).toInt())
        // Repose le marker JPEG en début après le copyOf.
        oversize[0] = 0xFF.toByte(); oversize[1] = 0xD8.toByte(); oversize[2] = 0xFF.toByte()
        val zip = buildZip(
            meta = META_V1,
            captures = capturesJson(CaptureRecord(1L, 1000L, "a.jpg")),
            photos = mapOf("a.jpg" to oversize),
        )
        val result = importStream(ByteArrayInputStream(zip), photosDir, dao)
        assertEquals(ImportResult.Failure(ImportError.TOO_LARGE), result)
        assertTrue(dao.inserts.isEmpty())
    }

    // Note : pas de test JVM pour `MAX_TOTAL_BYTES` (500 Mo) — un test fidèle
    // accumulerait ~500 Mo en RAM avant le throw, risque d'OOM sur la heap par
    // défaut du test runner. La protection est vérifiable par lecture de
    // `checkTotal` dans BackupImporter.kt.

    @Test fun `importStream rejects zip with more than MAX_ENTRY_COUNT entries`() = runBlocking {
        val photos = (0..MAX_ENTRY_COUNT).associate { "p$it.jpg" to jpegBytes("X") }
        val zip = buildZip(meta = META_V1, captures = capturesJson(), photos = photos)
        val result = importStream(ByteArrayInputStream(zip), photosDir, dao)
        assertEquals(ImportResult.Failure(ImportError.TOO_LARGE), result)
        assertTrue(dao.inserts.isEmpty())
    }

    @Test fun `importStream skips non-JPEG photos and counts them as photosMissing`() = runBlocking {
        // Bytes ne commençant pas par FF D8 FF — un fichier renommé en .jpg.
        val zip = buildZip(
            meta = META_V1,
            captures = capturesJson(CaptureRecord(1L, 1000L, "a.jpg")),
            photos = mapOf("a.jpg" to "PKfakezip".toByteArray()),
        )
        val result = importStream(ByteArrayInputStream(zip), photosDir, dao)
        assertEquals(ImportResult.Success(imported = 1, skipped = 0, photosMissing = 1), result)
        assertFalse(File(photosDir, "a.jpg").exists())
    }

    @Test fun `importStream silently skips entries with path traversal markers`() = runBlocking {
        // Les noms d'entrée contenant `..` ou `\\` ou démarrant par `/` sont refusés.
        // L'entry valide en parallèle doit toujours s'importer.
        val zip = buildZip(
            meta = META_V1,
            captures = capturesJson(CaptureRecord(1L, 1000L, "ok.jpg")),
            photos = mapOf(
                "ok.jpg" to jpegBytes("OK"),
            ),
            extraEntries = listOf(
                "../etc/passwd" to "ROOT".toByteArray(),
                "photos/..\\evil.jpg" to jpegBytes("E"),
                "/abs/path.jpg" to jpegBytes("A"),
            ),
        )
        val result = importStream(ByteArrayInputStream(zip), photosDir, dao)
        assertEquals(ImportResult.Success(imported = 1, skipped = 0, photosMissing = 0), result)
        assertEquals(1, dao.inserts.size)
        assertTrue(File(photosDir, "ok.jpg").exists())
        // Aucun fichier surnuméraire écrit.
        assertEquals(setOf("ok.jpg"), photosDir.listFiles()?.map { it.name }?.toSet())
    }

    // ---------- helpers ----------

    private data class CaptureRecord(val arbreId: Long, val ts: Long, val photo: String)

    private fun capturesJson(vararg records: CaptureRecord): String =
        records.joinToString(prefix = "[", postfix = "]") { rec ->
            """{"arbreId": ${rec.arbreId}, "speciesIndex": 0, "remarquable": false, "timestamp": ${rec.ts}, "latitudeDevice": 48.85, "longitudeDevice": 2.35, "photoFilename": "${rec.photo}", "season": 1}"""
        }

    private fun buildZip(
        meta: String?,
        captures: String?,
        photos: Map<String, ByteArray>,
        extraEntries: List<Pair<String, ByteArray>> = emptyList(),
    ): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip ->
            if (meta != null) {
                zip.putNextEntry(ZipEntry(META_JSON))
                zip.write(meta.toByteArray())
                zip.closeEntry()
            }
            if (captures != null) {
                zip.putNextEntry(ZipEntry(CAPTURES_JSON))
                zip.write(captures.toByteArray())
                zip.closeEntry()
            }
            for ((name, bytes) in photos) {
                zip.putNextEntry(ZipEntry(PHOTOS_DIR + name))
                zip.write(bytes)
                zip.closeEntry()
            }
            for ((name, bytes) in extraEntries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    private fun jpegBytes(suffix: String): ByteArray =
        byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()) + suffix.toByteArray()

    private companion object {
        const val META_V1 = """{"schemaVersion": 1, "appVersionCode": 7, "appVersionName": "0.7.0", "captureCount": 2, "exportedAt": 0}"""
    }
}

private class FakeCaptureDao : CaptureDao {
    val inserts = mutableListOf<CaptureEntity>()
    private val seen = HashSet<Pair<Long, Long>>()

    override suspend fun insert(capture: CaptureEntity): Long {
        inserts.add(capture)
        seen.add(capture.arbreId to capture.timestamp)
        return inserts.size.toLong()
    }

    override suspend fun captureExists(arbreId: Long, timestamp: Long): Boolean =
        (arbreId to timestamp) in seen

    // Le path d'import ne touche pas les Flows ni les snapshots.
    override fun capturedSpeciesIndices(): Flow<List<Int>> = flowOf(emptyList())
    override fun capturedSpeciesIndicesForSeason(season: Int): Flow<List<Int>> = flowOf(emptyList())
    override fun capturedRemarquableIds(): Flow<List<Long>> = flowOf(emptyList())
    override fun capturedRemarquableIdsForSeason(season: Int): Flow<List<Long>> = flowOf(emptyList())
    override fun capturesPourArbre(arbreId: Long): Flow<List<CaptureEntity>> = flowOf(emptyList())
    override fun toutesLesCaptures(): Flow<List<CaptureEntity>> = flowOf(emptyList())
    override fun capturesRemarquables(): Flow<List<CaptureEntity>> = flowOf(emptyList())
    override fun firstCaptureTimestamp(): Flow<Long?> = flowOf(null)
    override fun captureCount(): Flow<Int> = flowOf(0)
    override suspend fun allCapturesSnapshot(): List<CaptureEntity> = emptyList()
    override suspend fun speciesAlreadyCaptured(speciesIndex: Int): Boolean = false
}
