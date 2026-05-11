package app.arbre.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Source unique du `Flow<List<BadgeState>>` consommé par `ProfileScreen`
 * (preview des derniers débloqués) et `BadgesScreen` (grille complète). Ré-émet
 * à chaque INSERT capture via le Flow Room sous-jacent.
 */
class BadgeRepository(
    private val captureRepo: CaptureRepository,
    private val arbreRepo: ArbreRepository,
    private val speciesInfoRepo: SpeciesInfoRepository,
    private val speciesIndex: SpeciesIndex,
) {
    fun badges(): Flow<List<BadgeState>> = captureRepo.toutesLesCaptures()
        .map { captures ->
            val arbreIds = captures.map { it.arbreId }.toSet()
            val arbresById = arbreRepo.arbresParIds(arbreIds)
            BadgeEvaluator.evaluate(captures, arbresById, speciesInfoRepo, speciesIndex)
        }
}
