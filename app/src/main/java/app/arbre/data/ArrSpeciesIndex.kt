package app.arbre.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Index par arrondissement (20 arr. + 2 bois), pré-calculé au build dans
 * `assets/arr-species.json` (clé = slug ArrKey).
 *
 * Trois agrégats par arr :
 * - `species` : espèces recensées dans l'arr (≥ 1 arbre). Sert au compte
 *   « Arrondissements visités » (≥ 1 capture quelconque).
 * - `remarquables` : sous-ensemble des espèces correspondant à au moins un
 *   arbre **remarquable** dans l'arr. Sert au badge « Familier du Xe » et à la
 *   barre « Arrondissements complétés ». Peut être vide (deux arr concrets
 *   sans remarquable) — ces arr sont exclus du dénominateur « Complétés » et
 *   n'engendrent pas de `BadgeDef`, mais restent dans `keys`.
 * - `centroid` : moyenne arithmétique (lon, lat) des arbres de l'arr.
 *   Utilisé par la Recherche universelle (sprint S2) pour le fly-to.
 *
 * Asset absent (build pas encore régénéré) → repo vide : les badges
 * arrondissement sont alors simplement absents du catalogue, le reste marche.
 */
class ArrSpeciesIndex(
    private val byKey: Map<ArrKey, Set<Int>>,
    private val remarquablesByKey: Map<ArrKey, Set<Int>> = emptyMap(),
    private val centroidsByKey: Map<ArrKey, Pair<Double, Double>> = emptyMap(),
) {

    /** Les ArrKey couverts, triés par `sortKey()` (1..20, Vincennes, Boulogne). */
    val keys: List<ArrKey> = byKey.keys.sortedBy { it.sortKey() }

    /** Sous-ensemble de [keys] ayant au moins un arbre remarquable — dénominateur
     *  du badge « Familier du Xe » et de la barre « Arrondissements complétés ». */
    val keysWithRemarquables: List<ArrKey> =
        keys.filter { remarquablesByKey[it]?.isNotEmpty() == true }

    /** Espèces recensées dans cet arrondissement ; vide si inconnu. */
    fun speciesOf(key: ArrKey): Set<Int> = byKey[key].orEmpty()

    /** Espèces remarquables recensées dans cet arrondissement ; vide si aucune. */
    fun remarquablesOf(key: ArrKey): Set<Int> = remarquablesByKey[key].orEmpty()

    /** Centroïde (lon, lat) moyen des arbres de l'arr ; `null` si inconnu. */
    fun centroidOf(key: ArrKey): Pair<Double, Double>? = centroidsByKey[key]

    val isEmpty: Boolean get() = byKey.isEmpty()

    companion object {
        fun load(context: Context, asset: String = "arr-species.json"): ArrSpeciesIndex {
            val text = try {
                context.assets.open(asset).bufferedReader().use { it.readText() }
            } catch (_: Throwable) {
                return ArrSpeciesIndex(emptyMap())
            }
            val obj = JSONObject(text)
            val species = HashMap<ArrKey, Set<Int>>()
            val remarquables = HashMap<ArrKey, Set<Int>>()
            val centroids = HashMap<ArrKey, Pair<Double, Double>>()
            for (slug in obj.keys()) {
                val key = slugToArrKey(slug) ?: continue
                val entry = obj.getJSONObject(slug)
                species[key] = readIntSet(entry.getJSONArray("species"))
                if (entry.has("remarquables")) {
                    remarquables[key] = readIntSet(entry.getJSONArray("remarquables"))
                }
                if (!entry.isNull("centroid")) {
                    val arr = entry.getJSONArray("centroid")
                    if (arr.length() >= 2) {
                        centroids[key] = arr.getDouble(0) to arr.getDouble(1)
                    }
                }
            }
            return ArrSpeciesIndex(species, remarquables, centroids)
        }

        private fun readIntSet(arr: JSONArray): Set<Int> {
            val out = HashSet<Int>(arr.length())
            for (i in 0 until arr.length()) out.add(arr.getInt(i))
            return out
        }

        private fun slugToArrKey(slug: String): ArrKey? = when (slug) {
            "vincennes" -> ArrKey.BoisVincennes
            "boulogne" -> ArrKey.BoisBoulogne
            else -> slug.toIntOrNull()?.takeIf { it in 1..20 }?.let { ArrKey.Paris(it) }
        }
    }
}
