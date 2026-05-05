package app.arbre.data

/**
 * Définition statique d'un badge. Pas de table Room — le déblocage est
 * calculé à partir des captures par `BadgeEvaluator`. Ajouter un badge :
 * entrée dans `BadgeCatalog` + critère dans `BadgeEvaluator` + icône dans
 * `ui/badges/BadgeIcons.kt`.
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
    SAISONS("Saisons"),
    DEMESURE("Démesure"),
}

data class BadgeState(
    val def: BadgeDef,
    val unlockedAt: Long?,
) {
    val unlocked: Boolean get() = unlockedAt != null
}

object BadgeCatalog {
    val FIRST_CAPTURE = BadgeDef(
        id = "first_capture",
        label = "Première capture",
        description = "Tu as croisé ton premier arbre.",
        category = BadgeCategory.DECOUVERTE,
    )
    val PROMENADE = BadgeDef(
        id = "promenade",
        label = "Promenade",
        description = "10 captures.",
        category = BadgeCategory.DECOUVERTE,
    )
    val MARCHEUR = BadgeDef(
        id = "marcheur",
        label = "Marcheur",
        description = "50 captures.",
        category = BadgeCategory.DECOUVERTE,
    )
    val CENTURION = BadgeDef(
        id = "centurion",
        label = "Centurion",
        description = "100 captures.",
        category = BadgeCategory.DECOUVERTE,
    )

    val BOTANISTE_AMATEUR = BadgeDef(
        id = "botaniste_amateur",
        label = "Botaniste amateur",
        description = "50 espèces différentes.",
        category = BadgeCategory.BOTANIQUE,
    )
    val BOTANISTE_CONFIRME = BadgeDef(
        id = "botaniste_confirme",
        label = "Botaniste confirmé",
        description = "200 espèces différentes.",
        category = BadgeCategory.BOTANIQUE,
    )
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

    val CHASSEUR_REMARQUABLES = BadgeDef(
        id = "chasseur_remarquables",
        label = "Chasseur de remarquables",
        description = "10 arbres remarquables.",
        category = BadgeCategory.REMARQUABLES,
    )
    val LEGENDE = BadgeDef(
        id = "legende",
        label = "Légende",
        description = "50 arbres remarquables.",
        category = BadgeCategory.REMARQUABLES,
    )

    val RONDE_DES_SAISONS = BadgeDef(
        id = "ronde_des_saisons",
        label = "Ronde des saisons",
        description = "Au moins une capture dans chacune des 4 saisons.",
        category = BadgeCategory.SAISONS,
    )
    val ANNEE_COMPLETE = BadgeDef(
        id = "annee_complete",
        label = "Année complète",
        description = "Une capture chaque mois sur 12 mois consécutifs.",
        category = BadgeCategory.SAISONS,
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
        FIRST_CAPTURE,
        PROMENADE,
        MARCHEUR,
        CENTURION,
        BOTANISTE_AMATEUR,
        BOTANISTE_CONFIRME,
        ESPECE_RARE,
        TOURNEUR_DE_PARIS,
        TOUR_COMPLET,
        CHASSEUR_REMARQUABLES,
        LEGENDE,
        RONDE_DES_SAISONS,
        ANNEE_COMPLETE,
        GEANT,
        VIEUX_SAGE,
    )
}
