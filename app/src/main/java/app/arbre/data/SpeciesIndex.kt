package app.arbre.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Lookup `(genre, espece) <-> int speciesIndex` aligné avec
 * `assets/species-index.json` (généré par `tools/build_dataset.py`).
 *
 * Les int sont préservés entre regénérations du dataset (cf. build_dataset.py)
 * pour que les captures stockées en Room restent valides après une mise à
 * jour de l'asset DB.
 */
data class SpeciesEntry(
    val index: Int,
    val genre: String,
    val espece: String,
    /**
     * Nom commun le plus fréquent pour cette (genre, espece) dans le dataset
     * OpenData. Null si aucun arbre de l'espèce n'a de `libellefrancais`
     * renseigné. Calculé à build-time par `tools/build_dataset.py`.
     */
    val nomCommun: String? = null,
) {
    val displayName: String get() = "$genre $espece"
    /**
     * Nom à afficher en primaire dans l'Arboretum (liste / Catalogue / fiche
     * arbre). Tombe sur le binôme si le dataset n'expose pas de nom commun
     * pour l'espèce.
     */
    val displayNomCommun: String get() = nomCommun ?: displayName
}

class SpeciesIndex(entries: List<SpeciesEntry>) {

    private val byIndex: Map<Int, SpeciesEntry> = entries.associateBy { it.index }
    private val byKey: Map<Pair<String, String>, Int> =
        entries.associate { (it.genre to it.espece) to it.index }
    // Ordre stable par speciesIndex croissant — c'est l'ordre « annuaire »
    // de référence ; le Catalogue Arboretum réordonne par count Paris
    // décroissant à l'affichage. Le tri est fait une fois à l'init et
    // conservé en mémoire.
    private val ordered: List<SpeciesEntry> = entries.sortedBy { it.index }

    val total: Int get() = byIndex.size

    fun get(index: Int): SpeciesEntry? = byIndex[index]

    fun indexOf(genre: String, espece: String): Int? = byKey[genre to espece]

    fun indexOf(arbre: Arbre): Int? = indexOf(arbre.genre, arbre.espece)

    fun entries(): List<SpeciesEntry> = ordered

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
) {
    companion object {
        fun load(context: Context, asset: String = "dataset-stats.json"): DatasetStats {
            val text = context.assets.open(asset).bufferedReader().use { it.readText() }
            val o = JSONObject(text)
            return DatasetStats(
                totalArbres = o.getInt("totalArbres"),
                totalEspeces = o.getInt("totalEspeces"),
                totalRemarquables = o.getInt("totalRemarquables"),
            )
        }
    }
}
