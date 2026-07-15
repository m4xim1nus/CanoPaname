package app.arbre.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Rôle d'une photo de référence : principale (hero) ou détail (collage). */
enum class PhotoRole { PRINCIPAL, DETAIL }

/**
 * Une photo de référence embarquée (`assets/species-photos/`), produite par les
 * sprints S9/S10 (`tools/build_dataset.py`, bloc `_write_species_photos`).
 * L'attribution (`source`/`license`/`author`/`sourceUrl`) est portée par chaque
 * photo, mais toutes les photos d'une même espèce la partagent (les cascades S9
 * Paris et S10 Wikidata/iNaturalist sont disjointes par espèce).
 */
data class ReferencePhoto(
    val file: String,
    val role: PhotoRole,
    val source: String,
    val license: String,
    val author: String,
    val sourceUrl: String?,
) {
    /** Chemin asset relatif (à ouvrir via `context.assets.open(...)`). */
    val assetPath: String get() = "species-photos/$file"
}

/**
 * Jeu de photos d'une espèce : une principale + 0..N détails. Comme toutes les
 * photos partagent la même attribution (source/licence/auteur/url) par
 * construction, on l'expose dérivée de la principale.
 */
data class SpeciesPhotos(
    val principal: ReferencePhoto,
    val details: List<ReferencePhoto>,
) {
    /** Principale d'abord, puis les détails dans l'ordre du manifest. */
    val all: List<ReferencePhoto> get() = listOf(principal) + details

    val source: String get() = principal.source
    val license: String get() = principal.license
    val author: String get() = principal.author
    val sourceUrl: String? get() = principal.sourceUrl
}

/**
 * Jeu de photos d'une espèce accompagné du nom d'affichage de sa licence
 * (résolu via `SpeciesPhotoRepository.licenseFor`), regroupés pour le hero de la
 * fiche espèce — évite d'empiler photos + licence en paramètres du composable.
 */
data class HeroPhotos(val photos: SpeciesPhotos, val licenseName: String?)

/**
 * Vignette d'une cellule Catalogue : photo de référence de l'espèce si elle
 * existe (asset, même visuel que le hero de la fiche), sinon 1re capture
 * perso. Regroupées pour garder la signature de `CatalogueCell` compacte
 * (même motif que [HeroPhotos]).
 */
data class CataloguePhotos(val referencePath: String?, val captureFile: File?)

/** Métadonnée de licence (`meta.licenses` du manifest). */
data class PhotoLicense(val name: String, val url: String)

/** Métadonnée de source (`meta.sources` du manifest). */
data class PhotoSourceMeta(val name: String, val authors: String)

/**
 * Cache des photos de référence par espèce (`sk`), plus les tables de licences
 * et de sources pour l'attribution. Absence d'asset ou manifest vide → repo
 * vide (la fiche espèce retombe sur son hero texte). Pendant « images » du
 * `SpeciesInfoRepository`.
 */
class SpeciesPhotoRepository(
    private val bySk: Map<Int, SpeciesPhotos>,
    val licenses: Map<String, PhotoLicense>,
    val sources: Map<String, PhotoSourceMeta>,
) {

    fun get(sk: Int): SpeciesPhotos? = bySk[sk]

    fun licenseFor(key: String): PhotoLicense? = licenses[key]

    fun sourceFor(key: String): PhotoSourceMeta? = sources[key]

    /** Vue complète (pour l'écran crédits) : sk → photos, dans l'ordre du manifest. */
    fun all(): Map<Int, SpeciesPhotos> = bySk

    /** Nombre d'espèces illustrées. */
    val coveredCount: Int get() = bySk.size

    companion object {
        /**
         * Asset absent/illisible → repo vide (pattern `GenreInfoRepository.load`).
         * La fiche espèce reste fonctionnelle (hero texte).
         */
        fun load(context: Context, asset: String = "species-photos.json"): SpeciesPhotoRepository {
            val text = try {
                context.assets.open(asset).bufferedReader().use { it.readText() }
            } catch (_: Throwable) {
                return SpeciesPhotoRepository(emptyMap(), emptyMap(), emptyMap())
            }
            return parseSpeciesPhotosManifest(JSONObject(text))
        }
    }
}

/**
 * Parse le manifest `species-photos.json` → `SpeciesPhotoRepository`. Racine
 * **objet** `{meta:{v, licenses, sources}, photos:{"<sk>":[...]}}`. Clés de
 * `photos` = sk en String → `toInt()`. Principale = 1er élément `r=="p"`
 * (fallback élément 0), détails = les `r=="d"` dans l'ordre. `internal` /
 * top-level pour rester directement testable en JVM (comme `parseSpeciesAttributes`).
 */
internal fun parseSpeciesPhotosManifest(root: JSONObject): SpeciesPhotoRepository {
    val meta = root.optJSONObject("meta")
    val licenses = parseLicenses(meta?.optJSONObject("licenses"))
    val sources = parseSources(meta?.optJSONObject("sources"))
    val photosObj = root.optJSONObject("photos")
    val bySk = parsePhotos(photosObj)
    return SpeciesPhotoRepository(bySk, licenses, sources)
}

private fun parseLicenses(o: JSONObject?): Map<String, PhotoLicense> {
    if (o == null) return emptyMap()
    val map = HashMap<String, PhotoLicense>(o.length())
    for (key in o.keys()) {
        val e = o.getJSONObject(key)
        map[key] = PhotoLicense(name = e.getString("name"), url = e.getString("url"))
    }
    return map
}

private fun parseSources(o: JSONObject?): Map<String, PhotoSourceMeta> {
    if (o == null) return emptyMap()
    val map = HashMap<String, PhotoSourceMeta>(o.length())
    for (key in o.keys()) {
        val e = o.getJSONObject(key)
        map[key] = PhotoSourceMeta(name = e.getString("name"), authors = e.optString("authors"))
    }
    return map
}

private fun parsePhotos(o: JSONObject?): Map<Int, SpeciesPhotos> {
    if (o == null) return emptyMap()
    val map = HashMap<Int, SpeciesPhotos>(o.length())
    for (key in o.keys()) {
        val sk = key.toIntOrNull()
        val arr = o.optJSONArray(key)
        if (sk != null && arr != null) {
            parseSpeciesPhotos(arr)?.let { map[sk] = it }
        }
    }
    return map
}

/** Une liste `[{f,r,src,lic,by,u}, ...]` → `SpeciesPhotos`, ou null si vide. */
private fun parseSpeciesPhotos(arr: JSONArray): SpeciesPhotos? {
    if (arr.length() == 0) return null
    val photos = List(arr.length()) { i -> parsePhoto(arr.getJSONObject(i)) }
    // Principale = 1er élément marqué "p" ; fallback = premier élément.
    // Exclusion par identité : dans le cas fallback la principale a le rôle
    // DETAIL et serait sinon dupliquée dans `all`.
    val principal = photos.firstOrNull { it.role == PhotoRole.PRINCIPAL } ?: photos.first()
    val details = photos.filter { it !== principal && it.role == PhotoRole.DETAIL }
    return SpeciesPhotos(principal = principal, details = details)
}

private fun parsePhoto(o: JSONObject): ReferencePhoto = ReferencePhoto(
    file = o.getString("f"),
    role = if (o.optString("r") == "p") PhotoRole.PRINCIPAL else PhotoRole.DETAIL,
    source = o.getString("src"),
    license = o.getString("lic"),
    author = o.getString("by"),
    sourceUrl = o.optStringOrNull("u"),
)

private fun JSONObject.optStringOrNull(key: String): String? =
    if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotEmpty() } else null
