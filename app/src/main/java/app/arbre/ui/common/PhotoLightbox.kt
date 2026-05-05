package app.arbre.ui.common

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Visionneuse plein écran pour les photos user (galeries fiche-espèce et
 * fiche-remarquable). Pinch-zoom 1×→5× + pan via `transformable`, double-tap
 * pour reset, tap simple pour fermer. Pas de swipe entre photos — on garde
 * minimaliste.
 *
 * `selectedIndex == null` → Dialog non monté, économise les recompositions.
 */
@Composable
fun PhotoLightbox(
    photoFiles: List<File>,
    selectedIndex: Int?,
    onDismiss: () -> Unit,
) {
    if (selectedIndex == null) return
    val file = photoFiles.getOrNull(selectedIndex) ?: return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = true,
            dismissOnBackPress = true,
        ),
    ) {
        var bitmap by remember(file) { mutableStateOf<ImageBitmap?>(null) }
        LaunchedEffect(file) {
            bitmap = withContext(Dispatchers.IO) {
                runCatching {
                    BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
                }.getOrNull()
            }
        }

        var scale by remember(file) { mutableStateOf(1f) }
        var offset by remember(file) { mutableStateOf(Offset.Zero) }
        val transformState = rememberTransformableState { zoomChange, panChange, _ ->
            scale = (scale * zoomChange).coerceIn(1f, 5f)
            offset += panChange
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .transformable(state = transformState)
                .pointerInput(file) {
                    detectTapGestures(
                        onTap = { onDismiss() },
                        onDoubleTap = {
                            scale = 1f
                            offset = Offset.Zero
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            val bm = bitmap
            if (bm != null) {
                Image(
                    bitmap = bm,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        },
                )
            }
        }
    }
}
