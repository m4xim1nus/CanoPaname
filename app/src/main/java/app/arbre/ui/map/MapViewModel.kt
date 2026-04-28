package app.arbre.ui.map

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.arbre.data.Arbre
import app.arbre.data.ArbreRepository
import kotlinx.coroutines.launch
import org.maplibre.android.camera.CameraPosition

/**
 * Survit à la nav stack : mémorise la position de la caméra et l'arbre
 * actuellement ouvert (en bottom sheet par-dessus la carte).
 *
 * Le fetch de l'arbre se fait ici plutôt que dans le composable de la fiche :
 * sinon le sheet est mesuré pendant le « Chargement… » et fige une hauteur
 * tronquée à la 2e ouverture (cache Room chaud → fetch plus rapide que
 * l'animation d'ouverture). En préchargeant, le sheet n'apparaît qu'avec un
 * contenu réel, donc la mesure est correcte d'emblée.
 */
class MapViewModel(private val repo: ArbreRepository) : ViewModel() {

    var lastCamera: CameraPosition? = null
        private set

    var openedArbre: Arbre? by mutableStateOf(null)
        private set

    fun rememberCamera(position: CameraPosition) {
        lastCamera = position
    }

    fun openDetail(id: Long) {
        viewModelScope.launch {
            openedArbre = repo.arbreParId(id)
        }
    }

    fun closeDetail() {
        openedArbre = null
    }

    class Factory(private val repo: ArbreRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MapViewModel(repo) as T
    }
}
