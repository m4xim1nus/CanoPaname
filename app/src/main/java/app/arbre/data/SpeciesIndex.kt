package app.arbre.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Lookup `(genre, espece) ↔ int speciesIndex`. Les int sont préservés entre
 * regénérations du dataset par `tools/build_dataset.py` — sinon les rows
 * `Capture.speciesIndex` deviendraient invalides après une mise à jour.
 *
 * Champs étendus par le cycle Catalogue (sprint 4) :
 * - `nv` : nom vernaculaire **unique** post-désambiguation. Cascade côté
 *   script : VERNACULAR_OVERRIDES → Wikidata P1843 → Wikipedia frTitle →
 *   construit. `null` ssi l'asset legacy (pré-régénération sprint 5) est lu.
 * - `pokedexNumber` : numéro Pokédex stable (séquence par `index` croissant
 *   sur les seules espèces identifiées avec count > 0). `null` pour les
 *   `unknownSpecies`, les zombies count=0, et tout l'asset legacy.
 * - `unknownSpecies` : `true` ssi entrée `(G, sp.)` issue de la normalisation
 *   sprint 1 (espèce non identifiée). Section dédiée en fin de catalogue.
 */
data class SpeciesEntry(
    val index: Int,
    val genre: String,
    val espece: String,
    /** Nom commun le plus fréquent dans OpenData ; null si jamais renseigné. */
    val nomCommun: String? = null,
    /** Nom vernaculaire unique du cycle Catalogue ; null sur asset legacy. */
    val nv: String? = null,
    /** Numéro Pokédex stable ; null pour `unknownSpecies` et asset legacy. */
    val pokedexNumber: Int? = null,
    /** Entrée `(G, sp.)` non identifiée. */
    val unknownSpecies: Boolean = false,
) {
    val displayName: String get() = "$genre $espece"
    /**
     * Triple fallback : `nv` (Catalogue) → `nomCommun` (OpenData) → binôme.
     * Toutes les call-sites bénéficient gratuitement de `nv` quand l'asset
     * a été régénéré ; sinon comportement historique préservé.
     */
    val displayNomCommun: String get() = nv ?: nomCommun ?: displayName
}

class SpeciesIndex(entries: List<SpeciesEntry>) {

    private val byIndex: Map<Int, SpeciesEntry> = entries.associateBy { it.index }
    private val byKey: Map<Pair<String, String>, Int> =
        entries.associate { (it.genre to it.espece) to it.index }
    // Ordre annuaire de référence (par speciesIndex croissant). Le Catalogue
    // Arboretum applique son propre tri par count Paris à l'affichage.
    private val ordered: List<SpeciesEntry> = entries.sortedBy { it.index }

    /**
     * `genre → set des sks de ce genre`. Pré-calculé une fois, sert au calcul
     * d'auto-débloquage des fiches `(G, sp.)` (cycle Catalogue) : capturer
     * n'importe quel sk du genre marque la fiche `unknownSpecies` débloquée.
     */
    private val sksByGenre: Map<String, Set<Int>> =
        entries.groupBy { it.genre }.mapValues { (_, list) -> list.map { it.index }.toSet() }

    /** Set des sks `unknownSpecies == true` (entrées `(G, sp.)`). */
    val unknownSks: Set<Int> = entries.filter { it.unknownSpecies }.map { it.index }.toSet()

    val total: Int get() = byIndex.size

    fun get(index: Int): SpeciesEntry? = byIndex[index]

    fun indexOf(genre: String, espece: String): Int? = byKey[genre to espece]

    fun indexOf(arbre: Arbre): Int? = indexOf(arbre.genre, arbre.espece)

    fun entries(): List<SpeciesEntry> = ordered

    /** `null` si le sk n'est pas dans l'index. */
    fun genreOf(index: Int): String? = byIndex[index]?.genre

    /**
     * Toutes les entrées d'un genre donné, ordonnées par `index` croissant.
     * Cycle Catalogue (sprint 4bis) : alimente le mini-catalogue affiché sur
     * la fiche `(G, sp.)` (« j'ai 3/55 chênes »).
     */
    fun entriesOfGenre(genre: String): List<SpeciesEntry> {
        val sks = sksByGenre[genre] ?: return emptyList()
        return sks.mapNotNull { byIndex[it] }.sortedBy { it.index }
    }

    /**
     * Liste alphabétique des genres ayant **au moins une espèce identifiée**
     * (i.e. non `unknownSpecies`). Les genres only-unknown (genres dont toutes
     * les entrées sont `(G, sp.)`, e.g. `Genista`, `Vitex`, `Ziziphus`) sont
     * exclus — ils seront couverts par les fiches genre du S8 (cf. ROADMAP).
     *
     * Source du mode Catalogue par chapitres (cycle Catalogue, sprint 7).
     */
    private val genresWithIdentified: List<String> = sksByGenre
        .filter { (_, sks) -> sks.any { sk -> byIndex[sk]?.unknownSpecies == false } }
        .keys
        .sortedBy { it.lowercase() }

    fun genres(): List<String> = genresWithIdentified

    /**
     * Nombre d'espèces **identifiées** du genre (exclut `unknownSpecies`).
     * Sert au compteur `X / Y` du header de chapitre en mode Catalogue.
     */
    fun genreCount(genre: String): Int {
        val sks = sksByGenre[genre] ?: return 0
        return sks.count { sk -> byIndex[sk]?.unknownSpecies == false }
    }

    /**
     * Nombre d'espèces **identifiées** du genre intersectées avec `capturedSks`.
     * Sert au numérateur du compteur `X / Y` du header de chapitre. Les `sp.`
     * capturés ne comptent pas — la sémantique est « progression Pokédex ».
     */
    fun capturedCountInGenre(genre: String, capturedSks: Set<Int>): Int {
        val sks = sksByGenre[genre] ?: return 0
        return sks.count { sk -> sk in capturedSks && byIndex[sk]?.unknownSpecies == false }
    }

    /**
     * Auto-débloquage genre-based des fiches `(G, sp.)` : un sk `unknownSpecies`
     * est considéré découvert dès qu'un sk frère du même genre est capturé.
     * Les sks identifiés (non `unknownSpecies`) sont découverts au sens strict
     * (présents dans `capturedSks`).
     */
    fun isDiscovered(index: Int, capturedSks: Set<Int>): Boolean {
        if (index in capturedSks) return true
        val entry = byIndex[index] ?: return false
        if (!entry.unknownSpecies) return false
        val frères = sksByGenre[entry.genre] ?: return false
        return frères.any { it != index && it in capturedSks }
    }

    companion object {
        fun load(context: Context, asset: String = "species-index.json"): SpeciesIndex {
            val text = context.assets.open(asset).bufferedReader().use { it.readText() }
            val arr = JSONArray(text)
            val entries = buildList(arr.length()) {
                for (i in 0 until arr.length()) {
                    val o: JSONObject = arr.getJSONObject(i)
                    add(
                        SpeciesEntry(
                            index = o.getInt("i"),
                            genre = o.getString("g"),
                            espece = o.getString("e"),
                            nomCommun = if (o.has("nc") && !o.isNull("nc")) {
                                o.optString("nc").takeIf { it.isNotEmpty() }
                            } else null,
                            nv = if (o.has("nv") && !o.isNull("nv")) {
                                o.optString("nv").takeIf { it.isNotEmpty() }
                            } else null,
                            pokedexNumber = if (o.has("n") && !o.isNull("n")) {
                                o.optInt("n").takeIf { it > 0 }
                            } else null,
                            unknownSpecies = o.optBoolean("u", false),
                        )
                    )
                }
            }
            return SpeciesIndex(entries)
        }
    }
}

data class DatasetStats(
    val totalArbres: Int,
    val totalEspeces: Int,
    val totalRemarquables: Int,
    /**
     * Nombre d'espèces identifiées (exclut les `unknownSpecies` et les zombies
     * count=0). Cycle Catalogue, sprint 2. Fallback sur `totalEspeces` si le
     * champ est absent (asset legacy).
     */
    val totalEspecesIdentifiees: Int,
) {
    companion object {
        fun load(context: Context, asset: String = "dataset-stats.json"): DatasetStats {
            val text = context.assets.open(asset).bufferedReader().use { it.readText() }
            val o = JSONObject(text)
            val totalEspeces = o.getInt("totalEspeces")
            return DatasetStats(
                totalArbres = o.getInt("totalArbres"),
                totalEspeces = totalEspeces,
                totalRemarquables = o.getInt("totalRemarquables"),
                totalEspecesIdentifiees = o.optInt("totalEspecesIdentifiees", totalEspeces),
            )
        }
    }
}
