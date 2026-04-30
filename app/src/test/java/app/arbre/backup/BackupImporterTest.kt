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
                "a.jpg" to "AAA".toByteArray(),
                "b.jpg" to "BBB".toByteArray(),
            ),
        )
        val result = importStream(ByteArrayInputStream(zip), photosDir, dao)

        assertEquals(ImportResult.Success(imported = 2, skipped = 0, photosMissing = 0), result)
        assertEquals(2, dao.inserts.size)
        assertArrayEquals("AAA".toByteArray(), File(photosDir, "a.jpg").readBytes())
        assertArrayEquals("BBB".toByteArray(), File(photosDir, "b.jpg").readBytes())
    }

    // ---------- Idempotence : roundtrip = 2nd import = 0 inséré ----------

    @Test fun `importStream is idempotent across two imports of the same zip`() = runBlocking {
        val zip = buildZip(
            meta = META_V1,
            captures = capturesJson(CaptureRecord(1L, 1000L, "a.jpg")),
            photos = mapOf("a.jpg" to "AAA".toByteArray()),
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
            photos = mapOf("a.jpg" to "AAA".toByteArray()),
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
            photos = mapOf("a.jpg" to "AAA".toByteArray()),
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
        }
        return baos.toByteArray()
    }

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
