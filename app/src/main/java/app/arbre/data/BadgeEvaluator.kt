package app.arbre.data

/**
 * Évalue l'état des badges (tous binaires). Balayage chronologique unique :
 * pour chaque critère, le `unlockedAt` est figé sur la capture qui a fait
 * basculer la condition. Coût O(n), trivial à l'échelle perso.
 */
object BadgeEvaluator {

    fun evaluate(
        captures: List<Capture>,
        arbresById: Map<Long, Arbre>,
        speciesInfo: SpeciesInfoRepository,
    ): List<BadgeState> {
        val sorted = captures.sortedBy { it.timestamp }
        val binaryUnlocks = mutableMapOf<String, Long>()

        for (capture in sorted) {
            val ts = capture.timestamp
            val arbre = arbresById[capture.arbreId]

            unlockBinaryOnce(binaryUnlocks, BadgeCatalog.PREMIERE_CAPTURE.id, true, ts)

            // Espèces ultra-rares : compte exact d'individus dans Paris (1..5).
            // Les captures de remarquables n'alimentent pas la dimension espèce.
            if (!capture.remarquable) {
                val count = speciesInfo.get(capture.speciesIndex)?.stats?.count
                val rarityBadge = count?.let(BadgeCatalog.ESPECE_RARETE::get)
                if (rarityBadge != null) {
                    unlockBinaryOnce(binaryUnlocks, rarityBadge.id, true, ts)
                }
            }

            val hauteur = arbre?.hauteurM
            if (hauteur != null && hauteur > 30) {
                unlockBinaryOnce(binaryUnlocks, BadgeCatalog.GEANT.id, true, ts)
            }
            if (hauteur != null && hauteur < 2) {
                unlockBinaryOnce(binaryUnlocks, BadgeCatalog.BONSAI.id, true, ts)
            }
            val circ = arbre?.circonferenceCm
            if (circ != null && circ > 400) {
                unlockBinaryOnce(binaryUnlocks, BadgeCatalog.VIEUX_SAGE.id, true, ts)
            }
            if (circ != null && circ < 10) {
                unlockBinaryOnce(binaryUnlocks, BadgeCatalog.JEUNE_POUSSE.id, true, ts)
            }
        }

        return BadgeCatalog.ALL.map { def ->
            BadgeState(def = def, unlockedAt = binaryUnlocks[def.id])
        }
    }

    /** 1..20 ou `null` pour les bois et exclaves. */
    fun parseArrondissement(adresse: String): Int? =
        (parseArrKey(adresse) as? ArrKey.Paris)?.num

    private fun unlockBinaryOnce(
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
