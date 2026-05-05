package app.arbre.data

/**
 * Ordre d'affichage du Catalogue : count Paris décroissant, alpha sur binôme
 * en cas d'égalité. Source unique du `#NNN` partagée entre `ArboretumScreen`
 * et `SpeciesDetailScreen` ; le `speciesIndex` Room (ordre d'ingestion CSV)
 * est stable pour la migration mais inutilisable comme rang d'affichage.
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

/** Rang 1-based ou `null` si l'espèce n'existe pas dans l'index. */
fun catalogueRank(
    sk: Int,
    speciesIndex: SpeciesIndex,
    speciesInfoRepo: SpeciesInfoRepository,
): Int? {
    val pos = catalogueOrder(speciesIndex, speciesInfoRepo).indexOfFirst { it.index == sk }
    return if (pos < 0) null else pos + 1
}
