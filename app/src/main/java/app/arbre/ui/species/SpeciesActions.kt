package app.arbre.ui.species

/**
 * Callbacks de navigation propagés à la fiche espèce. `onShowOnMap` reçoit
 * toujours un singleton `setOf(entry.index)` ici — c'est `GenreDetailScreen`
 * qui héberge le set polymorphe (sp. + sks identifiés capturés du genre).
 * `onRedirectToGenre` n'est invoqué que sur deep link historique pointant un
 * `unknownSpecies` (la fiche correspondante est absorbée par la fiche genre).
 */
data class SpeciesActions(
    val onBack: () -> Unit,
    val onShowOnMap: (Set<Int>) -> Unit = {},
    val onShowArbreOnMap: (Long) -> Unit = {},
    val onRemarquableClick: (Long) -> Unit = {},
    val onUnlockLost: () -> Unit = {},
    val onRedirectToGenre: (String) -> Unit = {},
)
