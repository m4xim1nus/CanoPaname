package app.arbre.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Dénominateur des badges « Familier d'arrondissement » : pour chaque ArrKey
 * (20 arrondissements + 2 bois), l'ensemble des `speciesIndex` recensés dans
 * cet arrondissement. Pré-calculé au build dans `assets/arr-species.json`
 * (clé = slug ArrKey) — évite un scan des ~217 k arbres au démarrage.
 *
 * Asset absent (build pas encore régénéré) → repo vide : les badges
 * arrondissement sont alors simplement absents du catalogue, le reste marche.
 */
class ArrSpeciesIndex(private val byKey: Map<ArrKey, Set<Int>>) {

    /** Les ArrKey couverts, triés par `sortKey()` (1..20, Vincennes, Boulogne). */
    val keys: List<ArrKey> = byKey.keys.sortedBy { it.sortKey() }

    /** Espèces recensées dans cet arrondissement ; vide si inconnu. */
    fun speciesOf(key: ArrKey): Set<Int> = byKey[key].orEmpty()

    val isEmpty: Boolean get() = byKey.isEmpty()

    companion object {
        fun load(context: Context, asset: String = "arr-species.json"): ArrSpeciesIndex {
            val text = try {
                context.assets.open(asset).bufferedReader().use { it.readText() }
            } catch (_: Throwable) {
                return ArrSpeciesIndex(emptyMap())
            }
            val obj = JSONObject(text)
            val map = HashMap<ArrKey, Set<Int>>()
            for (slug in obj.keys()) {
                val key = slugToArrKey(slug) ?: continue
                val arr: JSONArray = obj.getJSONArray(slug)
                val set = HashSet<Int>(arr.length())
                for (i in 0 until arr.length()) set.add(arr.getInt(i))
                map[key] = set
            }
            return ArrSpeciesIndex(map)
        }

        private fun slugToArrKey(slug: String): ArrKey? = when (slug) {
            "vincennes" -> ArrKey.BoisVincennes
            "boulogne" -> ArrKey.BoisBoulogne
            else -> slug.toIntOrNull()?.takeIf { it in 1..20 }?.let { ArrKey.Paris(it) }
        }
    }
}
