package app.arbre.ui.common

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.arbre.data.ReferencePhoto
import kotlinx.coroutines.CoroutineScope
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
 *
 * Le squelette (Dialog + pager + chevrons + gestes) est mutualisé avec
 * `ReferencePhotoLightbox` (photos de référence) via `LightboxScaffold` +
 * `ZoomablePage` ; seuls diffèrent le chargement du bitmap et les overlays.
 */
@Composable
fun PhotoLightbox(
    photoFiles: List<File>,
    selectedIndex: Int?,
    onDismiss: () -> Unit,
    onDeleteAt: ((Int) -> Unit)? = null,
    onJumpToMapAt: ((Int) -> Unit)? = null,
) {
    if (selectedIndex == null) return
    if (photoFiles.isEmpty()) return
    val initialPage = selectedIndex.coerceIn(0, photoFiles.lastIndex)

    LightboxScaffold(
        pageCount = photoFiles.size,
        initialPage = initialPage,
        onDismiss = onDismiss,
        overlay = { pagerState ->
            CaptureLightboxOverlay(pagerState, onDismiss, onDeleteAt, onJumpToMapAt)
        },
    ) { page, onScaleChange ->
        ZoomablePage(
            key = photoFiles[page],
            loader = {
                runCatching {
                    BitmapFactory.decodeFile(photoFiles[page].absolutePath)?.asImageBitmap()
                }.getOrNull()
            },
            onScaleChange = onScaleChange,
            onTap = onDismiss,
        )
    }
}

/**
 * Visionneuse plein écran pour les photos de **référence** embarquées (assets
 * `species-photos/`). Même squelette que `PhotoLightbox` (Dialog + pager +
 * chevrons + gestes) mais : décodage asset plein résolution (`sampleSize = 1`),
 * pas de delete/jump-to-map, et une caption d'attribution collée en bas (auteur
 * · licence + lien source). L'obligation CC-BY (crédit visible) est portée par
 * cette caption.
 */
@Composable
fun ReferencePhotoLightbox(
    photos: List<ReferencePhoto>,
    licenseName: String?,
    selectedIndex: Int?,
    onDismiss: () -> Unit,
    onOpenSource: (String) -> Unit,
) {
    if (selectedIndex == null) return
    if (photos.isEmpty()) return
    val context = LocalContext.current
    val initialPage = selectedIndex.coerceIn(0, photos.lastIndex)

    LightboxScaffold(
        pageCount = photos.size,
        initialPage = initialPage,
        onDismiss = onDismiss,
        overlay = { pagerState ->
            ReferenceAttributionCaption(
                photo = photos[pagerState.currentPage],
                licenseName = licenseName,
                onOpenSource = onOpenSource,
            )
        },
    ) { page, onScaleChange ->
        ZoomablePage(
            key = photos[page].file,
            loader = { decodeAssetBitmap(context, photos[page].assetPath, sampleSize = 1) },
            onScaleChange = onScaleChange,
            onTap = onDismiss,
        )
    }
}

/**
 * Squelette commun : Dialog plein écran + HorizontalPager (gelé quand on zoome)
 * + chevrons prev/next. `pageContent` rend une page (typiquement `ZoomablePage`) ;
 * son `onScaleChange` ne remonte que pour la page courante (gate du pager).
 * `overlay` place les commandes propres à chaque visionneuse (delete/jump ou
 * caption) et reçoit le `PagerState` pour refléter la page courante.
 */
@Composable
private fun LightboxScaffold(
    pageCount: Int,
    initialPage: Int,
    onDismiss: () -> Unit,
    overlay: @Composable BoxScope.(PagerState) -> Unit,
    pageContent: @Composable (page: Int, onScaleChange: (Float) -> Unit) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = true,
            dismissOnBackPress = true,
        ),
    ) {
        val pagerState = rememberPagerState(initialPage = initialPage) { pageCount }
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
                pageContent(page) {
                    if (page == pagerState.currentPage) currentScale = it
                }
            }

            if (pageCount > 1) {
                LightboxChevrons(pagerState, scope)
            }

            overlay(pagerState)
        }
    }
}

/** Chevrons prev/next, position et style identiques aux deux visionneuses. */
@Composable
private fun BoxScope.LightboxChevrons(pagerState: PagerState, scope: CoroutineScope) {
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
        enabled = pagerState.currentPage < pagerState.pageCount - 1,
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

/** Commandes de la lightbox captures : voir-sur-carte (haut gauche) + supprimer (haut droite). */
@Composable
private fun BoxScope.CaptureLightboxOverlay(
    pagerState: PagerState,
    onDismiss: () -> Unit,
    onDeleteAt: ((Int) -> Unit)?,
    onJumpToMapAt: ((Int) -> Unit)?,
) {
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues()
    if (onJumpToMapAt != null) {
        IconButton(
            onClick = {
                // Ferme la lightbox avant la navigation, évite le
                // flicker à l'ouverture du sheet de la map.
                val idx = pagerState.currentPage
                onDismiss()
                onJumpToMapAt(idx)
            },
            colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = statusBarPadding.calculateTopPadding() + 8.dp, start = 8.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.4f)),
        ) {
            Icon(
                imageVector = Icons.Outlined.Map,
                contentDescription = "Voir sur la carte",
            )
        }
    }
    if (onDeleteAt != null) {
        IconButton(
            onClick = { onDeleteAt(pagerState.currentPage) },
            colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
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

/**
 * Caption d'attribution en bas de la lightbox de référence : « auteur · licence »
 * sur fond noir semi-transparent, plus un bouton OpenInNew vers la source quand
 * une URL est disponible. Reflète la photo de la page courante.
 */
@Composable
private fun BoxScope.ReferenceAttributionCaption(
    photo: ReferencePhoto,
    licenseName: String?,
    onOpenSource: (String) -> Unit,
) {
    val label = buildString {
        append(photo.author)
        if (licenseName != null) append(" · ").append(licenseName)
    }
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(bottom = navBarPadding.calculateBottomPadding())
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White,
            modifier = Modifier.weight(1f),
        )
        val url = photo.sourceUrl
        if (url != null) {
            IconButton(
                onClick = { onOpenSource(url) },
                colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                    contentDescription = "Ouvrir la source",
                )
            }
        }
    }
}

/**
 * Page zoomable générique : le chargement du bitmap est délégué à `loader`
 * (fichier local ou asset), re-clé sur `key`. La logique de gestes (pinch
 * 1×→5×, pan clampé, double-tap reset) est identique quelle que soit la source.
 */
@Composable
private fun ZoomablePage(
    key: Any,
    loader: suspend () -> ImageBitmap?,
    onScaleChange: (Float) -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var bitmap by remember(key) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(key) {
        bitmap = withContext(Dispatchers.IO) { loader() }
    }

    var scale by remember(key) { mutableStateOf(1f) }
    var offset by remember(key) { mutableStateOf(Offset.Zero) }
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
            .pointerInput(key) {
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
            .pointerInput(key) {
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
