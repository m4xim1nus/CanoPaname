package app.arbre.data

/**
 * Évalue l'état des badges. Balayage chronologique unique : pour chaque
 * critère (binaire ou palier de progressif), le `unlockedAt` est figé sur la
 * capture qui a fait basculer la condition. Coût O(n × tiers), trivial à
 * l'échelle perso.
 *
 * Sprint 8 : sémantique `unknownSpecies` clarifiée. Une capture `(G, sp.)`
 * **n'incrémente plus** les compteurs « espèce » des badges (Botaniste,
 * Mosaïque). Cohérent avec la fiche genre dédiée — un `(G, sp.)` n'est pas
 * une espèce identifiée. Les badges count-based (Marcheur, Chasseur) ne sont
 * pas affectés. Le filtre repose sur `speciesIndex.unknownSks`.
 */
object BadgeEvaluator {

    fun evaluate(
        captures: List<Capture>,
        arbresById: Map<Long, Arbre>,
        speciesInfo: SpeciesInfoRepository,
        speciesIndex: SpeciesIndex,
    ): List<BadgeState> {
        val sorted = captures.sortedBy { it.timestamp }
        val tierUnlocks = mutableMapOf<Pair<String, Int>, Long>()
        val binaryUnlocks = mutableMapOf<String, Long>()

        val seenSpecies = mutableSetOf<Int>()
        val seenRemarquables = mutableSetOf<Long>()
        val seenArrondissements = mutableSetOf<Int>()
        val seenQuercusSks = mutableSetOf<Int>()
        var totalCount = 0
        val unknownSks = speciesIndex.unknownSks

        for (capture in sorted) {
            totalCount++
            val ts = capture.timestamp
            val arbre = arbresById[capture.arbreId]

            // Marcheur — captures totales (toutes captures, remarquables incluses).
            unlockProgressive(tierUnlocks, BadgeCatalog.MARCHEUR, totalCount, ts)

            // Botaniste — espèces identifiées distinctes. Les remarquables ont
            // leur propre dimension Chasseur ; les `(G, sp.)` n'augmentent pas
            // ce compteur (S8, sémantique clarifiée).
            if (!capture.remarquable && capture.speciesIndex !in unknownSks) {
                seenSpecies.add(capture.speciesIndex)
            }
            unlockProgressive(tierUnlocks, BadgeCatalog.BOTANISTE, seenSpecies.size, ts)

            // Mosaïque de chênes — espèces du genre Quercus identifiées
            // distinctes (S8). Lookup `arbre?.genre` (déjà chargé pour les
            // autres badges) ; l'exclusion sp. est garantie ci-dessus, mais
            // double-check sur unknownSks pour les captures orphelines.
            if (!capture.remarquable
                && arbre?.genre == "Quercus"
                && capture.speciesIndex !in unknownSks
            ) {
                seenQuercusSks.add(capture.speciesIndex)
            }
            unlockProgressive(
                tierUnlocks,
                BadgeCatalog.MOSAIQUE_QUERCUS,
                seenQuercusSks.size,
                ts,
            )

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

            if (capture.remarquable) {
                seenRemarquables.add(capture.arbreId)
            }
            unlockProgressive(tierUnlocks, BadgeCatalog.CHASSEUR, seenRemarquables.size, ts)

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
            if (def.isProgressive) {
                val currentCount = when (def.id) {
                    BadgeCatalog.MARCHEUR.id -> totalCount
                    BadgeCatalog.BOTANISTE.id -> seenSpecies.size
                    BadgeCatalog.CHASSEUR.id -> seenRemarquables.size
                    BadgeCatalog.MOSAIQUE_QUERCUS.id -> seenQuercusSks.size
                    else -> error("Compteur non câblé pour le badge progressif ${def.id}")
                }
                val tiers = def.tiers!!.map { td ->
                    BadgeTier(
                        threshold = td.threshold,
                        label = td.label,
                        unlockedAt = tierUnlocks[def.id to td.threshold],
                    )
                }
                BadgeState.Progressive(def = def, currentCount = currentCount, tiers = tiers)
            } else {
                BadgeState.Binary(def = def, unlockedAt = binaryUnlocks[def.id])
            }
        }
    }

    /** 1..20 ou `null` pour les bois et exclaves. */
    fun parseArrondissement(adresse: String): Int? =
        (parseArrKey(adresse) as? ArrKey.Paris)?.num

    private fun unlockProgressive(
        target: MutableMap<Pair<String, Int>, Long>,
        def: BadgeDef,
        currentValue: Int,
        timestamp: Long,
    ) {
        val tiers = def.tiers ?: return
        for (td in tiers) {
            val key = def.id to td.threshold
            if (currentValue >= td.threshold && key !in target) {
                target[key] = timestamp
            }
        }
    }

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
