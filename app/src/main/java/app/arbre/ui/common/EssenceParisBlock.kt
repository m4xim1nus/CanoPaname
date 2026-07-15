package app.arbre.ui.common

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Encart « L'essence à Paris » de la fiche espèce : prose éditoriale Ville de
 * Paris + lien vers la fiche essence PDF officielle. Calqué sur [WikipediaBlock]
 * (Card, lien cliquable, ligne d'attribution).
 *
 * Fusionne l'ancien `EssencePdfBlock`. Cas :
 * - `paris != null` : titre « L'essence à Paris » + prose ; lien PDF en footer si
 *   `pdfUrl != null`.
 * - `paris == null && pdfUrl != null` : fallback card lien PDF seule (titre
 *   « Fiche essence Ville de Paris », sans prose) — comportement historique.
 * - les deux `null` : rien (le call-site ne monte alors pas ce composable).
 */
@Composable
fun EssenceParisBlock(paris: String?, pdfUrl: String?, modifier: Modifier = Modifier) {
    if (paris == null && pdfUrl == null) return
    val ctx = LocalContext.current
    val title = if (paris != null) "L'essence à Paris" else "Fiche essence Ville de Paris"

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (paris != null) {
                Text(paris, style = MaterialTheme.typography.bodyMedium)
            }
            if (pdfUrl != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            runCatching {
                                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(pdfUrl)))
                            }
                        }
                        .padding(vertical = 8.dp),
                ) {
                    Icon(
                        Icons.Outlined.PictureAsPdf,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        "Fiche essence (PDF)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Icon(
                        Icons.AutoMirrored.Outlined.OpenInNew,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Text(
                "Source : Ville de Paris · Guide des essences",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
