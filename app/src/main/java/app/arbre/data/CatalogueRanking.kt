package app.arbre.data

/**
 * Ordre d'affichage du Catalogue.
 *
 * Cycle Catalogue (sprint 4) : si l'asset porte des `pokedexNumber` (champ `n`
 * de `species-index.json` post-régénération), trier par `n` croissant — c'est
 * le numéro Pokédex stable, partagé entre Catalogue et fiche-espèce. Sinon
 * fallback count Paris décroissant (logique pré-cycle, asset legacy).
 *
 * Les `unknownSpecies` (entrées `(G, sp.)`) sont **toujours en queue**, sans
 * `#`, triés alphabétiquement par genre. Section dédiée côté UI Arboretum.
 */
fun catalogueOrder(
    speciesIndex: SpeciesIndex,
    speciesInfoRepo: SpeciesInfoRepository,
): List<SpeciesEntry> {
    val (identified, unknowns) = speciesIndex.entries().partition { !it.unknownSpecies }
    val anyPokedex = identified.any { it.pokedexNumber != null }
    val identifiedSorted = if (anyPokedex) {
        // Tri Pokédex stable. Les rares `n == null` (zombies count=0) restent
        // en queue de la section identifiée, triés par count Paris.
        identified.sortedWith(
            compareBy<SpeciesEntry> { it.pokedexNumber == null }
                .thenBy { it.pokedexNumber ?: Int.MAX_VALUE }
                .thenByDescending { speciesInfoRepo.get(it.index)?.stats?.count ?: 0 }
                .thenBy { it.genre.lowercase() }
                .thenBy { it.espece.lowercase() }
        )
    } else {
        // Asset legacy : fallback count Paris décroissant, alpha en queue.
        identified.sortedWith(
            compareByDescending<SpeciesEntry> {
                speciesInfoRepo.get(it.index)?.stats?.count ?: 0
            }
                .thenBy { it.genre.lowercase() }
                .thenBy { it.espece.lowercase() }
        )
    }
    val unknownsSorted = unknowns.sortedWith(
        compareBy<SpeciesEntry> { it.genre.lowercase() }.thenBy { it.espece.lowercase() }
    )
    return identifiedSorted + unknownsSorted
}

/**
 * Rang affichable.
 *
 * - Si l'entrée porte un `pokedexNumber` → retourne ce numéro stable (O(1)).
 * - Si l'entrée est `unknownSpecies` → retourne `null` (pas de `#` côté UI).
 * - Sinon (asset legacy) → recompute via `catalogueOrder` (O(N), pratique).
 */
fun catalogueRank(
    sk: Int,
    speciesIndex: SpeciesIndex,
    speciesInfoRepo: SpeciesInfoRepository,
): Int? {
    val entry = speciesIndex.get(sk) ?: return null
    if (entry.unknownSpecies) return null
    entry.pokedexNumber?.let { return it }
    val pos = catalogueOrder(speciesIndex, speciesInfoRepo).indexOfFirst { it.index == sk }
    return if (pos < 0) null else pos + 1
}
