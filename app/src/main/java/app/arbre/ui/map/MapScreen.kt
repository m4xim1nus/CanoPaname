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
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
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
import app.arbre.data.rememberArbreRepository
import app.arbre.data.rememberCaptureRepository
import app.arbre.data.rememberSpeciesIndex
import app.arbre.data.rememberRemarquableInfoRepository
import app.arbre.data.rememberGenreInfoRepository
import app.arbre.data.rememberSpeciesInfoRepository
import app.arbre.data.resolvedFile
import app.arbre.ui.common.DeleteCaptureDialog
import app.arbre.ui.common.PhotoLightbox
import app.arbre.ui.common.showSnackbarFor
import app.arbre.ui.detail.ArbreDetailActions
import app.arbre.ui.detail.ArbreDetailContent
import app.arbre.ui.detail.ArbreDetailState
import app.arbre.ui.theme.arbresColors
import app.arbre.ui.theme.arbresMotion
import app.arbre.util.LocationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

// Caméra de bootstrap, NON-BLOQUANTE : lecture pure du dernier fix connu de
// `LocationProvider.currentLocation` (déjà amorcé par `LocationProvider.start`
// avec un last-known si dispo), sinon Paris. Surtout PAS d'attente d'un
// `getCurrentLocation()` ici — son timeout système (~30 s en intérieur GPS
// froid) bloquerait l'appel de `map.setStyle(...)` qui le suit, donc tout le
// chargement de la carte. Le recadrage sur la position réelle est fait par le
// `LaunchedEffect` de recadrage auto dès qu'un fix arrive (cf. plus bas).
// Pas de demande de permission ici — c'est le rôle du FAB de localisation.
private fun computeInitialCamera(ctx: Context): CameraPosition {
    if (!LocationProvider.hasFineLocationPermission(ctx)) return parisCamera()
    val loc = LocationProvider.currentLocation.value ?: return parisCamera()
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
    onRemarquableDetail: (Long) -> Unit = {},
    onFirstSpeciesCapture: (Int) -> Unit = {},
    onBack: (() -> Unit)? = null,
    /**
     * Set de sks à filtrer. `emptySet()` = mode normal (toute la carte).
     * Singleton = filtre fiche-espèce classique. Plusieurs sks = filtre genre
     * depuis la fiche genre : `{sk_sp.} ∪ {sks_du_genre_capturés}`.
     */
    filterSpecies: Set<Int> = emptySet(),
    pulseArbreId: Long? = null,
) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as ArbresApp
    val styleUrl = stringResource(R.string.map_style_url)
    val repo = rememberArbreRepository()
    val captureRepo = rememberCaptureRepository()
    val speciesIndex = rememberSpeciesIndex()
    val speciesInfoRepo = rememberSpeciesInfoRepository()
    val genreInfoRepo = rememberGenreInfoRepository()
    val remarquableInfoRepo = rememberRemarquableInfoRepository()
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

    val mapView = remember {
        MapView(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
    }
    var mapRef by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleRef by remember { mutableStateOf<Style?>(null) }
    var arbresPrets by remember { mutableStateOf(false) }
    // Plancher de durée du splash : on garde le voile vert affiché au moins le
    // temps de lire un tip avant de flipper `arbresPrets`. Mesuré depuis le mount.
    val mountElapsedMs = remember { android.os.SystemClock.elapsedRealtime() }
    suspend fun awaitSplashFloor(minMs: Long) {
        val elapsed = android.os.SystemClock.elapsedRealtime() - mountElapsedMs
        if (elapsed < minMs) delay(minMs - elapsed)
    }
    // `GeoJsonSource.setGeoJson(String)` sur une source déjà attachée — et, dans
    // une moindre mesure, le parse + clustering du ctor sur un gros corpus —
    // traitent les features EN BACKGROUND : la pose des layers rend la main bien
    // avant que les pins/clusters soient réellement rendus (1-3 s de décalage
    // observé sur device pour les 217 k features). Tant que le splash est levé,
    // on attend donc que la source ait produit des features rendues à l'écran —
    // sinon le voile s'efface sur une « carte vide ». Timeout de sécurité au cas
    // (improbable dans Paris) où le viewport ne couvre aucun arbre.
    suspend fun awaitArbresRendered(map: MapLibreMap, timeoutMs: Long) {
        withTimeoutOrNull(timeoutMs) {
            val screen = RectF(0f, 0f, mapView.width.toFloat(), mapView.height.toFloat())
            while (true) {
                val rendered = map.queryRenderedFeatures(
                    screen, POINTS_LAYER_ID, CLUSTERS_LAYER_ID,
                )
                if (rendered.isNotEmpty()) break
                delay(120)
            }
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

    LaunchedEffect(styleRef) {
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

    // Mid-session : à chaque changement des captures (debounce 1 s), régénère
    // le GeoJSON enrichi (flag `discovered` par feature) en background et le
    // re-pousse via `setArbresGeoJson`. C'est aussi lui qui fait le 1er
    // enrichment du cold-start fresh (le pipeline cold-start ne le fait pas,
    // 217 k features = trop lourd pour bloquer le 1er paint des pins). Aux
    // mounts suivants, `app.enrichedGeoJson` permet au cold-start de poser
    // direct l'enrichi ; ici on skip le re-enrich si les sets sont identiques
    // à `lastEnrichmentKey`. Skip total en mode filtré (déjà enrichi cold).
    LaunchedEffect(styleRef, filterSpecies) {
        if (isFiltered) return@LaunchedEffect
        val style = styleRef ?: return@LaunchedEffect
        combine(
            captureRepo.capturedSpeciesIndices(),
            captureRepo.capturedRemarquableIds(),
        ) { species, remarquables -> species to remarquables }
            .debounce(1000)
            .collect { (species, remarquables) ->
                val key = species to remarquables
                if (key == app.lastEnrichmentKey && app.enrichedGeoJson.value != null) {
                    return@collect
                }
                val tStart = android.os.SystemClock.elapsedRealtime()
                val rawJson = app.arbresGeoJsonAsync.await()
                // Ajoute les sp. genre-débloqués au set passé à l'enrichment,
                // pour que les clusters propagent l'état de découverte sur
                // les (G, sp.) — symétrique de `applyDiscoveryColor`.
                val effectiveSpecies = speciesIndex.effectivelyCapturedSpecies(species)
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
                val tPushed = android.os.SystemClock.elapsedRealtime()
                android.util.Log.i(
                    "MapScreen",
                    "Enrichi poussé mid-session (+${tPushed - tEnrich}ms UI)",
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
                            .build()
                    )
                    .useDefaultLocationEngine(true)
                    .build()
            )
        }
        component.isLocationComponentEnabled = true
        component.cameraMode = CameraMode.NONE
        component.renderMode = RenderMode.NORMAL
        // Au 1er lancement post-onboarding, notre `LocationListener` propre
        // ne reçoit pas d'updates pendant ~10 s alors que le `LocationEngine`
        // de MapLibre reçoit des fix dès t≈1 s. On consomme SA source — élimine
        // le bug « Active le GPS » au 1er run et garantit zéro drift entre le
        // pin user et la distance utilisée pour `captureAvailability`.
        if (maplibreLocationCleanup == null) {
            maplibreLocationCleanup = attachMapLibreLocationBridge(component)
        }
    }

    suspend fun centerOnUser() {
        val loc = LocationProvider.currentLocation.value
            ?: LocationProvider.currentOrLastKnown(ctx)
        if (loc == null) {
            snackbar.showSnackbar("Position indisponible (GPS désactivé ?)")
            return
        }
        mapRef?.animateCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), USER_ZOOM)
        )
        mapRef?.style?.let { enableLocationPin(mapRef!!, it) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            LocationProvider.start(ctx)
            // Bascule le FAB en mode pulse jusqu'au 1er fix (cf. LaunchedEffect
            // ci-dessous). Si on a déjà un last-known en mémoire, le flow
            // émettra immédiatement et le pulse s'éteindra dans la foulée.
            awaitingFirstFix = true
            scope.launch { centerOnUser() }
        } else {
            scope.launch { snackbar.showSnackbar("Permission de localisation refusée") }
        }
    }

    // Observer du 1er fix après grant permission. Tant que `awaitingFirstFix`
    // est vrai, on affiche une snackbar « Localisation en cours… » et on
    // attend le 1er fix non-null avec timeout 30 s. Au-delà, on stoppe le pulse
    // et on affiche un warning — le téléphone est probablement en intérieur ou
    // capteur HS.
    LaunchedEffect(awaitingFirstFix) {
        if (!awaitingFirstFix) return@LaunchedEffect
        val snackJob = launch {
            showSnackbarFor(snackbar, "Localisation en cours…")
        }
        val fix = withTimeoutOrNull(30_000) {
            LocationProvider.currentLocation.filterNotNull().first()
        }
        awaitingFirstFix = false
        snackJob.cancel()
        snackbar.currentSnackbarData?.dismiss()
        if (fix == null) {
            showSnackbarFor(snackbar, "GPS indisponible — sors à découvert")
        }
    }

    // Saut vers un arbre exact : depuis la fiche-remarquable ou la
    // `PhotoLightbox`, on navigue vers `Routes.map(arbreId)`. Au mount, on
    // attend que la map ET les layers soient prêtes, puis fly-to ~600 ms à
    // zoom élevé (z20) pour qu'aucun doute ne subsiste sur le pin ciblé, et
    // au callback `onFinish` on déclenche le pulse — pas d'ouverture du
    // sheet, l'utilisateur tape l'arbre lui-même s'il veut la fiche.
    LaunchedEffect(pulseArbreId, mapRef, styleRef, arbresPrets) {
        val id = pulseArbreId ?: return@LaunchedEffect
        val map = mapRef ?: return@LaunchedEffect
        val style = styleRef ?: return@LaunchedEffect
        if (!arbresPrets) return@LaunchedEffect
        val arbre = repo.arbreParId(id) ?: return@LaunchedEffect
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
    }

    // Recadrage GPS auto au 1er fix. `computeInitialCamera` étant non-bloquant,
    // sur un install frais (ou GPS froid en intérieur) la carte démarre sur Paris ;
    // dès qu'un fix arrive on recentre dessus à zoom 16 — sauf si : mode filtré,
    // saut vers un arbre (`pulseArbreId`), restauration d'une caméra mémorisée
    // (retour d'un autre écran → `lastCamera` non-null au mount), ou l'utilisateur
    // a déjà bougé la caméra. Ne tire qu'une fois (la 1re émission non-null).
    val freshMount = remember {
        viewModel.lastCamera == null && pulseArbreId == null && !isFiltered
    }
    LaunchedEffect(mapRef) {
        val map = mapRef ?: return@LaunchedEffect
        if (!freshMount) return@LaunchedEffect
        val fix = LocationProvider.currentLocation.filterNotNull().first()
        if (userMovedCamera) return@LaunchedEffect
        map.animateCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(fix.latitude, fix.longitude), USER_ZOOM)
        )
        map.style?.let { enableLocationPin(map, it) }
    }

    DisposableEffect(Unit) {
        val tStart = android.os.SystemClock.elapsedRealtime()
        val tProcess = app.processStartElapsedMs
        android.util.Log.i(
            "MapScreen",
            "MapView init (process+${tStart - tProcess}ms)",
        )
        LocationProvider.start(ctx)
        mapView.onCreate(null)
        mapView.onStart()
        mapView.onResume()
        mapView.getMapAsync { map ->
            mapRef = map
            // Rotation bloquée : la boussole en edge-to-edge se retrouve sous
            // l'inset status bar et devient intappable. Sans rotation libre,
            // la boussole n'a plus de raison d'être — d'où `isCompassEnabled`.
            map.uiSettings.isRotateGesturesEnabled = false
            map.uiSettings.isCompassEnabled = false
            map.cameraPosition = if (isFiltered) {
                parisCamera(PARIS_OVERVIEW_ZOOM)
            } else {
                viewModel.lastCamera ?: computeInitialCamera(ctx)
            }

            map.setStyle(Style.Builder().fromUri(styleUrl)) { style ->
                val tStyle = android.os.SystemClock.elapsedRealtime()
                android.util.Log.i(
                    "MapScreen",
                    "Style prêt (process+${tStyle - tProcess}ms)",
                )
                if (LocationProvider.hasFineLocationPermission(ctx)) {
                    enableLocationPin(map, style)
                }
                scope.launch {
                    try {
                        if (isFiltered) {
                            // Mode filtré : single-pass. GeoJSON filtré <
                            // 1 Mo (~38 k features pour Platanus max), le
                            // freeze d'`addArbresLayers` reste imperceptible.
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
                            styleRef = style
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
                            awaitSplashFloor(FILTER_SPLASH_MIN_MS)
                            arbresPrets = true
                        } else {
                            // Cold-start global, 2-passes : on pose les layers
                            // sur une source VIDE (instantané), puis on injecte
                            // les 217 k features via `setArbresGeoJson` — qui
                            // parse + cluster en background et ne bloque pas le
                            // UI thread. Le voile reste PLEINEMENT OPAQUE jusqu'à
                            // ce que les pins/clusters soient réellement rendus
                            // (`awaitArbresRendered` plus bas). Si on flippait
                            // `arbresPrets` avant que `setGeoJson` n'ait fini
                            // de parser, le voile s'effacerait sur une carte
                            // vide pendant 1-3 s, le temps du parse async.
                            addArbresLayers(style, EMPTY_GEOJSON)
                            styleRef = style
                            val tEmpty = android.os.SystemClock.elapsedRealtime()
                            android.util.Log.i(
                                "MapScreen",
                                "Layers vides posées (process+${tEmpty - tProcess}ms)",
                            )
                            // Si on a déjà un GeoJSON enrichi cached (mount
                            // post retour Profil → Map), on le pose direct
                            // — pins ET clusters bons d'un coup, 1 seul
                            // freeze UI. Sinon (cold-start fresh), on pose
                            // le rawJson nu pour que les pins apparaissent
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
                            // fresh ; remount avec cache enrichi → flip direct
                            // (inutile de ralentir une nav rapide).
                            if (cached == null) awaitSplashFloor(COLD_SPLASH_MIN_MS)
                            arbresPrets = true
                        }
                    } catch (e: Throwable) {
                        android.util.Log.e("MapScreen", "Échec chargement arbres", e)
                    } finally {
                        // Ne jamais rester coincé sous le splash si une étape a
                        // échoué (OOM possible au parse du GeoJSON). Idempotent
                        // si le chemin nominal a déjà flippé.
                        arbresPrets = true
                    }
                }
            }

            map.addOnCameraIdleListener { viewModel.rememberCamera(map.cameraPosition) }
            // Geste utilisateur sur la carte → on coupe le recadrage GPS auto.
            map.addOnCameraMoveStartedListener { reason ->
                if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                    userMovedCamera = true
                }
            }

            map.addOnMapClickListener { latLng ->
                val pixel = map.projection.toScreenLocation(latLng)
                val touch = RectF(pixel.x - 20f, pixel.y - 20f, pixel.x + 20f, pixel.y + 20f)

                val clusters = map.queryRenderedFeatures(touch, CLUSTERS_LAYER_ID)
                if (clusters.isNotEmpty()) {
                    userMovedCamera = true
                    val source = map.style?.getSourceAs<GeoJsonSource>(ARBRES_SOURCE_ID)
                    val zoom = source?.getClusterExpansionZoom(clusters.first())?.toDouble()
                        ?: (map.cameraPosition.zoom + 2.0)
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, zoom))
                    return@addOnMapClickListener true
                }

                val points = map.queryRenderedFeatures(touch, POINTS_LAYER_ID)
                val id = points.firstOrNull()?.getNumberProperty("id")?.toLong()
                if (id != null) {
                    viewModel.openDetail(id)
                    true
                } else {
                    false
                }
            }
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

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = { mapView })
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
            FloatingActionButton(
                onClick = onProfileClick,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(16.dp),
            ) {
                Icon(Icons.Outlined.Person, contentDescription = "Profil")
            }
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
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
        // Pulse infini 1.0 → 1.12 pendant `awaitingFirstFix`. `Modifier.scale`
        // affecte le draw, pas le layout, donc la hitbox du FAB reste stable.
        val pulse = rememberInfiniteTransition(label = "gpsFabPulse")
        val pulseScale by pulse.animateFloat(
            initialValue = 1f,
            targetValue = if (awaitingFirstFix) 1.12f else 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 800),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "gpsFabPulseScale",
        )
        FloatingActionButton(
            onClick = {
                if (LocationProvider.hasFineLocationPermission(ctx)) {
                    scope.launch { centerOnUser() }
                } else {
                    permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(16.dp)
                .padding(bottom = bottomShiftForHunt)
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
            onFirstSpeciesCapture = { sk ->
                viewModel.closeDetail()
                onFirstSpeciesCapture(sk)
            },
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
