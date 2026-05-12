package app.arbre.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class BadgeEvaluatorTest {

    // ---------- parseArrondissement ----------

    @Test fun `parseArrondissement matches 1er`() {
        assertEquals(1, BadgeEvaluator.parseArrondissement("12 RUE DE RIVOLI, 1er"))
    }

    @Test fun `parseArrondissement matches double-digit e suffix`() {
        assertEquals(11, BadgeEvaluator.parseArrondissement("BD VOLTAIRE, 11e"))
        assertEquals(20, BadgeEvaluator.parseArrondissement("AVE GAMBETTA, 20e"))
    }

    @Test fun `parseArrondissement returns null when no suffix`() {
        assertNull(BadgeEvaluator.parseArrondissement("Hors-Paris"))
        assertNull(BadgeEvaluator.parseArrondissement("BOIS DE BOULOGNE"))
    }

    @Test fun `parseArrondissement rejects out-of-range`() {
        assertNull(BadgeEvaluator.parseArrondissement("CHEMIN, 21e"))
        assertNull(BadgeEvaluator.parseArrondissement("PLACE, 99e"))
    }

    @Test fun `parseArrondissement matches bare suffix without leading comma`() {
        // Format normalisé (suffixe sans virgule). Pas le cas réel device, mais
        // accepté pour rester compatible avec les tests historiques.
        assertEquals(5, BadgeEvaluator.parseArrondissement("5e"))
        assertEquals(1, BadgeEvaluator.parseArrondissement("1er"))
        assertEquals(20, BadgeEvaluator.parseArrondissement("20e"))
    }

    @Test fun `parseArrondissement matches raw OpenData format`() {
        // Format réellement stocké en DB (cf. dump 2026-05-04).
        assertEquals(
            12,
            BadgeEvaluator.parseArrondissement("PARC DE BERCY / 128 QUAI DE BERCY, PARIS 12E ARRDT"),
        )
        assertEquals(1, BadgeEvaluator.parseArrondissement("RUE DE RIVOLI, PARIS 1ER ARRDT"))
        assertEquals(20, BadgeEvaluator.parseArrondissement("AVE GAMBETTA, PARIS 20E ARRDT"))
    }

    // ---------- parseArrKey (catalogue Remarquables) ----------

    @Test fun `parseArrKey Paris on full address`() {
        assertEquals(ArrKey.Paris(5), parseArrKey("Rue de Rivoli, 5e"))
    }

    @Test fun `parseArrKey Paris on bare suffix`() {
        assertEquals(ArrKey.Paris(11), parseArrKey("11e"))
    }

    @Test fun `parseArrKey raw Paris with parc prefix`() {
        assertEquals(
            ArrKey.Paris(19),
            parseArrKey("PARC DES BUTTES CHAUMONT / 7 RUE BOTZARIS, PARIS 19E ARRDT"),
        )
    }

    @Test fun `parseArrKey BoisVincennes`() {
        assertEquals(ArrKey.BoisVincennes, parseArrKey("Bois de Vincennes"))
        assertEquals(
            ArrKey.BoisVincennes,
            parseArrKey("ILE DE BERCY / LAC DAUMESNIL, BOIS DE VINCENNES"),
        )
    }

    @Test fun `parseArrKey BoisBoulogne`() {
        assertEquals(ArrKey.BoisBoulogne, parseArrKey("Bois de Boulogne"))
        assertEquals(
            ArrKey.BoisBoulogne,
            parseArrKey("VIVACES PARC DE BAGATELLE / ROUTE DE SEVRES A NEUILLY, BOIS DE BOULOGNE"),
        )
    }

    @Test fun `parseArrKey Other for null and unrecognised`() {
        assertEquals(ArrKey.Other, parseArrKey(null))
        assertEquals(ArrKey.Other, parseArrKey(""))
        assertEquals(ArrKey.Other, parseArrKey("Hauts-de-Seine"))
    }

    // ---------- evaluate : aucun ----------

    @Test fun `evaluate with no captures returns all badges locked`() {
        val states = eval(emptyList(), emptyMap(), emptySpeciesInfo())
        assertEquals(BadgeCatalog.ALL.size, states.size)
        assertEquals(BadgeCatalog.ALL.map { it.id }, states.map { it.def.id })
        assertTrue(states.all { !it.unlocked })
    }

    // ---------- première capture (binaire) ----------

    @Test fun `evaluate unlocks premiere capture on the very first capture`() {
        val first = parisTs("2025-04-01T10:00:00Z")
        val second = parisTs("2025-04-02T10:00:00Z")
        val states = eval(
            captures = listOf(
                capture(arbreId = 2L, ts = second),
                capture(arbreId = 1L, ts = first),
            ),
            arbresById = mapOf(1L to arbre(id = 1L), 2L to arbre(id = 2L)),
            speciesInfo = emptySpeciesInfo(),
        )
        assertEquals(first, binaryUnlockedAt(states, BadgeCatalog.PREMIERE_CAPTURE.id))
    }

    // ---------- démesure (binaires) ----------

    @Test fun `evaluate unlocks geant when hauteur exceeds 30m`() {
        val ts = parisTs("2025-06-01T08:00:00Z")
        val states = eval(
            captures = listOf(capture(arbreId = 7L, ts = ts)),
            arbresById = mapOf(7L to arbre(id = 7L, hauteurM = 35)),
            speciesInfo = emptySpeciesInfo(),
        )
        assertEquals(ts, binaryUnlockedAt(states, BadgeCatalog.GEANT.id))
        assertNull(binaryUnlockedAt(states, BadgeCatalog.BONSAI.id))
        assertNull(binaryUnlockedAt(states, BadgeCatalog.VIEUX_SAGE.id))
    }

    @Test fun `evaluate does not unlock geant at exactly 30m`() {
        val states = eval(
            captures = listOf(capture(arbreId = 7L, ts = 1L)),
            arbresById = mapOf(7L to arbre(id = 7L, hauteurM = 30)),
            speciesInfo = emptySpeciesInfo(),
        )
        assertNull(binaryUnlockedAt(states, BadgeCatalog.GEANT.id))
    }

    @Test fun `evaluate unlocks bonsai when hauteur below 2m`() {
        val ts = parisTs("2025-06-01T08:00:00Z")
        val states = eval(
            captures = listOf(capture(arbreId = 7L, ts = ts)),
            arbresById = mapOf(7L to arbre(id = 7L, hauteurM = 1)),
            speciesInfo = emptySpeciesInfo(),
        )
        assertEquals(ts, binaryUnlockedAt(states, BadgeCatalog.BONSAI.id))
        assertNull(binaryUnlockedAt(states, BadgeCatalog.GEANT.id))
    }

    @Test fun `evaluate does not unlock bonsai at exactly 2m`() {
        val states = eval(
            captures = listOf(capture(arbreId = 7L, ts = 1L)),
            arbresById = mapOf(7L to arbre(id = 7L, hauteurM = 2)),
            speciesInfo = emptySpeciesInfo(),
        )
        assertNull(binaryUnlockedAt(states, BadgeCatalog.BONSAI.id))
    }

    @Test fun `evaluate unlocks vieux sage when circ exceeds 400cm`() {
        val ts = parisTs("2025-06-01T08:00:00Z")
        val states = eval(
            captures = listOf(capture(arbreId = 8L, ts = ts)),
            arbresById = mapOf(8L to arbre(id = 8L, circonferenceCm = 450)),
            speciesInfo = emptySpeciesInfo(),
        )
        assertEquals(ts, binaryUnlockedAt(states, BadgeCatalog.VIEUX_SAGE.id))
        assertNull(binaryUnlockedAt(states, BadgeCatalog.JEUNE_POUSSE.id))
    }

    @Test fun `evaluate unlocks jeune pousse when circ below 10cm`() {
        val ts = parisTs("2025-06-01T08:00:00Z")
        val states = eval(
            captures = listOf(capture(arbreId = 8L, ts = ts)),
            arbresById = mapOf(8L to arbre(id = 8L, circonferenceCm = 5)),
            speciesInfo = emptySpeciesInfo(),
        )
        assertEquals(ts, binaryUnlockedAt(states, BadgeCatalog.JEUNE_POUSSE.id))
        assertNull(binaryUnlockedAt(states, BadgeCatalog.VIEUX_SAGE.id))
    }

    @Test fun `evaluate does not unlock jeune pousse at exactly 10cm`() {
        val states = eval(
            captures = listOf(capture(arbreId = 8L, ts = 1L)),
            arbresById = mapOf(8L to arbre(id = 8L, circonferenceCm = 10)),
            speciesInfo = emptySpeciesInfo(),
        )
        assertNull(binaryUnlockedAt(states, BadgeCatalog.JEUNE_POUSSE.id))
    }

    // ---------- espèces ultra-rares (binaires) ----------

    @Test fun `evaluate unlocks the rarity badge matching the species exact count`() {
        val ts = parisTs("2025-04-01T10:00:00Z")
        val info = speciesInfo(speciesIndex = 42, count = 3)
        val states = eval(
            captures = listOf(capture(arbreId = 1L, speciesIndex = 42, ts = ts)),
            arbresById = mapOf(1L to arbre(id = 1L)),
            speciesInfo = info,
        )
        assertEquals(ts, binaryUnlockedAt(states, BadgeCatalog.ESPECE_TRINITE.id))
        assertNull(binaryUnlockedAt(states, BadgeCatalog.ESPECE_UNIQUE.id))
        assertNull(binaryUnlockedAt(states, BadgeCatalog.ESPECE_COUPLE.id))
        assertNull(binaryUnlockedAt(states, BadgeCatalog.ESPECE_QUATUOR.id))
        assertNull(binaryUnlockedAt(states, BadgeCatalog.ESPECE_QUINTETTE.id))
    }

    @Test fun `evaluate unlocks espece unique for a single-individual species`() {
        val info = speciesInfo(speciesIndex = 7, count = 1)
        val states = eval(
            captures = listOf(capture(arbreId = 1L, speciesIndex = 7, ts = 1L)),
            arbresById = mapOf(1L to arbre(id = 1L)),
            speciesInfo = info,
        )
        assertNotNull(binaryUnlockedAt(states, BadgeCatalog.ESPECE_UNIQUE.id))
    }

    @Test fun `evaluate does not unlock any rarity badge for a common species`() {
        val info = speciesInfo(speciesIndex = 42, count = 6)
        val states = eval(
            captures = listOf(capture(arbreId = 1L, speciesIndex = 42, ts = 1L)),
            arbresById = mapOf(1L to arbre(id = 1L)),
            speciesInfo = info,
        )
        assertTrue(BadgeCatalog.ESPECE_RARETE.values.all { binaryUnlockedAt(states, it.id) == null })
    }

    @Test fun `evaluate ignores remarquable captures for rarity badges`() {
        // Une capture remarquable n'alimente pas la dimension espèce, même si
        // l'espèce sous-jacente est ultra-rare.
        val info = speciesInfo(speciesIndex = 42, count = 1)
        val states = eval(
            captures = listOf(capture(arbreId = 1L, speciesIndex = 42, remarquable = true, ts = 1L)),
            arbresById = mapOf(1L to arbre(id = 1L, remarquable = true)),
            speciesInfo = info,
        )
        assertNull(binaryUnlockedAt(states, BadgeCatalog.ESPECE_UNIQUE.id))
    }

    // ---------- helpers ----------

    private fun parisTs(iso: String): Long = Instant.parse(iso).toEpochMilli()

    private fun binaryUnlockedAt(states: List<BadgeState>, id: String): Long? =
        states.first { it.def.id == id }.unlockedAt

    private fun capture(
        arbreId: Long,
        ts: Long,
        speciesIndex: Int = 0,
        remarquable: Boolean = false,
        season: Season = Season.SPRING,
    ): Capture = Capture(
        id = 0L,
        arbreId = arbreId,
        speciesIndex = speciesIndex,
        remarquable = remarquable,
        timestamp = ts,
        latitudeDevice = 48.85,
        longitudeDevice = 2.35,
        photoPath = "x.jpg",
        season = season,
    )

    private fun arbre(
        id: Long,
        hauteurM: Int? = null,
        circonferenceCm: Int? = null,
        remarquable: Boolean = false,
        adresse: String? = "RUE TEST, 5e",
        genre: String = "Platanus",
        espece: String = "x acerifolia",
    ): Arbre = Arbre(
        id = id,
        genre = genre,
        espece = espece,
        varieteCultivar = null,
        nomCommun = null,
        hauteurM = hauteurM,
        circonferenceCm = circonferenceCm,
        remarquable = remarquable,
        adresse = adresse,
        latitude = 48.85,
        longitude = 2.35,
    )

    private fun emptySpeciesInfo(): SpeciesInfoRepository =
        SpeciesInfoRepository(emptyMap())

    private fun eval(
        captures: List<Capture>,
        arbresById: Map<Long, Arbre>,
        speciesInfo: SpeciesInfoRepository = emptySpeciesInfo(),
    ): List<BadgeState> =
        BadgeEvaluator.evaluate(captures, arbresById, speciesInfo)

    private fun speciesInfo(speciesIndex: Int, count: Int): SpeciesInfoRepository {
        val info = SpeciesInfo(
            index = speciesIndex,
            wikipediaTitle = null,
            wikidataQid = null,
            summary = null,
            pdfUrl = null,
            stats = SpeciesStats(
                count = count,
                proportion = 0.0,
                medianHeightM = null,
                medianCircCm = null,
                topArrAbs = emptyList(),
                topArrOver = emptyList(),
            ),
        )
        return SpeciesInfoRepository(mapOf(speciesIndex to info))
    }
}
