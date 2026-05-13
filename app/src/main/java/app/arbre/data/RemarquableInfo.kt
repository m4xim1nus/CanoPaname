package app.arbre.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Métadonnées d'un arbre remarquable. Tous les champs textuels sont nullables :
 * OpenData ne garantit pas leur présence. Un arbre sans aucun champ rempli
 * n'apparaît pas dans le JSON du tout.
 */
data class RemarquableInfo(
    val id: Long,
    val qualification: String?,
    val resume: String?,
    val description: String?,
    val datePlantation: String?,
    val cultivar: String?,
)

fun RemarquableInfo.isEmpty(): Boolean =
    qualification == null && resume == null && description == null &&
        datePlantation == null && cultivar == null

class RemarquableInfoRepository(private val byId: Map<Long, RemarquableInfo>) {

    fun get(id: Long): RemarquableInfo? = byId[id]

    val total: Int get() = byId.size

    companion object {
        fun load(
            context: Context,
            asset: String = "remarquables-info.json",
        ): RemarquableInfoRepository {
            val text = context.assets.open(asset).bufferedReader().use { it.readText() }
            val arr = JSONArray(text)
            val map = HashMap<Long, RemarquableInfo>(arr.length())
            for (i in 0 until arr.length()) {
                val o: JSONObject = arr.getJSONObject(i)
                val id = o.getLong("id")
                map[id] = RemarquableInfo(
                    id = id,
                    qualification = o.optStringOrNullRmq("qualif"),
                    resume = o.optStringOrNullRmq("resume"),
                    description = o.optStringOrNullRmq("desc"),
                    datePlantation = o.optStringOrNullRmq("plante"),
                    cultivar = o.optStringOrNullRmq("cultivar"),
                )
            }
            return RemarquableInfoRepository(map)
        }
    }
}

private fun JSONObject.optStringOrNullRmq(key: String): String? =
    if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotEmpty() } else null
