package app.arbre.ui.species

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import app.arbre.data.Arbre
import app.arbre.data.ArrCount
import app.arbre.data.Capture
import app.arbre.data.SpeciesEntry
import app.arbre.data.SpeciesInfo
import app.arbre.data.SpeciesStats
import app.arbre.data.rememberArbreRepository
import app.arbre.data.rememberCaptureRepository
import app.arbre.data.rememberSpeciesIndex
import app.arbre.data.rememberSpeciesInfoRepository
import app.arbre.ui.common.PhotoThumbnail
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeciesDetailScreen(
    speciesIndex: Int,
    onBack: () -> Unit,
    onShowOnMap: () -> Unit = {},
    celebrate: Boolean = false,
) {
    val speciesIndexRepo = rememberSpeciesIndex()
    val arbreRepo = rememberArbreRepository()
    val captureRepo = rememberCaptureRepository()
    val speciesInfoRepo = rememberSpeciesInfoRepository()

    val entry = speciesIndexRepo.get(speciesIndex)
    if (entry == null) {
        // Index inconnu : la nav s'auto-ferme.
        LaunchedEffect(speciesIndex) { onBack() }
        return
    }
    val info = speciesInfoRepo.get(speciesIndex)

    var arbreSample by remember(entry) { mutableStateOf<Arbre?>(null) }
    LaunchedEffect(entry) {
        arbreSample = arbreRepo.unArbreParEspece(entry.genre, entry.espece)
    }

    val captures by remember(speciesIndex) {
        captureRepo.toutesLesCaptures()
            .map { all -> all.filter { it.speciesIndex == speciesIndex } }
    }.collectAsState(initial = emptyList())

    val title = arbreSample?.nomCommun ?: entry.displayName

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (celebrate) {
                item { CelebrationBanner() }
            }

            item { IdentityBlock(entry, arbreSample) }

            if (captures.isNotEmpty()) {
                item { PhotoGallery(captures) }
            }

            item { WikipediaBlock(info) }

            info?.stats?.let { stats ->
                item { StatsBlock(stats) }
            }

            item { ShowOnMapButton(onShowOnMap) }
        }
    }
}

@Composable
private fun ShowOnMapButton(onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(
            Icons.Default.Map,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Text(
            "Voir sur la carte",
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun CelebrationBanner() {
    // Volontairement sobre : pas d'animation pétaradante, app perso. Le badge
    // tonal + l'étoile suffisent à signaler la 1re capture sans rendre la
    // navigation Arboretum→Espèce visuellement bruyante.
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Default.Star,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(32.dp),
            )
            Column {
                Text(
                    "Nouvelle espèce débloquée !",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    "Ajoutée à ton Arboretum",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
    }
}

@Composable
private fun IdentityBlock(entry: SpeciesEntry, sample: Arbre?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                sample?.nomCommun ?: entry.displayName,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                entry.displayName,
                style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun PhotoGallery(captures: List<Capture>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Tes photos (${captures.size})",
            style = MaterialTheme.typography.titleMedium,
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(captures, key = { it.id }) { capture ->
                PhotoThumbnail(
                    photoPath = capture.photoPath,
                    sampleSize = 2,
                    modifier = Modifier
                        .size(120.dp)
                        .clip(RoundedCornerShape(8.dp)),
                )
            }
        }
    }
}

@Composable
private fun WikipediaBlock(info: SpeciesInfo?) {
    val ctx = LocalContext.current
    val summary = info?.summary
    val wp = info?.wikipediaTitle
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("À propos", style = MaterialTheme.typography.titleMedium)
            if (summary.isNullOrBlank()) {
                Text(
                    "Pas d'info encyclopédique disponible pour cette espèce.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(summary, style = MaterialTheme.typography.bodyMedium)
                if (!wp.isNullOrBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val url = "https://fr.wikipedia.org/wiki/${wikipediaUrlPath(wp)}"
                                runCatching {
                                    ctx.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    )
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
                            Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsBlock(stats: SpeciesStats) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("À Paris", style = MaterialTheme.typography.titleMedium)
            Text(
                buildString {
                    append(formatCount(stats.count))
                    append(" arbres (")
                    append(formatPercent(stats.proportion))
                    append(" du dataset)")
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            val measures = listOfNotNull(
                stats.medianHeightM?.let { "Hauteur médiane : $it m" },
                stats.medianCircCm?.let { "Circonférence médiane : $it cm" },
            )
            measures.forEach {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            if (stats.topArrAbs.isNotEmpty()) {
                ArrSection(
                    title = "Plus nombreux",
                    items = stats.topArrAbs,
                    formatLine = { "${it.arr} (${formatCount(it.count)})" },
                )
            }
            if (stats.topArrOver.isNotEmpty()) {
                ArrSection(
                    title = "Sur-représentés",
                    items = stats.topArrOver,
                    formatLine = { item ->
                        val ratio = item.ratio?.let { formatRatio(it) } ?: ""
                        if (ratio.isEmpty()) item.arr else "${item.arr} ($ratio)"
                    },
                )
            }
        }
    }
}

@Composable
private fun ArrSection(
    title: String,
    items: List<ArrCount>,
    formatLine: (ArrCount) -> String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        items.forEach { item ->
            Text(formatLine(item), style = MaterialTheme.typography.bodyMedium)
        }
    }
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

/**
 * Wikipedia accepte les espaces et la plupart des caractères dans son URL ;
 * la convention est de remplacer les espaces par `_`. Les apostrophes et
 * accents passent tels quels (Android URI les encode si besoin via Uri.parse).
 */
private fun wikipediaUrlPath(title: String): String =
    title.replace(' ', '_')
