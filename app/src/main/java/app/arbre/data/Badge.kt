package app.arbre.data

/**
 * Définition statique d'un badge. Pas de table Room — le déblocage est calculé
 * à partir des captures par `BadgeEvaluator`. Un badge est soit binaire
 * (`tiers == null`), soit progressif (cumul de paliers ; chaque palier a son
 * propre `unlockedAt` figé). Ajouter un badge : entrée dans `BadgeCatalog` +
 * critère dans `BadgeEvaluator` + icône dans `ui/badges/BadgeIcons.kt`.
 */
data class BadgeDef(
    val id: String,
    val label: String,
    val description: String,
    val category: BadgeCategory,
    val tiers: List<TierDef>? = null,
    /** Unité comptée par les paliers (« captures », « espèces »…), affichée
     *  dans la pill de score. `null` pour les badges binaires. */
    val unitLabel: String? = null,
) {
    val isProgressive: Boolean get() = tiers != null
}

/** Définition statique d'un palier d'un badge progressif. */
data class TierDef(val threshold: Int, val label: String)

enum class BadgeCategory(val label: String) {
    DECOUVERTE("Découverte"),
    BOTANIQUE("Botanique"),
    GEOGRAPHIE("Géographie"),
    REMARQUABLES("Remarquables"),
    DEMESURE("Démesure"),
}

sealed class BadgeState {
    abstract val def: BadgeDef

    val unlocked: Boolean
        get() = when (this) {
            is Binary -> unlockedAt != null
            is Progressive -> tiers.any { it.unlockedAt != null }
        }

    data class Binary(
        override val def: BadgeDef,
        val unlockedAt: Long?,
    ) : BadgeState()

    data class Progressive(
        override val def: BadgeDef,
        val currentCount: Int,
        val tiers: List<BadgeTier>,
    ) : BadgeState() {
        val unlockedTierCount: Int get() = tiers.count { it.unlockedAt != null }
        val nextTier: BadgeTier? get() = tiers.firstOrNull { it.unlockedAt == null }
        val lastUnlockedTier: BadgeTier? get() = tiers.lastOrNull { it.unlockedAt != null }
        val lastUnlockedAt: Long? get() = tiers.mapNotNull { it.unlockedAt }.maxOrNull()
        val isFullyUnlocked: Boolean get() = nextTier == null
    }
}

/** État runtime d'un palier (déf + ts de déblocage figé, ou null si verrouillé). */
data class BadgeTier(
    val threshold: Int,
    val label: String,
    val unlockedAt: Long?,
)

object BadgeCatalog {

    val MARCHEUR = BadgeDef(
        id = "marcheur",
        label = "Marcheur",
        description = "Captures cumulées dans Paris.",
        category = BadgeCategory.DECOUVERTE,
        unitLabel = "captures",
        tiers = listOf(
            TierDef(1, "Première capture"),
            TierDef(10, "Promenade"),
            TierDef(25, "Flâneur"),
            TierDef(50, "Marcheur"),
            TierDef(100, "Centurion"),
            TierDef(250, "Endurance"),
        ),
    )

    val BOTANISTE = BadgeDef(
        id = "botaniste",
        label = "Botaniste",
        description = "Espèces différentes croisées.",
        category = BadgeCategory.BOTANIQUE,
        unitLabel = "espèces",
        tiers = listOf(
            TierDef(1, "Curieux"),
            TierDef(10, "Apprenti"),
            TierDef(25, "Pousse"),
            TierDef(50, "Amateur"),
            TierDef(100, "Passionné"),
            TierDef(200, "Confirmé"),
        ),
    )

    val ESPECE_RARE = BadgeDef(
        id = "espece_rare",
        label = "Espèce rare",
        description = "Une espèce avec moins de 100 individus à Paris.",
        category = BadgeCategory.BOTANIQUE,
    )

    /**
     * Mosaïque de chênes (S8) : capturer N espèces différentes du genre
     * Quercus. Les `Quercus sp.` ne comptent pas (cohérent avec la sémantique
     * S8 — un sp. n'est pas une espèce identifiée).
     */
    val MOSAIQUE_QUERCUS = BadgeDef(
        id = "mosaique_quercus",
        label = "Mosaïque de chênes",
        description = "Espèces différentes du genre Quercus capturées.",
        category = BadgeCategory.BOTANIQUE,
        unitLabel = "chênes",
        tiers = listOf(
            TierDef(3, "Bosquet"),
            TierDef(5, "Chênaie"),
            TierDef(10, "Forêt"),
        ),
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

    val CHASSEUR = BadgeDef(
        id = "chasseur",
        label = "Chasseur de remarquables",
        description = "Arbres remarquables capturés.",
        category = BadgeCategory.REMARQUABLES,
        unitLabel = "remarquables",
        tiers = listOf(
            TierDef(1, "Premier remarquable"),
            TierDef(5, "Connaisseur"),
            TierDef(10, "Chasseur"),
            TierDef(25, "Expert"),
            TierDef(50, "Légende"),
        ),
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
        MARCHEUR,
        BOTANISTE,
        ESPECE_RARE,
        MOSAIQUE_QUERCUS,
        TOURNEUR_DE_PARIS,
        TOUR_COMPLET,
        CHASSEUR,
        GEANT,
        VIEUX_SAGE,
    )

    /** Total des paliers (somme tiers progressifs + 1 par badge binaire) — base
     *  du compteur global « X / Y débloqués » dans `BadgesScreen`. */
    val totalTierCount: Int = ALL.sumOf { it.tiers?.size ?: 1 }
}
