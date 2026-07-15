package app.arbre.ui.common

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Décode une image embarquée en asset (`context.assets.open(...)`) en
 * downsamplant via `inSampleSize`. Bloquant → à appeler hors du thread UI
 * (Dispatchers.IO). Toute erreur (asset absent, décodage KO) → null.
 */
internal fun decodeAssetBitmap(context: Context, assetPath: String, sampleSize: Int): ImageBitmap? {
    val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    return try {
        context.assets.open(assetPath).use { stream ->
            BitmapFactory.decodeStream(stream, null, opts)?.asImageBitmap()
        }
    } catch (_: Throwable) {
        null
    }
}

/**
 * Affiche une image embarquée en asset (photos de référence des espèces),
 * décodée en IO avec `inSampleSize`. Calqué sur `PhotoThumbnail` : placeholder
 * `surfaceVariant` tant que le bitmap n'est pas prêt — jamais de spinner
 * (animations gelées sur le device de Max, échelle d'animation 0).
 */
@Composable
fun AssetImage(
    assetPath: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    sampleSize: Int = 2,
) {
    val context = LocalContext.current
    var bitmap by remember(assetPath, sampleSize) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(assetPath, sampleSize) {
        bitmap = withContext(Dispatchers.IO) {
            decodeAssetBitmap(context, assetPath, sampleSize)
        }
    }
    val bm = bitmap
    if (bm != null) {
        Image(
            bitmap = bm,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier,
        )
    } else {
        Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant))
    }
}
