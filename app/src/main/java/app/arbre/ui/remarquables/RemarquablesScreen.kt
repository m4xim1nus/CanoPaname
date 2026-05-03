package app.arbre.ui.remarquables

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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
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
import app.arbre.data.Season
import app.arbre.data.rememberArbreRepository
import app.arbre.data.rememberCaptureRepository
import app.arbre.data.rememberSeasonStore
import app.arbre.R
import app.arbre.ui.common.ArchiveBanner
import app.arbre.ui.common.EmptyState
import app.arbre.ui.common.PhotoThumbnail
import app.arbre.ui.common.SeasonSelector
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemarquablesScreen(
    onBack: () -> Unit,
    onRemarquableClick: (Long) -> Unit = {},
) {
    val arbreRepo = rememberArbreRepository()
    val captureRepo = rememberCaptureRepository()
    val seasonStore = rememberSeasonStore()
    val selectedSeason by seasonStore.selected.collectAsState()
    val currentSeason = Season.current()
    val isArchive = selectedSeason != currentSeason

    // Liste statique des 183 arbres remarquables — chargée une seule fois.
    // Pas de Flow : le set ne change pas après le premier launch.
    var tousRemarquables by remember { mutableStateOf<List<Arbre>>(emptyList()) }
    LaunchedEffect(Unit) {
        tousRemarquables = arbreRepo.arbresRemarquables()
    }

    val capturesRemarquables by captureRepo.capturesRemarquables()
        .collectAsState(initial = emptyList())
    // Set scopé sur la saison sélectionnée — un même remarquable capturé
    // en hiver et en été compte 2 fois dans le Pokédex saisonnier.
    val capturedIds by captureRepo.capturedRemarquableIds(selectedSeason)
        .collectAsState(initial = emptySet())

    // 1re photo de la saison sélectionnée pour chaque arbre.
    val capturesInSeason = remember(capturesRemarquables, selectedSeason) {
        capturesRemarquables.filter { it.season == selectedSeason }
    }
    val firstPhotoByArbreId: Map<Long, String> = remember(capturesInSeason) {
        capturesInSeason
            .groupBy { it.arbreId }
            .mapValues { (_, caps) -> caps.minBy { it.timestamp }.photoPath }
    }
    val lastCaptureTsByArbreId: Map<Long, Long> = remember(capturesInSeason) {
        capturesInSeason
            .groupBy { it.arbreId }
            .mapValues { (_, caps) -> caps.maxOf { it.timestamp } }
    }

    val total = tousRemarquables.size
    val nbDecouverts = tousRemarquables.count { it.id in capturedIds }

    // `rememberSaveable` pour conserver le mode au retour de la fiche-arbre —
    // miroir de l'Arboretum (cf. ArboretumScreen.kt : aller-retour Pokédex →
    // fiche → Pokédex sans flash sur la vue Liste).
    var viewMode by rememberSaveable { mutableStateOf(RemarquablesViewMode.LISTE) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Remarquables") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    SeasonSelector(
                        selected = selectedSeason,
                        onSelect = { seasonStore.select(it) },
                        isCurrent = !isArchive,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (isArchive) {
                ArchiveBanner(season = selectedSeason)
            }
            HeaderCard(
                nbDecouverts = nbDecouverts,
                total = total,
                season = selectedSeason,
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
                RemarquablesViewMode.LISTE -> ListeView(
                    tousRemarquables = tousRemarquables,
                    capturedIds = capturedIds,
                    firstPhotoByArbreId = firstPhotoByArbreId,
                    lastCaptureTsByArbreId = lastCaptureTsByArbreId,
                    onRemarquableClick = onRemarquableClick,
                )
                RemarquablesViewMode.POKEDEX -> PokedexView(
                    tousRemarquables = tousRemarquables,
                    capturedIds = capturedIds,
                    firstPhotoByArbreId = firstPhotoByArbreId,
                    onRemarquableClick = onRemarquableClick,
                )
            }
        }
    }
}

private enum class RemarquablesViewMode { LISTE, POKEDEX }

@Composable
private fun ViewModeSelector(
    current: RemarquablesViewMode,
    onSelect: (RemarquablesViewMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        SegmentedButton(
            selected = current == RemarquablesViewMode.LISTE,
            onClick = { onSelect(RemarquablesViewMode.LISTE) },
            shape = SegmentedButtonDefaults.itemShape(0, 2),
        ) { Text("Liste") }
        SegmentedButton(
            selected = current == RemarquablesViewMode.POKEDEX,
            onClick = { onSelect(RemarquablesViewMode.POKEDEX) },
            shape = SegmentedButtonDefaults.itemShape(1, 2),
        ) { Text("Pokédex") }
    }
}

@Composable
private fun ListeView(
    tousRemarquables: List<Arbre>,
    capturedIds: Set<Long>,
    firstPhotoByArbreId: Map<Long, String>,
    lastCaptureTsByArbreId: Map<Long, Long>,
    onRemarquableClick: (Long) -> Unit,
) {
    val decouverts = remember(tousRemarquables, capturedIds) {
        tousRemarquables.filter { it.id in capturedIds }
            .sortedByDescending { lastCaptureTsByArbreId[it.id] ?: 0L }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (decouverts.isNotEmpty()) {
            item { SectionHeader("Découverts (${decouverts.size}/${tousRemarquables.size})") }
            items(decouverts, key = { it.id }) { arbre ->
                DiscoveredCard(
                    arbre = arbre,
                    photoPath = firstPhotoByArbreId[arbre.id],
                    captureTs = lastCaptureTsByArbreId[arbre.id],
                    onClick = { onRemarquableClick(arbre.id) },
                )
            }
        } else if (tousRemarquables.isEmpty()) {
            item { LoadingState() }
        } else {
            item { RemarquablesEmptyState() }
        }
    }
}

/**
 * Vue annuaire : grille 3 colonnes ordonnée alphabétiquement par (genre, espèce, id).
 * Les remarquables capturés révèlent leur photo et leur nom ; les autres restent
 * en silhouette « ??? » avec leur numéro pour donner une idée de l'avancement.
 *
 * Variante de la vue Pokédex Arboretum : ici le numéro est stable (chaque
 * remarquable a son rang fixe dans la liste), tandis que pour les espèces le
 * numéro est juste un rang d'affichage.
 */
@Composable
private fun PokedexView(
    tousRemarquables: List<Arbre>,
    capturedIds: Set<Long>,
    firstPhotoByArbreId: Map<Long, String>,
    onRemarquableClick: (Long) -> Unit,
) {
    val ordered = remember(tousRemarquables) {
        tousRemarquables.sortedWith(
            compareBy(
                { it.genre.lowercase() },
                { it.espece.lowercase() },
                { it.id },
            )
        )
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(ordered, key = { _, a -> a.id }) { position, arbre ->
            val discovered = arbre.id in capturedIds
            val photoPath = if (discovered) firstPhotoByArbreId[arbre.id] else null
            PokedexCell(
                displayNumber = position + 1,
                arbre = arbre,
                photoPath = photoPath,
                discovered = discovered,
                onClick = if (discovered) {
                    { onRemarquableClick(arbre.id) }
                } else null,
            )
        }
    }
}

@Composable
private fun PokedexCell(
    displayNumber: Int,
    arbre: Arbre,
    photoPath: String?,
    discovered: Boolean,
    onClick: (() -> Unit)?,
) {
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
                if (discovered) arbre.nomAffichage else "???",
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

@Composable
private fun HeaderCard(
    nbDecouverts: Int,
    total: Int,
    season: Season,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                if (total > 0) {
                    "$nbDecouverts / $total remarquables découverts"
                } else {
                    "Chargement…"
                },
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                "${season.preposition} ${season.label.lowercase()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
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
private fun DiscoveredCard(
    arbre: Arbre,
    photoPath: String?,
    captureTs: Long?,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (photoPath != null) {
                PhotoThumbnail(
                    photoPath = photoPath,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(8.dp)),
                )
            } else {
                PlaceholderThumbnail(modifier = Modifier.size(72.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(arbre.nomAffichage, style = MaterialTheme.typography.titleMedium)
                if (arbre.nomCommun != null) {
                    Text(
                        "${arbre.genre} ${arbre.espece}",
                        style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_remarquable_plaque),
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        "Remarquable",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                arbre.adresse?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
                captureTs?.let {
                    Text(
                        "Capture : ${formatDate(it)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaceholderThumbnail(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "?",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun LoadingState() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text("Chargement des arbres remarquables…")
        }
    }
}

@Composable
private fun RemarquablesEmptyState() {
    EmptyState(
        title = "Aucun remarquable capturé.",
        body = "Pars à la chasse : la loupe en bas-gauche de la carte t'indique la distance au plus proche.",
        illustration = {
            Image(
                painter = painterResource(R.drawable.illus_empty_remarquables),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        },
    )
}

private val DATE_FORMAT: DateFormat = DateFormat.getDateInstance(DateFormat.SHORT)

private fun formatDate(epochMillis: Long): String =
    DATE_FORMAT.format(Date(epochMillis))
