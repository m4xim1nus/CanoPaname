package app.arbre.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import app.arbre.data.Season
import app.arbre.data.rememberSeasonStore

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
 * Tint discret du `surface` selon la saison vive — trait de personnalité,
 * pas recoloration. Ne **jamais** toucher `primary` : le vert du logo/splash
 * doit rester stable pour éviter le flash splash natif → overlay Compose.
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
    val season by rememberSeasonStore().selected.collectAsState()
    val motion = ArbresMotion()
    val baseScheme = if (darkTheme) BaseDarkColors else BaseLightColors
    val targetSurface = seasonalSurface(season, darkTheme)
    val animatedSurface by animateColorAsState(
        targetValue = targetSurface,
        animationSpec = tween(durationMillis = motion.medium, easing = motion.swayEasing),
        label = "seasonSurface",
    )
    val colorScheme = baseScheme.copy(
        surface = animatedSurface,
        background = animatedSurface,
    )
    val arbresColors = if (darkTheme) DarkArbresColors else LightArbresColors

    CompositionLocalProvider(
        LocalArbresColors provides arbresColors,
        LocalArbresMotion provides motion,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = ArbresTypography,
            content = content,
        )
    }
}
