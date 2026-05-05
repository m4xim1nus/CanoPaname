package app.arbre.data

/** Modèle d'arbre minimal aligné sur le dataset OpenData Paris `les-arbres`. */
data class Arbre(
    val id: Long,
    val genre: String,
    val espece: String,
    val varieteCultivar: String?,
    val nomCommun: String?,
    val hauteurM: Int?,
    val circonferenceCm: Int?,
    val remarquable: Boolean,
    val adresse: String?,
    val latitude: Double,
    val longitude: Double,
) {
    val nomAffichage: String
        get() = nomCommun ?: "$genre $espece"
}
