package app.arbre.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Fiche pré-cuite par espèce, alignée avec `assets/species-info.json` produit
 * par `tools/build_dataset.py`. Contient :
 *  - texte Wikipedia (summary FR + page title pour lien externe + QID),
 *  - stats parisiennes : count, proportion du dataset, médianes, top arr.
 *
 * Champs Wikipedia optionnels : si l'espèce n'a pas de page (hybride obscur,
 * cultivar, n. sp.), on garde les stats et on affiche un placeholder côté UI.
 */
data class SpeciesInfo(
    val index: Int,
    val wikipediaTitle: String?,
    val wikidataQid: String?,
    val summary: String?,
    val pdfUrl: String?,
    val stats: SpeciesStats,
)

data class SpeciesStats(
    val count: Int,
    val proportion: Double,
    val medianHeightM: Int?,
    val medianCircCm: Int?,
    val topArrAbs: List<ArrCount>,
    val topArrOver: List<ArrCount>,
)

/**
 * Une ligne de stats par arrondissement. `ratio` n'est rempli que pour la
 * sur-représentation (top topArrOver), sinon null.
 */
data class ArrCount(
    val arr: String,
    val count: Int,
    val ratio: Double?,
)

class SpeciesInfoRepository(private val byIndex: Map<Int, SpeciesInfo>) {

    fun get(index: Int): SpeciesInfo? = byIndex[index]

    val total: Int get() = byIndex.size

    companion object {
        fun load(context: Context, asset: String = "species-info.json"): SpeciesInfoRepository {
            val text = context.assets.open(asset).bufferedReader().use { it.readText() }
            val arr = JSONArray(text)
            val map = HashMap<Int, SpeciesInfo>(arr.length())
            for (i in 0 until arr.length()) {
                val o: JSONObject = arr.getJSONObject(i)
                val info = parseEntry(o)
                map[info.index] = info
            }
            return SpeciesInfoRepository(map)
        }

        private fun parseEntry(o: JSONObject): SpeciesInfo {
            val statsObj = o.getJSONObject("stats")
            val stats = SpeciesStats(
                count = statsObj.getInt("count"),
                proportion = statsObj.getDouble("proportion"),
                medianHeightM = statsObj.optIntOrNull("medianHm"),
                medianCircCm = statsObj.optIntOrNull("medianCircCm"),
                topArrAbs = parseArrList(statsObj.optJSONArray("topArrAbs")),
                topArrOver = parseArrList(statsObj.optJSONArray("topArrOver")),
            )
            return SpeciesInfo(
                index = o.getInt("i"),
                wikipediaTitle = o.optStringOrNull("wp"),
                wikidataQid = o.optStringOrNull("qid"),
                summary = o.optStringOrNull("summary"),
                pdfUrl = o.optStringOrNull("pdf"),
                stats = stats,
            )
        }

        private fun parseArrList(arr: JSONArray?): List<ArrCount> {
            if (arr == null) return emptyList()
            return List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                ArrCount(
                    arr = o.getString("arr"),
                    count = o.getInt("count"),
                    ratio = if (o.has("ratio")) o.getDouble("ratio") else null,
                )
            }
        }
    }
}

private fun JSONObject.optStringOrNull(key: String): String? =
    if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotEmpty() } else null

private fun JSONObject.optIntOrNull(key: String): Int? =
    if (has(key) && !isNull(key)) optInt(key) else null
