package app.arbre.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.arbre.R

/**
 * Typographie : Fraunces SemiBold (`res/font/fraunces_semibold.ttf`, ~70 Ko,
 * OFL) sur les niveaux Display/Headline/TitleLarge ; sans-serif système
 * (M3 default) ailleurs — la lisibilité du corps prime sur le caractère.
 */
private val Fraunces = FontFamily(
    Font(R.font.fraunces_semibold, FontWeight.SemiBold),
)

private val DefaultTypography = Typography()

val ArbresTypography: Typography = DefaultTypography.copy(
    displayLarge = DefaultTypography.displayLarge.copy(
        fontFamily = Fraunces,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.5).sp,
    ),
    displayMedium = DefaultTypography.displayMedium.copy(
        fontFamily = Fraunces,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.5).sp,
    ),
    displaySmall = DefaultTypography.displaySmall.copy(
        fontFamily = Fraunces,
        fontWeight = FontWeight.SemiBold,
    ),
    headlineLarge = DefaultTypography.headlineLarge.copy(
        fontFamily = Fraunces,
        fontWeight = FontWeight.SemiBold,
    ),
    headlineMedium = DefaultTypography.headlineMedium.copy(
        fontFamily = Fraunces,
        fontWeight = FontWeight.SemiBold,
    ),
    headlineSmall = DefaultTypography.headlineSmall.copy(
        fontFamily = Fraunces,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
    ),
    titleLarge = DefaultTypography.titleLarge.copy(
        fontFamily = Fraunces,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
    ),
    titleMedium = DefaultTypography.titleMedium.copy(
        fontWeight = FontWeight.Medium,
    ),
)

/**
 * Style binôme latin italique — sans-serif, car Fraunces n'a pas d'italique
 * en static OFL et la sans-serif italique reste lisible.
 */
val LatinBinomial: TextStyle = TextStyle(
    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
)
