package app.arbre.data

/**
 * Clé géographique dérivée du suffixe d'adresse. La DB stocke le format
 * brut du CSV OpenData (« PARIS 12E ARRDT », « BOIS DE VINCENNES »…) ; le
 * parser tolère aussi le format normalisé (« 5e », « 1er ») utilisé par
 * les tests. Les bois sont élevés en clé propre car ~10 % des remarquables
 * y vivent — fondre dans « Hors Paris » noierait l'info géo.
 */
sealed class ArrKey {
    data class Paris(val num: Int) : ArrKey()
    data object BoisVincennes : ArrKey()
    data object BoisBoulogne : ArrKey()
    data object Other : ArrKey()
}

private val PARIS_RAW = Regex("""^PARIS (\d{1,2})(?:ER|E) ARRDT$""")
private val PARIS_NORMALIZED = Regex("""^(\d{1,2})(?:er|e)$""")

/**
 * Parse l'arrondissement depuis le segment post-dernière-virgule, peu
 * importe le préfixe d'adresse (parc/square/voie).
 */
fun parseArrKey(adresse: String?): ArrKey {
    if (adresse.isNullOrBlank()) return ArrKey.Other
    val tail = adresse.substringAfterLast(", ", adresse).trim()

    val match = PARIS_RAW.find(tail) ?: PARIS_NORMALIZED.find(tail)
    if (match != null) {
        val n = match.groupValues[1].toIntOrNull()
        if (n != null && n in 1..20) return ArrKey.Paris(n)
    }

    return when (tail.uppercase()) {
        "BOIS DE VINCENNES" -> ArrKey.BoisVincennes
        "BOIS DE BOULOGNE" -> ArrKey.BoisBoulogne
        else -> ArrKey.Other
    }
}

/** Libellé court pour sous-texte de carte (« 1er », « 5e », « Bois… »). */
fun ArrKey.label(): String = when (this) {
    is ArrKey.Paris -> if (num == 1) "1er" else "${num}e"
    ArrKey.BoisVincennes -> "Bois de Vincennes"
    ArrKey.BoisBoulogne -> "Bois de Boulogne"
    ArrKey.Other -> "Hors Paris"
}

/** Libellé long pour sticky header de chapitre par arrondissement. */
fun ArrKey.headerLabel(): String = when (this) {
    is ArrKey.Paris -> if (num == 1) "1er arrondissement" else "${num}e arrondissement"
    ArrKey.BoisVincennes -> "Bois de Vincennes"
    ArrKey.BoisBoulogne -> "Bois de Boulogne"
    ArrKey.Other -> "Hors Paris"
}

/** Ordre canonique : 1..20 → Bois de Vincennes → Bois de Boulogne → Hors Paris. */
fun ArrKey.sortKey(): Int = when (this) {
    is ArrKey.Paris -> num
    ArrKey.BoisVincennes -> 21
    ArrKey.BoisBoulogne -> 22
    ArrKey.Other -> 23
}

/**
 * Slug stable utilisé comme clé dans `arr-species.json` et dans les ids de
 * badges « Familier d'arrondissement » (`familier_arr_{slug}`). Doit rester
 * aligné avec `arr_key_slug()` côté `tools/build_dataset.py`.
 */
fun ArrKey.idSlug(): String = when (this) {
    is ArrKey.Paris -> num.toString()
    ArrKey.BoisVincennes -> "vincennes"
    ArrKey.BoisBoulogne -> "boulogne"
    ArrKey.Other -> "other"
}

/** Inverse de [idSlug] : reconstruit l'ArrKey depuis un slug de badge « Familier du … ». */
fun arrKeyFromSlug(slug: String): ArrKey? = when (slug) {
    "vincennes" -> ArrKey.BoisVincennes
    "boulogne" -> ArrKey.BoisBoulogne
    "other" -> ArrKey.Other
    else -> slug.toIntOrNull()?.takeIf { it in 1..20 }?.let { ArrKey.Paris(it) }
}

private val ROMAN_1_20: List<String> = listOf(
    "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X",
    "XI", "XII", "XIII", "XIV", "XV", "XVI", "XVII", "XVIII", "XIX", "XX",
)

/** Chiffre romain de l'arrondissement (logo des badges), `null` pour les bois. */
fun ArrKey.romanNumeral(): String? = when (this) {
    is ArrKey.Paris -> ROMAN_1_20.getOrNull(num - 1)
    else -> null
}
