package app.arbre

import android.app.Application
import android.os.SystemClock
import android.util.Log
import app.arbre.backup.BackupExporter
import app.arbre.backup.BackupImporter
import app.arbre.data.ArbreDatabase
import app.arbre.data.ArbreRepository
import app.arbre.data.CaptureRepository
import app.arbre.data.DatasetStats
import app.arbre.data.OnboardingStore
import app.arbre.data.RemarquableInfoRepository
import app.arbre.data.SeasonStore
import app.arbre.data.SpeciesIndex
import app.arbre.data.SpeciesInfoRepository
import app.arbre.data.SplashTipsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
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
    val remarquableInfoRepository: RemarquableInfoRepository by lazy {
        RemarquableInfoRepository.load(this)
    }
    val splashTipsRepository: SplashTipsRepository by lazy {
        SplashTipsRepository.load(this)
    }

    val seasonStore: SeasonStore = SeasonStore()

    val onboardingStore: OnboardingStore by lazy { OnboardingStore(this) }

    val backupExporter: BackupExporter by lazy {
        BackupExporter(this, database.captureDao())
    }

    val backupImporter: BackupImporter by lazy {
        BackupImporter(this, database.captureDao())
    }

    /** t0 pour les logs de timing du cold start. */
    val processStartElapsedMs: Long = SystemClock.elapsedRealtime()

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Pré-chargement parallèle du GeoJSON 32 Mo, lancé dans `onCreate` en
     * vol pendant init MapLibre + Compose + setStyle. Sort la lecture du
     * chemin critique.
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

    /**
     * Cache process-singleton du GeoJSON enrichi par
     * `enrichGeoJsonWithDiscovery`. Au remount de `MapScreen` (retour Profil
     * → Map), évite de re-payer l'enrichment (~5-15 s) et un 2e
     * `setGeoJson` UI. Reste `null` au cold-start fresh — le rawJson nu est
     * posé d'abord (pins visibles ASAP), le mid-session ticke ~1 s après
     * pour le 1er enrichment.
     *
     * `lastEnrichmentKey` mémorise les sets captures qui ont produit
     * `enrichedGeoJson` — sert au mid-session à skipper un re-enrich
     * inutile au mount (les flows émettent leur état initial).
     */
    val enrichedGeoJson: MutableStateFlow<String?> = MutableStateFlow(null)

    @Volatile
    var lastEnrichmentKey: Pair<Set<Int>, Set<Long>>? = null

    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)
        // Touche le lazy pour démarrer la lecture asset en parallèle.
        arbresGeoJsonAsync
    }
}
