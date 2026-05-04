package app.arbre.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Tokens couleur custom hors M3 ColorScheme.
 *
 * Material3 ne couvre pas les concepts métier de l'app (or remarquable, écorce,
 * feuille…). Ces tokens sont exposés en parallèle via [LocalArbresColors] et
 * accessibles par [MaterialTheme.arbresColors] dans tout composable.
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
