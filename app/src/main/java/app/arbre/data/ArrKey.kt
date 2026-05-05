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

/** Libellé court pour sticky header / sous-texte (« 1er », « 5e », « Bois… »). */
fun ArrKey.label(): String = when (this) {
    is ArrKey.Paris -> if (num == 1) "1er" else "${num}e"
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
