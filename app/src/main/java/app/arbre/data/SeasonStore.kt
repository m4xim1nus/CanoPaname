package app.arbre.data

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Saison sélectionnée — en mémoire uniquement (volontairement non persisté :
 * la saison vive est l'état par défaut, retomber dessus à chaque session).
 * Les écrans qui montrent l'état de découverte lisent ce flow ; les écrans
 * globaux (Profil, badges) ignorent la sélection.
 */
class SeasonStore {
    val selected: MutableStateFlow<Season> = MutableStateFlow(Season.current())

    fun select(season: Season) {
        selected.value = season
    }
}
