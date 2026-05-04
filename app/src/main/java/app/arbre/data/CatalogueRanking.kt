package app.arbre.data

/**
 * Ordre d'affichage du Catalogue (Arboretum + fiche-espèce) : count Paris
 * décroissant, puis alpha par binôme. Une espèce sans `SpeciesInfo` retombe
 * sur `count = 0` et finit en queue de liste, alpha entre paires.
 *
 * Source de vérité unique pour le numéro `#NNN` qui apparaît dans le Catalogue
 * Arboretum (`displayNumber`) ET en sous-titre du `SpeciesDetailScreen`. Le
 * `speciesIndex` Room (ordre d'ingestion CSV) ne peut pas servir de numéro
 * d'affichage — il est stable pour la migration mais arbitraire pour l'usage.
 */
fun catalogueOrder(
    speciesIndex: SpeciesIndex,
    speciesInfoRepo: SpeciesInfoRepository,
): List<SpeciesEntry> =
    speciesIndex.entries().sortedWith(
        compareByDescending<SpeciesEntry> {
            speciesInfoRepo.get(it.index)?.stats?.count ?: 0
        }
            .thenBy { it.genre.lowercase() }
            .thenBy { it.espece.lowercase() }
    )

/**
 * Rang 1-based d'une espèce dans le Catalogue, ou `null` si l'espèce n'existe
 * pas dans l'index.
 */
fun catalogueRank(
    sk: Int,
    speciesIndex: SpeciesIndex,
    speciesInfoRepo: SpeciesInfoRepository,
): Int? {
    val pos = catalogueOrder(speciesIndex, speciesInfoRepo).indexOfFirst { it.index == sk }
    return if (pos < 0) null else pos + 1
}
