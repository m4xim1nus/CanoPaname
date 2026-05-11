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

    @Test fun `evaluate with no captures returns 9 badges all locked`() {
        val states = eval(emptyList(), emptyMap(), emptySpeciesInfo())
        assertEquals(9, states.size)
        assertEquals(BadgeCatalog.ALL.map { it.id }, states.map { it.def.id })
        assertTrue(states.all { !it.unlocked })
        // 4 Progressive (Marcheur, Botaniste, Mosaïque, Chasseur) + 5 Binary
        assertEquals(4, states.filterIsInstance<BadgeState.Progressive>().size)
        assertEquals(5, states.filterIsInstance<BadgeState.Binary>().size)
    }

    @Test fun `evaluate unlocks marcheur tier 1 after first capture`() {
        val ts = parisTs("2025-04-01T10:00:00Z")
        val arbre = arbre(id = 1L)
        val states = eval(
            captures = listOf(capture(arbreId = 1L, ts = ts)),
            arbresById = mapOf(1L to arbre),
            speciesInfo = emptySpeciesInfo(),
        )
        val marcheur = progressive(states, BadgeCatalog.MARCHEUR.id)
        assertEquals(1, marcheur.currentCount)
        assertEquals(1, marcheur.unlockedTierCount)
        assertEquals(ts, tierUnlockedAt(marcheur, threshold = 1))
        assertNull(tierUnlockedAt(marcheur, threshold = 10))
        assertEquals(10, marcheur.nextTier?.threshold)
    }

    @Test fun `evaluate unlocks marcheur tier 10 at tenth capture and freezes tier 1 timestamp`() {
        val captures = (0 until 12).map { i ->
            capture(arbreId = i.toLong(), ts = parisTs("2025-05-0${(i % 9) + 1}T10:00:00Z") + i)
        }
        val arbres = captures.associate { it.arbreId to arbre(id = it.arbreId) }
        val states = eval(captures, arbres, emptySpeciesInfo())
        val marcheur = progressive(states, BadgeCatalog.MARCHEUR.id)

        val sorted = captures.sortedBy { it.timestamp }
        assertEquals(sorted[0].timestamp, tierUnlockedAt(marcheur, threshold = 1))
        assertEquals(sorted[9].timestamp, tierUnlockedAt(marcheur, threshold = 10))
        assertNull(tierUnlockedAt(marcheur, threshold = 25))
        assertEquals(12, marcheur.currentCount)
        assertEquals(2, marcheur.unlockedTierCount)
    }

    @Test fun `evaluate exposes intermediate marcheur progress between tiers`() {
        // 37 captures → tiers 1, 10, 25 atteints, 50 verrouillé. nextTier = 50.
        val captures = (1..37).map { i -> capture(arbreId = i.toLong(), ts = i.toLong()) }
        val arbres = captures.associate { it.arbreId to arbre(id = it.arbreId) }
        val states = eval(captures, arbres, emptySpeciesInfo())
        val marcheur = progressive(states, BadgeCatalog.MARCHEUR.id)

        assertEquals(37, marcheur.currentCount)
        assertEquals(3, marcheur.unlockedTierCount)
        assertNotNull(tierUnlockedAt(marcheur, threshold = 25))
        assertNull(tierUnlockedAt(marcheur, threshold = 50))
        assertEquals(50, marcheur.nextTier?.threshold)
    }

    @Test fun `evaluate freezes tier unlockedAt on the threshold-crossing capture`() {
        // 10 captures (déclenche tier 10 à ts=10), puis 5 de plus à ts > 100.
        // Le tier 10 doit garder ts=10 (figé), pas se déplacer sur des captures ultérieures.
        val first10 = (1..10).map { i -> capture(arbreId = i.toLong(), ts = i.toLong()) }
        val later = (11..15).map { i -> capture(arbreId = i.toLong(), ts = (i * 100L)) }
        val arbres = (first10 + later).associate { it.arbreId to arbre(id = it.arbreId) }
        val states = eval(first10 + later, arbres, emptySpeciesInfo())
        val marcheur = progressive(states, BadgeCatalog.MARCHEUR.id)

        assertEquals(10L, tierUnlockedAt(marcheur, threshold = 10))
        assertEquals(1L, tierUnlockedAt(marcheur, threshold = 1))
        assertEquals(15, marcheur.currentCount)
    }

    // ---------- demesure (binaires) ----------

    @Test fun `evaluate unlocks geant when hauteur exceeds 30m`() {
        val ts = parisTs("2025-06-01T08:00:00Z")
        val states = eval(
            captures = listOf(capture(arbreId = 7L, ts = ts)),
            arbresById = mapOf(7L to arbre(id = 7L, hauteurM = 35)),
            speciesInfo = emptySpeciesInfo(),
        )
        assertEquals(ts, binaryUnlockedAt(states, BadgeCatalog.GEANT.id))
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

    @Test fun `evaluate unlocks vieux sage when circ exceeds 400cm`() {
        val ts = parisTs("2025-06-01T08:00:00Z")
        val states = eval(
            captures = listOf(capture(arbreId = 8L, ts = ts)),
            arbresById = mapOf(8L to arbre(id = 8L, circonferenceCm = 450)),
            speciesInfo = emptySpeciesInfo(),
        )
        assertEquals(ts, binaryUnlockedAt(states, BadgeCatalog.VIEUX_SAGE.id))
    }

    // ---------- espèce rare (binaire) ----------

    @Test fun `evaluate unlocks espece rare when species count below 100`() {
        val ts = parisTs("2025-04-01T10:00:00Z")
        val info = speciesInfo(speciesIndex = 42, count = 50)
        val states = eval(
            captures = listOf(capture(arbreId = 1L, speciesIndex = 42, ts = ts)),
            arbresById = mapOf(1L to arbre(id = 1L)),
            speciesInfo = info,
        )
        assertEquals(ts, binaryUnlockedAt(states, BadgeCatalog.ESPECE_RARE.id))
    }

    @Test fun `evaluate does not unlock espece rare for common species`() {
        val info = speciesInfo(speciesIndex = 42, count = 50_000)
        val states = eval(
            captures = listOf(capture(arbreId = 1L, speciesIndex = 42, ts = 1L)),
            arbresById = mapOf(1L to arbre(id = 1L)),
            speciesInfo = info,
        )
        assertNull(binaryUnlockedAt(states, BadgeCatalog.ESPECE_RARE.id))
    }

    @Test fun `evaluate ignores remarquable captures for botaniste`() {
        // 50 captures remarquables ne débloque pas le tier 50 du Botaniste.
        val captures = (0 until 50).map { i ->
            capture(arbreId = i.toLong(), speciesIndex = i, remarquable = true, ts = i.toLong())
        }
        val arbres = captures.associate { it.arbreId to arbre(id = it.arbreId, remarquable = true) }
        val states = eval(captures, arbres, emptySpeciesInfo())
        val botaniste = progressive(states, BadgeCatalog.BOTANISTE.id)
        assertEquals(0, botaniste.currentCount)
        assertEquals(0, botaniste.unlockedTierCount)
    }

    @Test fun `evaluate unlocks botaniste tiers progressively`() {
        // 12 espèces distinctes → tiers 1 et 10 atteints, 25 verrouillé.
        val captures = (0 until 12).map { i ->
            capture(arbreId = i.toLong(), speciesIndex = i, ts = i.toLong())
        }
        val arbres = captures.associate { it.arbreId to arbre(id = it.arbreId) }
        val states = eval(captures, arbres, emptySpeciesInfo())
        val botaniste = progressive(states, BadgeCatalog.BOTANISTE.id)

        assertEquals(12, botaniste.currentCount)
        assertEquals(2, botaniste.unlockedTierCount)
        assertNotNull(tierUnlockedAt(botaniste, threshold = 10))
        assertNull(tierUnlockedAt(botaniste, threshold = 25))
    }

    // ---------- géographie (binaires) ----------

    @Test fun `evaluate unlocks tourneur de Paris with 10 distinct arrondissements`() {
        val captures = (1..10).map { arrNum ->
            capture(arbreId = arrNum.toLong(), ts = arrNum.toLong())
        }
        val arbres = (1..10).associate { arrNum ->
            arrNum.toLong() to arbre(id = arrNum.toLong(), adresse = "RUE TEST, ${arrNum}e")
        }
        val states = eval(captures, arbres, emptySpeciesInfo())
        assertNotNull(binaryUnlockedAt(states, BadgeCatalog.TOURNEUR_DE_PARIS.id))
        assertNull(binaryUnlockedAt(states, BadgeCatalog.TOUR_COMPLET.id))
    }

    @Test fun `evaluate unlocks tour complet with 20 distinct arrondissements`() {
        val captures = (1..20).map { capture(arbreId = it.toLong(), ts = it.toLong()) }
        val arbres = (1..20).associate { arrNum ->
            arrNum.toLong() to arbre(id = arrNum.toLong(), adresse = "RUE TEST, ${arrNum}e")
        }
        val states = eval(captures, arbres, emptySpeciesInfo())
        assertNotNull(binaryUnlockedAt(states, BadgeCatalog.TOUR_COMPLET.id))
    }

    @Test fun `evaluate ignores out-of-Paris addresses for arrondissement count`() {
        // 10 captures hors-Paris : marcheur tier 10 se débloque, pas Tourneur.
        val captures = (1..10).map { capture(arbreId = it.toLong(), ts = it.toLong()) }
        val arbres = (1..10).associate { it.toLong() to arbre(id = it.toLong(), adresse = "Bois de Vincennes") }
        val states = eval(captures, arbres, emptySpeciesInfo())
        val marcheur = progressive(states, BadgeCatalog.MARCHEUR.id)
        assertNotNull(tierUnlockedAt(marcheur, threshold = 10))
        assertNull(binaryUnlockedAt(states, BadgeCatalog.TOURNEUR_DE_PARIS.id))
    }

    // ---------- chasseur (progressif) ----------

    @Test fun `evaluate unlocks chasseur tier 10 with 10 distinct remarquables`() {
        val captures = (0 until 10).map { i ->
            capture(arbreId = i.toLong(), remarquable = true, ts = i.toLong())
        }
        val arbres = captures.associate { it.arbreId to arbre(id = it.arbreId, remarquable = true) }
        val states = eval(captures, arbres, emptySpeciesInfo())
        val chasseur = progressive(states, BadgeCatalog.CHASSEUR.id)
        assertEquals(10, chasseur.currentCount)
        assertEquals(9L, tierUnlockedAt(chasseur, threshold = 10))
        assertNull(tierUnlockedAt(chasseur, threshold = 25))
    }

    @Test fun `evaluate dedupes remarquables on arbreId`() {
        // 10 captures du même arbre remarquable → 1 seul remarquable distinct.
        val captures = (0 until 10).map { i ->
            capture(arbreId = 42L, remarquable = true, ts = i.toLong())
        }
        val arbres = mapOf(42L to arbre(id = 42L, remarquable = true))
        val states = eval(captures, arbres, emptySpeciesInfo())
        val chasseur = progressive(states, BadgeCatalog.CHASSEUR.id)
        // Tier 1 atteint (1 remarquable distinct), tier 5 non.
        assertEquals(1, chasseur.currentCount)
        assertNotNull(tierUnlockedAt(chasseur, threshold = 1))
        assertNull(tierUnlockedAt(chasseur, threshold = 5))
    }

    // ---------- sémantique sp. (S8) ----------

    @Test fun `evaluate excludes unknownSpecies captures from botaniste`() {
        // sk=99 est unknownSpecies (Tilia sp.). 5 captures de ce sk seul.
        val index = SpeciesIndex(listOf(
            SpeciesEntry(index = 99, genre = "Tilia", espece = "sp.", unknownSpecies = true),
        ))
        val captures = (0 until 5).map { i ->
            capture(arbreId = i.toLong(), speciesIndex = 99, ts = i.toLong())
        }
        val arbres = captures.associate { it.arbreId to arbre(id = it.arbreId, genre = "Tilia", espece = "sp.") }
        val states = eval(captures, arbres, speciesIndex = index)
        val botaniste = progressive(states, BadgeCatalog.BOTANISTE.id)
        // Aucune progression (toutes les captures sont sp.).
        assertEquals(0, botaniste.currentCount)
        assertEquals(0, botaniste.unlockedTierCount)
    }

    @Test fun `evaluate counts identified species but not sp for botaniste`() {
        // Mix : 3 sks identifiés + 1 sp. → 3 distincts au compteur.
        val index = SpeciesIndex(listOf(
            SpeciesEntry(index = 0, genre = "Tilia", espece = "cordata"),
            SpeciesEntry(index = 1, genre = "Tilia", espece = "platyphyllos"),
            SpeciesEntry(index = 2, genre = "Tilia", espece = "tomentosa"),
            SpeciesEntry(index = 99, genre = "Tilia", espece = "sp.", unknownSpecies = true),
        ))
        val captures = listOf(
            capture(arbreId = 1L, speciesIndex = 0, ts = 1L),
            capture(arbreId = 2L, speciesIndex = 1, ts = 2L),
            capture(arbreId = 3L, speciesIndex = 2, ts = 3L),
            capture(arbreId = 4L, speciesIndex = 99, ts = 4L),
        )
        val arbres = captures.associate { it.arbreId to arbre(id = it.arbreId, genre = "Tilia") }
        val states = eval(captures, arbres, speciesIndex = index)
        val botaniste = progressive(states, BadgeCatalog.BOTANISTE.id)
        assertEquals(3, botaniste.currentCount)
    }

    // ---------- mosaique de chênes (progressif, S8) ----------

    @Test fun `evaluate unlocks mosaique tier 3 with 3 distinct quercus species`() {
        val index = SpeciesIndex(listOf(
            SpeciesEntry(index = 10, genre = "Quercus", espece = "robur"),
            SpeciesEntry(index = 11, genre = "Quercus", espece = "petraea"),
            SpeciesEntry(index = 12, genre = "Quercus", espece = "ilex"),
        ))
        val captures = listOf(
            capture(arbreId = 1L, speciesIndex = 10, ts = 1L),
            capture(arbreId = 2L, speciesIndex = 11, ts = 2L),
            capture(arbreId = 3L, speciesIndex = 12, ts = 3L),
        )
        val arbres = captures.associate { it.arbreId to arbre(id = it.arbreId, genre = "Quercus") }
        val states = eval(captures, arbres, speciesIndex = index)
        val mosaique = progressive(states, BadgeCatalog.MOSAIQUE_QUERCUS.id)
        assertEquals(3, mosaique.currentCount)
        assertEquals(3L, tierUnlockedAt(mosaique, threshold = 3))
        assertNull(tierUnlockedAt(mosaique, threshold = 5))
        assertNull(tierUnlockedAt(mosaique, threshold = 10))
    }

    @Test fun `evaluate ignores non-quercus captures for mosaique`() {
        val captures = (0 until 5).map { i ->
            capture(arbreId = i.toLong(), speciesIndex = i, ts = i.toLong())
        }
        // Tous Tilia, pas Quercus.
        val arbres = captures.associate { it.arbreId to arbre(id = it.arbreId, genre = "Tilia") }
        val states = eval(captures, arbres)
        val mosaique = progressive(states, BadgeCatalog.MOSAIQUE_QUERCUS.id)
        assertEquals(0, mosaique.currentCount)
        assertEquals(0, mosaique.unlockedTierCount)
    }

    @Test fun `evaluate dedupes mosaique on speciesIndex`() {
        // 5 captures du même Quercus robur → 1 sk distinct.
        val captures = (0 until 5).map { i ->
            capture(arbreId = i.toLong(), speciesIndex = 10, ts = i.toLong())
        }
        val arbres = captures.associate { it.arbreId to arbre(id = it.arbreId, genre = "Quercus") }
        val states = eval(captures, arbres)
        val mosaique = progressive(states, BadgeCatalog.MOSAIQUE_QUERCUS.id)
        assertEquals(1, mosaique.currentCount)
        assertNull(tierUnlockedAt(mosaique, threshold = 3))
    }

    @Test fun `evaluate excludes quercus sp from mosaique`() {
        // Quercus sp. (sk=99) capturé → ne compte pas dans Mosaïque même si genre = Quercus.
        val index = SpeciesIndex(listOf(
            SpeciesEntry(index = 99, genre = "Quercus", espece = "sp.", unknownSpecies = true),
        ))
        val captures = listOf(capture(arbreId = 1L, speciesIndex = 99, ts = 1L))
        val arbres = mapOf(1L to arbre(id = 1L, genre = "Quercus", espece = "sp."))
        val states = eval(captures, arbres, speciesIndex = index)
        val mosaique = progressive(states, BadgeCatalog.MOSAIQUE_QUERCUS.id)
        assertEquals(0, mosaique.currentCount)
    }

    // ---------- helpers ----------

    private fun parisTs(iso: String): Long = Instant.parse(iso).toEpochMilli()

    private fun progressive(states: List<BadgeState>, id: String): BadgeState.Progressive =
        states.first { it.def.id == id } as BadgeState.Progressive

    private fun tierUnlockedAt(progressive: BadgeState.Progressive, threshold: Int): Long? =
        progressive.tiers.first { it.threshold == threshold }.unlockedAt

    private fun binaryUnlockedAt(states: List<BadgeState>, id: String): Long? =
        (states.first { it.def.id == id } as BadgeState.Binary).unlockedAt

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

    /** SpeciesIndex minimal pour les tests : 0 unknownSks par défaut. Les
     *  tests qui exercent la sémantique sp. construisent leur propre index
     *  avec quelques `unknownSpecies = true`. */
    private fun emptySpeciesIndex(): SpeciesIndex = SpeciesIndex(emptyList())

    /** Wrapper de `BadgeEvaluator.evaluate` qui défaut `speciesIndex` au repo
     *  vide. Évite de réécrire 16 call-sites historiques quand seul le
     *  contrat de signature change (S8). */
    private fun eval(
        captures: List<Capture>,
        arbresById: Map<Long, Arbre>,
        speciesInfo: SpeciesInfoRepository = emptySpeciesInfo(),
        speciesIndex: SpeciesIndex = emptySpeciesIndex(),
    ): List<BadgeState> =
        BadgeEvaluator.evaluate(captures, arbresById, speciesInfo, speciesIndex)

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
