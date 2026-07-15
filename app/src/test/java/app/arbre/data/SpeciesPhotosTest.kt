package app.arbre.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests JVM du parsing du manifest `species-photos.json` via la fonction
 * `internal parseSpeciesPhotosManifest`, sans dépendance Android
 * (`Context.assets`). Le contrat de format est figé par S9/S10
 * (`tools/build_dataset.py`, bloc `_write_species_photos`).
 */
class SpeciesPhotosTest {

    // Bloc `meta` réutilisé par plusieurs fixtures (extrait réel du manifest).
    private val meta = """
        "meta": {
            "v": 1,
            "licenses": {
                "odbl-1.0": {"name": "Open Database License v1.0", "url": "https://opendatacommons.org/licenses/odbl/1-0/"},
                "cc0": {"name": "CC0 1.0", "url": "https://creativecommons.org/publicdomain/zero/1.0/"},
                "cc-by": {"name": "CC BY", "url": "https://creativecommons.org/licenses/by/4.0/"}
            },
            "sources": {
                "paris": {"name": "Ville de Paris — Guide des essences 2024", "authors": "J.E. Michaut, B. Morlon, B. Serres"},
                "wikimedia-commons": {"name": "Wikimedia Commons", "authors": ""},
                "inaturalist": {"name": "iNaturalist", "authors": ""}
            }
        }
    """.trimIndent()

    @Test fun `parses a paris entry with one principal and two details`() {
        // Extrait RÉEL du manifest (sk 0 = Aesculus hippocastanum).
        val root = JSONObject(
            """{
                $meta,
                "photos": {
                    "0": [
                        {"f":"0-0.webp","r":"p","src":"paris","lic":"odbl-1.0","by":"Ville de Paris","u":"https://opendata.paris.fr/x/a67c6ac8"},
                        {"f":"0-1.webp","r":"d","src":"paris","lic":"odbl-1.0","by":"Ville de Paris","u":"https://opendata.paris.fr/x/a67c6ac8"},
                        {"f":"0-2.webp","r":"d","src":"paris","lic":"odbl-1.0","by":"Ville de Paris","u":"https://opendata.paris.fr/x/a67c6ac8"}
                    ]
                }
            }"""
        )
        val repo = parseSpeciesPhotosManifest(root)
        val photos = repo.get(0)!!
        assertEquals(PhotoRole.PRINCIPAL, photos.principal.role)
        assertEquals("0-0.webp", photos.principal.file)
        assertEquals("species-photos/0-0.webp", photos.principal.assetPath)
        // Détails dans l'ordre du manifest.
        assertEquals(listOf("0-1.webp", "0-2.webp"), photos.details.map { it.file })
        assertTrue(photos.details.all { it.role == PhotoRole.DETAIL })
        // `all` = principale d'abord, puis détails.
        assertEquals(listOf("0-0.webp", "0-1.webp", "0-2.webp"), photos.all.map { it.file })
        // Attribution dérivée du principal.
        assertEquals("paris", photos.source)
        assertEquals("odbl-1.0", photos.license)
        assertEquals("Ville de Paris", photos.author)
        assertEquals("https://opendata.paris.fr/x/a67c6ac8", photos.sourceUrl)
        assertEquals(1, repo.coveredCount)
    }

    @Test fun `parses a cascade entry with a single wikimedia photo`() {
        // Extrait RÉEL du manifest (sk 14 = cascade Wikimedia Commons cc-by).
        val root = JSONObject(
            """{
                $meta,
                "photos": {
                    "14": [
                        {"f":"14-0.webp","r":"p","src":"wikimedia-commons","lic":"cc-by","by":"Udo Schröter","u":"https://commons.wikimedia.org/wiki/File:Busch_Jakobsberg.jpg"}
                    ]
                }
            }"""
        )
        val repo = parseSpeciesPhotosManifest(root)
        val photos = repo.get(14)!!
        assertEquals("14-0.webp", photos.principal.file)
        assertEquals("species-photos/14-0.webp", photos.principal.assetPath)
        assertEquals("wikimedia-commons", photos.source)
        assertEquals("cc-by", photos.license)
        assertEquals("Udo Schröter", photos.author)
        assertEquals("https://commons.wikimedia.org/wiki/File:Busch_Jakobsberg.jpg", photos.sourceUrl)
        // Cascade = 1 photo, pas de détail.
        assertTrue(photos.details.isEmpty())
        assertEquals(listOf("14-0.webp"), photos.all.map { it.file })
    }

    @Test fun `resolves licenses and sources from meta`() {
        val repo = parseSpeciesPhotosManifest(JSONObject("""{$meta, "photos": {}}"""))
        assertEquals("CC BY", repo.licenseFor("cc-by")!!.name)
        assertEquals("https://creativecommons.org/licenses/by/4.0/", repo.licenseFor("cc-by")!!.url)
        assertEquals("iNaturalist", repo.sourceFor("inaturalist")!!.name)
        assertEquals("J.E. Michaut, B. Morlon, B. Serres", repo.sourceFor("paris")!!.authors)
        // Source sans auteurs → chaîne vide (pas null).
        assertEquals("", repo.sourceFor("wikimedia-commons")!!.authors)
        // Clé inconnue → null.
        assertNull(repo.licenseFor("inconnue"))
        assertNull(repo.sourceFor("inconnue"))
    }

    @Test fun `empty photos map yields an empty repo`() {
        val repo = parseSpeciesPhotosManifest(JSONObject("""{$meta, "photos": {}}"""))
        assertNull(repo.get(0))
        assertEquals(0, repo.coveredCount)
        // Les tables meta restent chargées même sans photo.
        assertNotNull(repo.licenseFor("cc0"))
    }

    @Test fun `absent photos block yields an empty repo`() {
        val repo = parseSpeciesPhotosManifest(JSONObject("""{$meta}"""))
        assertNull(repo.get(0))
        assertEquals(0, repo.coveredCount)
    }

    @Test fun `photo without url yields null sourceUrl`() {
        val root = JSONObject(
            """{
                $meta,
                "photos": {
                    "42": [
                        {"f":"42-0.webp","r":"p","src":"inaturalist","lic":"cc0","by":"Anonyme"}
                    ]
                }
            }"""
        )
        val photos = parseSpeciesPhotosManifest(root).get(42)!!
        assertEquals("42-0.webp", photos.principal.file)
        assertNull(photos.sourceUrl)
        assertNull(photos.principal.sourceUrl)
    }

    @Test fun `falls back to first element when no principal is marked`() {
        // Garde-fou : aucune photo `r=="p"` → premier élément traité comme principal.
        val root = JSONObject(
            """{
                $meta,
                "photos": {
                    "7": [
                        {"f":"7-0.webp","r":"d","src":"paris","lic":"odbl-1.0","by":"Ville de Paris"},
                        {"f":"7-1.webp","r":"d","src":"paris","lic":"odbl-1.0","by":"Ville de Paris"}
                    ]
                }
            }"""
        )
        val photos = parseSpeciesPhotosManifest(root).get(7)!!
        assertEquals("7-0.webp", photos.principal.file)
        // La principale de fallback n'est pas dupliquée dans les détails.
        assertEquals(listOf("7-1.webp"), photos.details.map { it.file })
        assertEquals(listOf("7-0.webp", "7-1.webp"), photos.all.map { it.file })
    }

    @Test fun `string sk keys are converted to int`() {
        val root = JSONObject(
            """{
                $meta,
                "photos": {
                    "123": [
                        {"f":"123-0.webp","r":"p","src":"paris","lic":"odbl-1.0","by":"Ville de Paris"}
                    ]
                }
            }"""
        )
        val repo = parseSpeciesPhotosManifest(root)
        assertNotNull(repo.get(123))
        assertNull(repo.get(0))
    }
}
