package app.arbre.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        // Format réellement stocké en DB (cf. dump 2026-05-04). C'est le cas
        // qui fait foi pour les badges Tourneur/Tour Complet sur device.
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
        // Format réellement stocké : « ..., PARIS XE ARRDT » après un préfixe
        // long (parc/square/voie).
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

    // ---------- evaluate : aucun, premier ----------

    @Test fun `evaluate with no captures returns 13 locked badges`() {
        val states = BadgeEvaluator.evaluate(emptyList(), emptyMap(), emptySpeciesInfo())
        assertEquals(13, states.size)
        assertTrue(states.all { !it.unlocked })
        assertEquals(BadgeCatalog.ALL.map { it.id }, states.map { it.def.id })
    }

    @Test fun `evaluate unlocks first capture after one capture`() {
        val ts = parisTs("2025-04-01T10:00:00Z")
        val arbre = arbre(id = 1L)
        val states = BadgeEvaluator.evaluate(
            captures = listOf(capture(arbreId = 1L, ts = ts)),
            arbresById = mapOf(1L to arbre),
            speciesInfo = emptySpeciesInfo(),
        )
        assertEquals(ts, unlockedAt(states, BadgeCatalog.FIRST_CAPTURE.id))
        assertNull(unlockedAt(states, BadgeCatalog.PROMENADE.id))
    }

    @Test fun `evaluate unlocks promenade at tenth capture and freezes timestamp`() {
        val captures = (0 until 12).map { i ->
            capture(arbreId = i.toLong(), ts = parisTs("2025-05-0${(i % 9) + 1}T10:00:00Z") + i)
        }
        val arbres = captures.associate { it.arbreId to arbre(id = it.arbreId) }
        val states = BadgeEvaluator.evaluate(captures, arbres, emptySpeciesInfo())
        // Tri chronologique → 10ème capture (index 9) déclenche promenade.
        val tenthTs = captures.sortedBy { it.timestamp }[9].timestamp
        assertEquals(tenthTs, unlockedAt(states, BadgeCatalog.PROMENADE.id))
        assertNull(unlockedAt(states, BadgeCatalog.MARCHEUR.id))
    }

    // ---------- demesure ----------

    @Test fun `evaluate unlocks geant when hauteur exceeds 30m`() {
        val ts = parisTs("2025-06-01T08:00:00Z")
        val states = BadgeEvaluator.evaluate(
            captures = listOf(capture(arbreId = 7L, ts = ts)),
            arbresById = mapOf(7L to arbre(id = 7L, hauteurM = 35)),
            speciesInfo = emptySpeciesInfo(),
        )
        assertEquals(ts, unlockedAt(states, BadgeCatalog.GEANT.id))
        assertNull(unlockedAt(states, BadgeCatalog.VIEUX_SAGE.id))
    }

    @Test fun `evaluate does not unlock geant at exactly 30m`() {
        val states = BadgeEvaluator.evaluate(
            captures = listOf(capture(arbreId = 7L, ts = 1L)),
            arbresById = mapOf(7L to arbre(id = 7L, hauteurM = 30)),
            speciesInfo = emptySpeciesInfo(),
        )
        assertNull(unlockedAt(states, BadgeCatalog.GEANT.id))
    }

    @Test fun `evaluate unlocks vieux sage when circ exceeds 400cm`() {
        val ts = parisTs("2025-06-01T08:00:00Z")
        val states = BadgeEvaluator.evaluate(
            captures = listOf(capture(arbreId = 8L, ts = ts)),
            arbresById = mapOf(8L to arbre(id = 8L, circonferenceCm = 450)),
            speciesInfo = emptySpeciesInfo(),
        )
        assertEquals(ts, unlockedAt(states, BadgeCatalog.VIEUX_SAGE.id))
    }

    // ---------- espèce rare ----------

    @Test fun `evaluate unlocks espece rare when species count below 100`() {
        val ts = parisTs("2025-04-01T10:00:00Z")
        val info = speciesInfo(speciesIndex = 42, count = 50)
        val states = BadgeEvaluator.evaluate(
            captures = listOf(capture(arbreId = 1L, speciesIndex = 42, ts = ts)),
            arbresById = mapOf(1L to arbre(id = 1L)),
            speciesInfo = info,
        )
        assertEquals(ts, unlockedAt(states, BadgeCatalog.ESPECE_RARE.id))
    }

    @Test fun `evaluate does not unlock espece rare for common species`() {
        val info = speciesInfo(speciesIndex = 42, count = 50_000)
        val states = BadgeEvaluator.evaluate(
            captures = listOf(capture(arbreId = 1L, speciesIndex = 42, ts = 1L)),
            arbresById = mapOf(1L to arbre(id = 1L)),
            speciesInfo = info,
        )
        assertNull(unlockedAt(states, BadgeCatalog.ESPECE_RARE.id))
    }

    @Test fun `evaluate ignores remarquable captures for species accumulators`() {
        // 50 captures remarquables ne débloque pas botaniste_amateur.
        val captures = (0 until 50).map { i ->
            capture(arbreId = i.toLong(), speciesIndex = i, remarquable = true, ts = i.toLong())
        }
        val arbres = captures.associate { it.arbreId to arbre(id = it.arbreId, remarquable = true) }
        val states = BadgeEvaluator.evaluate(captures, arbres, emptySpeciesInfo())
        assertNull(unlockedAt(states, BadgeCatalog.BOTANISTE_AMATEUR.id))
    }

    // ---------- géographie ----------

    @Test fun `evaluate unlocks tourneur de Paris with 10 distinct arrondissements`() {
        val captures = (1..10).map { arrNum ->
            capture(arbreId = arrNum.toLong(), ts = arrNum.toLong())
        }
        val arbres = (1..10).associate { arrNum ->
            arrNum.toLong() to arbre(id = arrNum.toLong(), adresse = "RUE TEST, ${arrNum}e")
        }
        val states = BadgeEvaluator.evaluate(captures, arbres, emptySpeciesInfo())
        assertNotNull(unlockedAt(states, BadgeCatalog.TOURNEUR_DE_PARIS.id))
        assertNull(unlockedAt(states, BadgeCatalog.TOUR_COMPLET.id))
    }

    @Test fun `evaluate unlocks tour complet with 20 distinct arrondissements`() {
        val captures = (1..20).map { capture(arbreId = it.toLong(), ts = it.toLong()) }
        val arbres = (1..20).associate { arrNum ->
            arrNum.toLong() to arbre(id = arrNum.toLong(), adresse = "RUE TEST, ${arrNum}e")
        }
        val states = BadgeEvaluator.evaluate(captures, arbres, emptySpeciesInfo())
        assertNotNull(unlockedAt(states, BadgeCatalog.TOUR_COMPLET.id))
    }

    @Test fun `evaluate ignores out-of-Paris addresses for arrondissement count`() {
        // 10 captures, toutes hors-Paris (adresse sans suffixe arr). Promenade
        // se débloque (totalCount), pas Tourneur (0 arr distincts).
        val captures = (1..10).map { capture(arbreId = it.toLong(), ts = it.toLong()) }
        val arbres = (1..10).associate { it.toLong() to arbre(id = it.toLong(), adresse = "Bois de Vincennes") }
        val states = BadgeEvaluator.evaluate(captures, arbres, emptySpeciesInfo())
        assertNotNull(unlockedAt(states, BadgeCatalog.PROMENADE.id))
        assertNull(unlockedAt(states, BadgeCatalog.TOURNEUR_DE_PARIS.id))
    }

    // ---------- remarquables ----------

    @Test fun `evaluate unlocks chasseur remarquables with 10 distinct remarquables`() {
        val captures = (0 until 10).map { i ->
            capture(arbreId = i.toLong(), remarquable = true, ts = i.toLong())
        }
        val arbres = captures.associate { it.arbreId to arbre(id = it.arbreId, remarquable = true) }
        val states = BadgeEvaluator.evaluate(captures, arbres, emptySpeciesInfo())
        assertEquals(9L, unlockedAt(states, BadgeCatalog.CHASSEUR_REMARQUABLES.id))
        assertNull(unlockedAt(states, BadgeCatalog.LEGENDE.id))
    }

    @Test fun `evaluate dedupes remarquables on arbreId`() {
        // 10 captures du même arbre remarquable → 1 seul remarquable distinct.
        val captures = (0 until 10).map { i ->
            capture(arbreId = 42L, remarquable = true, ts = i.toLong())
        }
        val arbres = mapOf(42L to arbre(id = 42L, remarquable = true))
        val states = BadgeEvaluator.evaluate(captures, arbres, emptySpeciesInfo())
        assertNull(unlockedAt(states, BadgeCatalog.CHASSEUR_REMARQUABLES.id))
    }

    // ---------- unlockedAt figé ----------

    @Test fun `unlockedAt does not move when more captures arrive after threshold`() {
        // 10 captures (déclenche promenade au ts=10), puis 5 de plus à ts > 100.
        val first10 = (1..10).map { i -> capture(arbreId = i.toLong(), ts = i.toLong()) }
        val later = (11..15).map { i -> capture(arbreId = i.toLong(), ts = (i * 100L)) }
        val arbres = (first10 + later).associate { it.arbreId to arbre(id = it.arbreId) }
        val states = BadgeEvaluator.evaluate(first10 + later, arbres, emptySpeciesInfo())
        assertEquals(10L, unlockedAt(states, BadgeCatalog.PROMENADE.id))
    }

    // ---------- helpers ----------

    private fun parisTs(iso: String): Long = Instant.parse(iso).toEpochMilli()

    private fun unlockedAt(states: List<BadgeState>, id: String): Long? =
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
    ): Arbre = Arbre(
        id = id,
        genre = "Platanus",
        espece = "x acerifolia",
        varieteCultivar = null,
        nomCommun = "Platane",
        hauteurM = hauteurM,
        circonferenceCm = circonferenceCm,
        remarquable = remarquable,
        adresse = adresse,
        latitude = 48.85,
        longitude = 2.35,
    )

    private fun emptySpeciesInfo(): SpeciesInfoRepository =
        SpeciesInfoRepository(emptyMap())

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
