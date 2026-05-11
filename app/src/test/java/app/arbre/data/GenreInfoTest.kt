package app.arbre.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests JVM unitaires de `GenreInfoRepository` sans dépendance Android (pas
 * de `Context.assets`). On bypasse `load(context)` et on construit la map
 * directement, comme pour `SpeciesIndex`.
 */
class GenreInfoTest {

    @Test fun `repository get returns matching entry by genre`() {
        val info = GenreInfo(
            genre = "Quercus",
            nomFr = "Chêne",
            wikipediaTitle = "Quercus",
            summary = "Quercus est un genre…",
            stats = GenreStats(
                count = 17_542,
                speciesIdentified = 32,
                topSpecies = listOf(TopSpecies(sk = 0, nv = "Chêne pédonculé", count = 4_321)),
                topArr = listOf(ArrCount(arr = "12e", count = 2_340, ratio = null)),
            ),
        )
        val repo = GenreInfoRepository(mapOf("Quercus" to info))
        assertEquals("Chêne", repo.get("Quercus")?.nomFr)
        assertNull(repo.get("Tilia"))
        assertEquals(1, repo.total)
    }

    @Test fun `repository empty when asset missing`() {
        // Constructeur public direct simule le fallback de `load()` (asset absent).
        val repo = GenreInfoRepository(emptyMap())
        assertNull(repo.get("Quercus"))
        assertEquals(0, repo.total)
    }

    @Test fun `parsing tolerates missing optional fields`() {
        // Reproduit la logique de `parseEntry` à la main pour exercer le shape
        // minimal (juste `g` + `stats`).
        val arr = JSONArray(
            """[{"g":"Genista","stats":{"count":12,"speciesIdentified":0,"topSpecies":[],"topArr":[]}}]"""
        )
        val o = arr.getJSONObject(0)
        val info = parseEntryLikeRepo(o)
        assertEquals("Genista", info.genre)
        assertNull(info.nomFr)
        assertNull(info.wikipediaTitle)
        assertNull(info.summary)
        assertEquals(12, info.stats.count)
        assertEquals(0, info.stats.speciesIdentified)
        assertTrue(info.stats.topSpecies.isEmpty())
        assertTrue(info.stats.topArr.isEmpty())
    }

    @Test fun `parsing reads full payload`() {
        val arr = JSONArray(
            """[{
                "g":"Quercus",
                "fr":"Chêne",
                "wp":"Quercus",
                "summary":"Quercus est un genre…",
                "stats":{
                    "count":17542,
                    "speciesIdentified":32,
                    "topSpecies":[{"sk":12,"nv":"Chêne pédonculé","count":4321}],
                    "topArr":[{"arr":"12e","count":2340}]
                }
            }]"""
        )
        val o = arr.getJSONObject(0)
        val info = parseEntryLikeRepo(o)
        assertEquals("Chêne", info.nomFr)
        assertEquals("Quercus", info.wikipediaTitle)
        assertEquals(1, info.stats.topSpecies.size)
        assertEquals(12, info.stats.topSpecies[0].sk)
        assertEquals("Chêne pédonculé", info.stats.topSpecies[0].nv)
        assertEquals(4321, info.stats.topSpecies[0].count)
        assertEquals(1, info.stats.topArr.size)
        assertEquals("12e", info.stats.topArr[0].arr)
        assertEquals(2340, info.stats.topArr[0].count)
        assertNull(info.stats.topArr[0].ratio)
    }

    /** Reproduit `GenreInfoRepository.parseEntry` (privée companion) à la
     *  main pour rester offline-testable. Si un champ change côté repo,
     *  mettre à jour ici aussi. */
    private fun parseEntryLikeRepo(o: JSONObject): GenreInfo {
        val statsObj = o.getJSONObject("stats")
        val stats = GenreStats(
            count = statsObj.getInt("count"),
            speciesIdentified = statsObj.getInt("speciesIdentified"),
            topSpecies = run {
                val a = statsObj.optJSONArray("topSpecies") ?: return@run emptyList()
                List(a.length()) { i ->
                    val x = a.getJSONObject(i)
                    TopSpecies(sk = x.getInt("sk"), nv = x.getString("nv"), count = x.getInt("count"))
                }
            },
            topArr = run {
                val a = statsObj.optJSONArray("topArr") ?: return@run emptyList()
                List(a.length()) { i ->
                    val x = a.getJSONObject(i)
                    ArrCount(arr = x.getString("arr"), count = x.getInt("count"), ratio = null)
                }
            },
        )
        return GenreInfo(
            genre = o.getString("g"),
            nomFr = if (o.has("fr") && !o.isNull("fr")) o.optString("fr").takeIf { it.isNotEmpty() } else null,
            wikipediaTitle = if (o.has("wp") && !o.isNull("wp")) o.optString("wp").takeIf { it.isNotEmpty() } else null,
            summary = if (o.has("summary") && !o.isNull("summary")) o.optString("summary").takeIf { it.isNotEmpty() } else null,
            stats = stats,
        )
    }
}
