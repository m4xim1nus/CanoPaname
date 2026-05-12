package app.arbre.ui.common

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos

/**
 * Horloges d'animation pilotées frame par frame via [withFrameNanos] — insensibles à l'échelle
 * d'animation système (options développeur → « Échelle d'animation des animateurs » = 0 force
 * `MotionDurationScale = 0`, ce qui fige instantanément toutes les API d'animation Compose qui
 * consultent ce token : `animate*AsState`, `rememberInfiniteTransition`, `Animatable.animateTo`,
 * `AnimatedVisibility`…). `withFrameNanos` ne le consulte pas — il rend juste l'horodatage du
 * frame, fourni par le `MonotonicFrameClock` adossé au `Choreographer`, qui tique à chaque vsync
 * quel que soit ce réglage.
 *
 * Pattern d'origine : la boucle `withFrameNanos` de `RadarGlyph` (`ui/map/HuntPanel.kt`).
 *
 * À réserver aux animations qui *doivent* rester vivantes même échelle = 0 — au premier chef le
 * splash cold-start (seul retour visuel pendant le chargement DB + GeoJSON) et les écrans de
 * célébration. Pas la peine de tout migrer : un bouton qui ne ripple pas, un FAB qui se décale
 * d'un coup en mode accessibilité, c'est le comportement attendu.
 */

/**
 * Millisecondes écoulées depuis le 1er frame qui suit le mount. Monotone, repart de 0 à chaque
 * entrée en composition. Le `State` est volontairement renvoyé non déballé : le lire dans un
 * `graphicsLayer { }` plutôt que dans le corps du composable défère l'invalidation à la phase de
 * dessin (pas de recomposition par frame).
 */
@Composable
fun rememberFrameMillis(): State<Long> {
    val state = remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        var startNanos = 0L
        while (true) {
            withFrameNanos { now ->
                if (startNanos == 0L) startNanos = now
                state.longValue = (now - startNanos) / 1_000_000L
            }
        }
    }
    return state
}

/**
 * Rampe one-shot 0 → 1 sur [durationMs], easée par [easing], qui reste figée à `1f` une fois
 * terminée (la boucle s'arrête alors d'elle-même). Remplace le pattern
 * `remember { Animatable(0f) }` + `LaunchedEffect { animateTo(1f, tween(durationMs, easing)) }`.
 */
@Composable
fun rememberFrameProgress(
    durationMs: Int,
    easing: Easing = LinearEasing,
): State<Float> {
    val state = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(durationMs) {
        val duration = durationMs.coerceAtLeast(1)
        var startNanos = 0L
        while (state.floatValue < 1f) {
            withFrameNanos { now ->
                if (startNanos == 0L) startNanos = now
                val elapsedMs = (now - startNanos) / 1_000_000L
                val raw = (elapsedMs.toFloat() / duration).coerceIn(0f, 1f)
                state.floatValue = easing.transform(raw)
            }
        }
    }
    return state
}

/**
 * Onde triangulaire 0 → 1 → 0 → … de période complète [periodMs] (chaque demi-leg easé par
 * [easing]). Boucle infinie. Remplace `rememberInfiniteTransition` + `animateFloat(repeatMode =
 * Reverse)` — attention, `periodMs` est l'**aller-retour entier**, là où `infiniteRepeatable`
 * prend la durée d'un seul leg : convertir `tween(d, Reverse)` en `rememberFramePingPong(d * 2)`.
 * Pour une plage `-1 → 1 → -1`, mapper la sortie par `v * 2f - 1f`.
 */
@Composable
fun rememberFramePingPong(
    periodMs: Int,
    easing: Easing = FastOutSlowInEasing,
): State<Float> {
    val state = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(periodMs) {
        val period = periodMs.coerceAtLeast(2)
        var startNanos = 0L
        while (true) {
            withFrameNanos { now ->
                if (startNanos == 0L) startNanos = now
                val elapsedMs = (now - startNanos) / 1_000_000L
                val phase = (elapsedMs % period).toFloat() / period   // 0..1
                val tri = if (phase < 0.5f) phase * 2f else 2f - phase * 2f   // 0..1..0
                state.floatValue = easing.transform(tri)
            }
        }
    }
    return state
}
