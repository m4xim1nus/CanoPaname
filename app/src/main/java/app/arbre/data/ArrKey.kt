package app.arbre.data

/**
 * Clé géographique d'une adresse d'arbre, dérivée du suffixe d'adresse produit
 * par `tools/build_dataset.py:build_address`. La DB stocke le **format brut**
 * du CSV OpenData (sans normalisation — `normalize_arr` n'est utilisé que pour
 * les stats `species-info.json`, pas pour l'adresse stockée). Exemples réels :
 *  - « …, PARIS 12E ARRDT » → [Paris(12)]
 *  - « …, PARIS 1ER ARRDT » → [Paris(1)] (la forme « 1ER » est spécifique)
 *  - « …, BOIS DE VINCENNES » → [BoisVincennes]
 *  - « …, BOIS DE BOULOGNE » → [BoisBoulogne]
 *  - tout le reste → [Other]
 *
 * Le parser tolère aussi un format normalisé (« 5e », « 1er ») au cas où
 * `build_address` serait un jour patché pour utiliser `normalize_arr` ; les
 * tests historiques l'utilisaient déjà.
 *
 * Les bois sont élevés en clé propre parce qu'ils représentent ~10 % des arbres
 * remarquables — fondre tout en « Hors Paris » noierait l'info géo dans le
 * Catalogue Remarquables.
 */
sealed class ArrKey {
    data class Paris(val num: Int) : ArrKey()
    data object BoisVincennes : ArrKey()
    data object BoisBoulogne : ArrKey()
    data object Other : ArrKey()
}

// Format brut OpenData : « PARIS 12E ARRDT » / « PARIS 1ER ARRDT ».
private val PARIS_RAW = Regex("""^PARIS (\d{1,2})(?:ER|E) ARRDT$""")

// Format normalisé : « 5e », « 1er », « 20e ».
private val PARIS_NORMALIZED = Regex("""^(\d{1,2})(?:er|e)$""")

/**
 * Parse l'arrondissement depuis le suffixe d'adresse. Travaille sur le segment
 * post-dernière-virgule pour matcher uniformément peu importe le préfixe
 * (parc/square/voie). Accepte le format brut OpenData et le format normalisé.
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
