package app.arbre.data

/**
 * Évalue l'état des badges. Balayage chronologique unique : pour chaque
 * badge, le `unlockedAt` est figé sur la capture qui a fait basculer le
 * critère. Coût O(n × b), trivial à l'échelle perso.
 */
object BadgeEvaluator {

    fun evaluate(
        captures: List<Capture>,
        arbresById: Map<Long, Arbre>,
        speciesInfo: SpeciesInfoRepository,
    ): List<BadgeState> {
        val sorted = captures.sortedBy { it.timestamp }
        val unlocks = mutableMapOf<String, Long>()

        val seenSpecies = mutableSetOf<Int>()
        val seenRemarquables = mutableSetOf<Long>()
        val seenArrondissements = mutableSetOf<Int>()
        var totalCount = 0

        for (capture in sorted) {
            totalCount++
            val ts = capture.timestamp
            val arbre = arbresById[capture.arbreId]

            unlockOnce(unlocks, BadgeCatalog.FIRST_CAPTURE.id, totalCount >= 1, ts)
            unlockOnce(unlocks, BadgeCatalog.PROMENADE.id, totalCount >= 10, ts)
            unlockOnce(unlocks, BadgeCatalog.MARCHEUR.id, totalCount >= 50, ts)
            unlockOnce(unlocks, BadgeCatalog.CENTURION.id, totalCount >= 100, ts)

            // Cohérence Arboretum : les remarquables ne comptent pas comme
            // espèce — ils ont leur catégorie dédiée.
            if (!capture.remarquable) {
                seenSpecies.add(capture.speciesIndex)
            }
            unlockOnce(unlocks, BadgeCatalog.BOTANISTE_AMATEUR.id, seenSpecies.size >= 50, ts)
            unlockOnce(unlocks, BadgeCatalog.BOTANISTE_CONFIRME.id, seenSpecies.size >= 200, ts)
            if (!capture.remarquable) {
                val count = speciesInfo.get(capture.speciesIndex)?.stats?.count
                if (count != null && count < 100) {
                    unlockOnce(unlocks, BadgeCatalog.ESPECE_RARE.id, true, ts)
                }
            }

            val arr = arbre?.adresse?.let(::parseArrondissement)
            if (arr != null) {
                seenArrondissements.add(arr)
            }
            unlockOnce(unlocks, BadgeCatalog.TOURNEUR_DE_PARIS.id, seenArrondissements.size >= 10, ts)
            unlockOnce(unlocks, BadgeCatalog.TOUR_COMPLET.id, seenArrondissements.size >= 20, ts)

            if (capture.remarquable) {
                seenRemarquables.add(capture.arbreId)
            }
            unlockOnce(unlocks, BadgeCatalog.CHASSEUR_REMARQUABLES.id, seenRemarquables.size >= 10, ts)
            unlockOnce(unlocks, BadgeCatalog.LEGENDE.id, seenRemarquables.size >= 50, ts)

            val hauteur = arbre?.hauteurM
            if (hauteur != null && hauteur > 30) {
                unlockOnce(unlocks, BadgeCatalog.GEANT.id, true, ts)
            }
            val circ = arbre?.circonferenceCm
            if (circ != null && circ > 400) {
                unlockOnce(unlocks, BadgeCatalog.VIEUX_SAGE.id, true, ts)
            }
        }

        return BadgeCatalog.ALL.map { def ->
            BadgeState(def = def, unlockedAt = unlocks[def.id])
        }
    }

    /** 1..20 ou `null` pour les bois et exclaves. */
    fun parseArrondissement(adresse: String): Int? =
        (parseArrKey(adresse) as? ArrKey.Paris)?.num

    private fun unlockOnce(
        target: MutableMap<String, Long>,
        id: String,
        condition: Boolean,
        timestamp: Long,
    ) {
        if (condition && id !in target) {
            target[id] = timestamp
        }
    }
}
