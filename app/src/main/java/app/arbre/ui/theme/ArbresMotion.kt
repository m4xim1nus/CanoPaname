package app.arbre.ui.theme

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Tokens motion (durées + easings) exposés à tous les composables.
 *
 * Sert le même rôle que [ArbresColors] : centraliser des constantes que les
 * écrans réinventaient chacun de leur côté (`tween(350)`, `tween(600)`…).
 * Permet d'accorder le motion language sans toucher à 15 fichiers.
 */
data class ArbresMotion(
    val micro: Int = 150,
    val short: Int = 300,
    val medium: Int = 600,
    val long: Int = 1200,
    val sway: Int = 2400,
    val celebration: Int = 1800,
    val swayEasing: Easing = FastOutSlowInEasing,
    val snapEasing: Easing = FastOutLinearInEasing,
)

internal val LocalArbresMotion = staticCompositionLocalOf { ArbresMotion() }

val MaterialTheme.arbresMotion: ArbresMotion
    @Composable
    @ReadOnlyComposable
    get() = LocalArbresMotion.current
