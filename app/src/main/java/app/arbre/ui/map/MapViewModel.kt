package app.arbre.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import app.arbre.data.ArbreRepository
import org.maplibre.android.camera.CameraPosition

/**
 * Survit à la nav stack, donc on l'utilise pour mémoriser la position de
 * la caméra entre deux entrées sur `MapScreen` (sortie vers la fiche détail
 * puis retour). Le repo n'est gardé en field que pour symétrie / extensions ;
 * la carte se nourrit aujourd'hui d'un asset GeoJSON clusterisé côté MapLibre.
 */
class MapViewModel(@Suppress("UNUSED_PARAMETER") repo: ArbreRepository) : ViewModel() {

    var lastCamera: CameraPosition? = null
        private set

    fun rememberCamera(position: CameraPosition) {
        lastCamera = position
    }

    class Factory(private val repo: ArbreRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MapViewModel(repo) as T
    }
}
