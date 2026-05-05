package app.arbre.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Tokens couleur custom hors M3 (or remarquable, écorce, feuille…). Exposés
 * via [LocalArbresColors], accessibles par [MaterialTheme.arbresColors].
 */
data class ArbresColors(
    val or: Color,
    val feuilleClaire: Color,
    val feuilleSombre: Color,
    val ecorce: Color,
    val remarquableOrange: Color,
)

internal val LightArbresColors = ArbresColors(
    or = Or,
    feuilleClaire = FeuilleClaire,
    feuilleSombre = FeuilleSombre,
    ecorce = Ecorce,
    remarquableOrange = RemarquableOrange,
)

internal val DarkArbresColors = ArbresColors(
    or = Or,
    feuilleClaire = FeuilleClaire,
    feuilleSombre = FeuilleSombre,
    ecorce = EcorceClaire,
    remarquableOrange = RemarquableOrange,
)

internal val LocalArbresColors = staticCompositionLocalOf {
    LightArbresColors
}

val MaterialTheme.arbresColors: ArbresColors
    @Composable
    @ReadOnlyComposable
    get() = LocalArbresColors.current
