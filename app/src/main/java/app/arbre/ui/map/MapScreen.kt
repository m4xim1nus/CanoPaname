package app.arbre.ui.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.RectF
import android.location.Location
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.arbre.ArbresApp
import app.arbre.R
import app.arbre.data.Season
import app.arbre.data.SpeciesEntry
import app.arbre.data.rememberArbreRepository
import app.arbre.data.rememberCaptureRepository
import app.arbre.data.rememberSeasonStore
import app.arbre.data.rememberSpeciesIndex
import app.arbre.data.rememberRemarquableInfoRepository
import app.arbre.data.rememberSpeciesInfoRepository
import app.arbre.ui.common.ArchiveBanner
import app.arbre.ui.common.SeasonAmbience
import app.arbre.ui.common.SeasonSelector
import app.arbre.ui.detail.ArbreDetailContent
import app.arbre.ui.theme.arbresColors
import app.arbre.ui.theme.arbresMotion
import app.arbre.util.LocationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.expressions.Expression.eq
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.has
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.expressions.Expression.match
import org.maplibre.android.style.expressions.Expression.not
import org.maplibre.android.style.expressions.Expression.switchCase
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource

private val PARIS = LatLng(48.8566, 2.3522)
private const val PARIS_ZOOM = 13.0
// Vue d'ensemble pour le mode filtré : zoom plus bas pour montrer la
// distribution spatiale de l'espèce sur tout Paris.
private const val PARIS_OVERVIEW_ZOOM = 11.5
private const val USER_ZOOM = 16.0
private const val ARBRES_SOURCE_ID = "arbres-source"
private const val POINTS_LAYER_ID = "arbres-points"
private const val CLUSTERS_LAYER_ID = "arbres-clusters"
private const val CLUSTER_COUNT_LAYER_ID = "arbres-cluster-count"
// Couleurs des pins centralisées dans `app.arbre.ui.theme.MapColors`. Aliases
// privés pour ne pas polluer les sites d'usage avec un préfixe long.
private val PIN_GREEN = app.arbre.ui.theme.MapColors.PIN_GREEN
private val PIN_ORANGE = app.arbre.ui.theme.MapColors.PIN_ORANGE
private val PIN_GREY = app.arbre.ui.theme.MapColors.PIN_GREY

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

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MissingPermission")
@Composable
fun MapScreen(
    onArboretumClick: () -> Unit = {},
    onRemarquablesClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onSpeciesClick: (Int) -> Unit = {},
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

    val seasonStore = rememberSeasonStore()
    val selectedSeason by seasonStore.selected.collectAsState()
    // `Season.current()` est calculée à la volée à chaque recomposition —
    // le coût est nul et on évite les bugs de bascule à minuit. Si on
    // recompose à 23h59 puis à 00h01, la valeur change naturellement.
    val currentSeason = Season.current()
    val isArchive = selectedSeason != currentSeason

    val capturedSpecies by captureRepo.capturedSpeciesIndices(selectedSeason)
        .collectAsState(initial = emptySet())
    val capturedRemarquables by captureRepo.capturedRemarquableIds(selectedSeason)
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
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(styleRef, selectedSeason) {
        val style = styleRef ?: return@LaunchedEffect
        combine(
            captureRepo.capturedSpeciesIndices(selectedSeason),
            captureRepo.capturedRemarquableIds(selectedSeason),
        ) { species, remarquables -> species to remarquables }
            .collect { (species, remarquables) ->
                applyDiscoveryColor(style, species, remarquables)
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
                            val rawJson = app.arbresGeoJsonAsync.await()
                            val tJson = android.os.SystemClock.elapsedRealtime()
                            android.util.Log.i(
                                "MapScreen",
                                "GeoJSON disponible (process+${tJson - tProcess}ms, ${rawJson.length / 1_000_000}Mo)",
                            )
                            val json = if (filterSpecies != null) {
                                withContext(Dispatchers.Default) {
                                    filterGeoJsonBySpecies(rawJson, filterSpecies)
                                }.also { filtered ->
                                    val tFilter = android.os.SystemClock.elapsedRealtime()
                                    android.util.Log.i(
                                        "MapScreen",
                                        "GeoJSON filtré sk=$filterSpecies (process+${tFilter - tProcess}ms, ${filtered.length / 1024}ko)",
                                    )
                                }
                            } else rawJson
                            addArbresLayers(style, json)
                            styleRef = style
                            arbresPrets = true
                            val tLayers = android.os.SystemClock.elapsedRealtime()
                            android.util.Log.i(
                                "MapScreen",
                                "Layers posées (process+${tLayers - tProcess}ms, total cold start)",
                            )
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
        SeasonAmbience(season = selectedSeason)
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
            // Bandeau « {Saison} — figé » plein écran en haut quand la saison
            // sélectionnée n'est pas la saison vive ; les FABs descendent en
            // dessous. Saison vive : pas de bandeau, FABs en haut comme avant.
            if (isArchive) {
                ArchiveBanner(
                    season = selectedSeason,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .windowInsetsPadding(WindowInsets.statusBars),
                )
            }
            val fabTopPadding = if (isArchive) 56.dp else 16.dp
            // Row TopStart : Profile FAB + sélecteur de saison (pill compacte
            // alignée verticalement sur le FAB pour rester discret).
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(start = 16.dp, top = fabTopPadding, end = 16.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FloatingActionButton(onClick = onProfileClick) {
                    Icon(Icons.Outlined.Person, contentDescription = "Profil")
                }
                SeasonSelector(
                    selected = selectedSeason,
                    onSelect = { seasonStore.select(it) },
                    isCurrent = !isArchive,
                )
            }
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(start = 16.dp, top = fabTopPadding, end = 16.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FloatingActionButton(onClick = onRemarquablesClick) {
                    // Plaque inspirée des plaques officielles « Arbre remarquable
                    // Ville de Paris ». Tint Unspecified pour préserver la
                    // bichromie verte/crème de l'asset.
                    Icon(
                        painter = painterResource(R.drawable.ic_remarquable_plaque),
                        contentDescription = "Remarquables",
                        tint = androidx.compose.ui.graphics.Color.Unspecified,
                    )
                }
                FloatingActionButton(onClick = onArboretumClick) {
                    Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = "Arboretum")
                }
            }
            // FAB loupe (BottomStart) : recherche de la cible la plus proche.
            // Réservé au mode non-filtré — en `MAP_FILTERED` l'utilisateur
            // chasse une espèce, pas un remarquable. Désactivé aussi en
            // archive saisonnière : la notion de « plus proche non découvert »
            // n'a pas de sens hors saison vive (cf. ROADMAP Sprint I).
            if (!isArchive) {
                FloatingActionButton(
                    onClick = ::onNearestRemarquableClick,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(16.dp),
                ) {
                    Icon(Icons.Outlined.Search, contentDescription = "Plus proche remarquable")
                }
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
            var availability by remember(openedArbre.id, isArchive) {
                mutableStateOf<CaptureAvailability?>(null)
            }
            LaunchedEffect(openedArbre.id, isArchive) {
                availability = if (isArchive) {
                    CaptureAvailability.Archived
                } else {
                    captureAvailability(ctx, openedArbre)
                }
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
                    onSpeciesClick = if (isDiscovered && sk != null) {
                        {
                            viewModel.closeDetail()
                            onSpeciesClick(sk)
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

private fun addArbresLayers(style: Style, json: String) {
    val source = GeoJsonSource(
        ARBRES_SOURCE_ID,
        json,
        GeoJsonOptions()
            .withCluster(true)
            .withClusterMaxZoom(14)
            .withClusterRadius(60),
    )
    style.addSource(source)

    // Points individuels (pas dans un cluster). Couleur initiale = gris : la
    // vraie expression case/match est appliquée par `applyDiscoveryColor` dès
    // que le LaunchedEffect collecte les Flows captures (sub-frame).
    val points = CircleLayer(POINTS_LAYER_ID, ARBRES_SOURCE_ID).withProperties(
        PropertyFactory.circleRadius(5f),
        PropertyFactory.circleColor(PIN_GREY),
        PropertyFactory.circleStrokeColor("#FFFFFF"),
        PropertyFactory.circleStrokeWidth(1f),
    )
    points.setFilter(not(has("point_count")))
    style.addLayer(points)

    // Bulles de clusters : rayon fixe pour démarrer, on graduera après.
    // Limite assumée : la couleur cluster ne reflète pas la progression.
    val clusters = CircleLayer(CLUSTERS_LAYER_ID, ARBRES_SOURCE_ID).withProperties(
        PropertyFactory.circleColor(PIN_GREEN),
        PropertyFactory.circleStrokeColor("#FFFFFF"),
        PropertyFactory.circleStrokeWidth(2f),
        PropertyFactory.circleOpacity(0.85f),
        PropertyFactory.circleRadius(20f),
    )
    clusters.setFilter(has("point_count"))
    style.addLayer(clusters)

    // Compte du cluster, en blanc, centré.
    // textFont DOIT pointer une fontstack que le style sert : OpenFreeMap
    // "liberty" sert "Noto Sans Regular". Une fontstack absente déclenche un
    // 404 sur /fonts/ qui invalide le rendu de toute la source côté natif.
    val count = SymbolLayer(CLUSTER_COUNT_LAYER_ID, ARBRES_SOURCE_ID).withProperties(
        PropertyFactory.textField(Expression.toString(get("point_count"))),
        PropertyFactory.textFont(arrayOf("Noto Sans Regular")),
        PropertyFactory.textSize(12f),
        PropertyFactory.textColor("#FFFFFF"),
        PropertyFactory.textAllowOverlap(true),
        PropertyFactory.textIgnorePlacement(true),
    )
    count.setFilter(has("point_count"))
    style.addLayer(count)
}

/**
 * Pré-filtre le GeoJSON pour ne garder que les features dont `properties.sk`
 * vaut `sk`. But : éviter le `std::bad_alloc` qu'on déclenchait côté natif
 * MapLibre quand on tentait de servir 217k features non clusterisées au z11
 * (crash reproduit 2026-04-29). Filtrer en amont ramène le corpus à ~max 38k
 * (Platanus) ou bien moins pour la plupart des espèces, et permet de garder
 * le clustering sur la source filtrée — donc une carte lisible avec
 * clusters d'espèce au dezoom et pins individuels au z14+.
 *
 * Implémentation : la sortie de `tools/build_dataset.py` est très régulière
 * (`json.dumps(separators=(",", ":"))`, ordre des clés stable), donc on peut
 * tokeniser sur `,{"type":"Feature"` et tester le suffixe `"sk":N}}` au lieu
 * de parser/reconstruire 32 Mo de JSON via `JSONObject` (qui exploserait la
 * heap). Coût : un seul scan linéaire de la string + StringBuilder.
 */
private fun filterGeoJsonBySpecies(json: String, sk: Int): String {
    val featureSeparator = ",{\"type\":\"Feature\""
    val skSuffix = "\"sk\":$sk}}"
    val featuresMarker = "\"features\":["
    val openIdx = json.indexOf(featuresMarker).let {
        if (it == -1) return EMPTY_GEOJSON else it + featuresMarker.length
    }
    val closeIdx = json.lastIndexOf("]}")
    if (openIdx >= closeIdx) return EMPTY_GEOJSON

    val sb = StringBuilder(64 * 1024)
    sb.append("{\"type\":\"FeatureCollection\",\"features\":[")
    var first = true
    var pos = openIdx
    while (pos < closeIdx) {
        val nextSep = json.indexOf(featureSeparator, pos + 1)
        val end = if (nextSep == -1 || nextSep >= closeIdx) closeIdx else nextSep
        // `endsWith` est sûr : `sk` est la DERNIÈRE clé de `properties` dans
        // le build script (Python 3.7+ préserve l'ordre d'insertion, et le
        // dump JSON l'utilise). Si on change l'ordre côté Python, casser ce
        // contrat ici se traduit par une carte filtrée vide — ne pas rater.
        if (json.regionMatches(end - skSuffix.length, skSuffix, 0, skSuffix.length)) {
            if (!first) sb.append(",")
            sb.append(json, pos, end)
            first = false
        }
        if (nextSep == -1 || nextSep >= closeIdx) break
        pos = nextSep + 1
    }
    sb.append("]}")
    return sb.toString()
}

private const val EMPTY_GEOJSON = "{\"type\":\"FeatureCollection\",\"features\":[]}"

private fun applyDiscoveryColor(
    style: Style,
    capturedSpecies: Set<Int>,
    capturedRemarquables: Set<Long>,
) {
    val pointsLayer = style.getLayer(POINTS_LAYER_ID) as? CircleLayer ?: return
    pointsLayer.setProperties(
        PropertyFactory.circleColor(
            buildDiscoveryExpression(capturedSpecies, capturedRemarquables)
        )
    )
}

/**
 * `case(remarquable, match-id, match-sk)` :
 *   - pour un pin remarquable capturé, orange ssi son `id` est dans le set ;
 *   - pour un pin normal capturé, vert ssi son `sk` est dans le set ;
 *   - défaut = gris.
 *
 * L'ordre des args du `match` est `[input, label1, out1, …, default]` — default
 * en DERNIER (cf. spec MapLibre style). Ne pas inverser : un default placé en
 * 2e position serait pris pour un label string et l'expression silencieusement
 * ignorée (les pins resteraient à leur couleur initiale).
 *
 * Quand le set est vide, `match` ne tolère pas zéro stop : on retombe sur un
 * `literal(grey)` direct.
 */
private fun buildDiscoveryExpression(
    capturedSpecies: Set<Int>,
    capturedRemarquables: Set<Long>,
): Expression {
    val speciesExpr = if (capturedSpecies.isEmpty()) {
        literal(PIN_GREY)
    } else {
        val stops = mutableListOf<Expression>()
        for (sk in capturedSpecies) {
            stops += literal(sk)
            stops += literal(PIN_GREEN)
        }
        stops += literal(PIN_GREY)
        match(get("sk"), *stops.toTypedArray())
    }
    val remarquableExpr = if (capturedRemarquables.isEmpty()) {
        literal(PIN_GREY)
    } else {
        val stops = mutableListOf<Expression>()
        for (id in capturedRemarquables) {
            // Cast Int : tous les `idbase` parisiens tiennent dans 32 bits,
            // évite les quirks de boxing Long de l'API Java MapLibre.
            stops += literal(id.toInt())
            stops += literal(PIN_ORANGE)
        }
        stops += literal(PIN_GREY)
        match(get("id"), *stops.toTypedArray())
    }
    return switchCase(
        eq(get("remarquable"), literal(true)),
        remarquableExpr,
        speciesExpr,
    )
}

@Composable
private fun FilterBanner(
    entry: SpeciesEntry,
    count: Int?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.widthIn(max = 320.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        shadowElevation = 4.dp,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Retour à la fiche-espèce",
                )
            }
            Column(
                modifier = Modifier
                    .padding(start = 4.dp)
                    .fillMaxWidth(),
            ) {
                Text(
                    entry.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                )
                if (count != null) {
                    Text(
                        "$count arbre${if (count > 1) "s" else ""} dans Paris",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Splash overlay du cold start — superposé tant que `arbresPrets == false`.
 * Couleur de fond synchronisée avec `@color/ic_launcher_background` du splash
 * natif (themes.xml) pour ne pas faire flasher la transition.
 *
 * Animation pure Compose (pas de Lottie) :
 * - Sway sinusoïdal ±3° de la silhouette d'arbre (rotation via graphicsLayer).
 * - Cascade fade+scale à l'apparition (0→1 alpha + 0.85→1 scale, 600 ms).
 * - LinearProgressIndicator or `arbresColors.or` (token de marque) plutôt que
 *   le CircularProgressIndicator blanc générique.
 */
@Composable
private fun ColdStartSplash() {
    // Doit rester en sync avec @color/ic_launcher_background.
    val splashGreen = MaterialTheme.colorScheme.primary
    val arbresColors = MaterialTheme.arbresColors
    val motion = MaterialTheme.arbresMotion

    val infinite = rememberInfiniteTransition(label = "sway")
    val sway by infinite.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = motion.sway, easing = motion.swayEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "swayAngle",
    )
    val intro by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = motion.medium, easing = motion.swayEasing),
        label = "intro",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(splashGreen),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer { alpha = intro },
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier
                    .size(168.dp)
                    .scale(0.85f + intro * 0.15f)
                    .graphicsLayer { rotationZ = sway },
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = "Arbres",
                color = Color.White,
                style = MaterialTheme.typography.displayMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Réveil des 217 855 arbres parisiens",
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(28.dp))
            LinearProgressIndicator(
                color = arbresColors.or,
                trackColor = Color.White.copy(alpha = 0.18f),
                modifier = Modifier
                    .widthIn(max = 200.dp)
                    .fillMaxWidth(0.45f),
            )
        }
    }
}

/**
 * Splash overlay dédié au mode `MAP_FILTERED` : on ne charge pas 217k arbres,
 * juste une espèce filtrée (pré-filtre Kotlin < 1 s + setStyle MapLibre 1-3 s).
 * Pas de silhouette d'arbre (pas un boot), pas d'animation infinie — juste
 * un voile bref pour éviter le flash de carte vide.
 */
@Composable
private fun FilterSplash(speciesLabel: String) {
    val splashGreen = MaterialTheme.colorScheme.primary
    val motion = MaterialTheme.arbresMotion
    val intro by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = motion.short, easing = motion.swayEasing),
        label = "filterIntro",
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(splashGreen),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(horizontal = 48.dp)
                .graphicsLayer { alpha = intro },
        ) {
            Text(
                text = "Filtrage de $speciesLabel…",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.5.dp,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}
