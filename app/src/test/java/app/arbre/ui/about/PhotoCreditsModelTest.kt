package app.arbre.ui.about

import app.arbre.data.PhotoLicense
import app.arbre.data.PhotoRole
import app.arbre.data.PhotoSourceMeta
import app.arbre.data.ReferencePhoto
import app.arbre.data.SpeciesEntry
import app.arbre.data.SpeciesIndex
import app.arbre.data.SpeciesPhotoRepository
import app.arbre.data.SpeciesPhotos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests JVM de `buildPhotoCreditsModel` (fonction pure) : comptage collectif
 * Paris (principales + détails), séparation Wikimedia/iNaturalist, tri
 * casse-insensible par nom, résolution du nom d'espèce (nv/nom commun/binôme)
 * et du nom de licence. Repos construits en mémoire via leurs constructeurs
 * publics, sans dépendance Android.
 */
class PhotoCreditsModelTest {

    private val licenses = mapOf(
        "odbl-1.0" to PhotoLicense("Open Database License v1.0", "https://opendatacommons.org/licenses/odbl/1-0/"),
        "cc-by" to PhotoLicense("CC BY", "https://creativecommons.org/licenses/by/4.0/"),
        "cc0" to PhotoLicense("CC0 1.0", "https://creativecommons.org/publicdomain/zero/1.0/"),
    )
    private val sources = mapOf(
        "paris" to PhotoSourceMeta("Ville de Paris — Guide des essences 2024", "J.E. Michaut, B. Morlon, B. Serres"),
        "wikimedia-commons" to PhotoSourceMeta("Wikimedia Commons", ""),
        "inaturalist" to PhotoSourceMeta("iNaturalist", ""),
    )

    private fun photo(file: String, role: PhotoRole, src: String, lic: String, by: String, url: String? = null) =
        ReferencePhoto(file, role, src, lic, by, url)

    private fun repo(bySk: Map<Int, SpeciesPhotos>) = SpeciesPhotoRepository(bySk, licenses, sources)

    private fun index(vararg entries: SpeciesEntry) = SpeciesIndex(entries.toList())

    @Test fun `counts every paris photo including details`() {
        val paris = SpeciesPhotos(
            principal = photo("0-0.webp", PhotoRole.PRINCIPAL, "paris", "odbl-1.0", "Ville de Paris"),
            details = listOf(
                photo("0-1.webp", PhotoRole.DETAIL, "paris", "odbl-1.0", "Ville de Paris"),
                photo("0-2.webp", PhotoRole.DETAIL, "paris", "odbl-1.0", "Ville de Paris"),
            ),
        )
        val model = buildPhotoCreditsModel(repo(mapOf(0 to paris)), index())
        assertEquals(3, model.parisPhotoCount)
        assertEquals(1, model.parisSpeciesCount)
        // Paris ne produit aucune ligne (bloc collectif).
        assertTrue(model.wikimediaRows.isEmpty())
        assertTrue(model.inatRows.isEmpty())
    }

    @Test fun `separates wikimedia and inaturalist rows`() {
        val wiki = SpeciesPhotos(photo("14-0.webp", PhotoRole.PRINCIPAL, "wikimedia-commons", "cc-by", "Udo"), emptyList())
        val inat = SpeciesPhotos(photo("42-0.webp", PhotoRole.PRINCIPAL, "inaturalist", "cc0", "Anonyme"), emptyList())
        val model = buildPhotoCreditsModel(
            repo(mapOf(14 to wiki, 42 to inat)),
            index(
                SpeciesEntry(index = 14, genre = "Sorbus", espece = "aria", nv = "Alisier blanc"),
                SpeciesEntry(index = 42, genre = "Acer", espece = "campestre", nv = "Érable champêtre"),
            ),
        )
        assertEquals(listOf("Alisier blanc"), model.wikimediaRows.map { it.speciesName })
        assertEquals("Udo", model.wikimediaRows.single().author)
        assertEquals(listOf("Érable champêtre"), model.inatRows.map { it.speciesName })
        assertEquals("Anonyme", model.inatRows.single().author)
    }

    @Test fun `sorts rows case-insensitively by species name`() {
        val z = SpeciesPhotos(photo("1-0.webp", PhotoRole.PRINCIPAL, "wikimedia-commons", "cc-by", "a"), emptyList())
        val a = SpeciesPhotos(photo("2-0.webp", PhotoRole.PRINCIPAL, "wikimedia-commons", "cc-by", "b"), emptyList())
        val b = SpeciesPhotos(photo("3-0.webp", PhotoRole.PRINCIPAL, "wikimedia-commons", "cc-by", "c"), emptyList())
        val model = buildPhotoCreditsModel(
            repo(mapOf(1 to z, 2 to a, 3 to b)),
            index(
                SpeciesEntry(index = 1, genre = "G", espece = "z", nv = "zèbre"),
                SpeciesEntry(index = 2, genre = "G", espece = "a", nv = "Alpha"),
                SpeciesEntry(index = 3, genre = "G", espece = "b", nv = "bêta"),
            ),
        )
        assertEquals(listOf("Alpha", "bêta", "zèbre"), model.wikimediaRows.map { it.speciesName })
    }

    @Test fun `falls back to binomial when no vernacular name`() {
        val wiki = SpeciesPhotos(photo("9-0.webp", PhotoRole.PRINCIPAL, "wikimedia-commons", "cc-by", "auteur"), emptyList())
        // Entrée sans nv ni nomCommun → displayNomCommun = binôme « Genre espèce ».
        val model = buildPhotoCreditsModel(
            repo(mapOf(9 to wiki)),
            index(SpeciesEntry(index = 9, genre = "Quercus", espece = "robur")),
        )
        assertEquals("Quercus robur", model.wikimediaRows.single().speciesName)
    }

    @Test fun `falls back to placeholder when sk is unknown to the index`() {
        val wiki = SpeciesPhotos(photo("77-0.webp", PhotoRole.PRINCIPAL, "wikimedia-commons", "cc-by", "auteur"), emptyList())
        val model = buildPhotoCreditsModel(repo(mapOf(77 to wiki)), index())
        assertEquals("Espèce #77", model.wikimediaRows.single().speciesName)
    }

    @Test fun `resolves license display name`() {
        val wiki = SpeciesPhotos(photo("5-0.webp", PhotoRole.PRINCIPAL, "wikimedia-commons", "cc-by", "auteur"), emptyList())
        val inat = SpeciesPhotos(photo("6-0.webp", PhotoRole.PRINCIPAL, "inaturalist", "cc0", "auteur"), emptyList())
        val model = buildPhotoCreditsModel(
            repo(mapOf(5 to wiki, 6 to inat)),
            index(
                SpeciesEntry(index = 5, genre = "G", espece = "a", nv = "A"),
                SpeciesEntry(index = 6, genre = "G", espece = "b", nv = "B"),
            ),
        )
        assertEquals("CC BY", model.wikimediaRows.single().licenseName)
        assertEquals("CC0 1.0", model.inatRows.single().licenseName)
    }
}
