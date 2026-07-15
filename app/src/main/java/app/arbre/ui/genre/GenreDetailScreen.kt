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
import app.arbre.data.ArrCount
import app.arbre.data.Capture
import app.arbre.data.CataloguePhotos
import app.arbre.data.SpeciesEntry
import app.arbre.data.SpeciesIndex
import app.arbre.data.SpeciesInfoRepository
import app.arbre.data.SpeciesPhotoRepository
import app.arbre.data.rememberCaptureRepository
import app.arbre.data.rememberGenreInfoRepository
import app.arbre.data.rememberSpeciesIndex
import app.arbre.data.rememberSpeciesInfoRepository
import app.arbre.data.rememberSpeciesPhotoRepository
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
 * Fiche genre dédiée : mini-catalogue des espèces identifiées du genre, carte
 * filtrée sur le genre, galerie des captures à espèce indéterminée (`G sp.`).
 * L'entrée `(G, sp.)` n'est pas exposée comme card d'espèce dans le Catalogue
 * — c'est cette fiche qui l'absorbe.
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
    actions: GenreActions,
) {
    val onBack = actions.onBack
    val onSpeciesClick = actions.onSpeciesClick
    val onShowOnMap = actions.onShowOnMap
    val onShowArbreOnMap = actions.onShowArbreOnMap
    val onUnlockLost = actions.onUnlockLost
    val speciesIndexRepo = rememberSpeciesIndex()
    val speciesInfoRepo = rememberSpeciesInfoRepository()
    val speciesPhotoRepo = rememberSpeciesPhotoRepository()
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
    // `initial = null` distingue « flow pas encore émis » de « set vraiment
    // vide ». Sans ça, la 1re recomposition voit `emptySet()` et déclenche un
    // faux-négatif du verrou ci-dessous → la fiche se ferme avant que le 1er
    // emit n'arrive (~10–50 ms via Room/SharedFlow). Une fois le 1er emit reçu,
    // le set est non-null pour toute la durée de vie du composable.
    val capturedSpeciesNullable by captureRepo.capturedSpeciesIndices()
        .collectAsState(initial = null)
    val capturedSpecies = capturedSpeciesNullable ?: run {
        // 1er render avant l'emit du Flow : on attend silencieusement.
        // Scaffold minimal pour ne pas flasher le fond blanc derrière la nav.
        Scaffold(
            topBar = {
                GenreDetailTopBar(title = genre, latinSubtitle = null, onBack = onBack)
            },
        ) { padding ->
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
        return
    }

    // Verrou genre : la fiche n'est accessible qu'à partir de la 1re capture
    // du genre (sp. ou identifiée). Cohérent avec la silhouette « ??? » posée
    // côté Arboretum sur les chapter headers non découverts.
    //
    // La sortie de fiche genre passe par `onUnlockLost` (pop vers MAP), pas
    // par `onBack` (pop d'un cran). Sert deux cas : (1) deep link cassé vers
    // un genre jamais capturé (rare), (2) suppression de la dernière capture
    // du genre depuis la galerie sp. ci-dessous. Unifier les deux chemins
    // évite une double pop concurrente : si le dialog appelait son propre
    // `onUnlockLost()` après la suspend `deleteCapture` et que la garde
    // réactive appelait `onBack()` pendant la recompo, on se retrouverait
    // avec un écran blanc terminal.
    if (!speciesIndexRepo.genreHasAnyCapture(genre, capturedSpecies)) {
        androidx.compose.runtime.LaunchedEffect(genre) { onUnlockLost() }
        return
    }
    // Filtre `isActive` (exclut `unknownSpecies` et zombies count=0) —
    // cohérent avec ArboretumScreen et avec le build qui calcule
    // `genre-info.json:stats.speciesIdentified` sur la même définition.
    val identifiedEntries: List<SpeciesEntry> = remember(allEntriesOfGenre, speciesInfoRepo) {
        allEntriesOfGenre
            .filter { it.isActive }
            .sortedWith(
                compareBy<SpeciesEntry> { it.pokedexNumber!! }
                    .thenByDescending { speciesInfoRepo.get(it.index)?.stats?.count ?: 0 }
                    .thenBy { it.espece.lowercase() }
            )
    }
    val spEntry: SpeciesEntry? = remember(allEntriesOfGenre) {
        allEntriesOfGenre.firstOrNull { it.unknownSpecies }
    }

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

    val genreFilterSet: Set<Int> = remember(genre, capturedSpecies) {
        speciesIndexRepo.genreFilterSet(genre, capturedSpecies)
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

            // « À propos » + « À Paris » contextualisent le genre — placés
            // avant le mini-catalogue qui, lui, est plus long et plus
            // opérationnel (navigation espèces).
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
                item { StatsBlock(stats = stats) }
            }

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
                        speciesPhotoRepo = speciesPhotoRepo,
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
                    pendingDeleteIndex = null
                    lightboxIndex = null
                    scope.launch { captureRepo.deleteCapture(capture, file) }
                    // Pas d'appel direct à `onUnlockLost` ici : la garde
                    // réactive `genreHasAnyCapture` au-dessus se charge de
                    // pop vers MAP si c'était la dernière capture du genre.
                    // Évite la double pop avec la recompo concurrente.
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
    speciesPhotoRepo: SpeciesPhotoRepository,
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
                // Photo de référence prioritaire sur la 1re capture, jamais
                // pour une cellule non découverte (la silhouette reste).
                photos = CataloguePhotos(
                    referencePath = if (discovered) {
                        speciesPhotoRepo.get(entry.index)?.principal?.assetPath
                    } else null,
                    captureFile = photoBySk[entry.index],
                ),
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
private fun StatsBlock(stats: app.arbre.data.GenreStats) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("À Paris", style = MaterialTheme.typography.titleMedium)
            // Résumé global (count + proportion) sur le modèle de la fiche
            // espèce. `proportion` nullable → asset legacy retombe sur le
            // simple count, intentionnellement sans % entre parenthèses.
            Text(
                buildString {
                    append(formatCount(stats.count))
                    append(" arbres")
                    stats.proportion?.let {
                        append(" (")
                        append(formatPercent(it))
                        append(" du dataset)")
                    }
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
            if (stats.topArr.isNotEmpty()) {
                ArrSection(
                    title = "Plus nombreux dans",
                    items = stats.topArr,
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
