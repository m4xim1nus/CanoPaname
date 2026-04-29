package app.arbre.ui.arboretum

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.arbre.data.Arbre
import app.arbre.data.Capture
import app.arbre.data.SpeciesEntry
import app.arbre.data.SpeciesIndex
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

    // `rememberSaveable` pour conserver le mode au retour de la fiche-espèce —
    // aller-retour Pokédex → fiche → Pokédex sans flash sur la vue Liste.
    var viewMode by rememberSaveable { mutableStateOf(ArboretumViewMode.LISTE) }

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            HeaderCard(
                speciesGroups.size,
                remarquables.size,
                stats.totalEspeces,
                stats.totalRemarquables,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
            )
            ViewModeSelector(
                current = viewMode,
                onSelect = { viewMode = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
            when (viewMode) {
                ArboretumViewMode.LISTE -> ListeView(
                    speciesGroups = speciesGroups,
                    remarquables = remarquables,
                    totalEspeces = stats.totalEspeces,
                    totalRemarquables = stats.totalRemarquables,
                    arbreRepo = arbreRepo,
                    onSpeciesClick = onSpeciesClick,
                )
                ArboretumViewMode.POKEDEX -> PokedexView(
                    speciesIndex = speciesIndex,
                    speciesGroups = speciesGroups,
                    onSpeciesClick = onSpeciesClick,
                )
            }
        }
    }
}

private enum class ArboretumViewMode { LISTE, POKEDEX }

@Composable
private fun ViewModeSelector(
    current: ArboretumViewMode,
    onSelect: (ArboretumViewMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        SegmentedButton(
            selected = current == ArboretumViewMode.LISTE,
            onClick = { onSelect(ArboretumViewMode.LISTE) },
            shape = SegmentedButtonDefaults.itemShape(0, 2),
        ) { Text("Liste") }
        SegmentedButton(
            selected = current == ArboretumViewMode.POKEDEX,
            onClick = { onSelect(ArboretumViewMode.POKEDEX) },
            shape = SegmentedButtonDefaults.itemShape(1, 2),
        ) { Text("Pokédex") }
    }
}

@Composable
private fun ListeView(
    speciesGroups: List<SpeciesGroup>,
    remarquables: List<Capture>,
    totalEspeces: Int,
    totalRemarquables: Int,
    arbreRepo: app.arbre.data.ArbreRepository,
    onSpeciesClick: (Int) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (speciesGroups.isNotEmpty()) {
            item {
                SectionHeader("Espèces (${speciesGroups.size}/$totalEspeces)")
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
                SectionHeader("Remarquables (${remarquables.size}/$totalRemarquables)")
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

/**
 * Vue annuaire : grille 3 colonnes ordonnée par speciesIndex. Les espèces
 * capturées révèlent leur photo et leur nom ; les autres restent en
 * silhouette « ??? » avec leur numéro pour donner une idée de l'avancement
 * sans spoiler l'identité (cf. ROADMAP : « cases vides pour les espèces non
 * encore capturées »).
 *
 * Pas de section remarquables ici : le speciesIndex est partagé avec les
 * arbres normaux, donc l'annuaire représente l'inventaire des espèces
 * tout court — pertinent autant pour les remarquables que les autres.
 */
@Composable
private fun PokedexView(
    speciesIndex: SpeciesIndex,
    speciesGroups: List<SpeciesGroup>,
    onSpeciesClick: (Int) -> Unit,
) {
    val firstPhotoBySk: Map<Int, String> = remember(speciesGroups) {
        speciesGroups.associate { it.entry.index to it.captures.first().photoPath }
    }
    // Ordre Pokédex : alphabétique par (genre, espece). Regroupe les espèces
    // d'un même genre (Acer platanoides, Acer pseudoplatanus, …) côte à côte
    // — l'ordre par speciesIndex (sk) reflèterait l'ordre d'ingestion CSV
    // qui est lié à l'ordre des captures et n'a pas de sens pour l'utilisateur.
    val ordered = remember(speciesIndex) {
        speciesIndex.entries().sortedWith(
            compareBy({ it.genre.lowercase() }, { it.espece.lowercase() })
        )
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(ordered, key = { _, e -> e.index }) { position, entry ->
            val photoPath = firstPhotoBySk[entry.index]
            PokedexCell(
                // Numéro 1-based — c'est un rang d'affichage, distinct du
                // speciesIndex stocké en Room (qui peut être 0 et reflète
                // l'ordre d'ingestion).
                displayNumber = position + 1,
                entry = entry,
                photoPath = photoPath,
                onClick = if (photoPath != null) {
                    { onSpeciesClick(entry.index) }
                } else null,
            )
        }
    }
}

@Composable
private fun PokedexCell(
    displayNumber: Int,
    entry: SpeciesEntry,
    photoPath: String?,
    onClick: (() -> Unit)?,
) {
    val discovered = photoPath != null
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
        colors = CardDefaults.cardColors(
            containerColor = if (discovered) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "#%03d".format(displayNumber),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (photoPath != null) {
                    PhotoThumbnail(
                        photoPath = photoPath,
                        sampleSize = 4,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "?",
                            style = MaterialTheme.typography.displayMedium,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }
            Text(
                if (discovered) entry.displayNomCommun else "???",
                style = MaterialTheme.typography.bodySmall,
                color = if (discovered) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Start,
            )
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
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
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
                Text(
                    group.entry.displayNomCommun,
                    style = MaterialTheme.typography.titleMedium,
                )
                // Sous-titre binomial italique tant que le nom commun est
                // disponible — utile pour discriminer Acer platanoides /
                // pseudoplatanus qui partagent l'étiquette « Erable ».
                if (group.entry.nomCommun != null) {
                    Text(
                        group.entry.displayName,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontStyle = FontStyle.Italic,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
