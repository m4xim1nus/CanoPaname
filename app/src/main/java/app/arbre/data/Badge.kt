package app.arbre.data

/**
 * Définition statique d'un badge. Volontairement minimal au Sprint H : on en
 * a un seul (« 1re capture »), on installe la grammaire pour ajouter
 * facilement les suivants en Phase 4.
 *
 * Pas de table Room : le déblocage se calcule à partir des captures
 * existantes (`unlockTimestamp`), donc tout reste dérivé. Ajouter un badge
 * = ajouter une entrée à `BadgeCatalog`.
 */
data class BadgeDef(
    val id: String,
    val label: String,
    val description: String,
)

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
    )

    val ALL: List<BadgeDef> = listOf(FIRST_CAPTURE)
}
