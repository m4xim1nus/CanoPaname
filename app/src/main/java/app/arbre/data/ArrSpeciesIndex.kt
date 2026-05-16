package app.arbre.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Index par arrondissement (20 arr. + 2 bois), pré-calculé au build dans
 * `assets/arr-species.json` (clé = slug ArrKey).
 *
 * Deux agrégats par arr :
 * - `remarquable_ids` : ids (`idbase`) des arbres **remarquables** physiques
 *   de l'arrondissement. Dénominateur du badge « Familier du Xe » et de la
 *   barre « Arrondissements complétés » — le critère exige la capture de
 *   **chaque** arbre remarquable (pas seulement de chaque espèce ; cf.
 *   `BadgeEvaluator.evaluateFamilierArr`). Peut être vide : deux arr concrets
 *   sans remarquable (2e et 6e) sont alors exclus du dénominateur
 *   « Complétés » et n'engendrent pas de `BadgeDef`, mais restent dans
 *   `keys` (compteur « Visités »).
 * - `centroid` : moyenne arithmétique (lon, lat) des arbres de l'arr.
 *   Utilisé par la Recherche universelle (sprint S3) pour le fly-to.
 *
 * Asset absent (build pas encore régénéré) → repo vide : les badges
 * arrondissement sont alors simplement absents du catalogue, le reste marche.
 */
class ArrSpeciesIndex(
    private val arrKeys: Set<ArrKey>,
    private val remarquableArbreIdsByKey: Map<ArrKey, Set<Long>> = emptyMap(),
    private val centroidsByKey: Map<ArrKey, Pair<Double, Double>> = emptyMap(),
) {

    /** Les ArrKey couverts par l'asset, triés par `sortKey()` (1..20, Vincennes, Boulogne). */
    val keys: List<ArrKey> = arrKeys.sortedBy { it.sortKey() }

    /** Sous-ensemble de [keys] ayant au moins un arbre remarquable — dénominateur
     *  du badge « Familier du Xe » et de la barre « Arrondissements complétés ». */
    val keysWithRemarquables: List<ArrKey> =
        keys.filter { remarquableArbreIdsByKey[it]?.isNotEmpty() == true }

    /** Ids d'arbres remarquables de l'arr ; vide si aucun. */
    fun remarquableArbreIdsOf(key: ArrKey): Set<Long> = remarquableArbreIdsByKey[key].orEmpty()

    /** Centroïde (lon, lat) moyen des arbres de l'arr ; `null` si inconnu. */
    fun centroidOf(key: ArrKey): Pair<Double, Double>? = centroidsByKey[key]

    val isEmpty: Boolean get() = arrKeys.isEmpty()

    companion object {
        fun load(context: Context, asset: String = "arr-species.json"): ArrSpeciesIndex {
            val text = try {
                context.assets.open(asset).bufferedReader().use { it.readText() }
            } catch (_: Throwable) {
                return ArrSpeciesIndex(emptySet())
            }
            val obj = JSONObject(text)
            val keysSet = HashSet<ArrKey>()
            val remarquableIds = HashMap<ArrKey, Set<Long>>()
            val centroids = HashMap<ArrKey, Pair<Double, Double>>()
            for (slug in obj.keys()) {
                val key = slugToArrKey(slug) ?: continue
                keysSet.add(key)
                val entry = obj.getJSONObject(slug)
                if (entry.has("remarquable_ids")) {
                    remarquableIds[key] = readLongSet(entry.getJSONArray("remarquable_ids"))
                }
                if (!entry.isNull("centroid")) {
                    val arr = entry.getJSONArray("centroid")
                    if (arr.length() >= 2) {
                        centroids[key] = arr.getDouble(0) to arr.getDouble(1)
                    }
                }
            }
            return ArrSpeciesIndex(keysSet, remarquableIds, centroids)
        }

        private fun readLongSet(arr: JSONArray): Set<Long> {
            val out = HashSet<Long>(arr.length())
            for (i in 0 until arr.length()) out.add(arr.getLong(i))
            return out
        }

        private fun slugToArrKey(slug: String): ArrKey? = when (slug) {
            "vincennes" -> ArrKey.BoisVincennes
            "boulogne" -> ArrKey.BoisBoulogne
            else -> slug.toIntOrNull()?.takeIf { it in 1..20 }?.let { ArrKey.Paris(it) }
        }
    }
}
