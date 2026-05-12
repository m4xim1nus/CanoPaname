package app.arbre.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Source unique du `Flow<List<BadgeState>>` consommé par `ProfileScreen`
 * (preview des derniers débloqués) et `BadgesScreen` (grille complète). Ré-émet
 * à chaque INSERT capture via le Flow Room sous-jacent.
 *
 * Le catalogue complet (statiques + familles dynamiques « Familier ») est
 * dérivé une fois du dataset ; `BadgeEvaluator` ne renvoie que les ts de
 * déblocage, qu'on zippe ici avec le catalogue.
 */
class BadgeRepository(
    private val captureRepo: CaptureRepository,
    private val arbreRepo: ArbreRepository,
    private val speciesInfoRepo: SpeciesInfoRepository,
    private val speciesIndex: SpeciesIndex,
    private val genreInfo: GenreInfoRepository,
    private val arrSpecies: ArrSpeciesIndex,
) {
    /** Catalogue complet, constant pour un dataset donné. */
    val catalog: List<BadgeDef> by lazy {
        BadgeCatalog.full(speciesIndex, genreInfo, arrSpecies)
    }

    fun badges(): Flow<List<BadgeState>> = captureRepo.toutesLesCaptures()
        .map { captures ->
            val arbreIds = captures.map { it.arbreId }.toSet()
            val arbresById = arbreRepo.arbresParIds(arbreIds)
            val unlocks = BadgeEvaluator.evaluate(
                captures, arbresById, speciesInfoRepo, speciesIndex, arrSpecies,
            )
            catalog.map { def -> BadgeState(def = def, unlockedAt = unlocks[def.id]) }
        }
}
