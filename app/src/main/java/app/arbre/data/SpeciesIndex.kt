package app.arbre.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Lookup `(genre, espece) ↔ int speciesIndex`. Les int sont préservés entre
 * regénérations du dataset par `tools/build_dataset.py` — sinon les rows
 * `Capture.speciesIndex` deviendraient invalides après une mise à jour.
 *
 * Champs dérivés du build dataset :
 * - `nv` : nom vernaculaire **unique** post-désambiguation. Cascade côté
 *   script : VERNACULAR_OVERRIDES → Wikidata P1843 → Wikipedia frTitle →
 *   construit. `null` ssi l'asset legacy ne porte pas le champ.
 * - `pokedexNumber` : numéro Pokédex stable (séquence par count décroissant
 *   sur les seules espèces identifiées avec count > 0). `null` pour les
 *   `unknownSpecies`, les zombies count=0, et tout l'asset legacy.
 * - `unknownSpecies` : `true` ssi entrée `(G, sp.)` (espèce non identifiée).
 *   Section dédiée en fin de catalogue.
 */
data class SpeciesEntry(
    val index: Int,
    val genre: String,
    val espece: String,
    /** Nom commun le plus fréquent dans OpenData ; null si jamais renseigné. */
    val nomCommun: String? = null,
    /** Nom vernaculaire unique post-désambiguation ; null sur asset legacy. */
    val nv: String? = null,
    /** Numéro Pokédex stable ; null pour `unknownSpecies` et asset legacy. */
    val pokedexNumber: Int? = null,
    /** Entrée `(G, sp.)` non identifiée. */
    val unknownSpecies: Boolean = false,
) {
    val displayName: String get() = "$genre $espece"
    /**
     * Triple fallback : `nv` (Catalogue) → `nomCommun` (OpenData) → binôme.
     * Toutes les call-sites bénéficient gratuitement de `nv` quand l'asset
     * a été régénéré ; sinon comportement historique préservé.
     */
    val displayNomCommun: String get() = nv ?: nomCommun ?: displayName

    /**
     * Entrée active du Pokédex : identifiée (non `(G, sp.)`) ET ayant des
     * arbres vivants dans le dataset courant (donc `pokedexNumber != null`).
     * Filtre canonique des affichages Arboretum / fiche-espèce / genre : une
     * entrée non-active est cachée de l'UI, mais conserve son `index` stable
     * dans le JSON pour que les captures historiques ne perdent jamais leur sk.
     */
    val isActive: Boolean get() = !unknownSpecies && pokedexNumber != null
}

class SpeciesIndex(entries: List<SpeciesEntry>) {

    private val byIndex: Map<Int, SpeciesEntry> = entries.associateBy { it.index }
    private val byKey: Map<Pair<String, String>, Int> =
        entries.associate { (it.genre to it.espece) to it.index }
    // Ordre annuaire de référence (par speciesIndex croissant). Le Catalogue
    // Arboretum applique son propre tri par count Paris à l'affichage.
    private val ordered: List<SpeciesEntry> = entries.sortedBy { it.index }

    /**
     * `genre → set des sks de ce genre`. Pré-calculé une fois, sert au calcul
     * d'auto-débloquage des fiches `(G, sp.)` : capturer n'importe quel sk du
     * genre marque la fiche `unknownSpecies` débloquée.
     */
    private val sksByGenre: Map<String, Set<Int>> =
        entries.groupBy { it.genre }.mapValues { (_, list) -> list.map { it.index }.toSet() }

    /** Set des sks `unknownSpecies == true` (entrées `(G, sp.)`). */
    val unknownSks: Set<Int> = entries.filter { it.unknownSpecies }.map { it.index }.toSet()

    val total: Int get() = byIndex.size

    fun get(index: Int): SpeciesEntry? = byIndex[index]

    fun indexOf(genre: String, espece: String): Int? = byKey[genre to espece]

    fun indexOf(arbre: Arbre): Int? = indexOf(arbre.genre, arbre.espece)

    fun entries(): List<SpeciesEntry> = ordered

    /** `null` si le sk n'est pas dans l'index. */
    fun genreOf(index: Int): String? = byIndex[index]?.genre

    /**
     * Toutes les entrées d'un genre donné, ordonnées par `index` croissant.
     * Alimente le mini-catalogue de la fiche genre (« j'ai 3/55 chênes »).
     */
    fun entriesOfGenre(genre: String): List<SpeciesEntry> {
        val sks = sksByGenre[genre] ?: return emptyList()
        return sks.mapNotNull { byIndex[it] }.sortedBy { it.index }
    }

    /**
     * Liste alphabétique des genres **catalogables comme chapitres d'espèces
     * identifiées**. Exclut « Non spécifié » et les genres only-unknown
     * (`Genista`, `Vitex`, `Ziziphus`) — leurs chapitres seraient vides
     * d'espèces, leurs `(G, sp.)` étant traités séparément.
     *
     * ⚠ Ne PAS utiliser comme dénominateur de progression de découverte
     * (utiliser [allGenres] qui couvre les 204 genres réellement capturables).
     * Source du mode Catalogue par chapitres.
     */
    private val genresWithIdentified: List<String> = sksByGenre
        .filter { (_, sks) -> sks.any { sk -> byIndex[sk]?.unknownSpecies == false } }
        .keys
        .sortedBy { it.lowercase() }

    fun genres(): List<String> = genresWithIdentified

    /**
     * Univers complet des genres : tous ceux présents dans l'index sauf le
     * cas dégénéré « Non spécifié ». Inclut les genres only-unknown
     * (`Genista`, `Vitex`, `Ziziphus`) — un `Genista sp.` capturé compte ici.
     * **Source de vérité pour les compteurs de progression** (`X / 204 genres
     * découverts` en Arboretum et Profil). Pilote aussi le routage des fiches
     * genre.
     */
    private val genresAllUseful: List<String> = sksByGenre.keys
        .asSequence()
        .filter { it != "Non spécifié" }
        .sortedBy { it.lowercase() }
        .toList()

    fun allGenres(): List<String> = genresAllUseful

    /**
     * Nombre d'espèces **actives** du genre (exclut `unknownSpecies` et
     * zombies sans `pokedexNumber`). Sert au compteur `X / Y` du header de
     * chapitre en mode Catalogue.
     */
    fun genreCount(genre: String): Int {
        val sks = sksByGenre[genre] ?: return 0
        return sks.count { sk -> byIndex[sk]?.isActive == true }
    }

    /**
     * Nombre d'espèces **actives** du genre intersectées avec `capturedSks`.
     * Sert au numérateur du compteur `X / Y` du header de chapitre. Les `sp.`
     * capturés et les zombies ne comptent pas — sémantique « progression
     * Pokédex ».
     */
    fun capturedCountInGenre(genre: String, capturedSks: Set<Int>): Int {
        val sks = sksByGenre[genre] ?: return 0
        return sks.count { sk -> sk in capturedSks && byIndex[sk]?.isActive == true }
    }

    /**
     * `true` ssi au moins un sk du genre (identifié OU `unknownSpecies`) est
     * dans `capturedSks`. Sert au verrouillage des fiches genre : une fiche
     * genre n'est accessible que si l'utilisateur a touché le genre,
     * directement (capture sp.) ou indirectement (capture identifiée).
     */
    fun genreHasAnyCapture(genre: String, capturedSks: Set<Int>): Boolean {
        val sks = sksByGenre[genre] ?: return false
        return sks.any { it in capturedSks }
    }

    /**
     * Étend `captured` avec les sks `unknownSpecies` dont le genre contient au
     * moins une capture (sp. ou identifiée). Sert à la coloration de la carte :
     * si l'utilisateur a capturé `Tilia cordata`, tous les pins `Tilia sp.`
     * doivent passer au vert — alignement avec l'auto-débloquage genre-based
     * déjà en place dans `isDiscovered`.
     */
    fun effectivelyCapturedSpecies(captured: Set<Int>): Set<Int> {
        if (captured.isEmpty()) return captured
        val capturedGenres = captured.mapNotNull { byIndex[it]?.genre }.toSet()
        val implicitSpSks = capturedGenres.asSequence()
            .flatMap { g -> sksByGenre[g]?.asSequence() ?: emptySequence() }
            .filter { byIndex[it]?.unknownSpecies == true }
            .toSet()
        if (implicitSpSks.isEmpty()) return captured
        return captured + implicitSpSks
    }

    /**
     * Auto-débloquage genre-based des fiches `(G, sp.)` : un sk `unknownSpecies`
     * est considéré découvert dès qu'un sk frère du même genre est capturé.
     * Les sks identifiés (non `unknownSpecies`) sont découverts au sens strict
     * (présents dans `capturedSks`).
     */
    fun isDiscovered(index: Int, capturedSks: Set<Int>): Boolean {
        if (index in capturedSks) return true
        val entry = byIndex[index] ?: return false
        if (!entry.unknownSpecies) return false
        val freres = sksByGenre[entry.genre] ?: return false
        return freres.any { it != index && it in capturedSks }
    }

    companion object {
        fun load(context: Context, asset: String = "species-index.json"): SpeciesIndex {
            val text = context.assets.open(asset).bufferedReader().use { it.readText() }
            val arr = JSONArray(text)
            val entries = buildList(arr.length()) {
                for (i in 0 until arr.length()) {
                    val o: JSONObject = arr.getJSONObject(i)
                    add(
                        SpeciesEntry(
                            index = o.getInt("i"),
                            genre = o.getString("g"),
                            espece = o.getString("e"),
                            nomCommun = if (o.has("nc") && !o.isNull("nc")) {
                                o.optString("nc").takeIf { it.isNotEmpty() }
                            } else null,
                            nv = if (o.has("nv") && !o.isNull("nv")) {
                                o.optString("nv").takeIf { it.isNotEmpty() }
                            } else null,
                            pokedexNumber = if (o.has("n") && !o.isNull("n")) {
                                o.optInt("n").takeIf { it > 0 }
                            } else null,
                            unknownSpecies = o.optBoolean("u", false),
                        )
                    )
                }
            }
            return SpeciesIndex(entries)
        }
    }
}

data class DatasetStats(
    val totalArbres: Int,
    val totalEspeces: Int,
    val totalRemarquables: Int,
    /**
     * Nombre d'espèces identifiées (exclut les `unknownSpecies` et les
     * zombies count=0). Fallback sur `totalEspeces` si le champ est absent
     * (asset legacy).
     */
    val totalEspecesIdentifiees: Int,
) {
    companion object {
        fun load(context: Context, asset: String = "dataset-stats.json"): DatasetStats {
            val text = context.assets.open(asset).bufferedReader().use { it.readText() }
            val o = JSONObject(text)
            val totalEspeces = o.getInt("totalEspeces")
            return DatasetStats(
                totalArbres = o.getInt("totalArbres"),
                totalEspeces = totalEspeces,
                totalRemarquables = o.getInt("totalRemarquables"),
                totalEspecesIdentifiees = o.optInt("totalEspecesIdentifiees", totalEspeces),
            )
        }
    }
}
