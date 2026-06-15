package app.arbre.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Fiche pré-cuite par espèce (texte Wikipedia FR + stats parisiennes).
 * Champs Wikipedia optionnels : pour une espèce sans page, on conserve les
 * stats et on affiche un placeholder côté UI.
 */
data class SpeciesInfo(
    val index: Int,
    val wikipediaTitle: String?,
    val wikidataQid: String?,
    val summary: String?,
    val pdfUrl: String?,
    val stats: SpeciesStats,
    val attributes: SpeciesAttributes? = null,
)

/**
 * Attributs structurés issus des fiches-essences Ville de Paris (bloc `ess` du
 * `species-info.json`, écrit par `tools/build_dataset.py`). Présent seulement
 * sur les espèces matchées (~169/934) ; `null` sur la longue traîne. Modalités
 * texte déjà en FR lisible (« Caduc », « Pleureur », « Exotique »…), champs vides
 * omis à la source — d'où tous les scalaires nullable et les listes possiblement
 * vides.
 */
data class SpeciesAttributes(
    val port: String?,
    val feuillage: String?,
    val taille: String?,
    val indigenat: String?,
    val origine: String?,
    val fleurs: Boolean?,
    val exposition: List<String>,
    val besoinsEau: List<String>,
    val sitePlantation: List<String>,
)

data class SpeciesStats(
    val count: Int,
    val proportion: Double,
    val medianHeightM: Int?,
    val medianCircCm: Int?,
    val topArrAbs: List<ArrCount>,
    val topArrOver: List<ArrCount>,
)

/** Une ligne par arrondissement ; `ratio` rempli seulement pour topArrOver. */
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
                attributes = parseSpeciesAttributes(o.optJSONObject("ess")),
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

/**
 * Parse le bloc `ess` (attributs Ville de Paris) → `SpeciesAttributes`, ou `null`
 * si le bloc est absent. Tolérant : toute clé manquante donne `null` (scalaires)
 * ou liste vide (listes). `internal` pour rester directement testable sans
 * `Context.assets`.
 */
internal fun parseSpeciesAttributes(ess: JSONObject?): SpeciesAttributes? {
    if (ess == null) return null
    return SpeciesAttributes(
        port = ess.optStringOrNull("port"),
        feuillage = ess.optStringOrNull("feuillage"),
        taille = ess.optStringOrNull("taille"),
        indigenat = ess.optStringOrNull("indigenat"),
        origine = ess.optStringOrNull("origine"),
        fleurs = ess.optBooleanOrNull("fleurs"),
        exposition = parseStringList(ess.optJSONArray("expo")),
        besoinsEau = parseStringList(ess.optJSONArray("eau")),
        sitePlantation = parseStringList(ess.optJSONArray("sites")),
    )
}

private fun parseStringList(arr: JSONArray?): List<String> =
    if (arr == null) emptyList() else List(arr.length()) { arr.getString(it) }

private fun JSONObject.optStringOrNull(key: String): String? =
    if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotEmpty() } else null

private fun JSONObject.optIntOrNull(key: String): Int? =
    if (has(key) && !isNull(key)) optInt(key) else null

private fun JSONObject.optBooleanOrNull(key: String): Boolean? =
    if (has(key) && !isNull(key)) optBoolean(key) else null
