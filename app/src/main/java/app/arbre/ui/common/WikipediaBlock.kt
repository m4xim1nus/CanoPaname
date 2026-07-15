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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Card « À propos » réutilisée par les fiches espèce et fiches genre.
 *
 * Rend le `summary` Wikipedia FR + lien sortant vers l'article + mention
 * licence CC BY-SA. Si `summary` est blank, affiche le placeholder
 * `emptyMessage`. Le titre Wikipedia (`wikipediaTitle`) est utilisé pour
 * construire l'URL : convention `espace → underscore`.
 *
 * `collapsedLines` (optionnel) : quand renseigné et qu'un `summary` est affiché,
 * le texte est tronqué à ce nombre de lignes tant qu'il déborde, avec un lien
 * « Lire la suite » / « Réduire » pour basculer. `null` (défaut) = comportement
 * historique intact (texte intégral, pas de troncature) — la fiche genre appelle
 * sans ce paramètre et ne change pas. Repli sans animation (contrat device de Max).
 */
@Composable
fun WikipediaBlock(
    summary: String?,
    wikipediaTitle: String?,
    modifier: Modifier = Modifier,
    title: String = "À propos",
    emptyMessage: String = "Pas d'info encyclopédique disponible.",
    collapsedLines: Int? = null,
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
                SummaryText(summary, collapsedLines)
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

/**
 * Le `summary` avec troncature optionnelle : `collapsedLines` non-null → texte
 * limité à ce nombre de lignes tant qu'il déborde, lien « Lire la suite » /
 * « Réduire » pour basculer (repli sans animation). `null` → texte intégral.
 */
@Composable
private fun SummaryText(summary: String, collapsedLines: Int?) {
    if (collapsedLines == null) {
        Text(summary, style = MaterialTheme.typography.bodyMedium)
        return
    }
    var expanded by remember(summary) { mutableStateOf(false) }
    var hasOverflow by remember(summary) { mutableStateOf(false) }
    Text(
        summary,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = if (expanded) Int.MAX_VALUE else collapsedLines,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { result ->
            if (!expanded) hasOverflow = result.hasVisualOverflow
        },
    )
    if (!expanded && hasOverflow) {
        Text(
            "Lire la suite",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable { expanded = true },
        )
    } else if (expanded) {
        Text(
            "Réduire",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable { expanded = false },
        )
    }
}

private fun wikipediaUrlPath(title: String): String = title.replace(' ', '_')
