package app.arbre.data

/**
 * Définition statique d'un badge. Pas de table Room — le déblocage est calculé
 * à partir des captures par `BadgeEvaluator`. Tous les badges sont binaires :
 * un critère franchi une fois fige le `unlockedAt` sur la capture déclenchante.
 * Ajouter un badge : entrée dans `BadgeCatalog` + critère dans `BadgeEvaluator`
 * + icône dans `ui/badges/BadgeIcons.kt`.
 */
data class BadgeDef(
    val id: String,
    val label: String,
    val description: String,
    val category: BadgeCategory,
)

enum class BadgeCategory(val label: String) {
    DECOUVERTE("Découverte"),
    BOTANIQUE("Botanique"),
    GEOGRAPHIE("Géographie"),
    REMARQUABLES("Remarquables"),
    DEMESURE("Démesure"),
}

/** État runtime d'un badge : déf + ts de déblocage figé, ou `null` si verrouillé. */
data class BadgeState(
    val def: BadgeDef,
    val unlockedAt: Long?,
) {
    val unlocked: Boolean get() = unlockedAt != null
}

object BadgeCatalog {

    val ESPECE_RARE = BadgeDef(
        id = "espece_rare",
        label = "Espèce rare",
        description = "Une espèce avec moins de 100 individus à Paris.",
        category = BadgeCategory.BOTANIQUE,
    )

    val TOURNEUR_DE_PARIS = BadgeDef(
        id = "tourneur_de_paris",
        label = "Tourneur de Paris",
        description = "Captures dans 10 arrondissements.",
        category = BadgeCategory.GEOGRAPHIE,
    )
    val TOUR_COMPLET = BadgeDef(
        id = "tour_complet",
        label = "Tour complet",
        description = "Les 20 arrondissements de Paris.",
        category = BadgeCategory.GEOGRAPHIE,
    )

    val GEANT = BadgeDef(
        id = "geant",
        label = "Géant",
        description = "Un arbre de plus de 30 m de haut.",
        category = BadgeCategory.DEMESURE,
    )
    val VIEUX_SAGE = BadgeDef(
        id = "vieux_sage",
        label = "Vieux sage",
        description = "Un arbre de plus de 4 m de circonférence.",
        category = BadgeCategory.DEMESURE,
    )

    val ALL: List<BadgeDef> = listOf(
        ESPECE_RARE,
        TOURNEUR_DE_PARIS,
        TOUR_COMPLET,
        GEANT,
        VIEUX_SAGE,
    )
}
