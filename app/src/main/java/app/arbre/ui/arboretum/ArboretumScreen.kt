package app.arbre.ui.arboretum

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.arbre.data.Arbre
import app.arbre.data.Capture
import app.arbre.data.SpeciesEntry
import app.arbre.data.rememberArbreRepository
import app.arbre.data.rememberCaptureRepository
import app.arbre.data.rememberDatasetStats
import app.arbre.data.rememberSpeciesIndex
import app.arbre.ui.common.PhotoThumbnail
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArboretumScreen(
    onBack: () -> Unit,
    onSpeciesClick: (Int) -> Unit = {},
) {
    val captureRepo = rememberCaptureRepository()
    val speciesIndex = rememberSpeciesIndex()
    val stats = rememberDatasetStats()
    val arbreRepo = rememberArbreRepository()

    // Toutes les captures, triées par speciesIndex (avec leurs Captures
    // concaténées) puis remarquables à part.
    val captures by captureRepo.toutesLesCaptures().collectAsState(initial = emptyList())

    val speciesGroups: List<SpeciesGroup> = captures
        .filter { !it.remarquable }
        .groupBy { it.speciesIndex }
        .mapNotNull { (sk, caps) ->
            val entry = speciesIndex.get(sk) ?: return@mapNotNull null
            SpeciesGroup(entry, caps.sortedByDescending { it.timestamp })
        }
        .sortedByDescending { it.captures.first().timestamp }

    val remarquables = captures.filter { it.remarquable }
        .sortedByDescending { it.timestamp }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Arboretum") },
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
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { HeaderCard(speciesGroups.size, remarquables.size, stats.totalEspeces, stats.totalRemarquables) }

            if (speciesGroups.isNotEmpty()) {
                item {
                    SectionHeader("Espèces (${speciesGroups.size}/${stats.totalEspeces})")
                }
                items(speciesGroups, key = { it.entry.index }) { group ->
                    SpeciesCard(
                        group = group,
                        countParEspece = arbreRepo::compterParEspece,
                        onClick = { onSpeciesClick(group.entry.index) },
                    )
                }
            }

            if (remarquables.isNotEmpty()) {
                item {
                    SectionHeader("Remarquables (${remarquables.size}/${stats.totalRemarquables})")
                }
                items(remarquables, key = { it.id }) { capture ->
                    RemarquableCard(capture, arbreRepo::arbreParId)
                }
            }

            if (speciesGroups.isEmpty() && remarquables.isEmpty()) {
                item { EmptyState() }
            }
        }
    }
}

private data class SpeciesGroup(
    val entry: SpeciesEntry,
    val captures: List<Capture>,
)

@Composable
private fun HeaderCard(
    nbEspeces: Int,
    nbRemarquables: Int,
    totalEspeces: Int,
    totalRemarquables: Int,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "$nbEspeces / $totalEspeces espèces découvertes",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                "$nbRemarquables / $totalRemarquables remarquables découverts",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun SectionHeader(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun SpeciesCard(
    group: SpeciesGroup,
    countParEspece: suspend (String, String) -> Int,
    onClick: () -> Unit,
) {
    val first = group.captures.first()
    val firstChrono = group.captures.last() // 1re capture = la plus ancienne
    var countInDataset by remember(group.entry) { mutableStateOf(-1) }
    LaunchedEffect(group.entry) {
        countInDataset = countParEspece(group.entry.genre, group.entry.espece)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PhotoThumbnail(
                photoPath = first.photoPath,
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(group.entry.displayName, style = MaterialTheme.typography.titleMedium)
                if (countInDataset > 0) {
                    Text(
                        "$countInDataset arbre${if (countInDataset > 1) "s" else ""} dans Paris",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "${group.captures.size} photo${if (group.captures.size > 1) "s" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "1re capture : ${formatDate(firstChrono.timestamp)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RemarquableCard(
    capture: Capture,
    arbreParId: suspend (Long) -> Arbre?,
) {
    var arbre by remember(capture.arbreId) { mutableStateOf<Arbre?>(null) }
    LaunchedEffect(capture.arbreId) {
        arbre = arbreParId(capture.arbreId)
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PhotoThumbnail(
                photoPath = capture.photoPath,
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    arbre?.nomAffichage ?: "Arbre #${capture.arbreId}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "★ Remarquable",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                arbre?.adresse?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    "Capture : ${formatDate(capture.timestamp)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Aucune capture pour l'instant.", style = MaterialTheme.typography.titleMedium)
            Text(
                "Approche-toi d'un arbre, tape son pin gris et capture-le pour révéler son espèce.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val DATE_FORMAT: DateFormat = DateFormat.getDateInstance(DateFormat.SHORT)

private fun formatDate(epochMillis: Long): String =
    DATE_FORMAT.format(Date(epochMillis))
