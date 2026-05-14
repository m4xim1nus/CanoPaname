package app.arbre.data

/**
 * Évalue l'état des badges (tous binaires). Balayage chronologique unique :
 * pour chaque critère, le ts de déblocage est figé sur la capture qui a fait
 * basculer la condition. Renvoie une map `id de badge → ts du déblocage` (les
 * badges absents de la map sont verrouillés) — l'assemblage avec les libellés
 * du catalogue (statique + familles dynamiques « Familier ») se fait dans
 * `BadgeRepository`. Coût O(n) par capture, trivial à l'échelle perso.
 */
object BadgeEvaluator {

    fun evaluate(
        captures: List<Capture>,
        arbresById: Map<Long, Arbre>,
        speciesInfo: SpeciesInfoRepository,
        speciesIndex: SpeciesIndex,
        arrSpecies: ArrSpeciesIndex,
    ): Map<String, Long> {
        val sorted = captures.sortedBy { it.timestamp }
        val unlocks = HashMap<String, Long>()

        // Accumulateurs des familles « Familier ».
        val capturedSks = HashSet<Int>()
        val capturedSksByArr = HashMap<ArrKey, MutableSet<Int>>()

        for (capture in sorted) {
            val ts = capture.timestamp
            val arbre = arbresById[capture.arbreId]

            unlockOnce(unlocks, BadgeCatalog.PREMIERE_CAPTURE.id, true, ts)

            // Espèces ultra-rares : compte exact d'individus dans Paris (1..5).
            // Les captures de remarquables n'alimentent pas la dimension espèce.
            if (!capture.remarquable) {
                val count = speciesInfo.get(capture.speciesIndex)?.stats?.count
                val rarityBadge = count?.let(BadgeCatalog.ESPECE_RARETE::get)
                if (rarityBadge != null) unlockOnce(unlocks, rarityBadge.id, true, ts)
            }

            val hauteur = arbre?.hauteurM
            if (hauteur != null && hauteur > 30) unlockOnce(unlocks, BadgeCatalog.GEANT.id, true, ts)
            if (hauteur != null && hauteur < 2) unlockOnce(unlocks, BadgeCatalog.BONSAI.id, true, ts)
            val circ = arbre?.circonferenceCm
            if (circ != null && circ > 400) unlockOnce(unlocks, BadgeCatalog.VIEUX_SAGE.id, true, ts)
            if (circ != null && circ < 10) unlockOnce(unlocks, BadgeCatalog.JEUNE_POUSSE.id, true, ts)

            // Familles « Familier » : on dérive l'espèce de l'arbre (robuste,
            // y compris pour les captures de remarquables).
            val sk = arbre?.let { speciesIndex.indexOf(it) }
            if (arbre != null && sk != null) {
                capturedSks.add(sk)
                evaluateFamilierGenre(unlocks, speciesIndex, sk, capturedSks, ts)
                evaluateFamilierArr(unlocks, speciesIndex, arrSpecies, capturedSksByArr, arbre, sk, ts)
            }
        }
        return unlocks
    }

    /** Familier d'un genre : toutes les espèces *identifiées* du genre capturées
     *  (`genreCount` exclut le `(G, sp.)`). N'agit que sur les genres éligibles. */
    private fun evaluateFamilierGenre(
        unlocks: MutableMap<String, Long>,
        speciesIndex: SpeciesIndex,
        sk: Int,
        capturedSks: Set<Int>,
        ts: Long,
    ) {
        val genre = speciesIndex.genreOf(sk) ?: return
        val target = speciesIndex.genreCount(genre)
        if (target >= BadgeCatalog.GENRE_FAMILIER_MIN_SPECIES &&
            speciesIndex.capturedCountInGenre(genre, capturedSks) == target
        ) {
            unlockOnce(unlocks, BadgeCatalog.genreBadgeId(genre), true, ts)
        }
    }

    /** Familier d'un arrondissement : couverture de toutes les espèces d'arbres
     *  **remarquables** de l'arrondissement de l'arbre. La propagation
     *  genre→`sp.` via `effectivelyCapturedSpecies` reste appliquée (un
     *  remarquable libellé `(Quercus, sp.)` est satisfait par n'importe quel
     *  chêne identifié). Les arr sans aucun remarquable sont court-circuités. */
    private fun evaluateFamilierArr(
        unlocks: MutableMap<String, Long>,
        speciesIndex: SpeciesIndex,
        arrSpecies: ArrSpeciesIndex,
        capturedSksByArr: MutableMap<ArrKey, MutableSet<Int>>,
        arbre: Arbre,
        sk: Int,
        ts: Long,
    ) {
        val arr = parseArrKey(arbre.adresse)
        val arrTarget = arrSpecies.remarquablesOf(arr)
        if (arrTarget.isEmpty()) return
        val seen = capturedSksByArr.getOrPut(arr) { HashSet() }
        seen.add(sk)
        if (speciesIndex.effectivelyCapturedSpecies(seen).containsAll(arrTarget)) {
            unlockOnce(unlocks, BadgeCatalog.arrBadgeId(arr), true, ts)
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
        if (condition && id !in target) target[id] = timestamp
    }
}
