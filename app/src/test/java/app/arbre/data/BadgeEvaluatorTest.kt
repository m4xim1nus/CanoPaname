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

    // ---------- Familier d'un genre ----------

    private val genreTestusId = BadgeCatalog.genreBadgeId("Testus")

    /** SpeciesIndex de test : un genre `Testus` à 7 espèces identifiées
     *  (sk 0..6) — donc éligible — + son `(Testus, sp.)` (sk 7, unknown). */
    private fun speciesIndexWithTestus(): SpeciesIndex = SpeciesIndex(
        (0..6).map { i -> SpeciesEntry(index = i, genre = "Testus", espece = "sp$i", pokedexNumber = i + 1) } +
            SpeciesEntry(index = 7, genre = "Testus", espece = "sp.", unknownSpecies = true),
    )

    private fun testusArbre(id: Long, speciesNo: Int): Arbre =
        arbre(id = id, genre = "Testus", espece = "sp$speciesNo", adresse = "ALLEE TEST")

    @Test fun `evaluate unlocks familier genre when all identified species captured`() {
        val idx = speciesIndexWithTestus()
        val captures = (0..6).map { i ->
            capture(arbreId = 100L + i, speciesIndex = i, ts = (i + 1).toLong())
        }
        val arbres = (0..6).associate { i -> (100L + i) to testusArbre(100L + i, i) }
        val states = eval(captures, arbres, emptySpeciesInfo(), speciesIndex = idx)
        // unlockedAt figé sur la 7e capture (ts = 7).
        assertEquals(7L, binaryUnlockedAt(states, genreTestusId))
    }

    @Test fun `evaluate does not unlock familier genre on partial capture`() {
        val idx = speciesIndexWithTestus()
        val captures = (0..5).map { i ->
            capture(arbreId = 100L + i, speciesIndex = i, ts = (i + 1).toLong())
        }
        val arbres = (0..5).associate { i -> (100L + i) to testusArbre(100L + i, i) }
        val states = eval(captures, arbres, emptySpeciesInfo(), speciesIndex = idx)
        assertNull(binaryUnlockedAt(states, genreTestusId))
    }

    @Test fun `evaluate has no badge for a genre below the species threshold`() {
        // `Petitus` : 3 espèces identifiées < GENRE_FAMILIER_MIN_SPECIES.
        val idx = SpeciesIndex(
            (0..2).map { i -> SpeciesEntry(index = i, genre = "Petitus", espece = "sp$i") },
        )
        val captures = (0..2).map { i ->
            capture(arbreId = 200L + i, speciesIndex = i, ts = (i + 1).toLong())
        }
        val arbres = (0..2).associate { i ->
            (200L + i) to arbre(id = 200L + i, genre = "Petitus", espece = "sp$i", adresse = "ALLEE TEST")
        }
        val states = eval(captures, arbres, emptySpeciesInfo(), speciesIndex = idx)
        assertTrue(states.none { it.def.id == BadgeCatalog.genreBadgeId("Petitus") })
    }

    // ---------- Familier d'un arrondissement ----------
    // Le dénominateur du badge est l'ensemble des **ids d'arbres** remarquables
    // physiques de l'arr — capture directe par id, pas de propagation espèce.
    // Les arr sans aucun remarquable n'engendrent pas de badge.

    private val arr5Id = BadgeCatalog.arrBadgeId(ArrKey.Paris(5))

    @Test fun `evaluate unlocks familier arrondissement when all remarquable ids captured`() {
        // Le 5e a 3 arbres remarquables (ids 10, 11, 12). Capturer chaque
        // arbre remarquable est nécessaire — couvrir l'espèce ne suffit pas.
        val idx = SpeciesIndex(
            listOf(
                SpeciesEntry(index = 0, genre = "Quercus", espece = "robur"),
                SpeciesEntry(index = 1, genre = "Tilia", espece = "cordata"),
            ),
        )
        val arrSpecies = ArrSpeciesIndex(
            arrKeys = setOf(ArrKey.Paris(5)),
            remarquableArbreIdsByKey = mapOf(ArrKey.Paris(5) to setOf(10L, 11L, 12L)),
        )
        val arbres = mapOf(
            10L to arbre(id = 10L, genre = "Quercus", espece = "robur", adresse = "RUE A, 5e"),
            11L to arbre(id = 11L, genre = "Quercus", espece = "robur", adresse = "RUE B, 5e"),
            12L to arbre(id = 12L, genre = "Tilia", espece = "cordata", adresse = "RUE C, 5e"),
        )

        // Capturer 10 + 11 (deux Quercus robur remarquables, même espèce) :
        // l'espèce Quercus robur est couverte, mais l'id 12 manque → pas de badge.
        val partial = eval(
            captures = listOf(
                capture(arbreId = 10L, speciesIndex = 0, ts = 1L, remarquable = true),
                capture(arbreId = 11L, speciesIndex = 0, ts = 2L, remarquable = true),
            ),
            arbresById = arbres,
            speciesIndex = idx,
            arrSpecies = arrSpecies,
        )
        assertNull(binaryUnlockedAt(partial, arr5Id))

        // Capture du dernier remarquable → badge unlocké au ts de cette capture.
        val full = eval(
            captures = listOf(
                capture(arbreId = 10L, speciesIndex = 0, ts = 1L, remarquable = true),
                capture(arbreId = 11L, speciesIndex = 0, ts = 2L, remarquable = true),
                capture(arbreId = 12L, speciesIndex = 1, ts = 3L, remarquable = true),
            ),
            arbresById = arbres,
            speciesIndex = idx,
            arrSpecies = arrSpecies,
        )
        assertEquals(3L, binaryUnlockedAt(full, arr5Id))
    }

    @Test fun `evaluate ignores non-remarquable captures for familier arrondissement`() {
        // Un arbre ordinaire (`remarquable = false`) du même id qu'une cible
        // remarquable ne devrait pas se produire en prod, mais on vérifie que
        // c'est bien le flag `capture.remarquable` qui gate la progression :
        // capturer le robur ordinaire sk=0 dans le 5e ne complète pas la cible
        // {10, 11} car les captures non-remarquables n'alimentent pas le set.
        val idx = SpeciesIndex(listOf(SpeciesEntry(index = 0, genre = "Quercus", espece = "robur")))
        val arrSpecies = ArrSpeciesIndex(
            arrKeys = setOf(ArrKey.Paris(5)),
            remarquableArbreIdsByKey = mapOf(ArrKey.Paris(5) to setOf(10L, 11L)),
        )
        val states = eval(
            captures = listOf(
                capture(arbreId = 1L, speciesIndex = 0, ts = 1L, remarquable = false),
                capture(arbreId = 2L, speciesIndex = 0, ts = 2L, remarquable = false),
            ),
            arbresById = mapOf(
                1L to arbre(id = 1L, genre = "Quercus", espece = "robur", adresse = "RUE A, 5e"),
                2L to arbre(id = 2L, genre = "Quercus", espece = "robur", adresse = "RUE B, 5e"),
            ),
            speciesIndex = idx,
            arrSpecies = arrSpecies,
        )
        assertNull(binaryUnlockedAt(states, arr5Id))
    }

    @Test fun `arrBadges omits arrondissements with no remarquables`() {
        // Le 5e est présent dans `keys` (au moins un arbre visité) mais sans
        // aucun remarquable : pas de `BadgeDef` généré.
        val arrSpecies = ArrSpeciesIndex(
            arrKeys = setOf(ArrKey.Paris(5)),
            remarquableArbreIdsByKey = emptyMap(),
        )
        val states = eval(
            captures = emptyList(),
            arbresById = emptyMap(),
            speciesIndex = SpeciesIndex(
                listOf(SpeciesEntry(index = 0, genre = "Quercus", espece = "robur")),
            ),
            arrSpecies = arrSpecies,
        )
        assertTrue(states.none { it.def.id == arr5Id })
    }

    @Test fun `evaluate ignores arrondissements absent from the asset`() {
        val idx = SpeciesIndex(listOf(SpeciesEntry(index = 0, genre = "Quercus", espece = "robur")))
        // Asset vide → aucun badge d'arrondissement dans le catalogue.
        val states = eval(
            captures = listOf(capture(arbreId = 1L, speciesIndex = 0, ts = 1L, remarquable = true)),
            arbresById = mapOf(1L to arbre(id = 1L, genre = "Quercus", espece = "robur", adresse = "RUE A, 5e")),
            speciesIndex = idx,
            arrSpecies = ArrSpeciesIndex(emptySet()),
        )
        assertTrue(states.none { it.def.id == arr5Id })
    }

    // ---------- pokédex (binaires, paliers cumulatifs) ----------

    @Test fun `evaluate locks all pokedex badges when no captures`() {
        val states = eval(
            captures = emptyList(),
            arbresById = emptyMap(),
            speciesIndex = pokedexSpeciesIndex(500),
        )
        BadgeCatalog.POKEDEX_THRESHOLDS.forEach { n ->
            assertNull(binaryUnlockedAt(states, BadgeCatalog.pokedexBadgeId(n)))
        }
    }

    @Test fun `evaluate unlocks pokedex_10 when the 10 lowest pokedex species are all captured`() {
        val ts0 = parisTs("2025-04-01T10:00:00Z")
        val idx = pokedexSpeciesIndex(20)
        val arbres = (1..10).associate { i ->
            i.toLong() to arbre(id = i.toLong(), genre = "Genus$i", espece = "species$i")
        }
        val captures = (1..10).map { i ->
            capture(arbreId = i.toLong(), speciesIndex = i - 1, ts = ts0 + i)
        }
        val states = eval(captures = captures, arbresById = arbres, speciesIndex = idx)
        assertNotNull(binaryUnlockedAt(states, BadgeCatalog.pokedexBadgeId(10)))
        assertNull(binaryUnlockedAt(states, BadgeCatalog.pokedexBadgeId(20)))
    }

    @Test fun `evaluate unlocks lower pokedex tiers in cascade when reaching pokedex_50`() {
        val ts0 = parisTs("2025-04-01T10:00:00Z")
        val idx = pokedexSpeciesIndex(100)
        val arbres = (1..50).associate { i ->
            i.toLong() to arbre(id = i.toLong(), genre = "Genus$i", espece = "species$i")
        }
        val captures = (1..50).map { i ->
            capture(arbreId = i.toLong(), speciesIndex = i - 1, ts = ts0 + i)
        }
        val states = eval(captures = captures, arbresById = arbres, speciesIndex = idx)
        assertNotNull(binaryUnlockedAt(states, BadgeCatalog.pokedexBadgeId(10)))
        assertNotNull(binaryUnlockedAt(states, BadgeCatalog.pokedexBadgeId(20)))
        assertNotNull(binaryUnlockedAt(states, BadgeCatalog.pokedexBadgeId(50)))
        assertNull(binaryUnlockedAt(states, BadgeCatalog.pokedexBadgeId(100)))
    }

    @Test fun `evaluate freezes pokedex_10 ts on the completing capture`() {
        val ts0 = parisTs("2025-04-01T10:00:00Z")
        val tsLast = parisTs("2025-04-10T10:00:00Z")
        val idx = pokedexSpeciesIndex(10)
        val arbres = (1..10).associate { i ->
            i.toLong() to arbre(id = i.toLong(), genre = "Genus$i", espece = "species$i")
        }
        val captures = (1..9).map { i ->
            capture(arbreId = i.toLong(), speciesIndex = i - 1, ts = ts0 + i)
        } + capture(arbreId = 10L, speciesIndex = 9, ts = tsLast)
        val states = eval(captures = captures, arbresById = arbres, speciesIndex = idx)
        assertEquals(tsLast, binaryUnlockedAt(states, BadgeCatalog.pokedexBadgeId(10)))
    }

    @Test fun `evaluate ignores remarquable captures for pokedex`() {
        val ts0 = parisTs("2025-04-01T10:00:00Z")
        val idx = pokedexSpeciesIndex(10)
        val arbres = (1..10).associate { i ->
            i.toLong() to arbre(id = i.toLong(), genre = "Genus$i", espece = "species$i")
        }
        // Toutes remarquables → aucun palier Pokédex ne doit se débloquer.
        val captures = (1..10).map { i ->
            capture(arbreId = i.toLong(), speciesIndex = i - 1, remarquable = true, ts = ts0 + i)
        }
        val states = eval(captures = captures, arbresById = arbres, speciesIndex = idx)
        assertNull(binaryUnlockedAt(states, BadgeCatalog.pokedexBadgeId(10)))
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

    private val emptySpeciesIndex = SpeciesIndex(emptyList())
    private val emptyArrSpecies = ArrSpeciesIndex(emptySet())
    private val emptyGenreInfo = GenreInfoRepository(emptyMap())

    /** SpeciesIndex avec `n` entrées actives, sk = i-1, pokedexNumber = i. */
    private fun pokedexSpeciesIndex(n: Int): SpeciesIndex =
        SpeciesIndex(
            (1..n).map { i ->
                SpeciesEntry(
                    index = i - 1,
                    genre = "Genus$i",
                    espece = "species$i",
                    pokedexNumber = i,
                )
            }
        )

    /**
     * Lance l'évaluateur et reconstitue la liste `BadgeState` (catalogue complet
     * pour les `speciesIndex`/`arrSpecies` fournis, zippé avec les ts de
     * déblocage) — comme le fait `BadgeRepository` en prod.
     */
    private fun eval(
        captures: List<Capture>,
        arbresById: Map<Long, Arbre>,
        speciesInfo: SpeciesInfoRepository = emptySpeciesInfo(),
        speciesIndex: SpeciesIndex = emptySpeciesIndex,
        arrSpecies: ArrSpeciesIndex = emptyArrSpecies,
    ): List<BadgeState> {
        val unlocks = BadgeEvaluator.evaluate(captures, arbresById, speciesInfo, speciesIndex, arrSpecies)
        return BadgeCatalog.full(speciesIndex, emptyGenreInfo, arrSpecies)
            .map { def -> BadgeState(def = def, unlockedAt = unlocks[def.id]) }
    }

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
