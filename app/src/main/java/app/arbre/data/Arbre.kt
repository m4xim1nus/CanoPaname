package app.arbre.data

/**
 * Modèle d'arbre minimal aligné sur le dataset OpenData Paris `les-arbres`.
 * Champs réduits au strict nécessaire pour le MVP.
 */
data class Arbre(
    val id: Long,
    val genre: String?,
    val espece: String?,
    val varieteCultivar: String?,
    val hauteurM: Int?,
    val circonferenceCm: Int?,
    val remarquable: Boolean,
    val adresse: String?,
    val latitude: Double,
    val longitude: Double,
) {
    val nomAffichage: String
        get() = listOfNotNull(genre, espece).joinToString(" ").ifBlank { "Arbre #$id" }
}
