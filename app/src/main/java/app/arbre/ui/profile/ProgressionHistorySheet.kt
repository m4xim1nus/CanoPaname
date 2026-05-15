package app.arbre.ui.profile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.arbre.data.ProgressionMetric
import app.arbre.data.WeekBucket
import app.arbre.data.WeeklySeries
import app.arbre.ui.theme.arbresColors
import kotlinx.coroutines.delay
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

/**
 * Sheet déclenché par long-press sur une barre de `ProgressionCard`. Affiche
 * l'histogramme hebdo des nouveautés liées à la métrique (cf. `ProgressionMetric`)
 * sur une fenêtre adaptative (max 16 semaines, cf. `rollingWindow`).
 *
 * Contrat de précharge : ce composable doit n'être monté que **si `series` est
 * déjà résolu** — sinon la 2e ouverture mesure faux (memo `feedback_compose_sheet`,
 * pattern « précharger AVANT d'afficher »). Côté `ProfileScreen`, le sheet
 * n'est ouvert que quand `openedSeries != null`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressionHistorySheet(
    metric: ProgressionMetric,
    series: WeeklySeries,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                metric.label,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                subtitleFor(series),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            WeeklyHistogram(series = series)
        }
    }
}

private fun subtitleFor(series: WeeklySeries): String {
    val n = series.weeks.size
    val nouv = series.totalNew
    val nouvLabel = if (nouv > 1) "$nouv nouveautés" else "$nouv nouveauté"
    return when {
        n <= 1 -> "Sur la dernière semaine · +$nouvLabel"
        else -> "Sur les $n dernières semaines · +$nouvLabel"
    }
}

private val WEEK_LABEL_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM", Locale.FRANCE)

/**
 * Histogramme barres verticales hand-rolled. Tap sur une barre → tooltip
 * flottant ancré au-dessus, auto-dismiss 2,5 s. La dernière barre (semaine en
 * cours) est rendue à `alpha = 0.5f` pour signaler son caractère partiel.
 */
@Composable
private fun WeeklyHistogram(series: WeeklySeries) {
    val barColor = MaterialTheme.arbresColors.or
    val baselineColor = MaterialTheme.colorScheme.outlineVariant
    val density = LocalDensity.current
    val histogramHeight = 180.dp
    val barGap = 4.dp
    val labelPadding = 28.dp

    val weeks = series.weeks
    if (weeks.isEmpty()) {
        Text(
            "Pas encore d'historique à afficher.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }

    val maxCount = max(1, weeks.maxOf { it.count })
    var tappedIndex by remember { mutableStateOf<Int?>(null) }
    var canvasWidthPx by remember { mutableIntStateOf(0) }

    LaunchedEffect(tappedIndex) {
        if (tappedIndex != null) {
            delay(2500)
            tappedIndex = null
        }
    }

    Box {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(histogramHeight + labelPadding)
                .pointerInput(weeks.size) {
                    detectTapGestures { offset ->
                        val idx = indexAtX(
                            x = offset.x,
                            totalWidthPx = canvasWidthPx.toFloat(),
                            count = weeks.size,
                        )
                        tappedIndex = if (idx in weeks.indices) idx else null
                    }
                },
        ) {
            canvasWidthPx = size.width.toInt()
            val labelPx = with(density) { labelPadding.toPx() }
            val gapPx = with(density) { barGap.toPx() }
            val histPx = size.height - labelPx
            drawBars(
                weeks = weeks,
                maxCount = maxCount,
                color = barColor,
                gapPx = gapPx,
                histogramHeightPx = histPx,
            )
            drawBaseline(color = baselineColor, yPx = histPx)
        }

        val idx = tappedIndex
        if (idx != null && canvasWidthPx > 0) {
            val cellPx = canvasWidthPx.toFloat() / weeks.size
            val centerXPx = cellPx * (idx + 0.5f)
            val centerXDp = with(density) { centerXPx.toDp() }
            TooltipBox(
                bucket = weeks[idx],
                anchorXDp = centerXDp,
            )
        }

        AxisLabels(weeks = weeks, histogramHeight = histogramHeight)
    }
}

private fun DrawScope.drawBars(
    weeks: List<WeekBucket>,
    maxCount: Int,
    color: Color,
    gapPx: Float,
    histogramHeightPx: Float,
) {
    if (weeks.isEmpty() || histogramHeightPx <= 0f) return
    val cellWidth = size.width / weeks.size
    val barWidth = (cellWidth - gapPx).coerceAtLeast(2f)
    weeks.forEachIndexed { i, bucket ->
        val cellLeft = i * cellWidth
        val barLeft = cellLeft + (cellWidth - barWidth) / 2f
        val ratio = bucket.count.toFloat() / maxCount
        val barH = histogramHeightPx * ratio
        val top = histogramHeightPx - barH
        val alpha = if (bucket.isCurrent) 0.5f else 1f
        drawRect(
            color = color.copy(alpha = alpha),
            topLeft = Offset(barLeft, top),
            size = Size(width = barWidth, height = barH),
        )
    }
}

private fun DrawScope.drawBaseline(color: Color, yPx: Float) {
    drawLine(
        color = color,
        start = Offset(0f, yPx),
        end = Offset(size.width, yPx),
        strokeWidth = 1f,
    )
}

private fun indexAtX(x: Float, totalWidthPx: Float, count: Int): Int {
    if (totalWidthPx <= 0f || count <= 0) return -1
    val cell = totalWidthPx / count
    if (cell <= 0f) return -1
    return (x / cell).toInt().coerceIn(0, count - 1)
}

@Composable
private fun TooltipBox(bucket: WeekBucket, anchorXDp: Dp) {
    val tooltipWidth = 140.dp
    val halfBox = tooltipWidth / 2
    val xOffset = (anchorXDp - halfBox).coerceAtLeast(0.dp)
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        tonalElevation = 4.dp,
        shadowElevation = 4.dp,
        modifier = Modifier
            .offset(x = xOffset, y = 4.dp)
            .height(56.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                "Semaine du ${bucket.week.mondayDate().format(WEEK_LABEL_FORMATTER)}",
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                "+${bucket.count}",
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun AxisLabels(weeks: List<WeekBucket>, histogramHeight: Dp) {
    if (weeks.size < 2) return
    val firstAge = weeks.size - 1
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = histogramHeight + 4.dp),
    ) {
        Text(
            "il y a ${firstAge}sem",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.TopStart),
        )
        Text(
            "cette semaine",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.TopEnd),
        )
    }
}
