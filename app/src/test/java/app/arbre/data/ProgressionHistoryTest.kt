package app.arbre.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ProgressionHistoryTest {

    // ---------- IsoWeek ----------

    @Test fun `IsoWeek midweek 2026-05-15 is week 20`() {
        // Vendredi 15 mai 2026 = ISO week 20 de 2026.
        assertEquals(IsoWeek(2026, 20), ts(2026, 5, 15).toIsoWeek())
    }

    @Test fun `IsoWeek year boundary 2020-12-31 is week 53 of 2020`() {
        // 2020 a 53 semaines ISO ; le 31 décembre 2020 (jeudi) appartient à la
        // semaine 53 de 2020 (pas semaine 1 de 2021).
        assertEquals(IsoWeek(2020, 53), ts(2020, 12, 31).toIsoWeek())
    }

    @Test fun `IsoWeek year boundary 2019-01-01 is week 1 of 2019`() {
        // 1er janvier 2019 (mardi) = semaine 1 de 2019.
        assertEquals(IsoWeek(2019, 1), ts(2019, 1, 1).toIsoWeek())
    }

    @Test fun `IsoWeek year boundary 2017-01-01 is week 52 of 2016`() {
        // 1er janvier 2017 (dimanche) appartient à la semaine 52 de 2016 — la
        // semaine ISO commence le lundi ; ce dimanche est donc le dernier jour
        // de la semaine 52 commencée le lundi 2016-12-26.
        assertEquals(IsoWeek(2016, 52), ts(2017, 1, 1).toIsoWeek())
    }

    @Test fun `IsoWeek comparable orders chronologically`() {
        assertTrue(IsoWeek(2026, 5) < IsoWeek(2026, 20))
        assertTrue(IsoWeek(2026, 53) < IsoWeek(2027, 1))
    }

    // ---------- rollingWindow ----------

    @Test fun `rollingWindow with same week returns one`() {
        val t = ts(2026, 5, 15)
        val w = rollingWindow(firstMs = t, nowMs = t)
        assertEquals(1, w.size)
        assertEquals(IsoWeek(2026, 20), w[0])
    }

    @Test fun `rollingWindow with 3 weeks of history returns 4 weeks`() {
        // firstMs sem 17, nowMs sem 20 → fenêtre = [17, 18, 19, 20].
        val w = rollingWindow(
            firstMs = ts(2026, 4, 24),
            nowMs = ts(2026, 5, 15),
        )
        assertEquals(4, w.size)
        assertEquals(IsoWeek(2026, 17), w.first())
        assertEquals(IsoWeek(2026, 20), w.last())
    }

    @Test fun `rollingWindow clamps to max 16 weeks`() {
        // firstMs il y a 30 sem, nowMs maintenant → clamp à 16.
        val w = rollingWindow(
            firstMs = ts(2025, 10, 13), // ~sem 42 2025
            nowMs = ts(2026, 5, 15), // sem 20 2026
        )
        assertEquals(16, w.size)
        assertEquals(IsoWeek(2026, 20), w.last())
    }

    @Test fun `rollingWindow spans year boundary contiguously`() {
        // firstMs début déc 2025, nowMs mi-janvier 2026.
        val w = rollingWindow(
            firstMs = ts(2025, 12, 8), // sem 50 2025
            nowMs = ts(2026, 1, 12), // sem 3 2026
        )
        // Sem 50, 51, 52, 1 (2026), 2, 3 → 6 semaines.
        assertEquals(6, w.size)
        assertEquals(IsoWeek(2025, 50), w.first())
        assertEquals(IsoWeek(2026, 3), w.last())
    }

    // ---------- computeSeries ESPECES ----------

    @Test fun `computeSeries ESPECES counts new species per week`() = runBlocking {
        val speciesIndex = makeSpeciesIndex(
            SpeciesEntry(index = 1, genre = "Quercus", espece = "robur"),
            SpeciesEntry(index = 2, genre = "Quercus", espece = "petraea"),
            SpeciesEntry(index = 3, genre = "Tilia", espece = "cordata"),
            SpeciesEntry(index = 9, genre = "Tilia", espece = "sp.", unknownSpecies = true),
        )
        val captures = listOf(
            cap(sk = 1, ts = ts(2026, 5, 4)), // sem 19 — nouvelle (Quercus robur)
            cap(sk = 1, ts = ts(2026, 5, 5)), // sem 19 — recap, ignoré
            cap(sk = 2, ts = ts(2026, 5, 11)), // sem 20 — nouvelle (Quercus petraea)
            cap(sk = 3, ts = ts(2026, 5, 11)), // sem 20 — nouvelle (Tilia cordata)
            cap(sk = 9, ts = ts(2026, 5, 12)), // sem 20 — sp. → exclu (alignement nbIdentifiees)
        )
        val ctx = SeriesContext(
            captures = captures,
            arbresById = emptyMap(),
            speciesIndex = speciesIndex,
            badges = emptyList(),
            nowMs = ts(2026, 5, 15),
            firstMs = ts(2026, 5, 4),
        )
        val series = computeSeries(ProgressionMetric.ESPECES, ctx)
        // Sem 19, 20 → 2 buckets.
        assertEquals(2, series.weeks.size)
        assertEquals(1, series.weeks[0].count) // sem 19 : Quercus robur
        assertEquals(2, series.weeks[1].count) // sem 20 : Q. petraea + T. cordata
        assertEquals(3, series.totalNew)
        assertTrue(series.weeks[1].isCurrent)
    }

    // ---------- computeSeries REMARQUABLES ----------

    @Test fun `computeSeries REMARQUABLES counts distinct remarquable arbreIds`() = runBlocking {
        val captures = listOf(
            cap(arbreId = 100L, sk = 1, ts = ts(2026, 5, 4), remarquable = true), // sem 19
            cap(arbreId = 100L, sk = 1, ts = ts(2026, 5, 5), remarquable = true), // recap, ignoré
            cap(arbreId = 200L, sk = 2, ts = ts(2026, 5, 11), remarquable = true), // sem 20
            cap(arbreId = 300L, sk = 3, ts = ts(2026, 5, 11), remarquable = false), // non-remarquable, ignoré
        )
        val ctx = SeriesContext(
            captures = captures,
            arbresById = emptyMap(),
            speciesIndex = makeSpeciesIndex(),
            badges = emptyList(),
            nowMs = ts(2026, 5, 15),
            firstMs = ts(2026, 5, 4),
        )
        val series = computeSeries(ProgressionMetric.REMARQUABLES, ctx)
        assertEquals(2, series.weeks.size)
        assertEquals(1, series.weeks[0].count)
        assertEquals(1, series.weeks[1].count)
        assertEquals(2, series.totalNew)
    }

    // ---------- computeSeries GENRES_DEC ----------

    @Test fun `computeSeries GENRES_DEC joins captures to arbres for genre`() = runBlocking {
        val arbres = mapOf(
            10L to arbre(id = 10, genre = "Quercus", adresse = "RUE A, PARIS 5E ARRDT"),
            11L to arbre(id = 11, genre = "Quercus", adresse = "RUE B, PARIS 5E ARRDT"),
            12L to arbre(id = 12, genre = "Tilia", adresse = "RUE C, PARIS 5E ARRDT"),
        )
        val captures = listOf(
            cap(arbreId = 10L, sk = 1, ts = ts(2026, 5, 4)), // sem 19 — nouveau genre Quercus
            cap(arbreId = 11L, sk = 2, ts = ts(2026, 5, 5)), // sem 19 — encore Quercus, ignoré
            cap(arbreId = 12L, sk = 3, ts = ts(2026, 5, 11)), // sem 20 — nouveau genre Tilia
        )
        val ctx = SeriesContext(
            captures = captures,
            arbresById = arbres,
            speciesIndex = makeSpeciesIndex(),
            badges = emptyList(),
            nowMs = ts(2026, 5, 15),
            firstMs = ts(2026, 5, 4),
        )
        val series = computeSeries(ProgressionMetric.GENRES_DEC, ctx)
        assertEquals(2, series.weeks.size)
        assertEquals(1, series.weeks[0].count)
        assertEquals(1, series.weeks[1].count)
        assertEquals(2, series.totalNew)
    }

    // ---------- computeSeries ARR_VIS ----------

    @Test fun `computeSeries ARR_VIS excludes ArrKey Other`() = runBlocking {
        val arbres = mapOf(
            10L to arbre(id = 10, genre = "Quercus", adresse = "RUE A, PARIS 5E ARRDT"), // 5e
            11L to arbre(id = 11, genre = "Quercus", adresse = null), // Other
            12L to arbre(id = 12, genre = "Tilia", adresse = "RUE C, PARIS 6E ARRDT"), // 6e
        )
        val captures = listOf(
            cap(arbreId = 10L, sk = 1, ts = ts(2026, 5, 4)), // sem 19 — 5e
            cap(arbreId = 11L, sk = 2, ts = ts(2026, 5, 5)), // sem 19 — Other, ignoré
            cap(arbreId = 12L, sk = 3, ts = ts(2026, 5, 11)), // sem 20 — 6e
        )
        val ctx = SeriesContext(
            captures = captures,
            arbresById = arbres,
            speciesIndex = makeSpeciesIndex(),
            badges = emptyList(),
            nowMs = ts(2026, 5, 15),
            firstMs = ts(2026, 5, 4),
        )
        val series = computeSeries(ProgressionMetric.ARR_VIS, ctx)
        assertEquals(2, series.weeks.size)
        assertEquals(1, series.weeks[0].count)
        assertEquals(1, series.weeks[1].count)
        assertEquals(2, series.totalNew)
    }

    // ---------- computeSeries GENRES_COMPL ----------

    @Test fun `computeSeries GENRES_COMPL buckets familier_genre unlocks`() = runBlocking {
        val badges = listOf(
            badgeState("familier_genre_quercus", ts(2026, 5, 4)), // sem 19
            badgeState("familier_genre_tilia", ts(2026, 5, 12)), // sem 20
            badgeState("familier_arr_5", ts(2026, 5, 12)), // mauvais préfixe, ignoré
            badgeState("premiere_capture", ts(2026, 5, 11)), // hors préfixe, ignoré
            BadgeState(def = def("familier_genre_acer"), unlockedAt = null), // verrouillé, ignoré
        )
        val ctx = SeriesContext(
            captures = emptyList(),
            arbresById = emptyMap(),
            speciesIndex = makeSpeciesIndex(),
            badges = badges,
            nowMs = ts(2026, 5, 15),
            firstMs = ts(2026, 5, 4),
        )
        val series = computeSeries(ProgressionMetric.GENRES_COMPL, ctx)
        assertEquals(2, series.weeks.size)
        assertEquals(1, series.weeks[0].count)
        assertEquals(1, series.weeks[1].count)
        assertEquals(2, series.totalNew)
    }

    @Test fun `computeSeries empty captures yields empty series`() = runBlocking {
        val ctx = SeriesContext(
            captures = emptyList(),
            arbresById = emptyMap(),
            speciesIndex = makeSpeciesIndex(),
            badges = emptyList(),
            nowMs = ts(2026, 5, 15),
            firstMs = ts(2026, 5, 15),
        )
        val series = computeSeries(ProgressionMetric.ESPECES, ctx)
        assertEquals(1, series.weeks.size) // 1 semaine (la courante)
        assertEquals(0, series.weeks[0].count)
        assertEquals(0, series.totalNew)
    }

    // ---------- Helpers ----------

    private fun ts(year: Int, month: Int, day: Int, hour: Int = 12): Long =
        LocalDate.of(year, month, day).atTime(hour, 0)
            .atZone(ZoneId.of("Europe/Paris")).toInstant().toEpochMilli()

    private fun cap(
        arbreId: Long = 1L,
        sk: Int = 1,
        ts: Long,
        remarquable: Boolean = false,
    ): Capture = Capture(
        id = 0L,
        arbreId = arbreId,
        speciesIndex = sk,
        remarquable = remarquable,
        timestamp = ts,
        latitudeDevice = 0.0,
        longitudeDevice = 0.0,
        photoPath = "",
        season = Season.SPRING,
    )

    private fun arbre(
        id: Long,
        genre: String,
        espece: String = "sp.",
        adresse: String?,
    ): Arbre = Arbre(
        id = id,
        genre = genre,
        espece = espece,
        varieteCultivar = null,
        nomCommun = null,
        hauteurM = null,
        circonferenceCm = null,
        remarquable = false,
        adresse = adresse,
        latitude = 0.0,
        longitude = 0.0,
    )

    private fun makeSpeciesIndex(vararg entries: SpeciesEntry): SpeciesIndex =
        SpeciesIndex(entries.toList())

    private fun def(id: String): BadgeDef = BadgeDef(
        id = id,
        label = id,
        description = "",
        category = BadgeCategory.BOTANIQUE,
    )

    private fun badgeState(id: String, ts: Long): BadgeState =
        BadgeState(def = def(id), unlockedAt = ts)
}
