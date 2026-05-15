package app.arbre.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.WeekFields

/**
 * Pipeline de séries temporelles hebdomadaires pour les 7 barres de
 * `ProfileScreen.ProgressionCard`. Déclenché par un long-press sur une barre,
 * affiché dans `ProgressionHistorySheet`. Pur Kotlin, testable JVM, aucune
 * dépendance Compose ni Room.
 *
 * Granularité : semaine ISO `WeekFields.ISO` en `Europe/Paris` (cohérent avec
 * `ProfileScreen.daysSince`). La fenêtre est adaptative (`min(MAX_WEEKS,
 * weeksBetween(firstCapture, now) + 1)`) et inclut la semaine en cours en
 * dernière position.
 *
 * 6 métriques sur 7 se calculent par `min(ts) groupBy <clé>` (espèces,
 * remarquables, genres, arr) ou via les ts d'unlock figés par `BadgeEvaluator`
 * (genres et arr complétés). La 7ème (Arbres déverrouillés) demande des
 * snapshots cumulatifs successifs → lambda suspendue injectée.
 */

const val MAX_WEEKS: Int = 16

private val PARIS_ZONE: ZoneId = ZoneId.of("Europe/Paris")

enum class ProgressionMetric(val label: String) {
    ARBRES("Arbres déverrouillés"),
    REMARQUABLES("Remarquables capturés"),
    ESPECES("Espèces capturées"),
    GENRES_DEC("Genres découverts"),
    GENRES_COMPL("Genres complétés"),
    ARR_VIS("Arrondissements visités"),
    ARR_COMPL("Arrondissements complétés"),
}

data class IsoWeek(val weekBasedYear: Int, val week: Int) : Comparable<IsoWeek> {
    override fun compareTo(other: IsoWeek): Int {
        val y = weekBasedYear.compareTo(other.weekBasedYear)
        return if (y != 0) y else week.compareTo(other.week)
    }

    fun mondayDate(): LocalDate = LocalDate.now(PARIS_ZONE)
        .with(WeekFields.ISO.weekBasedYear(), weekBasedYear.toLong())
        .with(WeekFields.ISO.weekOfWeekBasedYear(), week.toLong())
        .with(DayOfWeek.MONDAY)
}

fun Long.toIsoWeek(zone: ZoneId = PARIS_ZONE): IsoWeek {
    val date = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()
    val wf = WeekFields.ISO
    return IsoWeek(
        weekBasedYear = date.get(wf.weekBasedYear()),
        week = date.get(wf.weekOfWeekBasedYear()),
    )
}

data class WeekBucket(val week: IsoWeek, val count: Int, val isCurrent: Boolean)

data class WeeklySeries(val weeks: List<WeekBucket>, val totalNew: Int)

/**
 * Fenêtre contiguë d'au plus [maxWeeks] semaines ISO finissant à la semaine
 * de `nowMs`, tronquée à la semaine de `firstMs` (pas de semaines avant la
 * 1ère capture). Inclut la semaine en cours en dernier. Garantit `size ≥ 1`.
 */
fun rollingWindow(firstMs: Long, nowMs: Long, maxWeeks: Int = MAX_WEEKS): List<IsoWeek> {
    val now = nowMs.toIsoWeek()
    val first = firstMs.toIsoWeek()
    if (first > now) return listOf(now)
    val weeks = ArrayDeque<IsoWeek>()
    var cur = now
    repeat(maxWeeks) {
        if (cur < first) return@repeat
        weeks.addFirst(cur)
        cur = cur.previous()
    }
    return weeks.toList()
}

private fun IsoWeek.previous(): IsoWeek {
    val mondayBefore = mondayDate().minusWeeks(1)
    return IsoWeek(
        weekBasedYear = mondayBefore.get(WeekFields.ISO.weekBasedYear()),
        week = mondayBefore.get(WeekFields.ISO.weekOfWeekBasedYear()),
    )
}

data class SeriesContext(
    val captures: List<Capture>,
    val arbresById: Map<Long, Arbre>,
    val speciesIndex: SpeciesIndex,
    val badges: List<BadgeState>,
    val nowMs: Long,
    val firstMs: Long,
)

/**
 * Calcule la série hebdo de la métrique demandée.
 *
 * La branche [ProgressionMetric.ARBRES] exige une lambda suspendue
 * (`ArbreRepository.nombreArbresDecouverts`) — la métrique est un *snapshot
 * cumulatif*, pas un compteur de premières captures, donc impossible à
 * dériver des seuls timestamps sans rejouer le calcul à chaque borne de
 * semaine. Les 6 autres branches sont purement en mémoire, O(captures).
 */
suspend fun computeSeries(
    metric: ProgressionMetric,
    ctx: SeriesContext,
    arbresDecouvertsAt: suspend (capturedSk: Set<Int>, capturedRemarquableIds: Set<Long>) -> Int =
        { _, _ -> 0 },
): WeeklySeries {
    val window = rollingWindow(ctx.firstMs, ctx.nowMs)
    if (window.isEmpty()) return WeeklySeries(emptyList(), 0)
    val currentWeek = window.last()

    if (metric == ProgressionMetric.ARBRES) {
        return computeArbresSeries(window, currentWeek, ctx, arbresDecouvertsAt)
    }

    val firstTimestamps: List<Long> = when (metric) {
        ProgressionMetric.ESPECES -> firstTsByKey(ctx.captures) { c ->
            if (c.remarquable) null
            else if (ctx.speciesIndex.get(c.speciesIndex)?.unknownSpecies == true) null
            else c.speciesIndex.toLong()
        }
        ProgressionMetric.REMARQUABLES -> firstTsByKey(ctx.captures) { c ->
            if (c.remarquable) c.arbreId else null
        }
        ProgressionMetric.GENRES_DEC -> firstTsByKey(ctx.captures) { c ->
            ctx.arbresById[c.arbreId]?.genre
        }
        ProgressionMetric.ARR_VIS -> firstTsByKey(ctx.captures) { c ->
            val arbre = ctx.arbresById[c.arbreId] ?: return@firstTsByKey null
            val arr = parseArrKey(arbre.adresse)
            if (arr is ArrKey.Other) null else arr
        }
        ProgressionMetric.GENRES_COMPL -> badgeUnlockTs(ctx, BadgeCatalog.FAMILIER_GENRE_PREFIX)
        ProgressionMetric.ARR_COMPL -> badgeUnlockTs(ctx, BadgeCatalog.FAMILIER_ARR_PREFIX)
        ProgressionMetric.ARBRES -> error("handled above")
    }

    return bucketize(firstTimestamps, window, currentWeek)
}

private fun <K> firstTsByKey(captures: List<Capture>, key: (Capture) -> K?): List<Long> {
    val first = HashMap<K, Long>()
    for (c in captures) {
        val k = key(c) ?: continue
        val cur = first[k]
        if (cur == null || c.timestamp < cur) first[k] = c.timestamp
    }
    return first.values.toList()
}

private fun badgeUnlockTs(ctx: SeriesContext, prefix: String): List<Long> =
    ctx.badges.asSequence()
        .filter { it.def.id.startsWith(prefix) }
        .mapNotNull { it.unlockedAt }
        .toList()

private fun bucketize(
    timestamps: List<Long>,
    window: List<IsoWeek>,
    currentWeek: IsoWeek,
): WeeklySeries {
    val weekIndex: Map<IsoWeek, Int> = window.withIndex().associate { (i, w) -> w to i }
    val counts = IntArray(window.size)
    var totalInWindow = 0
    for (ts in timestamps) {
        val w = ts.toIsoWeek()
        val idx = weekIndex[w] ?: continue
        counts[idx]++
        totalInWindow++
    }
    val buckets = window.mapIndexed { i, w ->
        WeekBucket(week = w, count = counts[i], isCurrent = w == currentWeek)
    }
    return WeeklySeries(buckets, totalInWindow)
}

/**
 * Pour « Arbres déverrouillés » : on rejoue les captures dans l'ordre
 * chronologique, en construisant l'ensemble cumulatif `(capturedSk,
 * capturedRem)` à la fin de chaque semaine de la fenêtre. À chaque borne, on
 * délègue le compte à [arbresDecouvertsAt] (qui agrège côté DB le nombre
 * d'arbres ordinaires couverts par espèce + les remarquables capturés).
 * Coût : (window.size + 1) appels à la lambda — pour 16 semaines, négligeable
 * tant que `nombreArbresDecouverts` reste O(|capturedSk|) avec queries DB
 * indexées.
 */
private suspend fun computeArbresSeries(
    window: List<IsoWeek>,
    currentWeek: IsoWeek,
    ctx: SeriesContext,
    arbresDecouvertsAt: suspend (Set<Int>, Set<Long>) -> Int,
): WeeklySeries {
    val sorted = ctx.captures.sortedBy { it.timestamp }
    val capturedSk = HashSet<Int>()
    val capturedRem = HashSet<Long>()
    var cursor = 0
    val windowStartMs = startOfWeekMs(window.first())
    while (cursor < sorted.size && sorted[cursor].timestamp < windowStartMs) {
        sorted[cursor].accumulate(capturedSk, capturedRem)
        cursor++
    }
    var previous = arbresDecouvertsAt(capturedSk, capturedRem)
    val counts = IntArray(window.size)
    var totalInWindow = 0
    for ((i, w) in window.withIndex()) {
        val endMs = startOfWeekMs(w) + 7L * 24 * 3600 * 1000
        while (cursor < sorted.size && sorted[cursor].timestamp < endMs) {
            sorted[cursor].accumulate(capturedSk, capturedRem)
            cursor++
        }
        val now = arbresDecouvertsAt(capturedSk, capturedRem)
        val delta = (now - previous).coerceAtLeast(0)
        counts[i] = delta
        totalInWindow += delta
        previous = now
    }
    val buckets = window.mapIndexed { i, w ->
        WeekBucket(week = w, count = counts[i], isCurrent = w == currentWeek)
    }
    return WeeklySeries(buckets, totalInWindow)
}

private fun Capture.accumulate(
    capturedSk: MutableSet<Int>,
    capturedRem: MutableSet<Long>,
) {
    if (remarquable) capturedRem.add(arbreId) else capturedSk.add(speciesIndex)
}

private fun startOfWeekMs(week: IsoWeek): Long =
    week.mondayDate().atStartOfDay(PARIS_ZONE).toInstant().toEpochMilli()
