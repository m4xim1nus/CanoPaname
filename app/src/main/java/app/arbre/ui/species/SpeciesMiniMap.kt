package app.arbre.ui.species

import android.util.Log
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import app.arbre.ArbresApp
import app.arbre.R
import kotlinx.coroutines.launch
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.eq
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource

/**
 * Mini-carte de la fiche-espèce : carte centrée sur Paris (z11) qui n'affiche
 * que les pins de l'espèce demandée.
 *
 * Volontairement non clusterisée : sans filtre côté source, les clusters
 * agrègeraient tous les arbres et donneraient une distribution trompeuse.
 * Pour les espèces denses (Platanus 38k), MapLibre rend les pins individuels
 * sans souci à z11.
 *
 * Pas de FABs, pas de tap, pas de location pin — c'est une vignette
 * informative, pas un écran interactif. Source GeoJSON dédiée (reparse côté
 * MapLibre) ; coût RAM borné à la durée d'affichage de la fiche.
 */
private const val MINI_SOURCE_ID = "species-mini-source"
private const val MINI_POINTS_LAYER_ID = "species-mini-points"
private const val PIN_GREEN = "#2E7D32"
private val PARIS = LatLng(48.8566, 2.3522)
private const val PARIS_OVERVIEW_ZOOM = 11.0

@Composable
fun SpeciesMiniMap(
    speciesIndex: Int,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as ArbresApp
    val styleUrl = stringResource(R.string.map_style_url)
    val scope = rememberCoroutineScope()

    val mapView = remember {
        MapView(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
    }

    DisposableEffect(Unit) {
        mapView.onCreate(null)
        mapView.onStart()
        mapView.onResume()
        mapView.getMapAsync { map ->
            map.cameraPosition = CameraPosition.Builder()
                .target(PARIS)
                .zoom(PARIS_OVERVIEW_ZOOM)
                .build()
            map.setStyle(Style.Builder().fromUri(styleUrl)) { style ->
                scope.launch {
                    try {
                        val json = app.arbresGeoJsonAsync.await()
                        addSpeciesLayer(style, json, speciesIndex)
                    } catch (e: Throwable) {
                        Log.e("SpeciesMiniMap", "Échec chargement layer", e)
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

    AndroidView(factory = { mapView }, modifier = modifier)
}

private fun addSpeciesLayer(style: Style, json: String, speciesIndex: Int) {
    style.addSource(GeoJsonSource(MINI_SOURCE_ID, json))
    val points = CircleLayer(MINI_POINTS_LAYER_ID, MINI_SOURCE_ID).withProperties(
        PropertyFactory.circleRadius(4f),
        PropertyFactory.circleColor(PIN_GREEN),
        PropertyFactory.circleStrokeColor("#FFFFFF"),
        PropertyFactory.circleStrokeWidth(1f),
    )
    points.setFilter(eq(get("sk"), literal(speciesIndex)))
    style.addLayer(points)
}
