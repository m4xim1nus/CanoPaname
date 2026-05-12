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

    val PREMIERE_CAPTURE = BadgeDef(
        id = "premiere_capture",
        label = "Première capture",
        description = "Ton premier arbre révélé.",
        category = BadgeCategory.DECOUVERTE,
    )

    // Espèces ultra-rares : une espèce dont le nombre d'individus dans Paris
    // est exactement N. Seuils calés sur la distribution réelle du dataset
    // (≈ 237 espèces à 1 ind., 113 à 2, 75 à 3, 30 à 4, 35 à 5 — toutes
    // atteignables en tapant le bon pin gris).
    val ESPECE_UNIQUE = BadgeDef(
        id = "espece_unique",
        label = "Unique",
        description = "Une espèce dont il n'existe qu'un seul arbre dans Paris.",
        category = BadgeCategory.BOTANIQUE,
    )
    val ESPECE_COUPLE = BadgeDef(
        id = "espece_couple",
        label = "Couple",
        description = "Une espèce dont il n'existe que deux arbres dans Paris.",
        category = BadgeCategory.BOTANIQUE,
    )
    val ESPECE_TRINITE = BadgeDef(
        id = "espece_trinite",
        label = "Trinité",
        description = "Une espèce dont il n'existe que trois arbres dans Paris.",
        category = BadgeCategory.BOTANIQUE,
    )
    val ESPECE_QUATUOR = BadgeDef(
        id = "espece_quatuor",
        label = "Quatuor",
        description = "Une espèce dont il n'existe que quatre arbres dans Paris.",
        category = BadgeCategory.BOTANIQUE,
    )
    val ESPECE_QUINTETTE = BadgeDef(
        id = "espece_quintette",
        label = "Quintette",
        description = "Une espèce dont il n'existe que cinq arbres dans Paris.",
        category = BadgeCategory.BOTANIQUE,
    )

    /** Espèce → palier de rareté correspondant (compte exact 1..5), ou `null`. */
    val ESPECE_RARETE: Map<Int, BadgeDef> = mapOf(
        1 to ESPECE_UNIQUE,
        2 to ESPECE_COUPLE,
        3 to ESPECE_TRINITE,
        4 to ESPECE_QUATUOR,
        5 to ESPECE_QUINTETTE,
    )

    val GEANT = BadgeDef(
        id = "geant",
        label = "Géant",
        description = "Un arbre de plus de 30 m de haut.",
        category = BadgeCategory.DEMESURE,
    )
    val BONSAI = BadgeDef(
        id = "bonsai",
        label = "Bonsaï",
        description = "Un arbre de moins de 2 m de haut.",
        category = BadgeCategory.DEMESURE,
    )
    val VIEUX_SAGE = BadgeDef(
        id = "vieux_sage",
        label = "Vieux sage",
        description = "Un arbre de plus de 4 m de circonférence.",
        category = BadgeCategory.DEMESURE,
    )
    val JEUNE_POUSSE = BadgeDef(
        id = "jeune_pousse",
        label = "Jeune pousse",
        description = "Un arbre de moins de 10 cm de circonférence.",
        category = BadgeCategory.DEMESURE,
    )

    val ALL: List<BadgeDef> = listOf(
        PREMIERE_CAPTURE,
        ESPECE_UNIQUE,
        ESPECE_COUPLE,
        ESPECE_TRINITE,
        ESPECE_QUATUOR,
        ESPECE_QUINTETTE,
        GEANT,
        BONSAI,
        VIEUX_SAGE,
        JEUNE_POUSSE,
    )
}
