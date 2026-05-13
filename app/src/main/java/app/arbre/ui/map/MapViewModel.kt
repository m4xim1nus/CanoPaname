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
 * Survit à la nav stack : mémorise la caméra et l'arbre actuellement ouvert.
 *
 * Le fetch de l'arbre est fait ici (pas dans le composable de la fiche) pour
 * que le sheet n'apparaisse qu'avec un contenu réel — sinon il est mesuré
 * pendant un état « Chargement… » et fige une hauteur tronquée à la 2e
 * ouverture, quand le cache Room chaud rend le fetch plus rapide que
 * l'animation d'ouverture.
 *
 * `SavedStateHandle` n'est utilisé que pour la capture en cours, seul cas où
 * une perte d'état pendant un intent externe (caméra système) ferait
 * disparaître une donnée non-récupérable côté utilisateur.
 */
class MapViewModel(
    private val repo: ArbreRepository,
    private val state: SavedStateHandle,
) : ViewModel() {

    var lastCamera: CameraPosition? = null
        private set

    var openedArbre: Arbre? by mutableStateOf(null)
        private set

    /**
     * Cache de `arbresRemarquables()` pour le mode chasse — peuplé au 1er
     * passage en mode chasse, survit aux remounts. Le flag `huntActive` vit
     * côté `MapScreen` (`remember`) : le mode se ferme automatiquement quand
     * on quitte l'écran.
     */
    var remarquablesCache: List<Arbre>? = null

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
        // Garde legacy : si l'utilisateur a upgradé entre l'intent caméra
        // ouvert et son résultat, l'ancienne clé `K_PHOTO_PATH_LEGACY`
        // (chemin absolu pré-v3) est encore en `SavedStateHandle`. À
        // retirer en v1.0.1+ (probabilité d'occurrence ≈ 0).
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
