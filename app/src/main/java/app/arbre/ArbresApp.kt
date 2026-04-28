package app.arbre

import android.app.Application
import app.arbre.data.ArbreDatabase
import app.arbre.data.ArbreRepository
import app.arbre.data.CaptureRepository
import app.arbre.data.DatasetStats
import app.arbre.data.SpeciesIndex
import org.maplibre.android.MapLibre

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

    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)
    }
}
