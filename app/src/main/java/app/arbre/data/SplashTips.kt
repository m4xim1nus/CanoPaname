package app.arbre.data

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Tip splash. `text` peut contenir des placeholders `{xxx}` substitués au
 * runtime par [SplashTipsController]. `requires` non vide → tous les
 * placeholders listés doivent être > 0 (filtre les phrases joueur en
 * début de partie).
 */
data class SplashTip(
    val id: String,
    val category: String,
    val text: String,
    val requires: List<String> = emptyList(),
)

/**
 * Chargé une fois au boot. Parse fail → fallback `tips`/`intro` vides ; le
 * splash continue sans tip plutôt que de crasher l'app.
 */
class SplashTipsRepository private constructor(
    val intro: List<String>,
    val tips: List<SplashTip>,
) {
    val tipsById: Map<String, SplashTip> = tips.associateBy { it.id }

    /** Tips éligibles immédiatement, sans `requires` à satisfaire. */
    val unconditionalTips: List<SplashTip> = tips.filter { it.requires.isEmpty() }

    companion object {
        private const val TAG = "SplashTips"

        fun load(context: Context, asset: String = "splash-tips.json"): SplashTipsRepository {
            return try {
                val text = context.assets.open(asset).bufferedReader().use { it.readText() }
                val root = JSONObject(text)
                val introArr = root.getJSONArray("intro")
                val intro = buildList(introArr.length()) {
                    for (i in 0 until introArr.length()) add(introArr.getString(i))
                }
                val tipsArr = root.getJSONArray("tips")
                val tips = buildList(tipsArr.length()) {
                    for (i in 0 until tipsArr.length()) {
                        val o = tipsArr.getJSONObject(i)
                        add(
                            SplashTip(
                                id = o.getString("id"),
                                category = o.getString("category"),
                                text = o.getString("text"),
                                requires = parseRequires(o),
                            )
                        )
                    }
                }
                SplashTipsRepository(intro, tips)
            } catch (e: Exception) {
                Log.w(TAG, "splash-tips.json illisible, splash sans tips", e)
                SplashTipsRepository(emptyList(), emptyList())
            }
        }

        private fun parseRequires(o: JSONObject): List<String> {
            if (!o.has("requires")) return emptyList()
            val arr: JSONArray = o.getJSONArray("requires")
            return buildList(arr.length()) {
                for (i in 0 until arr.length()) add(arr.getString(i))
            }
        }
    }
}
