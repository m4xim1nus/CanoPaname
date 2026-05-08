package app.arbre.ui.theme

import androidx.compose.ui.graphics.Color

// Source unique de vérité pour la palette. Les pins MapLibre sont en hex
// String (l'API MapLibre Java ne consomme pas les Color Compose).
internal val FeuilleClaire = Color(0xFF81C784)
internal val FeuilleSombre = Color(0xFF2E7D32)
internal val Ecorce = Color(0xFF6D4C41)
internal val EcorceClaire = Color(0xFFBCAAA4)
internal val EcorceMoyenne = Color(0xFF8D6E63)
internal val EcorceMoyenneClaire = Color(0xFFA1887F)
internal val Or = Color(0xFFC9A227)

// DOIT rester aligné avec `MapColors.PIN_ORANGE` pour que badge ↔ pin carte.
internal val RemarquableOrange = Color(0xFFFB8C00)

/** Pins MapLibre — l'API Java consomme du String hex, pas de [Color]. */
object MapColors {
    const val PIN_GREEN: String = "#2E7D32"
    const val PIN_ORANGE: String = "#FB8C00"
    const val PIN_GREY: String = "#9E9E9E"
    const val CLUSTER_MIXED: String = "#81C784"
}
