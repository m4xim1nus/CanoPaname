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
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
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
import app.arbre.data.CataloguePhotos
import app.arbre.data.GenreInfoRepository
import app.arbre.data.SpeciesEntry
import app.arbre.data.SpeciesIndex
import app.arbre.data.SpeciesPhotoRepository
import app.arbre.data.rememberArbreRepository
import app.arbre.data.rememberCaptureRepository
import app.arbre.data.rememberDatasetStats
import app.arbre.data.rememberGenreInfoRepository
import app.arbre.data.rememberSpeciesIndex
import app.arbre.data.rememberSpeciesPhotoRepository
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
    onGenreClick: (String) -> Unit = {},
) {
    val captureRepo = rememberCaptureRepository()
    val speciesIndex = rememberSpeciesIndex()
    val stats = rememberDatasetStats()
    val arbreRepo = rememberArbreRepository()
    val genreInfoRepo = rememberGenreInfoRepository()

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

    // Cycle Catalogue : sépare les sks actifs (compteur principal) des
    // sks `unknownSpecies` et zombies. `capturedSks` alimente l'auto-débloquage
    // genre-based des cards Catalogue + le compteur de genres découverts.
    val capturedSks: Set<Int> = speciesGroups.map { it.entry.index }.toSet()
    val nbIdentifiees = speciesGroups.count { it.entry.isActive }
    val nbGenresDecouverts = remember(speciesIndex, capturedSks) {
        speciesIndex.allGenres().count { g -> speciesIndex.genreHasAnyCapture(g, capturedSks) }
    }
    val totalGenres = remember(speciesIndex) { speciesIndex.allGenres().size }

    // 2 niveaux de navigation. Niveau 1 = Catalogue (annuaire) vs Historique
    // (timeline des captures). Niveau 2 sous Catalogue = tri par fréquence
    // (Pokédex stable) ou par genre. Deux `rememberSaveable` distincts pour
    // que le sous-tri persiste indépendamment du toggle haut.
    var tab by rememberSaveable { mutableStateOf(ArboretumTab.CATALOGUE) }
    var catalogueSort by rememberSaveable { mutableStateOf(CatalogueSort.PAR_FREQUENCE) }

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
                nbGenresDecouverts = nbGenresDecouverts,
                totalGenres = totalGenres,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
            )
            TabSelector(
                current = tab,
                onSelect = { tab = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
            if (tab == ArboretumTab.CATALOGUE) {
                CatalogueSortSelector(
                    current = catalogueSort,
                    onSelect = { catalogueSort = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            // Les captures `(G, sp.)` n'apparaissent dans aucun mode du
            // Catalogue : elles restent accessibles via la fiche genre (qui
            // héberge la galerie photos sp.). Les captures sur espèces
            // zombies (légalement orphelines suite à un import ou une
            // régénération de dataset) sont aussi cachées, pour cohérence
            // avec le dénominateur certifié.
            val speciesGroupsIdentifiees = speciesGroups.filter { it.entry.isActive }
            when (tab) {
                ArboretumTab.HISTORIQUE -> DecouverteView(
                    speciesGroups = speciesGroupsIdentifiees,
                    totalEspecesIdentifiees = stats.totalEspecesIdentifiees,
                    arbreRepo = arbreRepo,
                    onSpeciesClick = onSpeciesClick,
                )
                ArboretumTab.CATALOGUE -> when (catalogueSort) {
                    CatalogueSort.PAR_FREQUENCE -> FrequenceView(
                        speciesIndex = speciesIndex,
                        speciesGroups = speciesGroupsIdentifiees,
                        capturedSks = capturedSks,
                        onSpeciesClick = onSpeciesClick,
                    )
                    CatalogueSort.PAR_GENRE -> CatalogueView(
                        speciesIndex = speciesIndex,
                        speciesGroups = speciesGroupsIdentifiees,
                        capturedSks = capturedSks,
                        genreInfoRepo = genreInfoRepo,
                        onSpeciesClick = onSpeciesClick,
                        onGenreClick = onGenreClick,
                    )
                }
            }
        }
    }
}

private enum class ArboretumTab { CATALOGUE, HISTORIQUE }

private enum class CatalogueSort { PAR_FREQUENCE, PAR_GENRE }

@Composable
private fun TabSelector(
    current: ArboretumTab,
    onSelect: (ArboretumTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs = ArboretumTab.entries
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        tabs.forEachIndexed { idx, tab ->
            SegmentedButton(
                selected = current == tab,
                onClick = { onSelect(tab) },
                shape = SegmentedButtonDefaults.itemShape(idx, tabs.size),
            ) {
                Text(stringResource(when (tab) {
                    ArboretumTab.CATALOGUE -> R.string.segment_catalogue
                    ArboretumTab.HISTORIQUE -> R.string.segment_historique
                }))
            }
        }
    }
}

@Composable
private fun CatalogueSortSelector(
    current: CatalogueSort,
    onSelect: (CatalogueSort) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sorts = CatalogueSort.entries
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        sorts.forEachIndexed { idx, sort ->
            SegmentedButton(
                selected = current == sort,
                onClick = { onSelect(sort) },
                shape = SegmentedButtonDefaults.itemShape(idx, sorts.size),
            ) {
                Text(stringResource(when (sort) {
                    CatalogueSort.PAR_FREQUENCE -> R.string.segment_par_frequence
                    CatalogueSort.PAR_GENRE -> R.string.segment_par_genre
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
 * Vue Fréquence : annuaire exhaustif des espèces actives
 * (`SpeciesEntry.isActive` : identifiées + au moins un arbre vivant), tri par
 * `pokedexNumber` backend croissant (= count Paris décroissant figé au build).
 * `#NNN` affiché = le `pokedexNumber` lui-même. Les non-capturées apparaissent
 * en silhouette `???` via le rendu `discovered = false` de `CatalogueCell`.
 * Les `unknownSpecies` n'ont pas de rang de fréquence ; les zombies
 * (`count=0`) sont également cachés.
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
    val photoRepo = rememberSpeciesPhotoRepository()
    val ordered = remember(speciesIndex) {
        speciesIndex.entries()
            .filter { it.isActive }
            .sortedBy { it.pokedexNumber!! }
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
                displayLabel = "#%03d".format(entry.pokedexNumber!!),
                entry = entry,
                photos = cellPhotos(photoRepo, entry.index, discovered, photoFile),
                discovered = discovered,
                onClick = if (discovered) {
                    { onSpeciesClick(entry.index) }
                } else null,
            )
        }
    }
}

/**
 * Vignette des cellules découvertes : photo de référence de l'espèce
 * (même visuel que le hero de la fiche) prioritaire sur la 1re capture.
 * Les non-découvertes gardent la silhouette « ??? » — la photo de réf.
 * ne dévoile jamais une espèce non capturée.
 */
private fun cellPhotos(
    photoRepo: SpeciesPhotoRepository,
    sk: Int,
    discovered: Boolean,
    captureFile: File?,
): CataloguePhotos = CataloguePhotos(
    referencePath = if (discovered) photoRepo.get(sk)?.principal?.assetPath else null,
    captureFile = captureFile,
)

/**
 * Vue Catalogue par genre : annuaire exhaustif groupé par genre. Chapitres en
 * **ordre alphabétique**, en-tête `Nom FR (latin) · X / Y` (X = espèces
 * capturées du genre, Y = espèces identifiées du genre — `sp.` exclus).
 * Intra-genre, tri par `pokedexNumber` croissant (= le `#NNN` affiché,
 * cohérent avec la vue Par fréquence et la fiche espèce).
 *
 * Les genres sans capture (sp. ou identifiée) sont rendus en silhouette
 * « ??? » non cliquable. Les espèces non capturées restent affichées en
 * silhouette `???` individuelle via `CatalogueCell`.
 *
 * Pas de cards `(G, sp.)` — l'entrée est entièrement absorbée par la fiche
 * genre. Le tap sur un header découvert ouvre cette fiche.
 */
@Composable
private fun CatalogueView(
    speciesIndex: SpeciesIndex,
    speciesGroups: List<SpeciesGroup>,
    capturedSks: Set<Int>,
    genreInfoRepo: GenreInfoRepository,
    onSpeciesClick: (Int) -> Unit,
    onGenreClick: (String) -> Unit,
) {
    val ctx = LocalContext.current
    val firstPhotoBySk: Map<Int, File> = remember(speciesGroups, ctx) {
        speciesGroups.associate { it.entry.index to it.captures.first().resolvedFile(ctx) }
    }
    val photoRepo = rememberSpeciesPhotoRepository()
    // Pré-calcul des chapitres. Memoisé sur (speciesIndex, capturedSks) :
    // recompute uniquement à la capture. Le filtre `isActive` masque
    // `unknownSpecies` et zombies count=0.
    val chapters = remember(speciesIndex, capturedSks) {
        speciesIndex.genres().map { genre ->
            val identifiedSorted = speciesIndex.entriesOfGenre(genre)
                .filter { it.isActive }
                .sortedBy { it.pokedexNumber!! }
            GenreChapter(
                genre = genre,
                identified = identifiedSorted,
                captured = speciesIndex.capturedCountInGenre(genre, capturedSks),
                total = speciesIndex.genreCount(genre),
                discovered = speciesIndex.genreHasAnyCapture(genre, capturedSks),
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
                GenreChapterHeader(
                    chapter = chapter,
                    nomFr = genreInfoRepo.get(chapter.genre)?.nomFr,
                    onClick = if (chapter.discovered) {
                        { onGenreClick(chapter.genre) }
                    } else null,
                )
            }
            gridItems(
                chapter.identified,
                key = { entry -> entry.index },
            ) { entry ->
                val discovered = entry.index in capturedSks
                val photoFile = if (discovered) firstPhotoBySk[entry.index] else null
                CatalogueCell(
                    displayLabel = "#%03d".format(entry.pokedexNumber!!),
                    entry = entry,
                    photos = cellPhotos(photoRepo, entry.index, discovered, photoFile),
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
    val identified: List<SpeciesEntry>,
    val captured: Int,
    val total: Int,
    val discovered: Boolean,
)

@Composable
private fun GenreChapterHeader(
    chapter: GenreChapter,
    nomFr: String?,
    onClick: (() -> Unit)?,
) {
    val baseModifier = Modifier
        .fillMaxWidth()
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }
        .padding(top = 16.dp, bottom = 4.dp)
    Row(
        modifier = baseModifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // Genre découvert → titre = nom FR (fallback latin), sous-titre
            // latin italique si nomFr présent. Genre non découvert → silhouette
            // « ??? » seule (titre ET latin masqués), pas de chevron (le
            // `onClick == null` neutralise le tap).
            if (!chapter.discovered) {
                Text(
                    "???",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val displayTitle = nomFr ?: chapter.genre
                Text(
                    displayTitle,
                    style = MaterialTheme.typography.titleMedium,
                )
                if (nomFr != null) {
                    Text(
                        chapter.genre,
                        style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Text(
            "${chapter.captured} / ${chapter.total}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (chapter.discovered) {
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = "Ouvrir la fiche du genre ${chapter.genre}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
    nbIdentifiees: Int,
    totalEspecesIdentifiees: Int,
    nbGenresDecouverts: Int,
    totalGenres: Int,
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
            Text(
                "$nbGenresDecouverts / $totalGenres genres découverts",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(top = 4.dp),
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
                // apportent un nom différent du binôme latin.
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
