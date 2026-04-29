package app.arbre.data

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * État partagé de la saison sélectionnée par l'utilisateur. Vit en mémoire
 * uniquement (singleton dans `ArbresApp`) — au relancement de l'app, on
 * retombe sur `Season.current()`. Pas de persistance volontaire : la saison
 * vive est l'instance « par défaut » du jeu, et un retour explicite vers
 * une archive est attendu chaque session si l'utilisateur le souhaite.
 *
 * Les écrans qui montrent l'état de découverte (Map, Arboretum,
 * Remarquables) lisent ce flow ; les écrans globaux (Profil, badges)
 * ignorent la sélection.
 */
class SeasonStore {
    val selected: MutableStateFlow<Season> = MutableStateFlow(Season.current())

    fun select(season: Season) {
        selected.value = season
    }
}
