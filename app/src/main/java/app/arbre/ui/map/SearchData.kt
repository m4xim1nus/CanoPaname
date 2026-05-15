package app.arbre.ui.map

import app.arbre.data.ArrKey
import app.arbre.data.ArrSpeciesIndex
import app.arbre.data.GenreInfoRepository
import app.arbre.data.SpeciesIndex
import app.arbre.data.label
import app.arbre.data.sortKey
import java.text.Normalizer

data class SearchData(
    val species: List<SpeciesSearchItem>,
    val genres: List<GenreSearchItem>,
    val arrs: List<ArrSearchItem>,
) {
    companion object
}

data class SpeciesSearchItem(
    val sk: Int,
    val display: String,
    val latin: String,
    val haystack: String,
)

data class GenreSearchItem(
    val genre: String,
    val display: String,
    val haystack: String,
)

data class ArrSearchItem(
    val key: ArrKey,
    val label: String,
    val lon: Double,
    val lat: Double,
    val haystack: String,
)

private val DIACRITICS = Regex("\\p{InCombiningDiacriticalMarks}+")

fun normalizeQuery(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD)
        .replace(DIACRITICS, "")
        .lowercase()
        .trim()

fun SearchData.Companion.build(
    speciesIndex: SpeciesIndex,
    genreInfo: GenreInfoRepository,
    arrIndex: ArrSpeciesIndex,
    captured: Set<Int>,
): SearchData {
    val species = buildList {
        for (entry in speciesIndex.entries()) {
            if (!entry.isActive) continue
            if (entry.index !in captured) continue
            val display = entry.displayNomCommun
            val latin = entry.displayName
            add(
                SpeciesSearchItem(
                    sk = entry.index,
                    display = display,
                    latin = latin,
                    haystack = normalizeQuery("$display $latin"),
                )
            )
        }
    }.sortedWith(compareBy({ it.display.lowercase() }, { it.sk }))

    val genres = buildList {
        for (g in speciesIndex.allGenres()) {
            if (!speciesIndex.genreHasAnyCapture(g, captured)) continue
            val nomFr = genreInfo.get(g)?.nomFr
            val display = if (nomFr != null) "$g — $nomFr" else g
            add(
                GenreSearchItem(
                    genre = g,
                    display = display,
                    haystack = normalizeQuery("$g ${nomFr.orEmpty()}"),
                )
            )
        }
    }.sortedBy { it.display.lowercase() }

    val arrs = buildList {
        for (key in arrIndex.keys) {
            val centroid = arrIndex.centroidOf(key) ?: continue
            val label = key.label()
            add(
                ArrSearchItem(
                    key = key,
                    label = label,
                    lon = centroid.first,
                    lat = centroid.second,
                    haystack = normalizeQuery(label),
                )
            )
        }
    }.sortedBy { it.key.sortKey() }

    return SearchData(species = species, genres = genres, arrs = arrs)
}

private val ORDINALS_FR: Map<String, Int> = mapOf(
    "premier" to 1, "premiere" to 1,
    "deuxieme" to 2, "second" to 2, "seconde" to 2,
    "troisieme" to 3,
    "quatrieme" to 4,
    "cinquieme" to 5,
    "sixieme" to 6,
    "septieme" to 7,
    "huitieme" to 8,
    "neuvieme" to 9,
    "dixieme" to 10,
    "onzieme" to 11,
    "douzieme" to 12,
    "treizieme" to 13,
    "quatorzieme" to 14,
    "quinzieme" to 15,
    "seizieme" to 16,
    "dix-septieme" to 17, "dixseptieme" to 17,
    "dix-huitieme" to 18, "dixhuitieme" to 18,
    "dix-neuvieme" to 19, "dixneuvieme" to 19,
    "vingtieme" to 20,
)

private val PARIS_NUM = Regex("^(\\d{1,2})(?:er|e|eme|ieme|ieme)?$")
private val ZIPCODE = Regex("^750(\\d{2})$")

/**
 * Parse une requête utilisateur en `ArrKey` Paris(1..20) | BoisVincennes |
 * BoisBoulogne. Accepte chiffres, ordinaux français, code postal 750NN, et
 * mots-clés « vincennes »/« boulogne ». Insensible casse + accents.
 */
fun parseArrQuery(query: String): ArrKey? {
    val q = normalizeQuery(query)
    if (q.isEmpty()) return null

    PARIS_NUM.matchEntire(q)?.let { m ->
        val n = m.groupValues[1].toIntOrNull() ?: return@let
        if (n in 1..20) return ArrKey.Paris(n)
    }
    ZIPCODE.matchEntire(q)?.let { m ->
        val n = m.groupValues[1].toIntOrNull() ?: return@let
        if (n in 1..20) return ArrKey.Paris(n)
    }
    ORDINALS_FR[q]?.let { return ArrKey.Paris(it) }
    if ("vincennes" in q) return ArrKey.BoisVincennes
    if ("boulogne" in q) return ArrKey.BoisBoulogne
    return null
}
