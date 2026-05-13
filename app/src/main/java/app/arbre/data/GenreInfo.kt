package app.arbre.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Fiche pré-cuite par genre : résumé Wikipedia FR du genre + stats Paris
 * agrégées (count, top espèces du genre, top arrondissements). Pendant
 * fiche genre du `SpeciesInfo` (pour les fiches espèce).
 *
 * `wikipediaTitle` / `summary` sont absents pour les genres dont l'article
 * Wikipedia FR n'a pas pu être récupéré au build (`{"miss": true}` cached).
 * `nomFr` est absent pour les genres sans entrée `GENRE_FR` (rendu fallback :
 * binôme latin du genre comme titre).
 */
data class GenreInfo(
    val genre: String,
    val nomFr: String?,
    val wikipediaTitle: String?,
    val summary: String?,
    val stats: GenreStats,
)

data class GenreStats(
    /** Nombre total d'arbres du genre à Paris (toutes espèces du genre). */
    val count: Int,
    /** Nombre d'espèces identifiées du genre (exclut `unknownSpecies`). */
    val speciesIdentified: Int,
    /** Top 3 espèces identifiées du genre, ordre count décroissant. */
    val topSpecies: List<TopSpecies>,
    /** Top 3 arrondissements Paris pour ce genre, ordre count décroissant. */
    val topArr: List<ArrCount>,
    // Champs optionnels, alignés avec `SpeciesStats`. Tous nullables / vides →
    // rétrocompat asset legacy garantie (un asset minimal charge sans crash).
    /** Proportion du dataset Paris (ex. 0.0123 pour 1,23 %). */
    val proportion: Double? = null,
    /** Hauteur médiane des arbres du genre (m). `null` si pas de mesures. */
    val medianHeightM: Int? = null,
    /** Circonférence médiane des arbres du genre (cm). `null` si pas de mesures. */
    val medianCircCm: Int? = null,
    /** Top 3 arrondissements sur-représentés (ratio + count) ; `ratio` rempli. */
    val topArrOver: List<ArrCount> = emptyList(),
)

data class TopSpecies(
    val sk: Int,
    val nv: String,
    val count: Int,
)

class GenreInfoRepository(private val byGenre: Map<String, GenreInfo>) {

    fun get(genre: String): GenreInfo? = byGenre[genre]

    val total: Int get() = byGenre.size

    companion object {
        /**
         * Asset absent → repo vide. `GenreDetailScreen` reste fonctionnel via
         * fallback Kotlin (mini-catalogue + carte filtrée OK, juste pas de
         * sections Wikipedia/stats).
         */
        fun load(context: Context, asset: String = "genre-info.json"): GenreInfoRepository {
            val text = try {
                context.assets.open(asset).bufferedReader().use { it.readText() }
            } catch (_: Throwable) {
                return GenreInfoRepository(emptyMap())
            }
            val arr = JSONArray(text)
            val map = HashMap<String, GenreInfo>(arr.length())
            for (i in 0 until arr.length()) {
                val o: JSONObject = arr.getJSONObject(i)
                val info = parseEntry(o)
                map[info.genre] = info
            }
            return GenreInfoRepository(map)
        }

        private fun parseEntry(o: JSONObject): GenreInfo {
            val statsObj = o.getJSONObject("stats")
            val stats = GenreStats(
                count = statsObj.getInt("count"),
                speciesIdentified = statsObj.getInt("speciesIdentified"),
                topSpecies = parseTopSpecies(statsObj.optJSONArray("topSpecies")),
                topArr = parseArrList(statsObj.optJSONArray("topArr")),
                // Champs optionnels — asset legacy renvoie `null` / vide partout,
                // fiche genre fonctionne sans ces stats.
                proportion = statsObj.optDoubleOrNull("proportion"),
                medianHeightM = statsObj.optIntOrNull("medianHm"),
                medianCircCm = statsObj.optIntOrNull("medianCircCm"),
                topArrOver = parseArrList(statsObj.optJSONArray("topArrOver")),
            )
            return GenreInfo(
                genre = o.getString("g"),
                nomFr = o.optStringOrNull("fr"),
                wikipediaTitle = o.optStringOrNull("wp"),
                summary = o.optStringOrNull("summary"),
                stats = stats,
            )
        }

        private fun parseTopSpecies(arr: JSONArray?): List<TopSpecies> {
            if (arr == null) return emptyList()
            return List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                TopSpecies(
                    sk = o.getInt("sk"),
                    nv = o.getString("nv"),
                    count = o.getInt("count"),
                )
            }
        }

        private fun parseArrList(arr: JSONArray?): List<ArrCount> {
            if (arr == null) return emptyList()
            return List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                ArrCount(
                    arr = o.getString("arr"),
                    count = o.getInt("count"),
                    ratio = if (o.has("ratio") && !o.isNull("ratio")) o.getDouble("ratio") else null,
                )
            }
        }
    }
}

private fun JSONObject.optStringOrNull(key: String): String? =
    if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotEmpty() } else null

private fun JSONObject.optIntOrNull(key: String): Int? =
    if (has(key) && !isNull(key)) optInt(key) else null

private fun JSONObject.optDoubleOrNull(key: String): Double? =
    if (has(key) && !isNull(key)) optDouble(key) else null
