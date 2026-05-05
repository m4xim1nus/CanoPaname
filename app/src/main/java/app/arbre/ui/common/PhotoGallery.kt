package app.arbre.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import java.io.File

/**
 * Galerie horizontale des photos prises par l'utilisateur (fiche-espèce ou
 * fiche-remarquable). `onPhotoClick(index)` ouvre généralement un
 * `PhotoLightbox` sur l'index correspondant.
 */
@Composable
fun PhotoGallery(
    photoFiles: List<File>,
    modifier: Modifier = Modifier,
    title: String = "Tes photos (${photoFiles.size})",
    onPhotoClick: (Int) -> Unit = {},
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(photoFiles) { index, file ->
                PhotoThumbnail(
                    photoFile = file,
                    sampleSize = 2,
                    modifier = Modifier
                        .size(120.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onPhotoClick(index) },
                )
            }
        }
    }
}
