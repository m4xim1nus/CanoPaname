package app.arbre.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

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

@Composable
fun ArbresTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val motion = ArbresMotion()
    val colorScheme = if (darkTheme) BaseDarkColors else BaseLightColors
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
