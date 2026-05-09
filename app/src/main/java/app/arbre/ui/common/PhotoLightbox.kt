package app.arbre.ui.common

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

/**
 * Visionneuse plein écran pour les photos user (galeries fiche-espèce et
 * fiche-remarquable). Pinch-zoom 1×→5× + pan clampé aux bords, double-tap
 * pour reset, tap simple pour fermer. Swipe horizontal et chevrons pour
 * naviguer entre photos d'une même galerie ; pager gelé dès qu'on zoome.
 *
 * `selectedIndex == null` → Dialog non monté, économise les recompositions.
 */
@Composable
fun PhotoLightbox(
    photoFiles: List<File>,
    selectedIndex: Int?,
    onDismiss: () -> Unit,
    onDeleteAt: ((Int) -> Unit)? = null,
) {
    if (selectedIndex == null) return
    if (photoFiles.isEmpty()) return
    val initialPage = selectedIndex.coerceIn(0, photoFiles.lastIndex)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = true,
            dismissOnBackPress = true,
        ),
    ) {
        val pagerState = rememberPagerState(initialPage = initialPage) { photoFiles.size }
        val scope = rememberCoroutineScope()
        var currentScale by remember { mutableStateOf(1f) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = currentScale == 1f,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                ZoomablePage(
                    file = photoFiles[page],
                    onScaleChange = {
                        if (page == pagerState.currentPage) currentScale = it
                    },
                    onTap = onDismiss,
                )
            }

            if (photoFiles.size > 1) {
                IconButton(
                    onClick = {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                    },
                    enabled = pagerState.currentPage > 0,
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = Color.White,
                        disabledContentColor = Color.White.copy(alpha = 0.3f),
                    ),
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 8.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.4f)),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ChevronLeft,
                        contentDescription = "Photo précédente",
                    )
                }
                IconButton(
                    onClick = {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    },
                    enabled = pagerState.currentPage < photoFiles.lastIndex,
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = Color.White,
                        disabledContentColor = Color.White.copy(alpha = 0.3f),
                    ),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 8.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.4f)),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ChevronRight,
                        contentDescription = "Photo suivante",
                    )
                }
            }

            if (onDeleteAt != null) {
                val statusBarPadding = WindowInsets.statusBars.asPaddingValues()
                IconButton(
                    onClick = { onDeleteAt(pagerState.currentPage) },
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = Color.White,
                    ),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = statusBarPadding.calculateTopPadding() + 8.dp, end = 8.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.4f)),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "Supprimer cette photo",
                    )
                }
            }
        }
    }
}

@Composable
private fun ZoomablePage(
    file: File,
    onScaleChange: (Float) -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
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
    var boxSize by remember { mutableStateOf(IntSize.Zero) }

    fun clampOffset(candidate: Offset, atScale: Float): Offset {
        val bm = bitmap ?: return Offset.Zero
        if (boxSize.width <= 0 || boxSize.height <= 0) return Offset.Zero
        val boxW = boxSize.width.toFloat()
        val boxH = boxSize.height.toFloat()
        val imgW = bm.width.toFloat()
        val imgH = bm.height.toFloat()
        val fitScale = minOf(boxW / imgW, boxH / imgH)
        val fitW = imgW * fitScale
        val fitH = imgH * fitScale
        val maxPanX = max(0f, (fitW * atScale - boxW) / 2f)
        val maxPanY = max(0f, (fitH * atScale - boxH) / 2f)
        return Offset(
            candidate.x.coerceIn(-maxPanX, maxPanX),
            candidate.y.coerceIn(-maxPanY, maxPanY),
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { boxSize = it }
            .pointerInput(file) {
                // Détecteur custom : consomme le pinch (multi-touch) toujours,
                // et le pan 1-doigt seulement si scale > 1f. À scale=1f + 1 doigt
                // on ne consomme rien → le HorizontalPager parent prend le swipe.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pressedCount = event.changes.count { it.pressed }
                        if (pressedCount >= 2) {
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            val newScale = (scale * zoomChange).coerceIn(1f, 5f)
                            scale = newScale
                            offset = clampOffset(offset + panChange, newScale)
                            onScaleChange(newScale)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        } else if (pressedCount == 1 && scale > 1f) {
                            val panChange = event.calculatePan()
                            offset = clampOffset(offset + panChange, scale)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .pointerInput(file) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        scale = 1f
                        offset = Offset.Zero
                        onScaleChange(1f)
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
