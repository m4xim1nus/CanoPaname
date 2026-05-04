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

/**
 * Galerie horizontale des photos prises par l'utilisateur (fiche-espèce ou
 * fiche-remarquable). `onPhotoClick(index)` ouvre généralement un
 * `PhotoLightbox` sur l'index correspondant.
 */
@Composable
fun PhotoGallery(
    photoPaths: List<String>,
    modifier: Modifier = Modifier,
    title: String = "Tes photos (${photoPaths.size})",
    onPhotoClick: (Int) -> Unit = {},
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(photoPaths) { index, path ->
                PhotoThumbnail(
                    photoPath = path,
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
