package app.arbre.ui.species

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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.arbre.data.ArrCount
import app.arbre.data.SpeciesStats
import app.arbre.ui.theme.ArbresTheme
import java.text.NumberFormat
import java.util.Locale

/**
 * Card « À Paris » de la fiche espèce : fusionne l'ancien `StatsBlock` (stats
 * dataset) et l'encart « L'essence à Paris » (`EssenceParisBlock`). La prose
 * éditoriale Ville de Paris devient l'intro ; suivent le compteur, les mesures
 * médianes, les arrondissements dominants / sur-représentés, le lien PDF de la
 * fiche essence officielle et l'attribution source.
 *
 * Chaque section est omise si sa donnée est absente. Le call-site monte toujours
 * ce bloc dès qu'il y a des `stats` (la partie compteur est alors garantie).
 */
@Composable
fun AParisBlock(stats: SpeciesStats, essenceParis: String?, pdfUrl: String?) {
    val ctx = LocalContext.current

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("À Paris", style = MaterialTheme.typography.titleMedium)

            if (essenceParis != null) {
                Text(essenceParis, style = MaterialTheme.typography.bodyMedium)
            }

            Text(
                "${formatCount(stats.count)} arbres · ${formatPercent(stats.proportion)} des arbres parisiens",
                style = MaterialTheme.typography.bodyLarge,
            )

            val measures = listOfNotNull(
                stats.medianHeightM?.let { "Hauteur médiane $it m" },
                stats.medianCircCm?.let { "Circonférence médiane $it cm" },
            )
            if (measures.isNotEmpty()) {
                Text(
                    measures.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (stats.topArrAbs.isNotEmpty()) {
                ArrLine(
                    label = "Plus nombreux : ",
                    text = stats.topArrAbs.joinToString(" · ") {
                        "${it.arr} (${formatCount(it.count)})"
                    },
                )
            }
            if (stats.topArrOver.isNotEmpty()) {
                ArrLine(
                    label = "Sur-représentés : ",
                    text = stats.topArrOver.joinToString(" · ") { item ->
                        val ratio = item.ratio?.let { formatRatio(it) }
                        if (ratio == null) item.arr else "${item.arr} ($ratio)"
                    },
                )
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

            if (essenceParis != null || pdfUrl != null) {
                Text(
                    "Source : Ville de Paris · Guide des essences",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Ligne annotée « label (SemiBold) : liste d'arrondissements ». */
@Composable
private fun ArrLine(label: String, text: String) {
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(label) }
            append(text)
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

private val FR_LOCALE: Locale = Locale.FRENCH
private val FR_NUMBER: NumberFormat = NumberFormat.getInstance(FR_LOCALE)
private val FR_PERCENT: NumberFormat = NumberFormat.getPercentInstance(FR_LOCALE).apply {
    minimumFractionDigits = 1
    maximumFractionDigits = 1
}
private val FR_RATIO: NumberFormat = NumberFormat.getInstance(FR_LOCALE).apply {
    minimumFractionDigits = 1
    maximumFractionDigits = 1
}

private fun formatCount(n: Int): String = FR_NUMBER.format(n)

private fun formatPercent(p: Double): String = FR_PERCENT.format(p)

private fun formatRatio(r: Double): String = "×${FR_RATIO.format(r)}"

@Preview(showBackground = true)
@Composable
private fun AParisBlockPreview() {
    ArbresTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            AParisBlock(
                stats = SpeciesStats(
                    count = 294,
                    proportion = 0.0014,
                    medianHeightM = 5,
                    medianCircCm = 35,
                    topArrAbs = listOf(
                        ArrCount("17e", 45, null),
                        ArrCount("12e", 44, null),
                        ArrCount("13e", 42, null),
                    ),
                    topArrOver = listOf(
                        ArrCount("6e", 4, 4.9),
                        ArrCount("11e", 3, 3.3),
                    ),
                ),
                essenceParis = "Essence emblématique des grands boulevards parisiens.",
                pdfUrl = "https://example.org/fiche.pdf",
            )
        }
    }
}
