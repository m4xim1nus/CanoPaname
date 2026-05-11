package app.arbre.ui.genre

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import app.arbre.data.Capture
import app.arbre.data.SpeciesEntry
import app.arbre.data.SpeciesIndex
import app.arbre.data.SpeciesInfoRepository
import app.arbre.data.rememberCaptureRepository
import app.arbre.data.rememberGenreInfoRepository
import app.arbre.data.rememberSpeciesIndex
import app.arbre.data.rememberSpeciesInfoRepository
import app.arbre.data.resolvedFile
import app.arbre.ui.common.CatalogueCell
import app.arbre.ui.common.DeleteCaptureDialog
import app.arbre.ui.common.PhotoGallery
import app.arbre.ui.common.PhotoLightbox
import app.arbre.ui.common.ShowOnMapButton
import app.arbre.ui.common.WikipediaBlock
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Fiche genre dédiée (S8). Remplace l'ancienne fiche `(G, sp.)` enrichie du
 * S5 : la mécanique de mini-catalogue + carte filtrée + galerie photos sp.
 * habite désormais ici, et l'entrée `(G, sp.)` n'est plus exposée comme card
 * d'espèce dans le Catalogue.
 *
 * Couvre les 202 genres utiles (199 avec espèces identifiées + 3 only-unknown
 * Genista/Vitex/Ziziphus, exclut « Non spécifié »). Les sections optionnelles
 * absentes pour les cas dégénérés :
 * - genre only-unknown → pas de section « Espèces du genre » ;
 * - genre sans entrée `(G, sp.)` (91 cas) → pas de section galerie sp. ;
 * - asset `genre-info.json` legacy → pas de sections Wikipedia/stats.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenreDetailScreen(
    genre: String,
    onBack: () -> Unit,
    onSpeciesClick: (Int) -> Unit = {},
    onShowOnMap: (Set<Int>) -> Unit = {},
    onShowArbreOnMap: (Long) -> Unit = {},
    onUnlockLost: () -> Unit = {},
) {
    val speciesIndexRepo = rememberSpeciesIndex()
    val speciesInfoRepo = rememberSpeciesInfoRepository()
    val genreInfoRepo = rememberGenreInfoRepository()
    val captureRepo = rememberCaptureRepository()

    val info = remember(genre) { genreInfoRepo.get(genre) }

    // Toutes les espèces du genre (identifiées + sp.) — on les sépare ensuite.
    val allEntriesOfGenre = remember(genre, speciesIndexRepo) {
        speciesIndexRepo.entriesOfGenre(genre)
    }
    if (allEntriesOfGenre.isEmpty()) {
        // Genre absent du species-index (deep link cassé) : retour silencieux.
        androidx.compose.runtime.LaunchedEffect(genre) { onBack() }
        return
    }
    val identifiedEntries: List<SpeciesEntry> = remember(allEntriesOfGenre, speciesInfoRepo) {
        allEntriesOfGenre
            .filter { !it.unknownSpecies }
            .sortedWith(
                compareBy<SpeciesEntry> { it.pokedexNumber == null }
                    .thenBy { it.pokedexNumber ?: Int.MAX_VALUE }
                    .thenByDescending { speciesInfoRepo.get(it.index)?.stats?.count ?: 0 }
                    .thenBy { it.espece.lowercase() }
            )
    }
    val spEntry: SpeciesEntry? = remember(allEntriesOfGenre) {
        allEntriesOfGenre.firstOrNull { it.unknownSpecies }
    }

    val capturedSpecies by captureRepo.capturedSpeciesIndices()
        .collectAsState(initial = emptySet())

    // Captures `(G, sp.)` du genre — alimentent la galerie photos sp.
    val capturesSp by remember(spEntry) {
        if (spEntry == null) flowOf(emptyList())
        else captureRepo.toutesLesCaptures()
            .map { all -> all.filter { it.speciesIndex == spEntry.index } }
    }.collectAsState(initial = emptyList())

    // Captures de TOUTES les espèces identifiées du genre — alimentent la
    // mini-galerie photo des cards via `genrePhotosBySk`.
    val capturesIdentifiedOfGenre by remember(identifiedEntries) {
        if (identifiedEntries.isEmpty()) flowOf(emptyList<Capture>())
        else {
            val sks = identifiedEntries.map { it.index }.toSet()
            captureRepo.toutesLesCaptures()
                .map { all -> all.filter { !it.remarquable && it.speciesIndex in sks } }
        }
    }.collectAsState(initial = emptyList())

    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val genrePhotosBySk: Map<Int, java.io.File> = remember(capturesIdentifiedOfGenre, ctx) {
        capturesIdentifiedOfGenre
            .groupBy { it.speciesIndex }
            .mapValues { (_, caps) -> caps.maxByOrNull { it.timestamp }!!.resolvedFile(ctx) }
    }
    val spPhotoFiles = capturesSp.map { it.resolvedFile(ctx) }

    // Set sk pour la carte filtrée : sp. (s'il existe) + chaque espèce
    // identifiée du genre **capturée**. Sémantique inchangée vs S5 : focus
    // « ce que j'ai à résoudre + mes trophées du genre ».
    val genreFilterSet: Set<Int> = remember(genre, capturedSpecies, identifiedEntries, spEntry) {
        val capturedSiblings = identifiedEntries
            .map { it.index }
            .filter { it in capturedSpecies }
            .toSet()
        capturedSiblings + setOfNotNull(spEntry?.index)
    }

    var lightboxIndex by remember(spEntry?.index ?: -1) { mutableStateOf<Int?>(null) }
    var pendingDeleteIndex by remember(spEntry?.index ?: -1) { mutableStateOf<Int?>(null) }

    val titleText = info?.nomFr ?: genre

    Scaffold(
        topBar = {
            GenreDetailTopBar(
                title = titleText,
                latinSubtitle = if (info?.nomFr != null) genre else null,
                onBack = onBack,
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
            item { IdentityBlock(genre, info, identifiedEntries.size) }

            if (identifiedEntries.isNotEmpty()) {
                item {
                    GenreCatalogueHeader(
                        identified = identifiedEntries,
                        capturedSpecies = capturedSpecies,
                    )
                }
                items(identifiedEntries.chunked(3)) { row ->
                    GenreCatalogueRow(
                        row = row,
                        speciesIndexRepo = speciesIndexRepo,
                        speciesInfoRepo = speciesInfoRepo,
                        capturedSpecies = capturedSpecies,
                        photoBySk = genrePhotosBySk,
                        onSpeciesClick = onSpeciesClick,
                    )
                }
            }

            if (spPhotoFiles.isNotEmpty()) {
                item {
                    Text(
                        "Tes captures à espèce indéterminée",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                item {
                    PhotoGallery(
                        photoFiles = spPhotoFiles,
                        onPhotoClick = { idx -> lightboxIndex = idx },
                        onPhotoLongClick = { idx -> pendingDeleteIndex = idx },
                    )
                }
            }

            if (info?.summary != null) {
                item {
                    WikipediaBlock(
                        summary = info.summary,
                        wikipediaTitle = info.wikipediaTitle,
                        emptyMessage = "Pas d'info encyclopédique disponible pour ce genre.",
                    )
                }
            }

            info?.stats?.let { stats ->
                item { StatsBlock(stats = stats, onSpeciesClick = onSpeciesClick) }
            }

            item {
                ShowOnMapButton(
                    onClick = { onShowOnMap(genreFilterSet) },
                    enabled = genreFilterSet.isNotEmpty(),
                )
            }
        }

        PhotoLightbox(
            photoFiles = spPhotoFiles,
            selectedIndex = lightboxIndex,
            onDismiss = { lightboxIndex = null },
            onDeleteAt = { idx -> pendingDeleteIndex = idx },
            onJumpToMapAt = { idx ->
                capturesSp.getOrNull(idx)?.arbreId?.let(onShowArbreOnMap)
            },
        )

        pendingDeleteIndex?.let { idx ->
            val capture = capturesSp.getOrNull(idx)
            val file = spPhotoFiles.getOrNull(idx)
            if (capture == null || file == null) {
                pendingDeleteIndex = null
                return@let
            }
            DeleteCaptureDialog(
                isLastOfEntity = capturesSp.size == 1,
                entityKindLabel = "ce genre (espèce indéterminée)",
                entityName = titleText,
                onConfirm = {
                    val wasLast = capturesSp.size == 1
                    pendingDeleteIndex = null
                    lightboxIndex = null
                    scope.launch {
                        captureRepo.deleteCapture(capture, file)
                        if (wasLast) onUnlockLost()
                    }
                },
                onDismiss = { pendingDeleteIndex = null },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GenreDetailTopBar(
    title: String,
    latinSubtitle: String?,
    onBack: () -> Unit,
) {
    TopAppBar(
        title = {
            Column {
                Text(title)
                if (latinSubtitle != null) {
                    Text(
                        latinSubtitle,
                        style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Retour")
            }
        },
    )
}

@Composable
private fun IdentityBlock(
    genre: String,
    info: app.arbre.data.GenreInfo?,
    speciesIdentifiedFallback: Int,
) {
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
                info?.nomFr ?: genre,
                style = MaterialTheme.typography.titleLarge,
            )
            if (info?.nomFr != null) {
                Text(
                    genre,
                    style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            val totalCount = info?.stats?.count
            val identifiedCount = info?.stats?.speciesIdentified ?: speciesIdentifiedFallback
            val countLabel = if (totalCount != null) {
                "${formatCount(totalCount)} arbres dans Paris · $identifiedCount espèce${if (identifiedCount > 1) "s" else ""} au catalogue"
            } else {
                "$identifiedCount espèce${if (identifiedCount > 1) "s" else ""} au catalogue"
            }
            Text(
                countLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun GenreCatalogueHeader(
    identified: List<SpeciesEntry>,
    capturedSpecies: Set<Int>,
) {
    val total = identified.size
    val capturedHere = identified.count { it.index in capturedSpecies }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            "Espèces du genre",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            "$capturedHere / $total capturée${if (capturedHere > 1) "s" else ""}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GenreCatalogueRow(
    row: List<SpeciesEntry>,
    speciesIndexRepo: SpeciesIndex,
    speciesInfoRepo: SpeciesInfoRepository,
    capturedSpecies: Set<Int>,
    photoBySk: Map<Int, java.io.File>,
    onSpeciesClick: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        row.forEach { entry ->
            val discovered = speciesIndexRepo.isDiscovered(entry.index, capturedSpecies)
            val count = speciesInfoRepo.get(entry.index)?.stats?.count
            val label = entry.pokedexNumber?.let { "#%03d".format(it) } ?: "—"
            CatalogueCell(
                displayLabel = label,
                entry = entry,
                photoFile = photoBySk[entry.index],
                discovered = discovered,
                onClick = if (discovered) {
                    { onSpeciesClick(entry.index) }
                } else null,
                count = count,
                modifier = Modifier.weight(1f),
            )
        }
        repeat(3 - row.size) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatsBlock(
    stats: app.arbre.data.GenreStats,
    onSpeciesClick: (Int) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("À Paris", style = MaterialTheme.typography.titleMedium)
            if (stats.topSpecies.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "Espèces les plus fréquentes",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    stats.topSpecies.forEach { top ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSpeciesClick(top.sk) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${top.nv} (${formatCount(top.count)})",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            if (stats.topArr.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "Plus nombreux dans",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    stats.topArr.forEach { item ->
                        Text(
                            "${item.arr} (${formatCount(item.count)})",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

private val FR_NUMBER: NumberFormat = NumberFormat.getInstance(Locale.FRENCH)

private fun formatCount(n: Int): String = FR_NUMBER.format(n)
