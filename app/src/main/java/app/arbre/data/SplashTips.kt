package app.arbre.data

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Tip splash écrit en `assets/splash-tips.json`. Le `text` peut contenir des
 * placeholders `{xxx}` qui sont substitués au runtime par
 * [SplashTipsController]. Si `requires` est non vide, tous les placeholders
 * listés doivent être > 0 dans la session courante pour que le tip soit
 * éligible (filtre les phrases joueur en début de partie).
 *
 * Catégories utilisées : `intro`, `dataset`, `history`, `popculture`, `player`.
 * Les phrases dataset sont générées par `tools/build_dataset.py` ; les autres
 * proviennent de `tools/splash-tips-static.json` et sont fusionnées au build.
 */
data class SplashTip(
    val id: String,
    val category: String,
    val text: String,
    val requires: List<String> = emptyList(),
)

/**
 * Singleton chargé une fois depuis `assets/splash-tips.json` au boot. En cas
 * de parse fail (asset corrompu, JSON malformé), le repository part avec
 * `tips` vide et `intro` vide — le splash continue sans tip plutôt que de
 * crasher.
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
