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
        val seenArrondissements = mutableSetOf<Int>()

        for (capture in sorted) {
            val ts = capture.timestamp
            val arbre = arbresById[capture.arbreId]

            if (!capture.remarquable) {
                val count = speciesInfo.get(capture.speciesIndex)?.stats?.count
                if (count != null && count < 100) {
                    unlockBinaryOnce(binaryUnlocks, BadgeCatalog.ESPECE_RARE.id, true, ts)
                }
            }

            val arr = arbre?.adresse?.let(::parseArrondissement)
            if (arr != null) {
                seenArrondissements.add(arr)
            }
            unlockBinaryOnce(binaryUnlocks, BadgeCatalog.TOURNEUR_DE_PARIS.id, seenArrondissements.size >= 10, ts)
            unlockBinaryOnce(binaryUnlocks, BadgeCatalog.TOUR_COMPLET.id, seenArrondissements.size >= 20, ts)

            val hauteur = arbre?.hauteurM
            if (hauteur != null && hauteur > 30) {
                unlockBinaryOnce(binaryUnlocks, BadgeCatalog.GEANT.id, true, ts)
            }
            val circ = arbre?.circonferenceCm
            if (circ != null && circ > 400) {
                unlockBinaryOnce(binaryUnlocks, BadgeCatalog.VIEUX_SAGE.id, true, ts)
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
