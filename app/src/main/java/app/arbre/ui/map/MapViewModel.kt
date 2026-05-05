package app.arbre.ui.map

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.arbre.data.Arbre
import app.arbre.data.ArbreRepository
import kotlinx.coroutines.launch
import org.maplibre.android.camera.CameraPosition
import java.io.File

/**
 * Survit à la nav stack : mémorise la position de la caméra et l'arbre
 * actuellement ouvert (en bottom sheet par-dessus la carte).
 *
 * Le fetch de l'arbre se fait ici plutôt que dans le composable de la fiche :
 * sinon le sheet est mesuré pendant le « Chargement… » et fige une hauteur
 * tronquée à la 2e ouverture (cache Room chaud → fetch plus rapide que
 * l'animation d'ouverture). En préchargeant, le sheet n'apparaît qu'avec un
 * contenu réel, donc la mesure est correcte d'emblée.
 *
 * Le `SavedStateHandle` n'est utilisé que pour la capture en cours
 * (`pendingCapture`), parce que c'est le seul endroit où la perte d'état
 * pendant un intent externe (caméra système) ferait disparaître une donnée
 * non-récupérable côté utilisateur.
 */
class MapViewModel(
    private val repo: ArbreRepository,
    private val state: SavedStateHandle,
) : ViewModel() {

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

    fun savePending(p: PendingCapture) {
        state[K_ARBRE_ID] = p.arbreId
        state[K_SPECIES_INDEX] = p.speciesIndex
        state[K_REMARQUABLE] = p.remarquable
        state[K_PHOTO_BASENAME] = p.photoBasename
        state[K_LAT] = p.captureLatitude
        state[K_LON] = p.captureLongitude
        state[K_TIMESTAMP] = p.captureTimestamp
    }

    fun consumePending(): PendingCapture? {
        val arbreId = state.get<Long>(K_ARBRE_ID) ?: return null
        // Lecture tolérante : avant la migration v3, la clé stockait un chemin
        // absolu. Si une capture a été ouverte juste avant l'upgrade et que le
        // process est mort entre l'intent caméra et son résultat, on a encore
        // l'ancienne clé en `SavedStateHandle`. On extrait alors le basename
        // pour rester sain. À retirer en v1.0.1+ (probabilité d'occurrence ≈ 0).
        val basename = state.get<String>(K_PHOTO_BASENAME)
            ?: state.get<String>(K_PHOTO_PATH_LEGACY)?.let { File(it).name }
            ?: return null
        val pending = PendingCapture(
            arbreId = arbreId,
            speciesIndex = state.get<Int>(K_SPECIES_INDEX) ?: return null,
            remarquable = state.get<Boolean>(K_REMARQUABLE) ?: return null,
            photoBasename = basename,
            captureLatitude = state.get<Double>(K_LAT) ?: return null,
            captureLongitude = state.get<Double>(K_LON) ?: return null,
            captureTimestamp = state.get<Long>(K_TIMESTAMP) ?: return null,
        )
        state.remove<Long>(K_ARBRE_ID)
        state.remove<Int>(K_SPECIES_INDEX)
        state.remove<Boolean>(K_REMARQUABLE)
        state.remove<String>(K_PHOTO_BASENAME)
        state.remove<String>(K_PHOTO_PATH_LEGACY)
        state.remove<Double>(K_LAT)
        state.remove<Double>(K_LON)
        state.remove<Long>(K_TIMESTAMP)
        return pending
    }

    companion object {
        private const val K_ARBRE_ID = "pending.arbreId"
        private const val K_SPECIES_INDEX = "pending.speciesIndex"
        private const val K_REMARQUABLE = "pending.remarquable"
        private const val K_PHOTO_BASENAME = "pending.photoBasename"
        private const val K_PHOTO_PATH_LEGACY = "pending.photoPath"
        private const val K_LAT = "pending.lat"
        private const val K_LON = "pending.lon"
        private const val K_TIMESTAMP = "pending.timestamp"
    }
}
