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
 * Card « À propos » réutilisée par les fiches espèce et fiches genre.
 *
 * Rend le `summary` Wikipedia FR + lien sortant vers l'article + mention
 * licence CC BY-SA. Si `summary` est blank, affiche le placeholder
 * `emptyMessage`. Le titre Wikipedia (`wikipediaTitle`) est utilisé pour
 * construire l'URL : convention `espace → underscore`.
 */
@Composable
fun WikipediaBlock(
    summary: String?,
    wikipediaTitle: String?,
    modifier: Modifier = Modifier,
    title: String = "À propos",
    emptyMessage: String = "Pas d'info encyclopédique disponible.",
) {
    val ctx = LocalContext.current
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (summary.isNullOrBlank()) {
                Text(
                    emptyMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(summary, style = MaterialTheme.typography.bodyMedium)
                if (!wikipediaTitle.isNullOrBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val url =
                                    "https://fr.wikipedia.org/wiki/${wikipediaUrlPath(wikipediaTitle)}"
                                runCatching {
                                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                }
                            }
                            .padding(vertical = 8.dp),
                    ) {
                        Text(
                            "Lire sur Wikipedia",
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            runCatching {
                                ctx.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("https://creativecommons.org/licenses/by-sa/4.0/"),
                                    )
                                )
                            }
                        }
                        .padding(vertical = 4.dp),
                ) {
                    Text(
                        "Source : Wikipédia FR · CC BY-SA 4.0",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Icon(
                        Icons.AutoMirrored.Outlined.OpenInNew,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
    }
}

private fun wikipediaUrlPath(title: String): String = title.replace(' ', '_')
