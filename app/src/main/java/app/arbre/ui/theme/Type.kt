package app.arbre.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.arbre.R

/**
 * Typographie de l'app.
 *
 * - **Display / Headline / TitleLarge** : Fraunces SemiBold bundlée
 *   (`res/font/fraunces_semibold.ttf`, ~70 Ko, OFL). Donne du caractère aux
 *   accroches sans dépendance réseau ni Google Fonts runtime.
 * - **TitleMedium / Body / Label** : sans-serif système (default M3). Lisible,
 *   éprouvé, gratuit côté APK.
 *
 * Pas d'override sur les bodies/labels — la lisibilité du corps prime sur le
 * caractère, et changer la sans-serif aurait surchargé l'identité.
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
    // titleMedium / titleSmall / body* / label* gardent la sans-serif système.
    titleMedium = DefaultTypography.titleMedium.copy(
        fontWeight = FontWeight.Medium,
    ),
)

/**
 * TextStyle pour le binôme latin italique des fiches-espèces / arbres.
 * Reste en sans-serif (Fraunces ne fournit pas son italique en static OFL et
 * la sans-serif italique reste lisible et neutre).
 */
val LatinBinomial: TextStyle = TextStyle(
    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
)
