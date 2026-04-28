package app.arbre

import android.app.Application
import org.maplibre.android.MapLibre

class ArbresApp : Application() {
    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)
    }
}
