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
) {
    val displayName: String get() = "$genre $espece"
}

class SpeciesIndex(entries: List<SpeciesEntry>) {

    private val byIndex: Map<Int, SpeciesEntry> = entries.associateBy { it.index }
    private val byKey: Map<Pair<String, String>, Int> =
        entries.associate { (it.genre to it.espece) to it.index }

    val total: Int get() = byIndex.size

    fun get(index: Int): SpeciesEntry? = byIndex[index]

    fun indexOf(genre: String, espece: String): Int? = byKey[genre to espece]

    fun indexOf(arbre: Arbre): Int? = indexOf(arbre.genre, arbre.espece)

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
