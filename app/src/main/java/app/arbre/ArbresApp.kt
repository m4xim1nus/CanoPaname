package app.arbre

import android.app.Application
import app.arbre.data.ArbreDatabase
import app.arbre.data.ArbreRepository
import org.maplibre.android.MapLibre

class ArbresApp : Application() {

    val arbreRepository: ArbreRepository by lazy {
        ArbreRepository(ArbreDatabase.get(this).arbreDao())
    }

    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)
    }
}
