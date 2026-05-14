package app.arbre.data

/**
 * Définition d'un badge. Pas de table Room — le déblocage est calculé à partir
 * des captures par `BadgeEvaluator` (tous binaires : un critère franchi une
 * fois fige le `unlockedAt` sur la capture déclenchante).
 *
 * Deux origines : les badges **statiques** ([BadgeCatalog.ALL]) et deux familles
 * **dynamiques** dérivées du dataset — « Familier des … » (un genre avec ≥
 * [BadgeCatalog.GENRE_FAMILIER_MIN_SPECIES] espèces identifiées) et « Familier
 * du … » (les 20 arrondissements + 2 bois). Le catalogue complet =
 * [BadgeCatalog.full].
 *
 * Ajouter un badge statique : entrée dans [BadgeCatalog] + critère dans
 * `BadgeEvaluator` + icône dans `ui/badges/BadgeIcons.kt`.
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

    /** Un genre obtient un badge « Familier des … » s'il a au moins ce nombre
     *  d'espèces **identifiées** (cf. distribution dataset : 26 genres). */
    const val GENRE_FAMILIER_MIN_SPECIES = 7

    const val FAMILIER_GENRE_PREFIX = "familier_genre_"
    const val FAMILIER_ARR_PREFIX = "familier_arr_"

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

    /** Les badges statiques. Les familles « Familier » s'y ajoutent dans [full]. */
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

    /** Id stable d'un badge « Familier des … » à partir du genre latin. */
    fun genreBadgeId(genre: String): String =
        FAMILIER_GENRE_PREFIX + genre.lowercase().replace(' ', '_')

    /** Id stable d'un badge « Familier du … » à partir d'un ArrKey. */
    fun arrBadgeId(key: ArrKey): String = FAMILIER_ARR_PREFIX + key.idSlug()

    /**
     * Genres éligibles à un badge « Familier des … » (≥
     * [GENRE_FAMILIER_MIN_SPECIES] espèces identifiées), ordre count décroissant
     * puis latin. Le critère de comptage est `SpeciesIndex.genreCount` (exclut
     * les `unknownSpecies`).
     */
    fun familierGenres(speciesIndex: SpeciesIndex): List<String> =
        speciesIndex.allGenres()
            .filter { speciesIndex.genreCount(it) >= GENRE_FAMILIER_MIN_SPECIES }
            .sortedWith(compareByDescending<String> { speciesIndex.genreCount(it) }.thenBy { it })

    /** Badges « Familier des … » (un par genre éligible). */
    fun genreBadges(speciesIndex: SpeciesIndex, genreInfo: GenreInfoRepository): List<BadgeDef> =
        familierGenres(speciesIndex).map { genre ->
            val plural = frenchPluralLower(genreInfo.get(genre)?.nomFr ?: genre)
            BadgeDef(
                id = genreBadgeId(genre),
                label = "Familier des $plural",
                description = "Toutes les espèces de $plural recensées à Paris, capturées.",
                category = BadgeCategory.BOTANIQUE,
            )
        }

    /** Badges « Familier du … » (un par ArrKey ayant au moins un arbre
     *  remarquable). Les arr sans remarquable sont absents du catalogue. */
    fun arrBadges(arrSpecies: ArrSpeciesIndex): List<BadgeDef> =
        arrSpecies.keysWithRemarquables.map { key ->
            val where = when (key) {
                is ArrKey.Paris -> "du ${key.label()} arrondissement"
                else -> "du ${key.label()}"
            }
            BadgeDef(
                id = arrBadgeId(key),
                label = "Familier du ${key.label()}",
                description = "Tous les arbres remarquables $where, capturés.",
                category = BadgeCategory.GEOGRAPHIE,
            )
        }

    /** Catalogue complet : statiques + Familier de genre + Familier d'arrondissement. */
    fun full(
        speciesIndex: SpeciesIndex,
        genreInfo: GenreInfoRepository,
        arrSpecies: ArrSpeciesIndex,
    ): List<BadgeDef> = ALL + genreBadges(speciesIndex, genreInfo) + arrBadges(arrSpecies)
}

/**
 * Pluriel français du nom vernaculaire d'un genre, minuscule initiale, pour les
 * libellés « Familier des chênes / bouleaux / érables ». Règle « -eau → -eaux »
 * + défaut « +s » : couvre les 26 genres à badge (seul irrégulier : Bouleau).
 */
private fun frenchPluralLower(nomFr: String): String {
    val plural = if (nomFr.endsWith("eau")) nomFr + "x" else nomFr + "s"
    return plural.replaceFirstChar { it.lowercase() }
}
