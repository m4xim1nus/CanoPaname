package app.arbre.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.arbre.data.CaptureEvent
import app.arbre.data.CaptureRepository
import app.arbre.data.SpeciesIndex
import app.arbre.ui.theme.arbresColors
import app.arbre.ui.theme.arbresMotion
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import kotlin.math.roundToInt

/**
 * Climax du moment de capture sur la carte. Écoute
 * [CaptureRepository.captureConfirmed], projette le `LatLng` capturé en
 * pixels via [MapLibreMap.projection], puis dessine :
 *  - un halo en expansion (8 → 48 px, alpha 0.6 → 0, durée `arbresMotion.short`),
 *  - un cœur scale 1× → 1.5× → 1× sur la même fenêtre,
 *  - si 1re espèce, le binomial Fraunces qui flotte 800 ms au-dessus puis fade.
 *
 * NE PAS attacher de `pointerInput` (même vide) : un `pointerInput {}` vide
 * intercepte les touches et empêche la carte sous-jacente de zoomer/panner.
 * La recoloration gris→vert du point reste pilotée par `applyDiscoveryColor`
 * (Flow Room) ; on ne synchronise pas explicitement, l'effet visuel se
 * superpose bien.
 */
@Composable
fun CaptureCelebrationOverlay(
    captureRepo: CaptureRepository,
    mapRef: MapLibreMap?,
    speciesIndex: SpeciesIndex,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.arbresMotion
    val arbresColors = MaterialTheme.arbresColors
    val density = LocalDensity.current

    var active by remember { mutableStateOf<ActiveCelebration?>(null) }
    var progressMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(captureRepo) {
        captureRepo.captureConfirmed.collect { event: CaptureEvent ->
            val binomial = if (event.isFirstOfSpecies) speciesIndex.get(event.speciesIndex)?.displayName else null
            active = ActiveCelebration(event, binomial)
            progressMs = 0L
        }
    }

    val totalDuration = motion.short.toLong() + 800L + motion.short.toLong() // halo + hold + fade
    LaunchedEffect(active) {
        val current = active ?: return@LaunchedEffect
        val start = withFrameMillis { it }
        while (true) {
            val now = withFrameMillis { it }
            progressMs = now - start
            if (progressMs >= totalDuration) {
                if (active === current) active = null
                break
            }
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        val current = active ?: return@Box
        val map = mapRef ?: return@Box
        val pixel = map.projection.toScreenLocation(
            LatLng(current.event.latitudeDevice, current.event.longitudeDevice)
        )
        val haloDuration = motion.short.toLong()
        val haloProgress = (progressMs.toFloat() / haloDuration).coerceIn(0f, 1f)

        Canvas(modifier = Modifier.fillMaxSize()) {
            // Halo qui s'étend.
            val haloRadiusPx = with(density) { (8.dp.toPx() + 40.dp.toPx() * haloProgress) }
            val haloAlpha = (1f - haloProgress) * 0.6f
            drawCircle(
                color = arbresColors.feuilleClaire.copy(alpha = haloAlpha),
                radius = haloRadiusPx,
                center = Offset(pixel.x, pixel.y),
            )
            // Cœur : pulse 1× → 1.5× → 1×.
            val pulse = if (haloProgress < 0.5f) 1f + haloProgress * 1f else 1.5f - (haloProgress - 0.5f) * 1f
            val coreRadiusPx = with(density) { 5.dp.toPx() * pulse }
            drawCircle(
                color = arbresColors.feuilleSombre.copy(alpha = (1f - haloProgress * 0.4f).coerceIn(0f, 1f)),
                radius = coreRadiusPx,
                center = Offset(pixel.x, pixel.y),
            )
        }

        // Binomial flottant : apparait après le halo (300 ms), hold 800 ms, fade 300 ms.
        val binomial = current.binomial
        if (binomial != null) {
            val appearAt = haloDuration  // 300 ms
            val holdEnd = appearAt + 800L
            val fadeEnd = totalDuration
            val textAlpha = when {
                progressMs < appearAt -> 0f
                progressMs < holdEnd -> 1f
                progressMs < fadeEnd -> 1f - (progressMs - holdEnd).toFloat() / (fadeEnd - holdEnd)
                else -> 0f
            }.coerceIn(0f, 1f)
            if (textAlpha > 0f) {
                val offsetYPx = with(density) { 32.dp.toPx() }
                val textXPx = pixel.x.roundToInt()
                val textYPx = (pixel.y - offsetYPx).roundToInt()
                Text(
                    text = binomial,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = arbresColors.feuilleSombre,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset { IntOffset(textXPx - 80, textYPx) }
                        .graphicsLayer { alpha = textAlpha },
                )
            }
        }
    }
}

private data class ActiveCelebration(
    val event: CaptureEvent,
    val binomial: String?,
)
