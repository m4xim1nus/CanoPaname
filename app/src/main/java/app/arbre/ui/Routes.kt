package app.arbre.ui

import android.net.Uri

object Routes {
    const val WELCOME = "welcome"
    const val WELCOME_REPLAY = "welcome_replay"
    // Le saut « voir cet arbre » (fly-to + pulse depuis une fiche) ne passe
    // pas par un param de route — il rejouerait à chaque retour sur l'entrée.
    // C'est un intent one-shot : `MapHost.pendingPulseArbreId`, posé avant un
    // `navigate(map())` en launchSingleTop (cf. ArbresNavHost).
    const val MAP = "map"
    const val ARBORETUM = "arboretum"
    const val PROFILE = "profile"
    const val BADGES = "badges"
    const val REMARQUABLES = "remarquables"
    const val REMARQUABLE_DETAIL = "remarquable_detail/{arbreId}"
    const val SPECIES = "species/{speciesIndex}"
    // Destination distincte de MAP : MapViewModel propre + caméra Paris z11
    // + entrée séparée du backstack. `speciesIndices` = set CSV de sks
    // (1 sk = filtre fiche-espèce normale ; N sks = filtre genre depuis la
    // fiche genre).
    const val MAP_FILTERED = "map_filtered/{speciesIndices}"
    // Fiche genre dédiée. `genre` est URL-encodé (peut contenir espace pour
    // les hybrides type « x Cupressocyparis »).
    const val GENRE = "genre/{genre}"
    const val ABOUT = "about"
    const val PHOTO_CREDITS = "photo_credits"

    fun species(speciesIndex: Int): String = "species/$speciesIndex"
    fun mapFiltered(speciesIndex: Int): String = mapFiltered(setOf(speciesIndex))
    fun mapFiltered(speciesIndices: Set<Int>): String =
        "map_filtered/${speciesIndices.sorted().joinToString(",")}"
    fun remarquableDetail(arbreId: Long): String = "remarquable_detail/$arbreId"
    fun map(): String = MAP
    fun genre(genre: String): String = "genre/${Uri.encode(genre)}"
}
