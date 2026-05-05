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
import androidx.compose.material.icons.outlined.Search
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import app.arbre.data.Season
import app.arbre.data.rememberArbreRepository
import app.arbre.data.rememberCaptureRepository
import app.arbre.data.rememberSpeciesIndex
import app.arbre.data.rememberRemarquableInfoRepository
import app.arbre.data.rememberSpeciesInfoRepository
import app.arbre.ui.common.SeasonAmbience
import app.arbre.ui.detail.ArbreDetailContent
import app.arbre.ui.theme.arbresColors
import app.arbre.ui.theme.arbresMotion
import app.arbre.util.LocationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
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
// Vue d'ensemble pour le mode filtré : zoom plus bas pour montrer la
// distribution spatiale de l'espèce sur tout Paris.
private const val PARIS_OVERVIEW_ZOOM = 11.5
private const val USER_ZOOM = 16.0

// Constantes layers + couleurs pins déplacées dans `MapLayers.kt` (visibilité
// internal pour l'usage cross-fichier ici). Composables splash + bandeau filtré
// déplacés dans `MapOverlays.kt`.

private fun parisCamera(zoom: Double = PARIS_ZOOM): CameraPosition =
    CameraPosition.Builder().target(PARIS).zoom(zoom).build()

/**
 * Caméra à utiliser au tout premier rendu de la carte. Si la permission est
 * déjà accordée et qu'un fix (frais ou last-known) est disponible, on ouvre
 * directement sur la position de l'utilisateur. Sinon on retombe sur Paris z13.
 *
 * On ne déclenche jamais de demande de permission ici : c'est le rôle du FAB.
 */
private suspend fun computeInitialCamera(ctx: Context): CameraPosition {
    if (!LocationProvider.hasFineLocationPermission(ctx)) return parisCamera()
    val loc = LocationProvider.currentOrLastKnown(ctx) ?: return parisCamera()
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
    filterSpecies: Int? = null,
) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as ArbresApp
    val styleUrl = stringResource(R.string.map_style_url)
    val repo = rememberArbreRepository()
    val captureRepo = rememberCaptureRepository()
    val speciesIndex = rememberSpeciesIndex()
    val speciesInfoRepo = rememberSpeciesInfoRepository()
    val remarquableInfoRepo = rememberRemarquableInfoRepository()
    val viewModel: MapViewModel = viewModel(
        factory = viewModelFactory {
            initializer { MapViewModel(repo, createSavedStateHandle()) }
        }
    )

    val filteredEntry = filterSpecies?.let { speciesIndex.get(it) }
    val filteredCount = filterSpecies?.let { speciesInfoRepo.get(it)?.stats?.count }

    // La carte affiche toujours la saison vive : le sélecteur de saison vit
    // dans Profil/Arboretum/Remarquables, pas sur le hub. `Season.current()`
    // est recalculée à chaque recomposition — coût nul, bascule de minuit
    // gérée naturellement.
    val currentSeason = Season.current()

    val capturedSpecies by captureRepo.capturedSpeciesIndices(currentSeason)
        .collectAsState(initial = emptySet())
    val capturedRemarquables by captureRepo.capturedRemarquableIds(currentSeason)
        .collectAsState(initial = emptySet())

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
    // Cleanup du bridge `LocationEngine` MapLibre → `LocationProvider` (cf.
    // `attachMapLibreLocationBridge`). Stocké hors de `enableLocationPin`
    // pour pouvoir être appelé au `onDispose` du `MapView`. Phase 10.5
    // sous-groupe F.
    var maplibreLocationCleanup by remember { mutableStateOf<(() -> Unit)?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(styleRef, currentSeason) {
        val style = styleRef ?: return@LaunchedEffect
        combine(
            captureRepo.capturedSpeciesIndices(currentSeason),
            captureRepo.capturedRemarquableIds(currentSeason),
        ) { species, remarquables -> species to remarquables }
            .collect { (species, remarquables) ->
                applyDiscoveryColor(style, species, remarquables)
            }
    }

    // Mid-session : à chaque changement des captures (debounce 1 s pour
    // lisser les rafales), regenerate le GeoJSON enrichi (flag `discovered`
    // par feature) en background et le re-pousse via `setArbresGeoJson`.
    // C'est lui qui fait le **1er enrichment** au tout 1er cold-start (pas
    // le pipeline cold-start, trop coûteux pour bloquer le 1er paint des
    // pins). Aux mounts suivants, le cache `app.enrichedGeoJson` permet au
    // cold-start de poser direct l'enrichi — ici on skip le re-enrich si
    // les sets sont identiques à `lastEnrichmentKey`. Skip le mode filtré.
    // Phase 10.5 H.
    LaunchedEffect(styleRef, currentSeason, filterSpecies) {
        if (filterSpecies != null) return@LaunchedEffect
        val style = styleRef ?: return@LaunchedEffect
        combine(
            captureRepo.capturedSpeciesIndices(currentSeason),
            captureRepo.capturedRemarquableIds(currentSeason),
        ) { species, remarquables -> species to remarquables }
            .debounce(1000)
            .collect { (species, remarquables) ->
                val key = species to remarquables
                if (key == app.lastEnrichmentKey && app.enrichedGeoJson.value != null) {
                    // Mount avec sets inchangés depuis le dernier enrich :
                    // le cold-start a déjà posé le cached enrichi, rien à faire.
                    return@collect
                }
                val tStart = android.os.SystemClock.elapsedRealtime()
                val rawJson = app.arbresGeoJsonAsync.await()
                val enriched = withContext(Dispatchers.Default) {
                    enrichGeoJsonWithDiscovery(rawJson, species, remarquables)
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
        // Bridge MapLibre → LocationProvider : au tout 1er lancement
        // post-onboarding, notre `LocationListener` propre ne reçoit pas
        // d'updates pendant ~10 s alors que le `LocationEngineDefault` que
        // MapLibre instancie reçoit des fix dès t≈1 s. On consomme SA
        // source au lieu d'attendre la nôtre — élimine la cause racine du
        // bug « Active le GPS » au 1er run et garantit zéro drift entre le
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
            scope.launch { centerOnUser() }
        } else {
            scope.launch { snackbar.showSnackbar("Permission de localisation refusée") }
        }
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
            // On bloque la rotation : la boussole MapLibre se retrouve sinon
            // sous l'inset status bar en edge-to-edge, intappable, et la
            // rotation libre n'apporte rien à l'usage « carnet de bord
            // naturaliste » (la carte reste nord-en-haut, comme un plan
            // imprimé). `isCompassEnabled = false` la cache puisque
            // dépourvue de raison d'être sans rotation.
            map.uiSettings.isRotateGesturesEnabled = false
            map.uiSettings.isCompassEnabled = false
            scope.launch {
                map.cameraPosition = if (filterSpecies != null) {
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
                            if (filterSpecies != null) {
                                // Mode filtré : single-pass. Le GeoJSON filtré est
                                // petit (~max 38k features pour Platanus, < 1 Mo),
                                // le freeze d'`addArbresLayers` reste imperceptible.
                                val rawJson = app.arbresGeoJsonAsync.await()
                                val tJson = android.os.SystemClock.elapsedRealtime()
                                android.util.Log.i(
                                    "MapScreen",
                                    "GeoJSON disponible (process+${tJson - tProcess}ms, ${rawJson.length / 1_000_000}Mo)",
                                )
                                // On enrichit aussi en mode filtré pour que les
                                // clusters d'espèce reflètent la progression
                                // (Phase 10.5 H). Coût négligeable sur < 1 Mo.
                                val initialCaptures = withTimeoutOrNull(2000) {
                                    combine(
                                        captureRepo.capturedSpeciesIndices(currentSeason),
                                        captureRepo.capturedRemarquableIds(currentSeason),
                                    ) { s, r -> s to r }.first()
                                } ?: (emptySet<Int>() to emptySet<Long>())
                                val json = withContext(Dispatchers.Default) {
                                    enrichGeoJsonWithDiscovery(
                                        filterGeoJsonBySpecies(
                                            rawJson,
                                            filterSpecies,
                                            initialCaptures.second,
                                        ),
                                        initialCaptures.first,
                                        initialCaptures.second,
                                    )
                                }.also { filtered ->
                                    val tFilter = android.os.SystemClock.elapsedRealtime()
                                    android.util.Log.i(
                                        "MapScreen",
                                        "GeoJSON filtré sk=$filterSpecies (process+${tFilter - tProcess}ms, ${filtered.length / 1024}ko)",
                                    )
                                }
                                addArbresLayers(style, json)
                                styleRef = style
                                arbresPrets = true
                                val tLayers = android.os.SystemClock.elapsedRealtime()
                                android.util.Log.i(
                                    "MapScreen",
                                    "Layers posées (process+${tLayers - tProcess}ms, total filtered)",
                                )
                            } else {
                                // Cold-start global : 2-passes flip-avant-load.
                                // Le `GeoJsonSource` ctor parse le JSON 32 Mo sur le UI
                                // thread (exigence MapLibre, cf. bug Phase 9 §4) et
                                // bloque ~700 ms — pendant ce temps Choreographer ne
                                // tique pas et le splash apparaît figé. Solution :
                                // on pose les layers VIDES (instantané), on flip
                                // `arbresPrets = true` pour que le splash joue son anim
                                // et sorte proprement, puis on injecte les arbres
                                // via `setArbresGeoJson` après — le freeze 700 ms est
                                // alors masqué par « carte vide » au lieu du splash.
                                addArbresLayers(style, EMPTY_GEOJSON)
                                styleRef = style
                                arbresPrets = true
                                val tEmpty = android.os.SystemClock.elapsedRealtime()
                                android.util.Log.i(
                                    "MapScreen",
                                    "Layers vides posées (process+${tEmpty - tProcess}ms, splash exit)",
                                )
                                // Phase 10.5 H : si on a déjà un GeoJSON enrichi
                                // dans le cache process-singleton (mount post
                                // retour Profil → Map), on le pose direct — pins
                                // ET clusters bons d'un coup, 1 seul freeze UI.
                                // Sinon (cold-start fresh), on pose le rawJson nu
                                // pour que les pins individuels apparaissent ASAP
                                // (~700 ms via `setArbresGeoJson`) ; le
                                // `LaunchedEffect` mid-session débouncedra
                                // l'enrichment ~1 s plus tard et re-poussera
                                // l'enrichi (les clusters s'allument en 2e wave).
                                // L'enrichment 217k features est trop coûteux
                                // (~5-15 s sur device GrapheneOS) pour bloquer
                                // le 1er paint.
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
                                    "Arbres injectés (process+${tLayers - tProcess}ms, total cold start)",
                                )
                            }
                        } catch (e: Throwable) {
                            android.util.Log.e("MapScreen", "Échec chargement arbres", e)
                        }
                    }
                }

                map.addOnCameraIdleListener { viewModel.rememberCamera(map.cameraPosition) }

                map.addOnMapClickListener { latLng ->
                    val pixel = map.projection.toScreenLocation(latLng)
                    val touch = RectF(pixel.x - 20f, pixel.y - 20f, pixel.x + 20f, pixel.y + 20f)

                    val clusters = map.queryRenderedFeatures(touch, CLUSTERS_LAYER_ID)
                    if (clusters.isNotEmpty()) {
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

    fun onNearestRemarquableClick() {
        scope.launch {
            val loc = LocationProvider.currentLocation.value
                ?: LocationProvider.currentOrLastKnown(ctx)
            if (loc == null) {
                snackbar.showSnackbar("Position indisponible (active le GPS)")
                return@launch
            }
            val tous = repo.arbresRemarquables()
            val restants = tous.filterNot { it.id in capturedRemarquables }
            if (restants.isEmpty()) {
                snackbar.showSnackbar("Tous les remarquables sont découverts")
                return@launch
            }
            val results = FloatArray(1)
            val nearest = restants.map { rem ->
                Location.distanceBetween(
                    loc.latitude, loc.longitude,
                    rem.latitude, rem.longitude,
                    results,
                )
                rem to results[0]
            }.minBy { it.second }
            snackbar.showSnackbar(
                "Plus proche remarquable non découvert : ${nearest.second.toInt()} m"
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = { mapView })
        SeasonAmbience(season = currentSeason)
        CaptureCelebrationOverlay(
            captureRepo = captureRepo,
            mapRef = mapRef,
            speciesIndex = speciesIndex,
        )
        if (filteredEntry != null && onBack != null) {
            FilterBanner(
                entry = filteredEntry,
                count = filteredCount,
                onBack = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(16.dp),
            )
        } else {
            // FAB Profil seul à gauche : la sélection de saison vit dans
            // Profil/Arboretum/Remarquables, pas ici.
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
                    // Pastille orange remarquable (cf. `ic_remarquable_badge`).
                    // Tint Unspecified pour préserver la bichromie orange/crème
                    // — la couleur *est* le sens, alignée sur le pin de la carte.
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
            // FAB loupe (BottomStart) : recherche du remarquable le plus
            // proche non découvert. Réservé au mode non-filtré — en
            // `MAP_FILTERED` l'utilisateur chasse une espèce.
            FloatingActionButton(
                onClick = ::onNearestRemarquableClick,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(16.dp),
            ) {
                Icon(
                    Icons.Outlined.Search,
                    contentDescription = "Plus proche remarquable",
                    tint = MaterialTheme.arbresColors.remarquableOrange,
                )
            }
        }
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
                .padding(16.dp),
        ) {
            Icon(Icons.Outlined.MyLocation, contentDescription = "Me localiser")
        }
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        // Splash overlay : reste au-dessus de la carte tant que les layers
        // d'arbres ne sont pas posées. Couleurs et icône alignées avec le
        // splash natif (themes.xml) pour une transition sans flicker.
        // En mode `MAP_FILTERED` on ne charge pas tout Paris, juste une
        // espèce — copy + animation différents.
        AnimatedVisibility(
            visible = !arbresPrets,
            enter = androidx.compose.animation.EnterTransition.None,
            exit = fadeOut(animationSpec = tween(durationMillis = MaterialTheme.arbresMotion.short)),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (filteredEntry != null) {
                FilterSplash(speciesLabel = filteredEntry.displayNomCommun)
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
            val sk = speciesIndex.indexOf(openedArbre)
            val isDiscovered = if (openedArbre.remarquable) {
                openedArbre.id in capturedRemarquables
            } else {
                sk != null && sk in capturedSpecies
            }
            val capturesArbre by captureRepo.capturesPourArbre(openedArbre.id)
                .collectAsState(initial = emptyList())
            // `captureAvailability` est non-bloquant (pure lecture du flow
            // `currentLocation` filtré sur âge), donc on la dérive directement
            // à chaque émission. La transition « Active le GPS » → « Capturer »
            // se fait dans la seconde quand le 1er fix arrive
            // post-onboarding ; le bouton suit aussi le mouvement live de
            // l'utilisateur. Cf. Phase 10.5 sous-groupe F.
            val currentLocation by LocationProvider.currentLocation.collectAsState()
            val availability = remember(openedArbre.id, currentLocation) {
                captureAvailability(openedArbre)
            }
            // Médianes de l'espèce pour situer l'arbre vs ses pairs. Le lookup
            // est local en RAM (singleton dans ArbresApp), pas de coût IO.
            val info = sk?.let { speciesInfoRepo.get(it) }
            val remarquableInfo = if (openedArbre.remarquable) {
                remarquableInfoRepo.get(openedArbre.id)
            } else null
            ModalBottomSheet(
                onDismissRequest = { viewModel.closeDetail() },
                sheetState = sheetState,
            ) {
                ArbreDetailContent(
                    arbre = openedArbre,
                    isDiscovered = isDiscovered,
                    nbPhotos = capturesArbre.size,
                    onCapturer = { capturer(openedArbre) },
                    captureAvailability = availability,
                    onSpeciesClick = if (sk != null && sk in capturedSpecies) {
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
                    medianHeightM = info?.stats?.medianHeightM,
                    medianCircCm = info?.stats?.medianCircCm,
                    remarquableInfo = remarquableInfo,
                )
            }
        }
    }
}

/**
 * Attache notre `LocationProvider` au `LocationEngine` qu'a déjà instancié
 * MapLibre via `useDefaultLocationEngine(true)`. Renvoie une closure de
 * cleanup à invoquer au `onDispose` du `MapView`. Cf. note dans
 * `enableLocationPin` pour le contexte du bug 1er-lancement résolu par ce
 * bridge (Phase 10.5 sous-groupe F).
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
    // Pousse aussi le dernier fix connu de MapLibre — gratuit et raccourcit
    // encore le délai si le pin user était déjà à l'écran avant que notre
    // bridge ne s'attache.
    engine.getLastLocation(callback)
    return { engine.removeLocationUpdates(callback) }
}
