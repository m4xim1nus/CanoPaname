package app.arbre.ui.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.RectF
import android.location.Location
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.arbre.R
import app.arbre.data.rememberArbreRepository
import app.arbre.data.rememberCaptureRepository
import app.arbre.data.rememberSpeciesIndex
import app.arbre.ui.detail.ArbreDetailContent
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
private const val USER_ZOOM = 16.0
private const val ARBRES_SOURCE_ID = "arbres-source"
private const val POINTS_LAYER_ID = "arbres-points"
private const val CLUSTERS_LAYER_ID = "arbres-clusters"
private const val CLUSTER_COUNT_LAYER_ID = "arbres-cluster-count"
private const val ARBRES_ASSET_PATH = "arbres-paris.geojson"
private const val PIN_GREEN = "#2E7D32"
private const val PIN_ORANGE = "#FB8C00"
private const val PIN_GREY = "#9E9E9E"

private fun parisCamera(): CameraPosition =
    CameraPosition.Builder().target(PARIS).zoom(PARIS_ZOOM).build()

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
fun MapScreen(onArboretumClick: () -> Unit = {}) {
    val ctx = LocalContext.current
    val styleUrl = stringResource(R.string.map_style_url)
    val repo = rememberArbreRepository()
    val captureRepo = rememberCaptureRepository()
    val speciesIndex = rememberSpeciesIndex()
    val viewModel: MapViewModel = viewModel(
        factory = viewModelFactory {
            initializer { MapViewModel(repo, createSavedStateHandle()) }
        }
    )

    val capturedSpecies by captureRepo.capturedSpeciesIndices()
        .collectAsState(initial = emptySet())
    val capturedRemarquables by captureRepo.capturedRemarquableIds()
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
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(styleRef) {
        val style = styleRef ?: return@LaunchedEffect
        combine(
            captureRepo.capturedSpeciesIndices(),
            captureRepo.capturedRemarquableIds(),
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
        val loc = LocationProvider.currentOrLastKnown(ctx)
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
            scope.launch { centerOnUser() }
        } else {
            scope.launch { snackbar.showSnackbar("Permission de localisation refusée") }
        }
    }

    DisposableEffect(Unit) {
        mapView.onCreate(null)
        mapView.onStart()
        mapView.onResume()
        mapView.getMapAsync { map ->
            mapRef = map
            scope.launch {
                map.cameraPosition = viewModel.lastCamera ?: computeInitialCamera(ctx)

                map.setStyle(Style.Builder().fromUri(styleUrl)) { style ->
                    if (LocationProvider.hasFineLocationPermission(ctx)) {
                        enableLocationPin(map, style)
                    }
                    scope.launch {
                        try {
                            val json = withContext(Dispatchers.IO) {
                                ctx.assets.open(ARBRES_ASSET_PATH).bufferedReader()
                                    .use { it.readText() }
                            }
                            android.util.Log.i(
                                "MapScreen",
                                "GeoJSON chargé : ${json.length / 1_000_000} Mo, premier caractère=${json.firstOrNull()}",
                            )
                            addArbresLayers(style, json)
                            styleRef = style
                            android.util.Log.i("MapScreen", "Source + layers ajoutées")
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
        }
    }

    fun onStarClick() {
        scope.launch {
            val loc = LocationProvider.currentOrLastKnown(ctx)
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
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FloatingActionButton(onClick = ::onStarClick) {
                Icon(Icons.Default.Star, contentDescription = "Plus proche remarquable")
            }
            FloatingActionButton(onClick = onArboretumClick) {
                Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = "Arboretum")
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
            Icon(Icons.Default.MyLocation, contentDescription = "Me localiser")
        }
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        val capturer = rememberCaptureController(
            viewModel = viewModel,
            captureRepo = captureRepo,
            speciesIndex = speciesIndex,
            snackbar = snackbar,
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
            var availability by remember(openedArbre.id) {
                mutableStateOf<CaptureAvailability?>(null)
            }
            LaunchedEffect(openedArbre.id) {
                availability = captureAvailability(ctx, openedArbre)
            }
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
