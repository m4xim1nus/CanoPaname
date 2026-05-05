package app.arbre.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import app.arbre.data.Season
import app.arbre.ui.theme.arbresColors
import app.arbre.ui.theme.arbresMotion
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Couche ambiante éphémère sur changement de saison (flocon → pétale →
 * feuille verte → feuille cuivrée), purement décorative.
 *
 * NE PAS ajouter de `pointerInput` au Canvas, même vide — il consume les
 * events et rendrait la carte sous-jacente non-interactive.
 */
@Composable
fun SeasonAmbience(
    season: Season,
    modifier: Modifier = Modifier,
) {
    val arbresColors = MaterialTheme.arbresColors
    val motion = MaterialTheme.arbresMotion
    val animProgress = remember(season) { Animatable(0f) }

    LaunchedEffect(season) {
        animProgress.snapTo(0f)
        animProgress.animateTo(1f, tween(motion.celebration))
    }
    val progress = animProgress.value

    val particles = remember(season) {
        val rng = Random(season.ordinal * 17L)
        List(16) {
            Particle(
                xFraction = rng.nextFloat(),
                phase = rng.nextFloat() * 2f * PI.toFloat(),
                rotationStart = rng.nextFloat() * 360f,
                rotationSpeed = (rng.nextFloat() - 0.5f) * 180f,
                size = 10f + rng.nextFloat() * 14f,
            )
        }
    }

    val color = when (season) {
        Season.WINTER -> Color.White
        Season.SPRING -> Color(0xFFEFAFC8)
        Season.SUMMER -> arbresColors.feuilleClaire
        Season.AUTUMN -> Color(0xFFC8771F)
    }

    Canvas(
        modifier = modifier.fillMaxSize(),
    ) {
        if (progress >= 1f) return@Canvas
        val w = size.width
        val h = size.height
        val envelope = when {
            progress < 0.15f -> progress / 0.15f
            progress < 0.7f -> 1f
            else -> 1f - (progress - 0.7f) / 0.3f
        }
        particles.forEach { p ->
            val cx = p.xFraction * w
            val cy = -p.size + progress * (h + p.size * 2f)
            val xWobble = sin(progress * 4f * PI.toFloat() + p.phase) * 12f
            val rotation = p.rotationStart + p.rotationSpeed * progress
            rotate(degrees = rotation, pivot = Offset(cx + xWobble, cy)) {
                drawSeasonShape(season, color, envelope, p.size, Offset(cx + xWobble, cy))
            }
        }
    }
}

private data class Particle(
    val xFraction: Float,
    val phase: Float,
    val rotationStart: Float,
    val rotationSpeed: Float,
    val size: Float,
)

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSeasonShape(
    season: Season,
    color: Color,
    alpha: Float,
    size: Float,
    center: Offset,
) {
    when (season) {
        Season.WINTER -> drawCircle(color = color.copy(alpha = alpha * 0.85f), radius = size * 0.4f, center = center)
        Season.SPRING -> drawCircle(color = color.copy(alpha = alpha * 0.75f), radius = size * 0.5f, center = center)
        Season.SUMMER -> drawCircle(color = color.copy(alpha = alpha * 0.7f), radius = size * 0.55f, center = center)
        Season.AUTUMN -> drawCircle(color = color.copy(alpha = alpha * 0.8f), radius = size * 0.5f, center = center)
    }
}
