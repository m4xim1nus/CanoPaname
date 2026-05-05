package app.arbre.ui.common

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Affiche un fichier image local (issu d'une capture utilisateur) en
 * downsamplant agressivement au moment du décodage. Le `inSampleSize` est
 * paramétrable : 4 pour les vignettes 72 dp de l'Arboretum, 2 pour la galerie
 * de la fiche-espèce qui peut s'étendre.
 */
@Composable
fun PhotoThumbnail(
    photoFile: File,
    modifier: Modifier = Modifier,
    sampleSize: Int = 4,
) {
    var bitmap by remember(photoFile, sampleSize) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(photoFile, sampleSize) {
        bitmap = withContext(Dispatchers.IO) {
            val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            try {
                BitmapFactory.decodeFile(photoFile.absolutePath, opts)?.asImageBitmap()
            } catch (e: Throwable) {
                null
            }
        }
    }
    val bm = bitmap
    if (bm != null) {
        Image(
            bitmap = bm,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    } else {
        Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant))
    }
}
