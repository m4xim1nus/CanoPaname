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
import androidx.compose.ui.input.pointer.pointerInput
import app.arbre.data.Season
import app.arbre.ui.theme.arbresColors
import app.arbre.ui.theme.arbresMotion
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Couche ambiante éphémère qui flotte au-dessus de l'écran 2-3 s à chaque
 * changement de saison. Particules saisonnières (flocon → pétale → feuille
 * verte → feuille cuivrée), purement décoratives, non bloquantes pour les
 * inputs (cf. `pointerInput {}` vide).
 *
 * `triggerKey` doit être quelque chose qui change à chaque switch (la saison
 * elle-même fait l'affaire). Le composable se monte avec animation 0→1, puis
 * fade-out passé `arbresMotion.celebration` (1800 ms).
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

    // Particules figées par recomposition de saison : positions x random,
    // chacune avec offset Y et rotation propres.
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
        Season.SPRING -> Color(0xFFEFAFC8) // pétale rose
        Season.SUMMER -> arbresColors.feuilleClaire
        Season.AUTUMN -> Color(0xFFC8771F) // feuille cuivrée
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {} // bloque rien : laisse passer les events
    ) {
        if (progress >= 1f) return@Canvas
        val w = size.width
        val h = size.height
        // Alpha enveloppe : monte vite (0-15 %), tient (15-70 %), fade (70-100 %).
        val envelope = when {
            progress < 0.15f -> progress / 0.15f
            progress < 0.7f -> 1f
            else -> 1f - (progress - 0.7f) / 0.3f
        }
        particles.forEach { p ->
            val cx = p.xFraction * w
            // Trajectoire : descente linéaire 0→h*1.1, légère oscillation X.
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
