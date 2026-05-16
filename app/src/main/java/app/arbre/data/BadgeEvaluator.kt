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
        // Ids d'arbres **remarquables** capturés, segmentés par arr de l'arbre.
        // Familier d'arr = couverture de tous les ids `remarquable_ids` de l'arr.
        val capturedRemarquableIdsByArr = HashMap<ArrKey, MutableSet<Long>>()
        // Accumulateur Pokédex : exclut les captures remarquables (sémantique
        // Catalogue/Arboretum, alignée sur les chapitres par fréquence).
        val capturedSksNonRemarquable = HashSet<Int>()

        // Pré-calcul une fois : palier N → set des sks d'espèces actives ayant
        // pokedexNumber ∈ [1..N]. Coût négligeable (≈ 6 × 800 entrées au pire).
        val pokedexTargets: Map<Int, Set<Int>> = BadgeCatalog.POKEDEX_THRESHOLDS
            .associateWith { threshold ->
                speciesIndex.entries()
                    .filter { it.isActive && (it.pokedexNumber ?: 0) in 1..threshold }
                    .map { it.index }
                    .toSet()
            }

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
                if (capture.remarquable) {
                    evaluateFamilierArr(unlocks, arrSpecies, capturedRemarquableIdsByArr, arbre, ts)
                }
                if (!capture.remarquable) {
                    capturedSksNonRemarquable.add(sk)
                    evaluatePokedex(unlocks, pokedexTargets, capturedSksNonRemarquable, ts)
                }
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

    /** Familier d'un arrondissement : capture de **chaque** arbre remarquable
     *  individuel de l'arr. Comparaison directe d'ids — pas de propagation
     *  espèce/genre. N'agit que sur les captures `remarquable == true`
     *  (l'appelant le garantit) et sur l'arr de l'arbre. Les arr sans aucun
     *  remarquable sont court-circuités. */
    private fun evaluateFamilierArr(
        unlocks: MutableMap<String, Long>,
        arrSpecies: ArrSpeciesIndex,
        capturedRemarquableIdsByArr: MutableMap<ArrKey, MutableSet<Long>>,
        arbre: Arbre,
        ts: Long,
    ) {
        val arr = parseArrKey(arbre.adresse)
        val arrTarget = arrSpecies.remarquableArbreIdsOf(arr)
        if (arrTarget.isEmpty()) return
        val seen = capturedRemarquableIdsByArr.getOrPut(arr) { HashSet() }
        seen.add(arbre.id)
        if (seen.containsAll(arrTarget)) {
            unlockOnce(unlocks, BadgeCatalog.arrBadgeId(arr), true, ts)
        }
    }

    /** Pokédex : palier N débloqué dès que toutes les espèces actives avec
     *  pokedexNumber ∈ [1..N] ont été capturées (hors remarquables — sémantique
     *  Catalogue par fréquence). 6 paliers indépendants, déclenchés en cascade. */
    private fun evaluatePokedex(
        unlocks: MutableMap<String, Long>,
        pokedexTargets: Map<Int, Set<Int>>,
        capturedNonRemarquable: Set<Int>,
        ts: Long,
    ) {
        for ((threshold, target) in pokedexTargets) {
            val id = BadgeCatalog.pokedexBadgeId(threshold)
            val pending = target.isNotEmpty() && id !in unlocks
            if (pending && capturedNonRemarquable.containsAll(target)) {
                unlocks[id] = ts
            }
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
