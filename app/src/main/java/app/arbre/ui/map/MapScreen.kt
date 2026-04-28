package app.arbre.ui.map

import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import app.arbre.R
import app.arbre.data.ArbreRepository
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

private val PARIS = LatLng(48.8566, 2.3522)

@Composable
fun MapScreen(onArbreClick: (Long) -> Unit) {
    val ctx = LocalContext.current
    val styleUrl = stringResource(R.string.map_style_url)
    val repo = remember { ArbreRepository() }

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
                .zoom(13.0)
                .build()
            map.setStyle(Style.Builder().fromUri(styleUrl)) {
                // TODO: ajouter une couche symbol pour les arbres dans la bbox visible.
                // Source: ArbreRepository.arbresDansBbox(...)
                @Suppress("UNUSED_VARIABLE")
                val sample = ArbreRepository.SAMPLE
            }
            map.addOnMapClickListener { latLng ->
                // Stub: clic sur la carte → ouvre le premier arbre échantillon.
                ArbreRepository.SAMPLE.firstOrNull()?.let { onArbreClick(it.id) }
                true
            }
        }
        onDispose {
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = { mapView })
    }
}
