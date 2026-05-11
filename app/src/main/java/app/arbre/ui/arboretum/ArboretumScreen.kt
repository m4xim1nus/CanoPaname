package app.arbre.ui.arboretum

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import app.arbre.data.Capture
import app.arbre.data.SpeciesEntry
import app.arbre.data.SpeciesIndex
import app.arbre.data.rememberArbreRepository
import app.arbre.data.rememberCaptureRepository
import app.arbre.data.rememberDatasetStats
import app.arbre.data.rememberSpeciesIndex
import app.arbre.data.resolvedFile
import app.arbre.R
import app.arbre.ui.common.CatalogueCell
import app.arbre.ui.common.EmptyState
import app.arbre.ui.common.PhotoThumbnail
import androidx.compose.foundation.Image
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import java.io.File
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

    val captures by captureRepo.toutesLesCaptures().collectAsState(initial = emptyList())

    // Les remarquables ont leur écran dédié — on agrège les autres captures
    // par espèce, toutes saisons confondues.
    val speciesGroups: List<SpeciesGroup> = captures
        .filter { !it.remarquable }
        .groupBy { it.speciesIndex }
        .mapNotNull { (sk, caps) ->
            val entry = speciesIndex.get(sk) ?: return@mapNotNull null
            SpeciesGroup(entry, caps.sortedByDescending { it.timestamp })
        }
        .sortedByDescending { it.captures.first().timestamp }

    // Cycle Catalogue : sépare les sks identifiés (compteur principal) des
    // sks `unknownSpecies` (« + N espèces indéterminées »). `capturedSks`
    // alimente l'auto-débloquage genre-based des cards Catalogue.
    val capturedSks: Set<Int> = speciesGroups.map { it.entry.index }.toSet()
    val nbIdentifiees = speciesGroups.count { !it.entry.unknownSpecies }
    val nbIndeterminees = speciesGroups.count { it.entry.unknownSpecies }

    var viewMode by rememberSaveable { mutableStateOf(ArboretumViewMode.DECOUVERTE) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Arboretum") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Retour")
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
                nbIdentifiees = nbIdentifiees,
                totalEspecesIdentifiees = stats.totalEspecesIdentifiees,
                nbIndeterminees = nbIndeterminees,
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
                ArboretumViewMode.DECOUVERTE -> DecouverteView(
                    speciesGroups = speciesGroups,
                    totalEspecesIdentifiees = stats.totalEspecesIdentifiees,
                    arbreRepo = arbreRepo,
                    onSpeciesClick = onSpeciesClick,
                )
                ArboretumViewMode.FREQUENCE -> FrequenceView(
                    speciesIndex = speciesIndex,
                    speciesGroups = speciesGroups,
                    capturedSks = capturedSks,
                    onSpeciesClick = onSpeciesClick,
                )
                ArboretumViewMode.CATALOGUE -> CatalogueView(
                    speciesIndex = speciesIndex,
                    speciesGroups = speciesGroups,
                    capturedSks = capturedSks,
                    onSpeciesClick = onSpeciesClick,
                )
            }
        }
    }
}

private enum class ArboretumViewMode { DECOUVERTE, FREQUENCE, CATALOGUE }

@Composable
private fun ViewModeSelector(
    current: ArboretumViewMode,
    onSelect: (ArboretumViewMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val modes = ArboretumViewMode.entries
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        modes.forEachIndexed { idx, mode ->
            SegmentedButton(
                selected = current == mode,
                onClick = { onSelect(mode) },
                shape = SegmentedButtonDefaults.itemShape(idx, modes.size),
            ) {
                Text(stringResource(when (mode) {
                    ArboretumViewMode.DECOUVERTE -> R.string.segment_decouverte
                    ArboretumViewMode.FREQUENCE -> R.string.segment_frequence
                    ArboretumViewMode.CATALOGUE -> R.string.segment_catalogue
                }))
            }
        }
    }
}

@Composable
private fun DecouverteView(
    speciesGroups: List<SpeciesGroup>,
    totalEspecesIdentifiees: Int,
    arbreRepo: app.arbre.data.ArbreRepository,
    onSpeciesClick: (Int) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (speciesGroups.isNotEmpty()) {
            // Compteur entête : on garde la fraction sur le total identifié
            // (cohérent avec le HeaderCard du dessus). Les `unknownSpecies`
            // capturés réellement apparaissent quand même dans la liste — la
            // vue Découverte = « ce que j'ai capturé », contrairement aux
            // modes Fréquence et Catalogue qui sont exhaustifs.
            item {
                SectionHeader("Espèces (${speciesGroups.size}/$totalEspecesIdentifiees)")
            }
            items(speciesGroups, key = { it.entry.index }) { group ->
                SpeciesCard(
                    group = group,
                    countParEspece = arbreRepo::compterParEspece,
                    onClick = { onSpeciesClick(group.entry.index) },
                )
            }
        } else {
            item { ArboretumEmptyState() }
        }
    }
}

/**
 * Vue Fréquence (cycle Catalogue, sprint 7) : annuaire exhaustif des espèces
 * identifiées (~800 entrées), tri par `pokedexNumber` backend croissant (=
 * count Paris décroissant figé au build). `#NNN` affiché = le `pokedexNumber`
 * lui-même. Les non-capturées apparaissent en silhouette `???` via le rendu
 * `discovered = false` de `CatalogueCell`. Les `unknownSpecies` ne sont pas
 * listées ici — elles n'ont pas de rang de fréquence.
 */
@Composable
private fun FrequenceView(
    speciesIndex: SpeciesIndex,
    speciesGroups: List<SpeciesGroup>,
    capturedSks: Set<Int>,
    onSpeciesClick: (Int) -> Unit,
) {
    val ctx = LocalContext.current
    val firstPhotoBySk: Map<Int, File> = remember(speciesGroups, ctx) {
        speciesGroups.associate { it.entry.index to it.captures.first().resolvedFile(ctx) }
    }
    // Tri stable par pokedexNumber croissant. Les rares entrées identifiées
    // sans pokedexNumber (zombies count=0 ou asset legacy) tombent en queue.
    val ordered = remember(speciesIndex) {
        speciesIndex.entries()
            .filter { !it.unknownSpecies }
            .sortedWith(
                compareBy<SpeciesEntry> { it.pokedexNumber == null }
                    .thenBy { it.pokedexNumber ?: Int.MAX_VALUE }
                    .thenBy { it.index }
            )
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        gridItems(ordered, key = { it.index }) { entry ->
            val discovered = entry.index in capturedSks
            val photoFile = if (discovered) firstPhotoBySk[entry.index] else null
            CatalogueCell(
                displayLabel = entry.pokedexNumber?.let { "#%03d".format(it) } ?: "—",
                entry = entry,
                photoFile = photoFile,
                discovered = discovered,
                onClick = if (discovered) {
                    { onSpeciesClick(entry.index) }
                } else null,
            )
        }
    }
}

/**
 * Vue Catalogue (cycle Catalogue, sprint 7) : annuaire exhaustif groupé par
 * genre. Chapitres en **ordre alphabétique** du genre, en-tête de chapitre
 * `Genre  ·  X / Y` (X = espèces capturées du genre, Y = espèces identifiées
 * du genre — `sp.` exclus du compteur). Intra-genre, tri par `pokedexNumber`
 * croissant (= count Paris décroissant figé). Le `#NNN` affiché est un
 * **rang front recalculé** (1..~802) dans l'ordre d'affichage genre→count-déc
 * — distinct du `pokedexNumber` backend stable.
 *
 * Les `unknownSpecies` apparaissent en queue de leur chapitre, sans `#`. Le
 * tap sur header de chapitre est désactivé au S7 ; le S8 ajoutera l'ouverture
 * de `GenreDetailScreen`.
 */
@Composable
private fun CatalogueView(
    speciesIndex: SpeciesIndex,
    speciesGroups: List<SpeciesGroup>,
    capturedSks: Set<Int>,
    onSpeciesClick: (Int) -> Unit,
) {
    val ctx = LocalContext.current
    val firstPhotoBySk: Map<Int, File> = remember(speciesGroups, ctx) {
        speciesGroups.associate { it.entry.index to it.captures.first().resolvedFile(ctx) }
    }
    // Pré-calcul des chapitres + assignation du `#N` front en un seul passage
    // (compteur incrémenté dans l'ordre d'affichage genre→pokedex). Memoisé
    // sur (speciesIndex, capturedSks) : recompute uniquement à la capture.
    val chapters = remember(speciesIndex, capturedSks) {
        var displayN = 1
        speciesIndex.genres().map { genre ->
            val all = speciesIndex.entriesOfGenre(genre)
            val identifiedSorted = all
                .filter { !it.unknownSpecies }
                .sortedWith(
                    compareBy<SpeciesEntry> { it.pokedexNumber == null }
                        .thenBy { it.pokedexNumber ?: Int.MAX_VALUE }
                        .thenBy { it.index }
                )
            val identifiedWithN = identifiedSorted.map { entry ->
                val n = displayN
                displayN += 1
                entry to n
            }
            val sps = all.filter { it.unknownSpecies }
            GenreChapter(
                genre = genre,
                identifiedWithDisplayN = identifiedWithN,
                sps = sps,
                captured = speciesIndex.capturedCountInGenre(genre, capturedSks),
                total = speciesIndex.genreCount(genre),
            )
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        chapters.forEach { chapter ->
            item(
                span = { GridItemSpan(maxLineSpan) },
                key = "chapter-${chapter.genre}",
            ) {
                GenreChapterHeader(chapter)
            }
            gridItems(
                chapter.identifiedWithDisplayN,
                key = { (entry, _) -> entry.index },
            ) { (entry, displayN) ->
                val discovered = entry.index in capturedSks
                val photoFile = if (discovered) firstPhotoBySk[entry.index] else null
                CatalogueCell(
                    displayLabel = "#%03d".format(displayN),
                    entry = entry,
                    photoFile = photoFile,
                    discovered = discovered,
                    onClick = if (discovered) {
                        { onSpeciesClick(entry.index) }
                    } else null,
                )
            }
            gridItems(chapter.sps, key = { it.index }) { entry ->
                val discovered = speciesIndex.isDiscovered(entry.index, capturedSks)
                val photoFile = if (entry.index in capturedSks) firstPhotoBySk[entry.index] else null
                CatalogueCell(
                    displayLabel = "—",
                    entry = entry,
                    photoFile = photoFile,
                    discovered = discovered,
                    onClick = if (discovered) {
                        { onSpeciesClick(entry.index) }
                    } else null,
                )
            }
        }
    }
}

private data class GenreChapter(
    val genre: String,
    val identifiedWithDisplayN: List<Pair<SpeciesEntry, Int>>,
    val sps: List<SpeciesEntry>,
    val captured: Int,
    val total: Int,
)

@Composable
private fun GenreChapterHeader(chapter: GenreChapter) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            chapter.genre,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            "${chapter.captured} / ${chapter.total}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private data class SpeciesGroup(
    val entry: SpeciesEntry,
    val captures: List<Capture>,
)

@Composable
private fun HeaderCard(
    nbIdentifiees: Int,
    totalEspecesIdentifiees: Int,
    nbIndeterminees: Int,
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
                "$nbIdentifiees / $totalEspecesIdentifiees espèces découvertes",
                style = MaterialTheme.typography.titleLarge,
            )
            if (nbIndeterminees > 0) {
                val plural = if (nbIndeterminees > 1) "s" else ""
                Text(
                    "+ $nbIndeterminees espèce$plural indéterminée$plural",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
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
    val ctx = LocalContext.current
    val first = group.captures.first()
    val firstChrono = group.captures.last()
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
                photoFile = first.resolvedFile(ctx),
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    group.entry.displayNomCommun,
                    style = MaterialTheme.typography.titleMedium,
                )
                // Sous-titre binôme italique : présent dès que `nv` ou `nc`
                // apportent un nom différent du binôme (cycle Catalogue).
                if (group.entry.nv != null || group.entry.nomCommun != null) {
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
private fun ArboretumEmptyState() {
    EmptyState(
        title = "Ton arboretum est vide.",
        body = "Approche-toi d'un arbre, tape son pin gris et capture-le pour révéler son espèce.",
        illustration = {
            Image(
                painter = painterResource(R.drawable.illus_empty_arboretum),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        },
    )
}

private val DATE_FORMAT: DateFormat = DateFormat.getDateInstance(DateFormat.SHORT)

private fun formatDate(epochMillis: Long): String =
    DATE_FORMAT.format(Date(epochMillis))
