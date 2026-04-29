package app.arbre.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import app.arbre.data.Season

private val BaseLightColors = lightColorScheme(
    primary = FeuilleSombre,
    secondary = Ecorce,
    tertiary = EcorceMoyenne,
)

private val BaseDarkColors = darkColorScheme(
    primary = FeuilleClaire,
    secondary = EcorceClaire,
    tertiary = EcorceMoyenneClaire,
)

/**
 * Tinting saisonnier discret du `surface` selon `Season.current()`.
 * L'écart est volontairement minuscule — c'est un trait de personnalité, pas
 * une recoloration. Ne touche **jamais** `primary` (le vert du logo/splash
 * doit rester stable, sinon flash entre splash natif et overlay).
 *
 * Sprint I réutilisera `Season.current()` pour le bucket de captures ; ce
 * helper reste l'API canonique de la saisonnalité.
 */
private fun seasonalSurface(season: Season, dark: Boolean): Color = when (season) {
    Season.SPRING -> if (dark) SaisonPrintempsDark else SaisonPrintemps
    Season.SUMMER -> if (dark) SaisonEteDark else SaisonEte
    Season.AUTUMN -> if (dark) SaisonAutomneDark else SaisonAutomne
    Season.WINTER -> if (dark) SaisonHiverDark else SaisonHiver
}

@Composable
fun ArbresTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // remember() : on calcule la saison une fois par recomposition initiale.
    // Pas besoin de réagir à un changement de saison à la milliseconde —
    // l'app est tuée et relancée tous les jours en pratique.
    val season = remember { Season.current() }
    val baseScheme = if (darkTheme) BaseDarkColors else BaseLightColors
    val tintedSurface = seasonalSurface(season, darkTheme)
    val colorScheme = baseScheme.copy(
        surface = tintedSurface,
        background = tintedSurface,
    )
    val arbresColors = if (darkTheme) DarkArbresColors else LightArbresColors

    CompositionLocalProvider(LocalArbresColors provides arbresColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = ArbresTypography,
            content = content,
        )
    }
}
