package app.arbre

import android.app.Application
import android.os.SystemClock
import android.util.Log
import app.arbre.data.ArbreDatabase
import app.arbre.data.ArbreRepository
import app.arbre.data.CaptureRepository
import app.arbre.data.DatasetStats
import app.arbre.data.SpeciesIndex
import app.arbre.data.SpeciesInfoRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import org.maplibre.android.MapLibre

private const val ARBRES_GEOJSON_ASSET = "arbres-paris.geojson"

class ArbresApp : Application() {

    private val database by lazy { ArbreDatabase.get(this) }

    val arbreRepository: ArbreRepository by lazy {
        ArbreRepository(database.arbreDao())
    }

    val captureRepository: CaptureRepository by lazy {
        CaptureRepository(database.captureDao())
    }

    val speciesIndex: SpeciesIndex by lazy { SpeciesIndex.load(this) }
    val datasetStats: DatasetStats by lazy { DatasetStats.load(this) }
    val speciesInfoRepository: SpeciesInfoRepository by lazy { SpeciesInfoRepository.load(this) }

    /** Démarrage du process. Sert de t0 pour les logs de timing du cold start. */
    val processStartElapsedMs: Long = SystemClock.elapsedRealtime()

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Pré-chargement parallèle du GeoJSON 32 Mo : déclenché dans onCreate, en
     * vol pendant l'init MapLibre + Compose + setStyle réseau. Le MapScreen
     * await ce Deferred au lieu de relire le fichier après setStyle, ce qui
     * sortait la lecture du chemin critique.
     */
    val arbresGeoJsonAsync: Deferred<String> by lazy {
        ioScope.async {
            val t0 = SystemClock.elapsedRealtime()
            val json = assets.open(ARBRES_GEOJSON_ASSET)
                .bufferedReader()
                .use { it.readText() }
            Log.i(
                "ArbresApp",
                "GeoJSON préchargé: ${json.length / 1_000_000} Mo en ${SystemClock.elapsedRealtime() - t0} ms"
            )
            json
        }
    }

    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)
        // Touche le lazy pour démarrer la lecture en parallèle.
        arbresGeoJsonAsync
    }
}
