package app.arbre.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Tokens couleur de l'app — source unique de vérité pour la palette.
 *
 * - Les couleurs M3 (primary/secondary/tertiary…) sont calées dans Theme.kt.
 * - Les tokens custom (or, ecorce, feuille…) sont exposés via [ArbresColors].
 * - Les pins MapLibre vivent en [String] hex (l'API MapLibre Java ne consomme
 *   pas les Color Compose).
 */
internal val FeuilleClaire = Color(0xFF81C784)
internal val FeuilleSombre = Color(0xFF2E7D32)
internal val Ecorce = Color(0xFF6D4C41)
internal val EcorceClaire = Color(0xFFBCAAA4)
internal val EcorceMoyenne = Color(0xFF8D6E63)
internal val EcorceMoyenneClaire = Color(0xFFA1887F)
internal val Or = Color(0xFFC9A227)

// Couleur d'identité des arbres remarquables. Alignée sur `MapColors.PIN_ORANGE`
// pour que la pastille orange du badge écho exactement le pin sur la carte.
internal val RemarquableOrange = Color(0xFFFB8C00)

// Réserve saisonnière — appliquée subtilement sur le surface dans Theme.kt.
internal val SaisonPrintemps = Color(0xFFE8F5E9) // vert très pâle
internal val SaisonEte = Color(0xFFDDEEDD)       // vert dense pâle
internal val SaisonAutomne = Color(0xFFF5EBD9)   // ocre crème
internal val SaisonHiver = Color(0xFFE6ECEF)     // bleu-gris très pâle

internal val SaisonPrintempsDark = Color(0xFF1B2620)
internal val SaisonEteDark = Color(0xFF1A231C)
internal val SaisonAutomneDark = Color(0xFF24201A)
internal val SaisonHiverDark = Color(0xFF1A1F22)

/**
 * Couleurs des pins MapLibre. MapLibre Java attend des [String] hex, donc on
 * reste sur ce format ici (et pas [Color]). Centralisé pour ne plus avoir 3
 * constantes dispersées dans MapScreen.kt.
 */
object MapColors {
    const val PIN_GREEN: String = "#2E7D32"
    const val PIN_ORANGE: String = "#FB8C00"
    const val PIN_GREY: String = "#9E9E9E"
}
