package app.arbre.data

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/**
 * Évalue l'état des badges à partir des captures de l'utilisateur.
 *
 * Approche : balayage chronologique unique. Pour chaque badge, on note le
 * timestamp de la capture qui a fait basculer le critère (déblocage figé
 * dans le temps). Coût O(n × b) où n = # captures et b = # badges, qui
 * reste largement négligeable pour l'usage perso (n se compte en
 * centaines, b en dizaines).
 *
 * Pure fonction : pas de side-effect, testable directement.
 */
object BadgeEvaluator {

    fun evaluate(
        captures: List<Capture>,
        arbresById: Map<Long, Arbre>,
        speciesInfo: SpeciesInfoRepository,
    ): List<BadgeState> {
        val sorted = captures.sortedBy { it.timestamp }
        val unlocks = mutableMapOf<String, Long>()

        // Accumulateurs maintenus à mesure du scan.
        val seenSpecies = mutableSetOf<Int>()
        val seenRemarquables = mutableSetOf<Long>()
        val seenSeasons = mutableSetOf<Season>()
        val seenArrondissements = mutableSetOf<Int>()
        val seenYearMonths = sortedSetOf<YearMonth>()
        var totalCount = 0

        for (capture in sorted) {
            totalCount++
            val ts = capture.timestamp
            val arbre = arbresById[capture.arbreId]

            // Découverte — # captures cumulées.
            unlockOnce(unlocks, BadgeCatalog.FIRST_CAPTURE.id, totalCount >= 1, ts)
            unlockOnce(unlocks, BadgeCatalog.PROMENADE.id, totalCount >= 10, ts)
            unlockOnce(unlocks, BadgeCatalog.MARCHEUR.id, totalCount >= 50, ts)
            unlockOnce(unlocks, BadgeCatalog.CENTURION.id, totalCount >= 100, ts)

            // Botanique — espèces (les remarquables ne comptent pas comme espèce
            // pour l'Arboretum, on garde la même règle ici).
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

            // Géographie — arrondissement parsé depuis l'adresse de l'arbre
            // (format normalisé par tools/build_dataset.py : « …, 5e »).
            val arr = arbre?.adresse?.let(::parseArrondissement)
            if (arr != null) {
                seenArrondissements.add(arr)
            }
            unlockOnce(unlocks, BadgeCatalog.TOURNEUR_DE_PARIS.id, seenArrondissements.size >= 10, ts)
            unlockOnce(unlocks, BadgeCatalog.TOUR_COMPLET.id, seenArrondissements.size >= 20, ts)

            // Remarquables — # arbres remarquables distincts.
            if (capture.remarquable) {
                seenRemarquables.add(capture.arbreId)
            }
            unlockOnce(unlocks, BadgeCatalog.CHASSEUR_REMARQUABLES.id, seenRemarquables.size >= 10, ts)
            unlockOnce(unlocks, BadgeCatalog.LEGENDE.id, seenRemarquables.size >= 50, ts)

            // Saisons.
            seenSeasons.add(capture.season)
            unlockOnce(unlocks, BadgeCatalog.RONDE_DES_SAISONS.id, seenSeasons.size == 4, ts)

            seenYearMonths.add(yearMonthOf(ts))
            if (hasTwelveConsecutiveMonths(seenYearMonths)) {
                unlockOnce(unlocks, BadgeCatalog.ANNEE_COMPLETE.id, true, ts)
            }

            // Démesure — caractéristiques de l'arbre capturé.
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

    /** Match « 1er » (1) ou « 5e », « 12e », … (2-20) en suffixe d'adresse. */
    private val ARR_PATTERN = Regex(""", (\d{1,2})(?:er|e)$""")

    fun parseArrondissement(adresse: String): Int? {
        val match = ARR_PATTERN.find(adresse) ?: return null
        val n = match.groupValues[1].toIntOrNull() ?: return null
        return if (n in 1..20) n else null
    }

    private val PARIS_ZONE: ZoneId = ZoneId.of("Europe/Paris")

    internal fun yearMonthOf(epochMillis: Long): YearMonth =
        YearMonth.from(Instant.ofEpochMilli(epochMillis).atZone(PARIS_ZONE))

    /**
     * Cherche une fenêtre de 12 mois consécutifs où chaque mois figure dans
     * [months]. Trié → on parcourt et on regarde chaque mois comme le
     * potentiel début d'une fenêtre.
     */
    internal fun hasTwelveConsecutiveMonths(months: Set<YearMonth>): Boolean {
        if (months.size < 12) return false
        return months.any { start ->
            (0 until 12).all { offset -> start.plusMonths(offset.toLong()) in months }
        }
    }

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
