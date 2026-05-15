package app.arbre.ui.map

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.arbre.data.Arbre
import app.arbre.data.ArbreRepository
import app.arbre.data.ArrSpeciesIndex
import app.arbre.data.CaptureRepository
import app.arbre.data.GenreInfoRepository
import app.arbre.data.SpeciesIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
     * Sheet de recherche universelle. `null` = fermé. Mêmes raisons que
     * `openedArbre` : on ne monte le `ModalBottomSheet` qu'avec un contenu
     * réel, sinon la 2e ouverture mesure une hauteur tronquée (cf. memo
     * `feedback_compose_sheet`). Les 3 listes (espèces capturées, genres
     * découverts, 22 arrondissements) sont pré-cuites côté `SearchData.build`.
     */
    var searchData: SearchData? by mutableStateOf(null)
        private set

    /**
     * Demande de fly-to vers le centroïde d'un arrondissement (lon, lat).
     * Posté par `flyToArr(...)`, consommé par un `LaunchedEffect` côté
     * `MapScreen` qui anime la caméra puis appelle `consumeArrFlyTo()`.
     * On reste donc sur l'écran Map — pas de query param `flyToArr` : un
     * `navigate(Routes.map(...))` remonterait MapScreen et rejouerait le
     * splash, en perdant `lastCamera`.
     */
    var pendingArrFlyTo: Pair<Double, Double>? by mutableStateOf(null)
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

    fun openSearch(
        speciesIndex: SpeciesIndex,
        genreInfo: GenreInfoRepository,
        arrIndex: ArrSpeciesIndex,
        captureRepo: CaptureRepository,
    ) {
        viewModelScope.launch {
            val captured = captureRepo.capturedSpeciesIndices().first()
            searchData = withContext(Dispatchers.Default) {
                SearchData.build(speciesIndex, genreInfo, arrIndex, captured)
            }
        }
    }

    fun closeSearch() {
        searchData = null
    }

    fun flyToArr(lon: Double, lat: Double) {
        pendingArrFlyTo = lon to lat
        searchData = null
    }

    fun consumeArrFlyTo() {
        pendingArrFlyTo = null
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
        val arbreId = state.get<Long>(K_ARBRE_ID)
        // Garde legacy : si l'utilisateur a upgradé entre l'intent caméra
        // ouvert et son résultat, l'ancienne clé `K_PHOTO_PATH_LEGACY`
        // (chemin absolu pré-v3) est encore en `SavedStateHandle`.
        val basename = state.get<String>(K_PHOTO_BASENAME)
            ?: state.get<String>(K_PHOTO_PATH_LEGACY)?.let { File(it).name }
        val speciesIndex = state.get<Int>(K_SPECIES_INDEX)
        val remarquable = state.get<Boolean>(K_REMARQUABLE)
        val lat = state.get<Double>(K_LAT)
        val lon = state.get<Double>(K_LON)
        val ts = state.get<Long>(K_TIMESTAMP)
        if (arbreId == null || basename == null || speciesIndex == null) return null
        if (remarquable == null || lat == null || lon == null || ts == null) return null
        val pending = PendingCapture(
            arbreId = arbreId,
            speciesIndex = speciesIndex,
            remarquable = remarquable,
            photoBasename = basename,
            captureLatitude = lat,
            captureLongitude = lon,
            captureTimestamp = ts,
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
