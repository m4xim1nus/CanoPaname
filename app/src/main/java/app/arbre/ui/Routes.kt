package app.arbre.ui

import android.net.Uri

object Routes {
    const val WELCOME = "welcome"
    const val WELCOME_REPLAY = "welcome_replay"
    // `pulseArbreId` en query param optionnel — depuis fiche-remarquable ou
    // PhotoLightbox on saute à un arbre exact (fly-to + pulse + sheet).
    const val MAP = "map?pulseArbreId={pulseArbreId}"
    const val ARBORETUM = "arboretum"
    const val PROFILE = "profile"
    const val BADGES = "badges"
    const val REMARQUABLES = "remarquables"
    const val REMARQUABLE_DETAIL = "remarquable_detail/{arbreId}"
    // `celebrate` en query param — compose-navigation n'autorise les
    // optionnels qu'après `?`.
    const val SPECIES = "species/{speciesIndex}?celebrate={celebrate}"
    // Destination distincte de MAP : MapViewModel propre + caméra Paris z11
    // + entrée séparée du backstack. `speciesIndices` = set CSV de sks
    // (1 sk = filtre fiche-espèce normale ; N sks = filtre genre depuis la
    // fiche genre).
    const val MAP_FILTERED = "map_filtered/{speciesIndices}"
    // Fiche genre dédiée. `genre` est URL-encodé (peut contenir espace pour
    // les hybrides type « x Cupressocyparis »).
    const val GENRE = "genre/{genre}"
    const val ABOUT = "about"

    fun species(speciesIndex: Int, celebrate: Boolean = false): String =
        "species/$speciesIndex?celebrate=$celebrate"
    fun mapFiltered(speciesIndex: Int): String = mapFiltered(setOf(speciesIndex))
    fun mapFiltered(speciesIndices: Set<Int>): String =
        "map_filtered/${speciesIndices.sorted().joinToString(",")}"
    fun remarquableDetail(arbreId: Long): String = "remarquable_detail/$arbreId"
    fun map(pulseArbreId: Long? = null): String =
        if (pulseArbreId != null) "map?pulseArbreId=$pulseArbreId" else "map"
    fun genre(genre: String): String = "genre/${Uri.encode(genre)}"
}
