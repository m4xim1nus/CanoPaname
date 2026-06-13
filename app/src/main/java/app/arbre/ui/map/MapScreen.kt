package app.arbre.ui.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.RectF
import android.location.Location
import android.os.Looper
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.arbre.ArbresApp
import app.arbre.R
import app.arbre.data.Arbre
import app.arbre.data.Capture
import app.arbre.data.CaptureRepository
import app.arbre.data.SpeciesIndex
import app.arbre.data.rememberArbreRepository
import app.arbre.data.rememberArrSpeciesIndex
import app.arbre.data.rememberCaptureRepository
import app.arbre.data.rememberOnboardingStore
import app.arbre.data.rememberSpeciesIndex
import app.arbre.data.rememberRemarquableInfoRepository
import app.arbre.data.rememberGenreInfoRepository
import app.arbre.data.rememberSpeciesInfoRepository
import app.arbre.data.resolvedFile
import app.arbre.ui.common.DeleteCaptureDialog
import app.arbre.ui.common.PhotoLightbox
import app.arbre.ui.common.rememberFramePingPong
import app.arbre.ui.common.showSnackbarFor
import app.arbre.ui.detail.ArbreDetailActions
import app.arbre.ui.detail.ArbreDetailContent
import app.arbre.ui.detail.ArbreDetailState
import app.arbre.ui.theme.arbresColors
import app.arbre.ui.theme.arbresMotion
import app.arbre.util.LocationProvider
import app.arbre.util.isRecentFix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponent
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.engine.LocationEngine
import org.maplibre.android.location.engine.LocationEngineCallback
import org.maplibre.android.location.engine.LocationEngineRequest
import org.maplibre.android.location.engine.LocationEngineResult
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.sources.GeoJsonSource

// ARCHITECTURE DU FICHIER — deux modes, deux cycles de vie :
//
// - Mode NORMAL (`filterSpecies` vide) : la MapView est PERSISTANTE, portée par
//   le `MapHost` Activity-scopé (cf. doc de tête de MapHost.kt). Le pipeline
//   d'init contenu (caméra → style → layers → push 217 k features →
//   `awaitArbresRendered`) ne tourne qu'UNE FOIS par vie d'Activity, dans
//   `host.scope`, et survit aux navigations ; les observers de découverte
//   (coloration pins + enrichment clusters, cf. `launchDiscoveryObservers`)
//   y tournent aussi en continu. `MapScreen` ne gère per-mount que : l'attache
//   de la view (`AndroidView` + vol de parent), le gel/dégel du rendu
//   (`screenAttached`/`screenDetached`), GPS + bridge MapLibre + pin user,
//   les listeners d'interaction, et les effets caméra (pulse, fly-to arr,
//   recadrage auto one-shot gated par `host.autoRecenterDone`).
//   Splash : `host.pinsRendered` — remount avec carte rendue = zéro voile.
//
// - Mode FILTRÉ (fiche espèce/genre → « voir sur la carte ») : MapView JETABLE
//   locale, pipeline single-pass enrichi d'emblée (< 1 Mo), cycle GL relayé
//   par le mount, tout meurt au dispose. Partager l'instance persistante
//   forcerait un re-push des 33 Mo au retour sur la carte principale.
//
// Géoloc (LocationManager natif, bridge LocationEngine MapLibre, caméra de
// bootstrap non-bloquante) : voir `computeInitialCamera` et
// `enableLocationPin` ci-dessous.

private val PARIS = LatLng(48.8566, 2.3522)
private const val PARIS_ZOOM = 13.0
private const val PARIS_OVERVIEW_ZOOM = 11.5
private const val USER_ZOOM = 16.0

// Durée minimale d'affichage du ColdStartSplash sur un cold-start fresh, pour
// qu'il ne flashe pas sur un device rapide (le temps de lire un tip). Mesurée
// depuis le mount de `MapScreen`. NON appliquée quand on pose un GeoJSON enrichi
// déjà en cache (remount retour Profil → Map) ni en mode filtré.
private const val COLD_SPLASH_MIN_MS = 2_500L
// Plancher réduit du FilterSplash (pré-filtre Kotlin < 1 s + setStyle 1-3 s).
private const val FILTER_SPLASH_MIN_MS = 1_000L

private fun parisCamera(zoom: Double = PARIS_ZOOM): CameraPosition =
    CameraPosition.Builder().target(PARIS).zoom(zoom).build()

// Plancher de durée du splash : on garde le voile vert affiché au moins le
// temps de lire un tip avant de flipper `arbresPrets`. Mesuré depuis le départ
// du pipeline de contenu (= 1er mount en mode normal, mount en mode filtré).
private suspend fun awaitSplashFloor(sinceMs: Long, minMs: Long) {
    val elapsed = android.os.SystemClock.elapsedRealtime() - sinceMs
    if (elapsed < minMs) delay(minMs - elapsed)
}

// Caméra de bootstrap, NON-BLOQUANTE : lecture pure du dernier fix connu de
// `LocationProvider.currentLocation` (déjà amorcé par `LocationProvider.start`
// avec un last-known si dispo), sinon Paris. Surtout PAS d'attente d'un
// `getCurrentLocation()` ici — son timeout système (~30 s en intérieur GPS
// froid) bloquerait l'appel de `map.setStyle(...)` qui le suit, donc tout le
// chargement de la carte. Le recadrage sur la position réelle est fait par le
// `LaunchedEffect` de recadrage auto dès qu'un fix arrive (cf. plus bas).
// Pas de demande de permission ici — c'est le rôle du FAB de localisation.
// Filtre `isRecentFix` : le seed de `start()` est déjà filtré, mais le
// singleton `LocationProvider` survit à la recréation d'Activity — une valeur
// d'une session précédente peut traîner dans le flow.
private fun computeInitialCamera(ctx: Context): CameraPosition {
    if (!LocationProvider.hasFineLocationPermission(ctx)) return parisCamera()
    val loc = LocationProvider.currentLocation.value?.takeIf { it.isRecentFix() }
        ?: return parisCamera()
    return CameraPosition.Builder()
        .target(LatLng(loc.latitude, loc.longitude))
        .zoom(USER_ZOOM)
        .build()
}

@OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.FlowPreview::class)
@SuppressLint("MissingPermission")
@Composable
fun MapScreen(
    onArboretumClick: () -> Unit = {},
    onRemarquablesClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onSpeciesClick: (Int) -> Unit = {},
    onGenreClick: (String) -> Unit = {},
    onRemarquableDetail: (Long) -> Unit = {},
    onFirstSpeciesCapture: (Int) -> Unit = {},
    onBack: (() -> Unit)? = null,
    /**
     * Set de sks à filtrer. `emptySet()` = mode normal (toute la carte).
     * Singleton = filtre fiche-espèce classique. Plusieurs sks = filtre genre
     * depuis la fiche genre : `{sk_sp.} ∪ {sks_du_genre_capturés}`.
     */
    filterSpecies: Set<Int> = emptySet(),
    /**
     * Holder Activity-scopé de la MapView persistante — requis en mode normal
     * (fourni par `ArbresNavHost`), ignoré/absent en mode filtré qui garde sa
     * MapView jetable locale. Cf. doc de tête de [MapHost].
     */
    mapHost: MapHost? = null,
) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as ArbresApp
    val styleUrl = stringResource(R.string.map_style_url)
    val repo = rememberArbreRepository()
    val captureRepo = rememberCaptureRepository()
    val speciesIndex = rememberSpeciesIndex()
    val speciesInfoRepo = rememberSpeciesInfoRepository()
    val genreInfoRepo = rememberGenreInfoRepository()
    val arrSpeciesIndex = rememberArrSpeciesIndex()
    val remarquableInfoRepo = rememberRemarquableInfoRepository()
    val onboardingStore = rememberOnboardingStore()
    val viewModel: MapViewModel = viewModel(
        factory = viewModelFactory {
            initializer { MapViewModel(repo, createSavedStateHandle()) }
        }
    )

    val isFiltered = filterSpecies.isNotEmpty()
    // Entry "représentative" du filtre. Singleton → l'entry directement.
    // Multi-sk (filtre genre) → l'entry `unknownSpecies` du genre (= le sk
    // `(G, sp.)` qui a déclenché la nav, présent dans le set par construction).
    // Fallback : 1re entry trouvée. La FilterBanner l'utilise pour le titre
    // (`displayNomCommun`) et le sous-titre (binôme italique conditionnel).
    val filteredEntry = if (isFiltered) {
        filterSpecies.firstNotNullOfOrNull { sk ->
            speciesIndex.get(sk)?.takeIf { it.unknownSpecies }
        } ?: filterSpecies.firstNotNullOfOrNull { sk -> speciesIndex.get(sk) }
    } else null
    // Count agrégé sur tous les sks du filtre (sommes simples, ordre de
    // grandeur cohérent — pas de doublons puisque chaque sk = 1 espèce
    // distincte). Nul si SpeciesInfo n'a pas encore résolu les stats.
    val filteredCount = if (isFiltered) {
        filterSpecies.sumOf { speciesInfoRepo.get(it)?.stats?.count ?: 0 }
            .takeIf { it > 0 }
    } else null
    // Pour le 2e label de la FilterBanner (mode genre uniquement) : « 3
    // espèces capturées + sp. » — donne du grain quand l'utilisateur visualise
    // un filtre de plusieurs espèces du même genre.
    val isGenreFilter = filterSpecies.size > 1
    // Nom (singulier) affiché par le `FilterSplash` : pour un filtre genre /
    // fiche `(G, sp.)` on prend le nom vernaculaire du genre (« Prunier »)
    // plutôt que le nv de l'entry `(G, sp.)` (« Prunier (Prunus sp.) ») ;
    // sinon le nv de l'espèce (`displayNomCommun` : nv → nomCommun → binôme).
    val splashSpeciesLabel = filteredEntry?.let { entry ->
        if (entry.unknownSpecies) {
            genreInfoRepo.get(entry.genre)?.nomFr ?: entry.displayNomCommun.substringBefore(" (")
        } else {
            entry.displayNomCommun
        }
    }

    val capturedSpecies by captureRepo.capturedSpeciesIndices()
        .collectAsState(initial = emptySet())
    val capturedRemarquables by captureRepo.capturedRemarquableIds()
        .collectAsState(initial = emptySet())

    // Mode chasse. `huntActive` est `remember`-é ici (pas dans le VM) pour
    // que le mode se ferme tout seul quand on quitte l'écran.
    // La liste des remarquables est chargée paresseusement au 1er passage en
    // mode chasse et mémorisée dans le VM (évite un re-query aux remounts).
    var huntActive by remember { mutableStateOf(false) }
    var remarquablesList by remember { mutableStateOf(viewModel.remarquablesCache) }
    LaunchedEffect(huntActive) {
        if (huntActive && remarquablesList == null) {
            val loaded = repo.arbresRemarquables()
            viewModel.remarquablesCache = loaded
            remarquablesList = loaded
        }
    }
    val density = LocalDensity.current
    var huntPanelHeightPx by remember { mutableIntStateOf(0) }
    val bottomShiftForHunt by animateDpAsState(
        targetValue = if (huntActive && huntPanelHeightPx > 0)
            with(density) { huntPanelHeightPx.toDp() } + 8.dp
        else 0.dp,
        label = "huntBottomShift",
    )

    // Mode normal : MapView persistante portée par le MapHost Activity-scopé.
    // Mode filtré : MapView jetable locale, détruite au dispose.
    val host = if (isFiltered) null else requireNotNull(mapHost) {
        "MapScreen en mode normal requiert un MapHost (cf. ArbresNavHost)"
    }
    val mapView = if (host != null) host.mapView else remember {
        MapView(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
    }
    // Refs map/style/prêt : en mode normal l'état vit dans le holder (Compose
    // state — il survit au démontage et un mount en cours de chargement voit
    // le pipeline aboutir) ; en mode filtré il est local au mount.
    var filteredMapRef by remember { mutableStateOf<MapLibreMap?>(null) }
    var filteredStyleRef by remember { mutableStateOf<Style?>(null) }
    var filteredArbresPrets by remember { mutableStateOf(false) }
    val mapRef = host?.map ?: filteredMapRef
    val styleRef = host?.style ?: filteredStyleRef
    // Pilote le splash. Remount avec carte déjà rendue (`host.pinsRendered`)
    // = zéro voile, retour instantané.
    val arbresPrets = host?.pinsRendered ?: filteredArbresPrets
    // `GeoJsonSource.setGeoJson(String)` sur une source déjà attachée — et, dans
    // une moindre mesure, le parse + clustering du ctor sur un gros corpus —
    // traitent les features EN BACKGROUND : la pose des layers rend la main bien
    // avant que les pins/clusters soient réellement rendus (1-3 s de décalage
    // observé sur device pour les 217 k features). Tant que le splash est levé,
    // on attend donc que la source ait produit des features rendues à l'écran —
    // sinon le voile s'efface sur une « carte vide ». Timeout de sécurité au cas
    // (improbable dans Paris) où le viewport ne couvre aucun arbre. Le timeout
    // ne court que quand la view est attachée et layoutée : détachée (pipeline
    // qui continue pendant l'onboarding ou une nav), elle ne rend RIEN — un
    // timeout qui courrait là flipperait `pinsRendered` sur une carte jamais
    // rendue, et le remount lèverait le voile sur une carte vide.
    suspend fun awaitArbresRendered(map: MapLibreMap, timeoutMs: Long) {
        var visibleElapsedMs = 0L
        while (visibleElapsedMs < timeoutMs) {
            if (mapView.isAttachedToWindow && mapView.width > 0) {
                val screen = RectF(0f, 0f, mapView.width.toFloat(), mapView.height.toFloat())
                val rendered = map.queryRenderedFeatures(
                    screen, POINTS_LAYER_ID, CLUSTERS_LAYER_ID,
                )
                if (rendered.isNotEmpty()) return
                visibleElapsedMs += 120
            }
            delay(120)
        }
    }
    // Vrai dès que l'utilisateur a bougé/redirigé la caméra (geste de carte ou
    // tap sur un cluster) — coupe le recadrage GPS auto pour ne pas le contrarier.
    var userMovedCamera by remember { mutableStateOf(false) }
    // Cleanup du bridge MapLibre LocationEngine → LocationProvider (cf.
    // `attachMapLibreLocationBridge`) — extrait pour être appelable depuis
    // `onDispose` du MapView.
    var maplibreLocationCleanup by remember { mutableStateOf<(() -> Unit)?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // Vrai pendant la fenêtre [grant permission GPS → 1er fix]. Pilote le pulse
    // du FAB GPS et la snackbar « Localisation en cours… ». Volontairement
    // non persisté : un process death pendant l'attente repart de zéro et le
    // user re-tap si besoin.
    var awaitingFirstFix by remember { mutableStateOf(false) }

    // Coloration des pins, mode FILTRÉ seulement. En mode normal, cet observer
    // et l'enrichment clusters sont holder-scoped (`launchDiscoveryObservers`,
    // lancés une fois par le pipeline d'init) : ils continuent de tourner
    // pendant que `MapScreen` est démonté — au retour d'une capture, la carte
    // est le plus souvent déjà à jour.
    LaunchedEffect(styleRef) {
        if (host != null) return@LaunchedEffect
        val style = styleRef ?: return@LaunchedEffect
        combine(
            captureRepo.capturedSpeciesIndices(),
            captureRepo.capturedRemarquableIds(),
        ) { species, remarquables -> species to remarquables }
            .collect { (species, remarquables) ->
                // Capturer une espèce identifiée d'un genre déverrouille les
                // pins (G, sp.) du même genre (verts). Cohérent avec
                // l'auto-débloquage genre-based appliqué côté Arboretum.
                applyDiscoveryColor(
                    style,
                    speciesIndex.effectivelyCapturedSpecies(species),
                    remarquables,
                )
            }
    }

    fun enableLocationPin(map: MapLibreMap, style: Style) {
        if (!LocationProvider.hasFineLocationPermission(ctx)) return
        val component = map.locationComponent
        if (!component.isLocationComponentActivated) {
            component.activateLocationComponent(
                LocationComponentActivationOptions.builder(ctx, style)
                    .locationComponentOptions(
                        LocationComponentOptions.builder(ctx)
                            .pulseEnabled(true)
                            // Cône de vision boussole (cf. doc du drawable).
                            .bearingDrawable(R.drawable.ic_location_cone)
                            // Z-order déterministe : la stack du puck est posée
                            // juste au-dessus de la layer arbres la plus haute,
                            // quel que soit le timing d'activation (défaut =
                            // top-of-stack à l'activation, timing-dépendant).
                            // PRÉCONDITION DURE : `CLUSTER_COUNT_LAYER_ID` doit
                            // exister à l'appel, sinon MapLibre abort natif
                            // (PendingJavaException → SIGABRT, pas d'exception
                            // Kotlin catchable). Chaque appelant DOIT donc passer
                            // un style post-`addArbresLayers` — vérifié sur les
                            // deux modes (principal : DisposableEffect(styleRef) ;
                            // filtré : après le addArbresLayers du scope.launch).
                            .layerAbove(CLUSTER_COUNT_LAYER_ID)
                            .build()
                    )
                    .useDefaultLocationEngine(true)
                    .build()
            )
        }
        component.isLocationComponentEnabled = true
        component.cameraMode = CameraMode.NONE
        // COMPASS : MapLibre instancie son CompassEngine interne (capteurs
        // rotation-vector) et oriente le bearingDrawable. Listener capteur
        // retiré avec `isLocationComponentEnabled = false` au dispose — pas
        // de drain boussole hors-carte.
        component.renderMode = RenderMode.COMPASS
        // Au 1er lancement post-onboarding, notre `LocationListener` propre
        // ne reçoit pas d'updates pendant ~10 s alors que le `LocationEngine`
        // de MapLibre reçoit des fix dès t≈1 s. On consomme SA source — élimine
        // le bug « Active le GPS » au 1er run et garantit zéro drift entre le
        // pin user et la distance utilisée pour `captureAvailability`.
        if (maplibreLocationCleanup == null) {
            maplibreLocationCleanup = attachMapLibreLocationBridge(component)
        }
    }

    fun recenterOn(loc: Location) {
        mapRef?.animateCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), USER_ZOOM)
        )
        mapRef?.style?.let { enableLocationPin(mapRef!!, it) }
    }

    // FAB « Me localiser » : fix frais en main → recentrage direct ; sinon
    // bascule en attente du 1er fix frais (pulse FAB + snackbar + recentrage,
    // cf. LaunchedEffect `awaitingFirstFix`). Pas de fallback last-known :
    // recentrer sur la position d'hier est pire qu'attendre ~1 s un fix réel.
    fun centerOnUser() {
        val loc = LocationProvider.currentLocation.value?.takeIf { it.isRecentFix() }
        if (loc != null) recenterOn(loc) else awaitingFirstFix = true
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            LocationProvider.start(ctx)
            // Bascule le FAB en mode pulse jusqu'au 1er fix frais, qui
            // recentrera la carte (cf. LaunchedEffect ci-dessous).
            awaitingFirstFix = true
        } else {
            scope.launch { snackbar.showSnackbar("Permission de localisation refusée") }
        }
    }

    // Observer du 1er fix frais (post-grant permission, ou FAB tapé sans fix
    // récent en main). Tant que `awaitingFirstFix` est vrai, on affiche une
    // snackbar « Localisation en cours… » et on attend un fix frais avec
    // timeout 30 s — puis on recentre dessus. Au-delà, on stoppe le pulse et
    // on affiche un warning — le téléphone est probablement en intérieur,
    // GPS désactivé ou capteur HS.
    LaunchedEffect(awaitingFirstFix) {
        if (!awaitingFirstFix) return@LaunchedEffect
        val snackJob = launch {
            showSnackbarFor(snackbar, "Localisation en cours…")
        }
        val fix = withTimeoutOrNull(30_000) {
            LocationProvider.currentLocation.filterNotNull().first { it.isRecentFix() }
        }
        awaitingFirstFix = false
        snackJob.cancel()
        snackbar.currentSnackbarData?.dismiss()
        if (fix == null) {
            showSnackbarFor(snackbar, "GPS indisponible — sors à découvert")
        } else {
            recenterOn(fix)
        }
    }

    // Saut vers un arbre exact : depuis une fiche (remarquable, espèce, genre)
    // on pose `MapHost.pendingPulseArbreId` puis on revient à l'entrée MAP en
    // launchSingleTop (cf. ArbresNavHost). Intent ONE-SHOT : consommé ici dès
    // le fly-to lancé, un retour ultérieur sur la carte ne rejoue rien. Au
    // mount, on attend que la map ET les layers soient prêtes, puis fly-to
    // ~600 ms à zoom élevé (z20) pour qu'aucun doute ne subsiste sur le pin
    // ciblé, et au callback `onFinish` on déclenche le pulse — pas d'ouverture
    // du sheet, l'utilisateur tape l'arbre lui-même s'il veut la fiche.
    // La consommation (write `pendingPulseArbreId = null`) relance l'effet,
    // qui early-return : elle doit donc rester APRÈS le lookup suspend et
    // l'`animateCamera` (fire-and-forget, son callback vit sur la map).
    LaunchedEffect(host?.pendingPulseArbreId, mapRef, styleRef, arbresPrets) {
        val id = host?.pendingPulseArbreId ?: return@LaunchedEffect
        val map = mapRef ?: return@LaunchedEffect
        val style = styleRef ?: return@LaunchedEffect
        if (!arbresPrets) return@LaunchedEffect
        // L'arbre ciblé peut être hors subset d'un filtre rapide actif :
        // l'intent « voir cet arbre » prime — on défiltre, le runner
        // re-pousse le corpus complet pendant le fly-to.
        host.quickFilter = null
        // Un saut volontaire vaut placement caméra : neutralise le recadrage
        // GPS auto qui le contrarierait au 1er fix.
        host.autoRecenterDone = true
        val arbre = repo.arbreParId(id)
        if (arbre == null) {
            host.pendingPulseArbreId = null
            return@LaunchedEffect
        }
        val target = LatLng(arbre.latitude, arbre.longitude)
        map.animateCamera(
            CameraUpdateFactory.newLatLngZoom(target, 20.0),
            600,
            object : MapLibreMap.CancelableCallback {
                override fun onCancel() = Unit
                override fun onFinish() {
                    addOrUpdatePulseSource(style, target.latitude, target.longitude)
                    animatePulse(style)
                }
            },
        )
        host.pendingPulseArbreId = null
    }

    // Fly-to centroïde d'arrondissement depuis la Recherche universelle.
    // Animé à z13 (l'arr entier rentre dans le viewport) sans halo : un pulse
    // à 1 km de diamètre visuel n'aurait pas de cible ponctuelle à marquer.
    LaunchedEffect(viewModel.pendingArrFlyTo, mapRef) {
        val target = viewModel.pendingArrFlyTo ?: return@LaunchedEffect
        val map = mapRef ?: return@LaunchedEffect
        val (lon, lat) = target
        map.animateCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(lat, lon), 13.0),
            700,
            null,
        )
        viewModel.consumeArrFlyTo()
    }

    // Recadrage GPS auto au 1er fix FRAIS. `computeInitialCamera` étant
    // non-bloquant, sur un install frais (ou GPS froid en intérieur) la carte
    // démarre sur Paris ; dès qu'un fix frais arrive on recentre dessus à
    // zoom 16 — sauf si : mode filtré, saut vers un arbre en attente
    // (`pendingPulseArbreId`), ou l'utilisateur a déjà bougé la caméra.
    // `isRecentFix` est essentiel : sans lui, un last-known de la veille
    // encore dans le flow serait consommé par le `first()` et grillerait le
    // recadrage — la carte resterait sur la position d'hier. Ne tire qu'une
    // fois par vie d'Activity (`host.autoRecenterDone`), jamais au remount —
    // la caméra de l'utilisateur est sacrée. La tentative est consommée dès
    // son départ : annulée par une nav avant le 1er fix, elle n'est pas
    // rejouée au retour (même contrat que l'ancien `freshMount`).
    LaunchedEffect(mapRef) {
        val map = mapRef ?: return@LaunchedEffect
        if (host == null || host.autoRecenterDone || host.pendingPulseArbreId != null) {
            return@LaunchedEffect
        }
        // Gate onboarding AVANT de consommer la tentative : au tout premier
        // lancement, `startDestination` étant la constante MAP, une instance
        // transiente monte sous le splash puis est purgée par la redirection
        // WELCOME — si elle consommait `autoRecenterDone` (getMapAsync répond
        // avant la redirection), le remount post-onboarding ne recadrait plus
        // jamais : carte sur Paris jusqu'au FAB. Suspend tant que l'onboarding
        // n'est pas fait — la transiente est démontée avec l'effet sans rien
        // griller ; l'instance stable passe dès le commit de `markDone()` (~ms).
        onboardingStore.onboardingDone.first { it }
        if (host.autoRecenterDone) return@LaunchedEffect
        host.autoRecenterDone = true
        val fix = LocationProvider.currentLocation.filterNotNull().first { it.isRecentFix() }
        if (userMovedCamera) return@LaunchedEffect
        map.animateCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(fix.latitude, fix.longitude), USER_ZOOM)
        )
        map.style?.let { enableLocationPin(map, it) }
    }

    // Hit-test partagé entre les deux modes : clusters d'abord (tap → zoom
    // d'expansion), puis pins (tap → fiche). Closure sur l'état du mount
    // courant (`userMovedCamera`, `viewModel`).
    fun handleMapClick(map: MapLibreMap, latLng: LatLng): Boolean {
        val pixel = map.projection.toScreenLocation(latLng)
        val touch = RectF(pixel.x - 20f, pixel.y - 20f, pixel.x + 20f, pixel.y + 20f)

        val clusters = map.queryRenderedFeatures(touch, CLUSTERS_LAYER_ID)
        if (clusters.isNotEmpty()) {
            userMovedCamera = true
            val source = map.style?.getSourceAs<GeoJsonSource>(ARBRES_SOURCE_ID)
            val zoom = source?.getClusterExpansionZoom(clusters.first())?.toDouble()
                ?: (map.cameraPosition.zoom + 2.0)
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, zoom))
            return true
        }

        val points = map.queryRenderedFeatures(touch, POINTS_LAYER_ID)
        val id = points.firstOrNull()?.getNumberProperty("id")?.toLong()
        return if (id != null) {
            viewModel.openDetail(id)
            true
        } else {
            false
        }
    }

    if (host != null) {
        // ——— Mode normal : MapView persistante (cf. doc de tête de MapHost) ———
        // Le cycle GL de la view est relayé depuis l'Activity par le holder ;
        // ici on ne gère que ce qui est lié au mount de l'écran.

        // GPS actif uniquement quand la carte est à l'écran ; le rendu de la
        // view persistante est gelé pendant l'absence (cf. MapHost.screenAttached).
        DisposableEffect(Unit) {
            android.util.Log.i(
                "MapScreen",
                "mount normal (initStarted=${host.contentInitStarted}, " +
                    "pinsRendered=${host.pinsRendered})",
            )
            host.screenAttached()
            LocationProvider.start(ctx)
            onDispose {
                android.util.Log.i("MapScreen", "dispose normal")
                LocationProvider.stop()
                host.screenDetached()
            }
        }

        // Init contenu one-shot (caméra initiale, style, layers, push GeoJSON),
        // dans le scope du holder : survit à une nav pendant le chargement —
        // le pipeline continue d'avancer pendant l'absence et les remounts se
        // raccordent à `host.map` / `host.style` / `host.pinsRendered` au lieu
        // de relancer. Aucune référence au ViewModel ni à l'état per-mount ici
        // (le mount qui a lancé l'init peut être mort quand le style aboutit).
        LaunchedEffect(Unit) {
            if (host.contentInitStarted) return@LaunchedEffect
            host.contentInitStarted = true
            val tStart = android.os.SystemClock.elapsedRealtime()
            val tProcess = app.processStartElapsedMs
            android.util.Log.i(
                "MapScreen",
                "MapView init (process+${tStart - tProcess}ms)",
            )
            mapView.getMapAsync { map ->
                host.map = map
                // Rotation bloquée : la boussole en edge-to-edge se retrouve sous
                // l'inset status bar et devient intappable. Sans rotation libre,
                // la boussole n'a plus de raison d'être — d'où `isCompassEnabled`.
                map.uiSettings.isRotateGesturesEnabled = false
                map.uiSettings.isCompassEnabled = false
                // One-shot par vie du holder : entre deux mounts, la caméra
                // persiste dans la view elle-même — rien à restaurer ici.
                map.cameraPosition = computeInitialCamera(ctx)

                map.setStyle(Style.Builder().fromUri(styleUrl)) { style ->
                    val tStyle = android.os.SystemClock.elapsedRealtime()
                    android.util.Log.i(
                        "MapScreen",
                        "Style prêt (process+${tStyle - tProcess}ms)",
                    )
                    host.scope.launch {
                        try {
                            // Cold-start global, 2-passes : on pose les layers
                            // sur une source VIDE (instantané), puis on injecte
                            // les 217 k features via `setArbresGeoJson` — qui
                            // parse + cluster en background et ne bloque pas le
                            // UI thread. Le voile reste PLEINEMENT OPAQUE jusqu'à
                            // ce que les pins/clusters soient réellement rendus
                            // (`awaitArbresRendered` plus bas). Si on flippait
                            // `pinsRendered` avant que `setGeoJson` n'ait fini
                            // de parser, le voile s'effacerait sur une carte
                            // vide pendant 1-3 s, le temps du parse async.
                            addArbresLayers(style, EMPTY_GEOJSON)
                            host.style = style
                            launchDiscoveryObservers(host, app, captureRepo, speciesIndex, style)
                            val tEmpty = android.os.SystemClock.elapsedRealtime()
                            android.util.Log.i(
                                "MapScreen",
                                "Layers vides posées (process+${tEmpty - tProcess}ms)",
                            )
                            // Si on a déjà un GeoJSON enrichi cached (process
                            // survivant à la mort du holder, ex. rotation), on
                            // le pose direct — pins ET clusters bons d'un coup,
                            // 1 seul freeze UI. Sinon (cold-start fresh), on
                            // pose le rawJson nu pour que les pins apparaissent
                            // ASAP (~700 ms) ; le LaunchedEffect mid-session
                            // déboucera l'enrichment ~1 s plus tard et
                            // re-poussera l'enrichi en 2e wave. Enrich des
                            // 217 k features = ~5-15 s sur device, trop
                            // coûteux pour bloquer le 1er paint.
                            val cached = app.enrichedGeoJson.value
                            val initialJson = cached ?: app.arbresGeoJsonAsync.await()
                            val tJson = android.os.SystemClock.elapsedRealtime()
                            android.util.Log.i(
                                "MapScreen",
                                "GeoJSON ${if (cached != null) "(cache enrichi)" else "(raw)"} disponible " +
                                    "(process+${tJson - tProcess}ms, ${initialJson.length / 1_000_000}Mo)",
                            )
                            setArbresGeoJson(style, initialJson)
                            val tLayers = android.os.SystemClock.elapsedRealtime()
                            android.util.Log.i(
                                "MapScreen",
                                "GeoJSON poussé dans la source (process+${tLayers - tProcess}ms, parse async en cours)",
                            )
                            // Le setGeoJson ci-dessus parse + cluster en
                            // background : on attend que les pins/clusters
                            // soient effectivement rendus avant de baisser
                            // le voile (sinon « carte vide » 1-3 s).
                            awaitArbresRendered(map, 10_000)
                            val tRendered = android.os.SystemClock.elapsedRealtime()
                            android.util.Log.i(
                                "MapScreen",
                                "Arbres rendus à l'écran (process+${tRendered - tProcess}ms, total cold start)",
                            )
                            // Plancher : pleine durée uniquement en cold-start
                            // fresh ; init sur cache enrichi → flip direct.
                            if (cached == null) awaitSplashFloor(tStart, COLD_SPLASH_MIN_MS)
                            host.pinsRendered = true
                        } catch (e: Throwable) {
                            android.util.Log.e("MapScreen", "Échec chargement arbres", e)
                        } finally {
                            // Ne jamais rester coincé sous le splash si une étape a
                            // échoué (OOM possible au parse du GeoJSON). Idempotent
                            // si le chemin nominal a déjà flippé.
                            host.pinsRendered = true
                        }
                    }
                }
            }
        }

        // Listeners d'interaction per-mount — add au mount, remove au dispose
        // (leurs closures capturent le ViewModel et l'état du mount courant).
        DisposableEffect(mapRef) {
            val map = mapRef
            if (map == null) {
                onDispose {}
            } else {
                val clickListener = MapLibreMap.OnMapClickListener { latLng ->
                    handleMapClick(map, latLng)
                }
                // Geste utilisateur sur la carte → coupe le recadrage GPS auto.
                val moveListener = MapLibreMap.OnCameraMoveStartedListener { reason ->
                    if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                        userMovedCamera = true
                    }
                }
                map.addOnMapClickListener(clickListener)
                map.addOnCameraMoveStartedListener(moveListener)
                onDispose {
                    map.removeOnMapClickListener(clickListener)
                    map.removeOnCameraMoveStartedListener(moveListener)
                }
            }
        }

        // Pin user + bridge GPS MapLibre re-attachés à chaque mount, détachés
        // au dispose — pas de fix MapLibre consommé en arrière-plan sur les
        // autres écrans. Le LocationComponent est aussi désactivé au dispose :
        // son pulse (`pulseEnabled`) est un ValueAnimator infini main-thread
        // qui continuerait d'invalider la carte gelée. `enableLocationPin` le
        // réactive au mount suivant. Au 1er run le style n'est pas prêt au
        // mount : l'effet re-tire quand `styleRef` se peuple.
        DisposableEffect(mapRef, styleRef) {
            val map = mapRef
            val style = styleRef
            if (map != null && style != null && LocationProvider.hasFineLocationPermission(ctx)) {
                enableLocationPin(map, style)
            }
            onDispose {
                maplibreLocationCleanup?.invoke()
                maplibreLocationCleanup = null
                map?.locationComponent
                    ?.takeIf { it.isLocationComponentActivated }
                    ?.isLocationComponentEnabled = false
            }
        }
    } else {
        // ——— Mode filtré : MapView jetable locale, pipeline single-pass ———
        // Comportement pré-MapHost intact : cycle GL relayé depuis le mount,
        // tout meurt au dispose.
        DisposableEffect(Unit) {
            val tStart = android.os.SystemClock.elapsedRealtime()
            val tProcess = app.processStartElapsedMs
            android.util.Log.i(
                "MapScreen",
                "MapView init (process+${tStart - tProcess}ms, filtered)",
            )
            LocationProvider.start(ctx)
            mapView.onCreate(null)
            mapView.onStart()
            mapView.onResume()
            mapView.getMapAsync { map ->
                filteredMapRef = map
                // Mêmes réglages UI que le mode normal (cf. commentaire là-bas).
                map.uiSettings.isRotateGesturesEnabled = false
                map.uiSettings.isCompassEnabled = false
                map.cameraPosition = parisCamera(PARIS_OVERVIEW_ZOOM)

                map.setStyle(Style.Builder().fromUri(styleUrl)) { style ->
                    val tStyle = android.os.SystemClock.elapsedRealtime()
                    android.util.Log.i(
                        "MapScreen",
                        "Style prêt (process+${tStyle - tProcess}ms)",
                    )
                    scope.launch {
                        try {
                            // Single-pass : GeoJSON filtré < 1 Mo (~38 k
                            // features pour Platanus max), le freeze
                            // d'`addArbresLayers` reste imperceptible.
                            // En mode genre (set de N sks), le total reste
                            // largement < 1 Mo car limité aux espèces
                            // capturées du genre + le sp. — soit un sous-
                            // ensemble strict de la fiche-espèce dominante.
                            val rawJson = app.arbresGeoJsonAsync.await()
                            val tJson = android.os.SystemClock.elapsedRealtime()
                            android.util.Log.i(
                                "MapScreen",
                                "GeoJSON disponible (process+${tJson - tProcess}ms, ${rawJson.length / 1_000_000}Mo)",
                            )
                            // Enrichi aussi en mode filtré pour que les
                            // clusters d'espèce reflètent la progression.
                            // Coût négligeable sur < 1 Mo.
                            val initialCaptures = withTimeoutOrNull(2000) {
                                combine(
                                    captureRepo.capturedSpeciesIndices(),
                                    captureRepo.capturedRemarquableIds(),
                                ) { s, r -> s to r }.first()
                            } ?: (emptySet<Int>() to emptySet<Long>())
                            // Étend le set de captures aux sp. genre-débloqués
                            // pour la coloration (les pins (G, sp.) de la
                            // fiche genre apparaissent verts).
                            val effectiveSpecies =
                                speciesIndex.effectivelyCapturedSpecies(initialCaptures.first)
                            val json = withContext(Dispatchers.Default) {
                                enrichGeoJsonWithDiscovery(
                                    filterGeoJsonBySpecies(
                                        rawJson,
                                        filterSpecies,
                                        initialCaptures.second,
                                    ),
                                    effectiveSpecies,
                                    initialCaptures.second,
                                )
                            }.also { filtered ->
                                val tFilter = android.os.SystemClock.elapsedRealtime()
                                android.util.Log.i(
                                    "MapScreen",
                                    "GeoJSON filtré sks=$filterSpecies (process+${tFilter - tProcess}ms, ${filtered.length / 1024}ko)",
                                )
                            }
                            addArbresLayers(style, json)
                            filteredStyleRef = style
                            // Pin user APRÈS addArbresLayers, jamais avant : le
                            // puck est posé via layerAbove(CLUSTER_COUNT_LAYER_ID),
                            // qui DOIT déjà exister — sinon MapLibre lève côté
                            // natif (PendingJavaException dans
                            // onDidFinishLoadingStyle → abort/SIGABRT). La carte
                            // principale tient cette précondition via son
                            // DisposableEffect(styleRef) post-init ; ici c'était
                            // appelé synchroniquement dans le callback setStyle,
                            // avant ce addArbresLayers async → crash systématique
                            // sur « Voir sur la carte » avec permission accordée.
                            if (LocationProvider.hasFineLocationPermission(ctx)) {
                                enableLocationPin(map, style)
                            }
                            val tLayers = android.os.SystemClock.elapsedRealtime()
                            android.util.Log.i(
                                "MapScreen",
                                "Layers posées (process+${tLayers - tProcess}ms, total filtered)",
                            )
                            // Attendre le rendu effectif (le clustering de la
                            // source filtrée peut traîner un peu), puis petit
                            // plancher : pré-filtre + setStyle sont souvent
                            // < 1 s, le voile flasherait sinon.
                            awaitArbresRendered(map, 6_000)
                            awaitSplashFloor(tStart, FILTER_SPLASH_MIN_MS)
                            filteredArbresPrets = true
                        } catch (e: Throwable) {
                            android.util.Log.e("MapScreen", "Échec chargement arbres", e)
                        } finally {
                            // Ne jamais rester coincé sous le splash si une étape
                            // a échoué. Idempotent si le chemin nominal a flippé.
                            filteredArbresPrets = true
                        }
                    }
                }

                map.addOnCameraMoveStartedListener { reason ->
                    if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                        userMovedCamera = true
                    }
                }
                map.addOnMapClickListener { latLng -> handleMapClick(map, latLng) }
            }
            onDispose {
                maplibreLocationCleanup?.invoke()
                maplibreLocationCleanup = null
                mapView.onPause()
                mapView.onStop()
                mapView.onDestroy()
                LocationProvider.stop()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Garde défensive : un View ne peut avoir qu'un parent — si la view
        // persistante est encore accrochée ailleurs (l'entrée MAP est unique
        // depuis le launchSingleTop des sauts pulse, mais un teardown tardif
        // reste possible), l'entrante la vole.
        AndroidView(factory = { mapView.also { v -> (v.parent as? ViewGroup)?.removeView(v) } })
        CaptureCelebrationOverlay(
            captureRepo = captureRepo,
            mapRef = mapRef,
            speciesIndex = speciesIndex,
        )
        if (filteredEntry != null && onBack != null) {
            // Sous-titre additionnel en mode genre (set > 1 sks) pour donner
            // du grain — combien d'espèces du genre sont capturées sur le
            // total identifié.
            val genreSubtitle = if (isGenreFilter) {
                val genreEntries = speciesIndex.entriesOfGenre(filteredEntry.genre)
                val totalGenre = genreEntries.count { !it.unknownSpecies }
                val capturedInSet = filterSpecies.count { sk ->
                    speciesIndex.get(sk)?.unknownSpecies == false
                }
                if (totalGenre > 0) "$capturedInSet / $totalGenre espèces du genre" else null
            } else null
            FilterBanner(
                entry = filteredEntry,
                count = filteredCount,
                genreSubtitle = genreSubtitle,
                onBack = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(16.dp),
            )
        } else {
            // Top-start : bandeau du filtre rapide quand il est actif (le FAB
            // Recherche cède son slot — chercher pendant un filtre est
            // marginal, le ✕ rend la carte entière d'abord), sinon 🔍
            // Recherche universelle (discret). Le banner reste affiché sur le
            // filtre désiré OU appliqué : au défiltrage, c'est l'appliqué qui
            // le maintient (avec spinner) jusqu'au re-push du corpus complet.
            val desiredQuickFilter = host?.quickFilter
            val appliedQuickFilter = host?.appliedQuickFilter
            val bannerQuickFilter = desiredQuickFilter ?: appliedQuickFilter
            if (bannerQuickFilter != null) {
                val quickFilterCount = remember(bannerQuickFilter) {
                    bannerQuickFilter.sks
                        .sumOf { speciesInfoRepo.get(it)?.stats?.count ?: 0 }
                        .takeIf { it > 0 }
                }
                QuickFilterBanner(
                    label = bannerQuickFilter.label,
                    count = quickFilterCount,
                    // Filtrage ou défiltrage en cours : spinner à la place
                    // du ✕ tant que la source ne contient pas le désiré.
                    busy = desiredQuickFilter?.sks != appliedQuickFilter?.sks,
                    onClear = { host?.quickFilter = null },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(16.dp),
                )
            } else {
                UtilityFab(
                    onClick = {
                        viewModel.openSearch(
                            speciesIndex = speciesIndex,
                            genreInfo = genreInfoRepo,
                            arrIndex = arrSpeciesIndex,
                            captureRepo = captureRepo,
                        )
                    },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(16.dp),
                ) {
                    Icon(Icons.Outlined.Search, contentDescription = "Recherche")
                }
            }
            // Pile bottom-end (haut → bas) : Remarquables, Arboretum, Profil.
            // Le shift `bottomShiftForHunt` la fait grimper au-dessus du HuntPanel.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(16.dp)
                    .padding(bottom = bottomShiftForHunt),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FloatingActionButton(onClick = onRemarquablesClick) {
                    // Tint Unspecified pour préserver la bichromie orange/crème
                    // de l'asset — la couleur *est* le sens, alignée sur le pin.
                    Icon(
                        painter = painterResource(R.drawable.ic_remarquable_badge),
                        contentDescription = "Remarquables",
                        tint = androidx.compose.ui.graphics.Color.Unspecified,
                    )
                }
                FloatingActionButton(onClick = onArboretumClick) {
                    Icon(
                        Icons.AutoMirrored.Outlined.MenuBook,
                        contentDescription = "Arboretum",
                        tint = MaterialTheme.arbresColors.feuilleSombre,
                    )
                }
                FloatingActionButton(onClick = onProfileClick) {
                    Icon(Icons.Outlined.Person, contentDescription = "Profil")
                }
            }
            if (!huntActive) {
                FloatingActionButton(
                    onClick = { huntActive = !huntActive },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(16.dp),
                ) {
                    Icon(
                        Icons.Outlined.Star,
                        contentDescription = "Chasse aux remarquables",
                        tint = MaterialTheme.arbresColors.remarquableOrange,
                    )
                }
            }
        }
        // Pulse 1.0 → 1.12 → 1.0 pendant `awaitingFirstFix`, piloté `withFrameNanos`
        // (cf. `ui/common/FrameClock.kt`) — vivant même à échelle d'animation système 0,
        // là où `rememberInfiniteTransition` se fige. `Modifier.scale` affecte le draw,
        // pas le layout, donc la hitbox du FAB reste stable.
        val pulseP by rememberFramePingPong(periodMs = 1_600)
        val pulseScale = if (awaitingFirstFix) 1f + pulseP * 0.12f else 1f
        UtilityFab(
            onClick = {
                if (LocationProvider.hasFineLocationPermission(ctx)) {
                    centerOnUser()
                } else {
                    permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(16.dp)
                .scale(pulseScale),
        ) {
            Icon(Icons.Outlined.MyLocation, contentDescription = "Me localiser")
        }

        if (huntActive && !isFiltered) {
            HuntPanel(
                remarquables = remarquablesList,
                capturedIds = capturedRemarquables,
                resolveName = { arbre ->
                    speciesIndex.indexOf(arbre)?.let { speciesIndex.get(it)?.displayNomCommun }
                        ?: arbre.nomAffichage
                },
                resolveQualification = { remarquableInfoRepo.get(it.id)?.qualification },
                onClose = { huntActive = false },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .onSizeChanged { huntPanelHeightPx = it.height },
            )
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomShiftForHunt),
        )

        // Splash : reste au-dessus de la carte tant que les layers d'arbres
        // ne sont pas posées. Couleurs/icône alignées avec le splash natif
        // (themes.xml) pour transition sans flicker.
        AnimatedVisibility(
            visible = !arbresPrets,
            enter = androidx.compose.animation.EnterTransition.None,
            exit = fadeOut(animationSpec = tween(durationMillis = MaterialTheme.arbresMotion.short)),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (splashSpeciesLabel != null) {
                FilterSplash(speciesLabel = splashSpeciesLabel)
            } else {
                ColdStartSplash()
            }
        }

        val capturer = rememberCaptureController(
            viewModel = viewModel,
            captureRepo = captureRepo,
            speciesIndex = speciesIndex,
            snackbar = snackbar,
            callbacks = CaptureCallbacks(
                onFirstSpeciesCapture = { sk ->
                    viewModel.closeDetail()
                    onFirstSpeciesCapture(sk)
                },
                onCelebrationTransitionStart = { sk ->
                    // Jamais de voile en mode filtré (host == null) : pas de nav
                    // vers la fiche espèce depuis MAP_FILTERED, on resterait
                    // coincé sous le voile jusqu'au timeout.
                    if (host != null) {
                        // `closeDetail()` (sortie de composition) détruit la
                        // fenêtre de la ModalBottomSheet instantanément, dans la
                        // même frame que la levée du voile. Surtout pas
                        // `sheetState.hide()` : la fenêtre dialog de la sheet vit
                        // au-dessus de TOUT le contenu de l'Activity, voile compris.
                        viewModel.closeDetail()
                        host.captureTransitionSk = sk
                    }
                },
                onCelebrationTransitionAbort = { host?.captureTransitionSk = null },
            ),
        )

        val openedArbre = viewModel.openedArbre
        if (openedArbre != null) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            val haptic = LocalHapticFeedback.current
            // Tic LongPress au mount du sheet : la transition pin → fiche
            // mérite un retour kinesthésique. Keyé sur `openedArbre.id` pour
            // refire à chaque nouveau pin sélectionné sans recompositions
            // parasites (la lambda n'est appelée qu'à la transition d'id).
            LaunchedEffect(openedArbre.id) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            val sk = speciesIndex.indexOf(openedArbre)
            // Aligné avec la coloration des pins qui utilise
            // `effectivelyCapturedSpecies` (auto-débloquage genre-based).
            // Sinon, un pin (G, sp.) débloqué indirectement (capture d'une
            // identifiée du genre) serait vert sur la carte mais le sheet
            // afficherait UnknownContent — incohérent.
            val isDiscovered = if (openedArbre.remarquable) {
                openedArbre.id in capturedRemarquables
            } else {
                sk != null && speciesIndex.isDiscovered(sk, capturedSpecies)
            }
            val capturesArbre by captureRepo.capturesPourArbre(openedArbre.id)
                .collectAsState(initial = emptyList())
            // Toutes les captures user — léger (Flow Room déjà cached côté
            // app), nécessaire seulement pour calculer si une suppression
            // re-verrouillerait l'espèce (cf. computeDeleteContext).
            val allCaptures by captureRepo.toutesLesCaptures()
                .collectAsState(initial = emptyList())
            val photoFiles = remember(capturesArbre) {
                capturesArbre.map { it.resolvedFile(ctx) }
            }
            var lightboxIndex by remember(openedArbre.id) { mutableStateOf<Int?>(null) }
            var pendingDeleteIndex by remember(openedArbre.id) { mutableStateOf<Int?>(null) }
            // `captureAvailability` lit le flow `currentLocation` filtré sur
            // âge (non-bloquant) ; recompute live à chaque émission pour que
            // « Active le GPS » bascule vers « Capturer » dès le 1er fix.
            val currentLocation by LocationProvider.currentLocation.collectAsState()
            val availability = remember(openedArbre.id, currentLocation) {
                captureAvailability(openedArbre)
            }
            val info = sk?.let { speciesInfoRepo.get(it) }
            val remarquableInfo = if (openedArbre.remarquable) {
                remarquableInfoRepo.get(openedArbre.id)
            } else null
            // Filtres rapides : pin non remarquable découvert, carte normale
            // (host) seulement. Le label est figé ici (nv espèce / nom du
            // genre) pour le QuickFilterBanner. La caméra ne bouge ni au
            // filtrage ni au défiltrage — le cadrage de l'utilisateur est
            // sacré, le subset se découvre en dézoomant soi-même.
            val quickFilterEntry = if (host != null && !openedArbre.remarquable &&
                sk != null && isDiscovered
            ) speciesIndex.get(sk) else null
            val genreFilterSks = quickFilterEntry?.let {
                speciesIndex.genreFilterSet(it.genre, capturedSpecies)
            } ?: emptySet()
            fun applyQuickFilter(sks: Set<Int>, label: String) {
                viewModel.closeDetail()
                host?.quickFilter = QuickFilter(sks, label)
            }
            ModalBottomSheet(
                onDismissRequest = { viewModel.closeDetail() },
                sheetState = sheetState,
            ) {
                // Cycle Catalogue : préfère le `nv` de l'entrée species-index
                // (« Chêne pédonculé ») au binôme nu (« Quercus robur »). Sur
                // asset legacy, fallback transparent vers `arbre.nomAffichage`.
                val displayName = sk?.let { speciesIndex.get(it)?.displayNomCommun }
                    ?: openedArbre.nomAffichage
                ArbreDetailContent(
                    state = ArbreDetailState(
                        arbre = openedArbre,
                        isDiscovered = isDiscovered,
                        displayName = displayName,
                        photoFiles = photoFiles,
                        medianHeightM = info?.stats?.medianHeightM,
                        medianCircCm = info?.stats?.medianCircCm,
                        remarquableInfo = remarquableInfo,
                        captureAvailability = availability,
                    ),
                    actions = ArbreDetailActions(
                        onPhotoClick = { idx -> lightboxIndex = idx },
                        onPhotoLongClick = { idx -> pendingDeleteIndex = idx },
                        onCapturer = { capturer(openedArbre) },
                        onSpeciesClick = if (sk != null && speciesIndex.isDiscovered(sk, capturedSpecies)) {
                            {
                                viewModel.closeDetail()
                                onSpeciesClick(sk)
                            }
                        } else null,
                        onRemarquableClick = if (openedArbre.remarquable &&
                            openedArbre.id in capturedRemarquables
                        ) {
                            {
                                viewModel.closeDetail()
                                onRemarquableDetail(openedArbre.id)
                            }
                        } else null,
                        onFilterSpecies = quickFilterEntry?.let { entry ->
                            { applyQuickFilter(setOf(entry.index), entry.displayNomCommun) }
                        },
                        // Masqué quand le set genre se réduirait au même
                        // singleton que l'espèce (bouton redondant) — et
                        // garde-fou set vide (filtrerait vers une carte vide).
                        onFilterGenre = quickFilterEntry
                            ?.takeIf {
                                genreFilterSks.isNotEmpty() &&
                                    genreFilterSks != setOf(it.index)
                            }
                            ?.let { entry ->
                                val genreLabel =
                                    genreInfoRepo.get(entry.genre)?.nomFr ?: entry.genre
                                { applyQuickFilter(genreFilterSks, genreLabel) }
                            },
                    ),
                )
            }

            PhotoLightbox(
                photoFiles = photoFiles,
                selectedIndex = lightboxIndex,
                onDismiss = { lightboxIndex = null },
                onDeleteAt = { idx -> pendingDeleteIndex = idx },
                // Pas d'onJumpToMapAt : on est déjà sur la carte sur ce pin.
            )

            pendingDeleteIndex?.let { idx ->
                val capture = capturesArbre.getOrNull(idx)
                val file = photoFiles.getOrNull(idx)
                if (capture == null || file == null) {
                    pendingDeleteIndex = null
                    return@let
                }
                val (isLast, kindLabel, entityName) = computeDeleteContext(
                    arbre = openedArbre,
                    capture = capture,
                    capturesArbre = capturesArbre,
                    allCaptures = allCaptures,
                )
                DeleteCaptureDialog(
                    isLastOfEntity = isLast,
                    entityKindLabel = kindLabel,
                    entityName = entityName,
                    onConfirm = {
                        pendingDeleteIndex = null
                        lightboxIndex = null
                        scope.launch { captureRepo.deleteCapture(capture, file) }
                        // Pas d'onUnlockLost : on est déjà sur la carte ; le
                        // sheet recompose tout seul en UnknownContent via les
                        // Flows réactifs (capturedSpecies / capturedRemarquables).
                    },
                    onDismiss = { pendingDeleteIndex = null },
                )
            }
        }

        viewModel.searchData?.let { data ->
            UniversalSearchSheet(
                data = data,
                onSpeciesTap = { sk ->
                    viewModel.closeSearch()
                    onSpeciesClick(sk)
                },
                onGenreTap = { g ->
                    viewModel.closeSearch()
                    onGenreClick(g)
                },
                onArrTap = { item -> viewModel.flyToArr(item.lon, item.lat) },
                onDismiss = { viewModel.closeSearch() },
            )
        }
    }
}

/**
 * Observers de découverte du mode normal, lancés UNE FOIS par le pipeline
 * d'init dans `host.scope` : ils survivent au démontage de `MapScreen` et
 * tiennent la carte à jour pendant l'absence. Aucune référence à du state
 * per-mount ici — le mount qui a lancé l'init peut être mort quand ils tirent.
 *
 * 1) **Coloration pins** : swap d'expression paint à chaque changement des
 *    captures (coût ∝ nb d'espèces capturées, pas au nb d'arbres) — sub-frame.
 * 2) **Pousseur de source** : décide ce que la source persistante doit
 *    contenir — subset du filtre rapide (`host.quickFilter`, boutons de la
 *    sheet) ou corpus complet enrichi — et le re-pousse quand les captures
 *    (debounce 1 s) ou le filtre (snapshotFlow, immédiat) changent.
 *    - Filtre actif : `filterGeoJsonBySpecies` sur le **rawJson** (contrat
 *      « sk dernière clé » — l'enrichi ne matche plus, il se termine par
 *      `discovered_remarquable`) puis enrich du subset (< 1 Mo, négligeable).
 *      Ne touche pas au cache full `app.enrichedGeoJson`.
 *    - Filtre null : pattern 2-vagues — au défiltrage, re-push immédiat du
 *      meilleur corpus dispo (enrichi même stale, sinon raw : les pins
 *      reviennent sans attendre, les clusters rattrapent), puis enrich full
 *      si la key a changé. C'est aussi lui qui fait le 1er enrichment du
 *      cold-start fresh (217 k features = trop lourd pour bloquer le 1er
 *      paint des pins). `app.enrichedGeoJson` / `app.lastEnrichmentKey`
 *      mémoïsent cross-holder (survivent à une recréation d'Activity).
 *    `lastPushed` (sks du filtre + sets captures) absorbe les ré-émissions
 *    Room à valeur identique ; `collectLatest` permet à un tap filtre
 *    d'annuler un enrich full en cours (5-15 s) — le défiltrage suivant
 *    ré-émettra et relancera l'enrich.
 *
 * Le mode filtré (MAP_FILTERED) garde ses effets inline côté composable (sa
 * MapView et son style meurent avec l'écran).
 */
@OptIn(kotlinx.coroutines.FlowPreview::class)
private fun launchDiscoveryObservers(
    host: MapHost,
    app: ArbresApp,
    captureRepo: CaptureRepository,
    speciesIndex: SpeciesIndex,
    style: Style,
) {
    host.scope.launch {
        combine(
            captureRepo.capturedSpeciesIndices(),
            captureRepo.capturedRemarquableIds(),
        ) { species, remarquables -> species to remarquables }
            .collect { (species, remarquables) ->
                // Capturer une espèce identifiée d'un genre déverrouille les
                // pins (G, sp.) du même genre (verts). Cohérent avec
                // l'auto-débloquage genre-based appliqué côté Arboretum.
                applyDiscoveryColor(
                    style,
                    speciesIndex.effectivelyCapturedSpecies(species),
                    remarquables,
                )
            }
    }
    host.scope.launch {
        // Dernier contenu effectivement poussé : (sks du filtre ou null,
        // captures espèces, captures remarquables). Absorbe les ré-émissions
        // Room à valeur identique.
        var lastPushed: Triple<Set<Int>?, Set<Int>, Set<Long>>? = null
        combine(
            combine(
                captureRepo.capturedSpeciesIndices(),
                captureRepo.capturedRemarquableIds(),
            ) { species, remarquables -> species to remarquables }
                .debounce(1000),
            snapshotFlow { host.quickFilter },
        ) { (species, remarquables), filter -> Triple(species, remarquables, filter) }
            .collectLatest { (species, remarquables, filter) ->
                val pushKey = Triple(filter?.sks, species, remarquables)
                if (pushKey == lastPushed) return@collectLatest
                val tStart = android.os.SystemClock.elapsedRealtime()
                val rawJson = app.arbresGeoJsonAsync.await()
                // Ajoute les sp. genre-débloqués au set passé à l'enrichment,
                // pour que les clusters propagent l'état de découverte sur
                // les (G, sp.) — symétrique de `applyDiscoveryColor`.
                val effectiveSpecies = speciesIndex.effectivelyCapturedSpecies(species)
                if (filter != null) {
                    val subset = withContext(Dispatchers.Default) {
                        enrichGeoJsonWithDiscovery(
                            filterGeoJsonBySpecies(rawJson, filter.sks, remarquables),
                            effectiveSpecies,
                            remarquables,
                        )
                    }
                    pushArbresGeoJsonAndAwait(host.mapView, style, subset)
                    lastPushed = pushKey
                    host.appliedQuickFilter = filter
                    android.util.Log.i(
                        "MapScreen",
                        "Filtre rapide poussé sks=${filter.sks} " +
                            "(${android.os.SystemClock.elapsedRealtime() - tStart}ms, ${subset.length / 1024}ko)",
                    )
                    return@collectLatest
                }
                val key = species to remarquables
                val cached = app.enrichedGeoJson.value
                    ?.takeIf { key == app.lastEnrichmentKey }
                when {
                    // 1er passage avec cache à jour (recréation d'Activity) :
                    // le pipeline d'init a déjà poussé ce cache — ne pas
                    // re-payer le parse 33 Mo.
                    cached != null && lastPushed == null -> lastPushed = pushKey
                    // Défiltrage sans nouvelle capture depuis le dernier
                    // enrich : retour direct au corpus complet.
                    cached != null -> {
                        pushArbresGeoJsonAndAwait(host.mapView, style, cached)
                        lastPushed = pushKey
                        host.appliedQuickFilter = null
                        android.util.Log.i("MapScreen", "Défiltrage : corpus enrichi (cache) re-poussé")
                    }
                    else -> {
                        // Vague 1, défiltrage seulement : re-push immédiat du
                        // meilleur corpus dispo (enrichi stale sinon raw) pour
                        // que les pins reviennent sans attendre l'enrich full.
                        if (lastPushed?.first != null) {
                            pushArbresGeoJsonAndAwait(
                                host.mapView, style, app.enrichedGeoJson.value ?: rawJson,
                            )
                            host.appliedQuickFilter = null
                            android.util.Log.i("MapScreen", "Défiltrage : corpus provisoire re-poussé (vague 1)")
                        }
                        val enriched = withContext(Dispatchers.Default) {
                            enrichGeoJsonWithDiscovery(rawJson, effectiveSpecies, remarquables)
                        }
                        val tEnrich = android.os.SystemClock.elapsedRealtime()
                        android.util.Log.i(
                            "MapScreen",
                            "GeoJSON enrichi mid-session (${tEnrich - tStart}ms bg, ${enriched.length / 1_000_000}Mo)",
                        )
                        app.enrichedGeoJson.value = enriched
                        app.lastEnrichmentKey = key
                        setArbresGeoJson(style, enriched)
                        lastPushed = pushKey
                        host.appliedQuickFilter = null
                        android.util.Log.i(
                            "MapScreen",
                            "Enrichi poussé mid-session (+${android.os.SystemClock.elapsedRealtime() - tEnrich}ms UI)",
                        )
                    }
                }
            }
    }
}

/**
 * Attache notre `LocationProvider` au `LocationEngine` qu'a déjà instancié
 * MapLibre via `useDefaultLocationEngine(true)`. Renvoie une closure de
 * cleanup à invoquer au `onDispose` du `MapView`. Cf. `enableLocationPin`
 * pour la cause racine que ce bridge corrige.
 */
@android.annotation.SuppressLint("MissingPermission")
private fun attachMapLibreLocationBridge(component: LocationComponent): (() -> Unit)? {
    val engine: LocationEngine = component.locationEngine ?: return null
    val callback = object : LocationEngineCallback<LocationEngineResult> {
        override fun onSuccess(result: LocationEngineResult?) {
            val loc: Location = result?.lastLocation ?: return
            LocationProvider.feedExternalFix(loc)
        }
        override fun onFailure(exception: Exception) = Unit
    }
    val request = LocationEngineRequest.Builder(500L)
        .setPriority(LocationEngineRequest.PRIORITY_HIGH_ACCURACY)
        .build()
    engine.requestLocationUpdates(request, callback, Looper.getMainLooper())
    // Pousse aussi le dernier fix connu — raccourcit le délai si le pin user
    // était déjà à l'écran avant que ce bridge ne s'attache.
    engine.getLastLocation(callback)
    return { engine.removeLocationUpdates(callback) }
}

/**
 * Décide le wording du `DeleteCaptureDialog` selon que la suppression
 * va re-verrouiller l'entité ou non. Pour un remarquable, c'est la
 * dernière capture **de cet arbre** qui re-verrouille (1 arbre = 1
 * remarquable). Pour une espèce, c'est la dernière capture **de
 * l'espèce** (toutes captures user confondues, indépendamment de
 * l'arbre individuel).
 */
private fun computeDeleteContext(
    arbre: Arbre,
    capture: Capture,
    capturesArbre: List<Capture>,
    allCaptures: List<Capture>,
): Triple<Boolean, String, String?> {
    return if (arbre.remarquable) {
        Triple(
            capturesArbre.size == 1,
            "cet arbre remarquable",
            arbre.adresse,
        )
    } else {
        val countSpecies = allCaptures.count { it.speciesIndex == capture.speciesIndex }
        Triple(
            countSpecies == 1,
            "cette espèce",
            arbre.nomCommun ?: arbre.nomAffichage,
        )
    }
}

// Palette verre dépoli des FAB utilitaires, figée hors du thème (cf. commentaire
// d'`UtilityFab`) : pas de `MaterialTheme` ici, c'est volontaire — ces tokens
// s'alignent sur la chromie claire de la carte, pas sur le scheme app.
private val FAB_GLASS_CONTAINER = Color.White.copy(alpha = 0.78f)
private val FAB_GLASS_CONTENT = Color(0xFF3C4043)

// FAB « utilitaire » discret (Recherche, Localiser) : même taille que les FAB
// gameplay (56 dp, touch target intact) mais palette **verre dépoli** figée
// hors du thème système. Raison : ces FAB survolent toujours la carte
// OpenFreeMap, qui n'a pas de variant dark — en thème dark, un
// `surfaceContainerHigh` se résoudrait en gris quasi-noir et tomberait
// comme un trou sur la chromie claire de la carte. On s'aligne donc sur le
// repère carte (toujours clair), pas sur le repère app. Le HuntPanel garde
// sa palette thème-aware : il est posé sur l'inset NavigationBar, un autre
// repère visuel.
//
// `elevation` strictement à 0 sur tous les états : sinon le `Surface` interne
// du FloatingActionButton applique un `surfaceColorAtElevation()` comme
// overlay par-dessus le container, et avec notre container translucide cet
// overlay clair devient visible — il dessine alors un mini-carré plus opaque
// au centre du FAB. Pas d'ombre nécessaire, le contraste icône/fond suffit
// à détacher le bouton de la carte.
@Composable
private fun UtilityFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = FAB_GLASS_CONTAINER,
        contentColor = FAB_GLASS_CONTENT,
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp,
        ),
        content = { content() },
    )
}
