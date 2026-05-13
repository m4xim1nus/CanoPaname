package app.arbre.ui.genre

/**
 * Callbacks de navigation propagés à la fiche genre. Regroupés en data class
 * pour éviter une signature `Composable` à 6 params, sans changer la sémantique
 * (chaque callback reste indépendant).
 */
data class GenreActions(
    val onBack: () -> Unit,
    val onSpeciesClick: (Int) -> Unit = {},
    val onShowOnMap: (Set<Int>) -> Unit = {},
    val onShowArbreOnMap: (Long) -> Unit = {},
    val onUnlockLost: () -> Unit = {},
)
