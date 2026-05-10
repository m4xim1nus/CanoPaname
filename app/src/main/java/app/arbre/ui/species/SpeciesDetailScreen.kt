package app.arbre.ui.species

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PictureAsPdf
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.arbre.R
import app.arbre.ui.theme.arbresColors
import app.arbre.ui.theme.arbresMotion
import app.arbre.data.Arbre
import app.arbre.data.ArbreRepository
import app.arbre.data.ArrCount
import app.arbre.data.Capture
import app.arbre.data.SpeciesEntry
import app.arbre.data.SpeciesIndex
import app.arbre.data.SpeciesInfo
import app.arbre.data.SpeciesInfoRepository
import app.arbre.data.SpeciesStats
import app.arbre.data.catalogueRank
import app.arbre.ui.common.CatalogueCell
import app.arbre.data.label
import app.arbre.data.parseArrKey
import app.arbre.data.rememberArbreRepository
import app.arbre.data.rememberCaptureRepository
import app.arbre.data.rememberSpeciesIndex
import app.arbre.data.rememberSpeciesInfoRepository
import app.arbre.data.resolvedFile
import app.arbre.ui.common.DeleteCaptureDialog
import app.arbre.ui.common.PhotoGallery
import app.arbre.ui.common.PhotoLightbox
import app.arbre.ui.common.ShowOnMapButton
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeciesDetailScreen(
    speciesIndex: Int,
    onBack: () -> Unit,
    /**
     * `Set<Int>` à filtrer sur la carte. Sprint 4bis (cycle Catalogue) :
     * fiche `(G, sp.)` envoie `{sk_sp.} ∪ {sks_du_genre_capturés}` ; fiches
     * normales envoient `setOf(sk)` singleton.
     */
    onShowOnMap: (Set<Int>) -> Unit = {},
    onShowArbreOnMap: (Long) -> Unit = {},
    onSpeciesClick: (Int) -> Unit = {},
    onRemarquableClick: (Long) -> Unit = {},
    onUnlockLost: () -> Unit = {},
    celebrate: Boolean = false,
) {
    val speciesIndexRepo = rememberSpeciesIndex()
    val arbreRepo = rememberArbreRepository()
    val captureRepo = rememberCaptureRepository()
    val speciesInfoRepo = rememberSpeciesInfoRepository()

    val entry = speciesIndexRepo.get(speciesIndex)
    if (entry == null) {
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

    // ~200 remarquables au total — chargés en mémoire et filtrés Kotlin.
    var remarquablesEspece by remember(speciesIndex) {
        mutableStateOf<List<Arbre>>(emptyList())
    }
    LaunchedEffect(speciesIndex) {
        remarquablesEspece = loadRemarquablesPourEspece(arbreRepo, speciesIndexRepo, speciesIndex)
    }

    // Toutes saisons confondues : décide ligne par ligne entre adresse
    // dévoilée + cliquable et silhouette « ??? + arrondissement ».
    val capturedRemarquables by captureRepo.capturedRemarquableIds()
        .collectAsState(initial = emptySet())
    val capturedSpecies by captureRepo.capturedSpeciesIndices()
        .collectAsState(initial = emptySet())

    var lightboxIndex by remember(speciesIndex) { mutableStateOf<Int?>(null) }
    var pendingDeleteIndex by remember(speciesIndex) { mutableStateOf<Int?>(null) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val photoFiles = captures.map { it.resolvedFile(ctx) }

    // Sprint 4bis (cycle Catalogue) : sur la fiche `(G, sp.)`, on construit
    // un mini-catalogue des espèces du genre + le set sk pour le filtre carte.
    // `genreEntries` : toutes les espèces identifiées du genre (sans le `sp.`
    // courant, qui est déjà la fiche), triées par `pokedexNumber` croissant
    // si présent, sinon par count Paris décroissant.
    // `genrePhotos` : 1re capture par sk frère, pour le slot photo des cards.
    // `genreFilterSet` : set sk passé à `onShowOnMap` (sp. + sks identifiés
    // capturés du genre).
    val genreEntries: List<SpeciesEntry> = remember(entry, speciesIndexRepo) {
        if (!entry.unknownSpecies) emptyList()
        else speciesIndexRepo.entriesOfGenre(entry.genre)
            .filter { it.index != entry.index && !it.unknownSpecies }
            .sortedWith(
                compareBy<SpeciesEntry> { it.pokedexNumber == null }
                    .thenBy { it.pokedexNumber ?: Int.MAX_VALUE }
                    .thenByDescending { speciesInfoRepo.get(it.index)?.stats?.count ?: 0 }
                    .thenBy { it.genre.lowercase() }
                    .thenBy { it.espece.lowercase() }
            )
    }
    val allCapturesForGenre by remember(entry, genreEntries) {
        if (!entry.unknownSpecies || genreEntries.isEmpty()) {
            flowOf(emptyList<Capture>())
        } else {
            val sks = genreEntries.map { it.index }.toSet()
            captureRepo.toutesLesCaptures()
                .map { all -> all.filter { !it.remarquable && it.speciesIndex in sks } }
        }
    }.collectAsState(initial = emptyList())
    val genrePhotos: Map<Int, java.io.File> = remember(allCapturesForGenre, ctx) {
        allCapturesForGenre
            .groupBy { it.speciesIndex }
            .mapValues { (_, caps) ->
                caps.maxByOrNull { it.timestamp }!!.resolvedFile(ctx)
            }
    }
    // Set sk pour `onShowOnMap` : sp. lui-même + chaque sk identifié du
    // genre **capturé** (pas tous les sks du genre — focus « ce que j'ai
    // résolu » + « sp. à résoudre », cf. BACKLOG cycle Catalogue).
    val genreFilterSet: Set<Int> = remember(entry, capturedSpecies, speciesIndexRepo) {
        if (!entry.unknownSpecies) setOf(entry.index)
        else {
            val capturedSiblings = capturedSpecies.filter { sk ->
                speciesIndexRepo.get(sk)?.let { e ->
                    e.genre == entry.genre && !e.unknownSpecies
                } == true
            }.toSet()
            setOf(entry.index) + capturedSiblings
        }
    }

    // Cycle Catalogue : `displayNomCommun` consomme le `nv` quand l'asset le
    // porte, sinon retombe sur `nomCommun` (ex. via `arbreSample`).
    val title = entry.displayNomCommun
    // `catalogueRank` retourne `null` pour les `unknownSpecies` — pas de `#`
    // côté topbar (cohérent avec la section dédiée Arboretum sans numéro).
    val rank = remember(speciesIndex, speciesIndexRepo, speciesInfoRepo) {
        catalogueRank(speciesIndex, speciesIndexRepo, speciesInfoRepo)
    }
    val catalogueTotal = remember(speciesIndexRepo) {
        // Total identifié pour parallélisme avec ArboretumScreen ; fallback
        // total si l'asset legacy ne porte pas le flag `u`.
        val identifiedCount = speciesIndexRepo.entries().count { !it.unknownSpecies }
        if (identifiedCount > 0) identifiedCount else speciesIndexRepo.total
    }
    Scaffold(
        topBar = {
            SpeciesDetailTopBar(
                title = title,
                catalogueRank = rank,
                catalogueTotal = catalogueTotal,
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
            if (celebrate) {
                item { CelebrationHero(entry) }
            }

            item { IdentityBlock(entry, arbreSample) }

            if (photoFiles.isNotEmpty()) {
                item {
                    PhotoGallery(
                        photoFiles = photoFiles,
                        onPhotoClick = { idx -> lightboxIndex = idx },
                        onPhotoLongClick = { idx -> pendingDeleteIndex = idx },
                    )
                }
            }

            if (entry.unknownSpecies && genreEntries.isNotEmpty()) {
                item {
                    GenreMiniCatalogueHeader(
                        genreEntries = genreEntries,
                        capturedSpecies = capturedSpecies,
                    )
                }
                items(genreEntries.chunked(3)) { row ->
                    GenreMiniCatalogueRow(
                        row = row,
                        speciesIndexRepo = speciesIndexRepo,
                        speciesInfoRepo = speciesInfoRepo,
                        capturedSpecies = capturedSpecies,
                        photoBySk = genrePhotos,
                        onSpeciesClick = onSpeciesClick,
                    )
                }
            }

            item { WikipediaBlock(info) }

            info?.pdfUrl?.let { pdfUrl ->
                item { EssencePdfBlock(pdfUrl) }
            }

            info?.stats?.let { stats ->
                item { StatsBlock(stats) }
            }

            if (remarquablesEspece.isNotEmpty()) {
                item {
                    RemarquablesDeCetteEspece(
                        remarquables = remarquablesEspece,
                        capturedIds = capturedRemarquables,
                        onClick = onRemarquableClick,
                    )
                }
            }

            item {
                // Sprint 4bis : sur la fiche `(G, sp.)`, le bouton filtre la
                // carte sur le set genre (sp. + identifiées capturées) plutôt
                // que sur le sk seul.
                ShowOnMapButton(onClick = { onShowOnMap(genreFilterSet) })
            }
        }
        PhotoLightbox(
            photoFiles = photoFiles,
            selectedIndex = lightboxIndex,
            onDismiss = { lightboxIndex = null },
            onDeleteAt = { idx -> pendingDeleteIndex = idx },
            onJumpToMapAt = { idx ->
                captures.getOrNull(idx)?.arbreId?.let(onShowArbreOnMap)
            },
        )

        pendingDeleteIndex?.let { idx ->
            val capture = captures.getOrNull(idx)
            val file = photoFiles.getOrNull(idx)
            if (capture == null || file == null) {
                pendingDeleteIndex = null
                return@let
            }
            DeleteCaptureDialog(
                isLastOfEntity = captures.size == 1,
                entityKindLabel = "cette espèce",
                entityName = entry.displayNomCommun,
                onConfirm = {
                    val wasLast = captures.size == 1
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

private suspend fun loadRemarquablesPourEspece(
    arbreRepo: ArbreRepository,
    speciesIndexRepo: SpeciesIndex,
    sk: Int,
): List<Arbre> {
    val all = arbreRepo.arbresRemarquables()
    return all.filter { speciesIndexRepo.indexOf(it) == sk }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpeciesDetailTopBar(
    title: String,
    catalogueRank: Int?,
    catalogueTotal: Int,
    onBack: () -> Unit,
) {
    TopAppBar(
        title = {
            Column {
                Text(title)
                if (catalogueRank != null) {
                    // Rang 1-based partagé avec `ArboretumScreen.CatalogueView`.
                    Text(
                        "#$catalogueRank / $catalogueTotal",
                        style = MaterialTheme.typography.bodySmall,
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

/** Climax « 1re capture » : cascade fade+scale fond → silhouette → binomial
 *  → label, ~1.8 s. Réutilise la grammaire visuelle du splash cold-start.
 */
@Composable
private fun CelebrationHero(entry: SpeciesEntry) {
    val arbresColors = MaterialTheme.arbresColors
    val motion = MaterialTheme.arbresMotion
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(durationMillis = motion.celebration, easing = motion.swayEasing))
    }
    val p = progress.value
    val bgAlpha = ((p / 0.17f).coerceIn(0f, 1f)) * 0.10f
    val silhouetteAlpha = ((p - 0.17f) / 0.33f).coerceIn(0f, 1f)
    val silhouetteScale = 0.85f + silhouetteAlpha * 0.15f
    val binomialAlpha = ((p - 0.5f) / 0.28f).coerceIn(0f, 1f)
    val labelAlpha = ((p - 0.78f) / 0.22f).coerceIn(0f, 1f)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp),
        colors = CardDefaults.cardColors(
            containerColor = arbresColors.feuilleSombre.copy(alpha = bgAlpha + 0.04f),
        ),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_arbre_canonical),
                    contentDescription = null,
                    tint = arbresColors.feuilleSombre,
                    modifier = Modifier
                        .size(120.dp)
                        .graphicsLayer {
                            alpha = silhouetteAlpha
                            scaleX = silhouetteScale
                            scaleY = silhouetteScale
                        },
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = entry.displayName,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontStyle = FontStyle.Italic,
                    ),
                    color = arbresColors.feuilleSombre,
                    modifier = Modifier.graphicsLayer { alpha = binomialAlpha },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Nouvelle espèce dans ton Arboretum",
                    style = MaterialTheme.typography.bodyMedium,
                    color = arbresColors.ecorce,
                    modifier = Modifier.graphicsLayer { alpha = labelAlpha },
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
                entry.displayNomCommun,
                style = MaterialTheme.typography.titleLarge,
            )
            // Sous-titre binôme italique : seulement quand le titre vient de
            // `nv` ou `nomCommun` (sinon le titre EST le binôme, redondance).
            if (entry.nv != null || entry.nomCommun != null || sample?.nomCommun != null) {
                Text(
                    entry.displayName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun RemarquablesDeCetteEspece(
    remarquables: List<Arbre>,
    capturedIds: Set<Long>,
    onClick: (Long) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Arbres remarquables de cette espèce (${remarquables.size})",
                style = MaterialTheme.typography.titleMedium,
            )
            remarquables.forEach { arbre ->
                if (arbre.id in capturedIds) {
                    DiscoveredRemarquableRow(arbre = arbre, onClick = onClick)
                } else {
                    LockedRemarquableRow(arbre = arbre)
                }
            }
        }
    }
}

@Composable
private fun DiscoveredRemarquableRow(arbre: Arbre, onClick: (Long) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(arbre.id) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_remarquable_badge),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(20.dp),
        )
        Text(
            arbre.adresse ?: "Adresse inconnue",
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

@Composable
private fun LockedRemarquableRow(arbre: Arbre) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_remarquable_badge),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(20.dp),
        )
        Text(
            "??? · ${parseArrKey(arbre.adresse).label()}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
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

@Composable
private fun EssencePdfBlock(pdfUrl: String) {
    val ctx = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                runCatching {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(pdfUrl)))
                }
            },
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Outlined.PictureAsPdf,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Fiche essence Ville de Paris",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "Document PDF officiel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Outlined.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
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

// Convention Wikipedia : espaces → `_`. Apostrophes/accents passent tels
// quels (Android `Uri.parse` les encode au besoin).
private fun wikipediaUrlPath(title: String): String =
    title.replace(' ', '_')

/**
 * En-tête de la section mini-catalogue genre : « Espèces du genre {Genre}
 * — X / N capturées ». Donne immédiatement la progression locale (« j'ai
 * 3/55 chênes ») sans noyer l'utilisateur dans la grille.
 */
@Composable
private fun GenreMiniCatalogueHeader(
    genreEntries: List<SpeciesEntry>,
    capturedSpecies: Set<Int>,
) {
    val genre = genreEntries.firstOrNull()?.genre ?: return
    val total = genreEntries.size
    val capturedHere = genreEntries.count { it.index in capturedSpecies }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            "Espèces du genre $genre",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            "$capturedHere / $total capturée${if (capturedHere > 1) "s" else ""}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Une ligne de 3 cards du mini-catalogue genre. La grille n'est pas un
 * `LazyVerticalGrid` (pas imbricable dans le LazyColumn parent) — on chunk
 * la liste côté call-site et on rend chaque batch en `Row` à largeurs égales,
 * paddé avec des `Spacer` si la dernière ligne a moins de 3 items.
 */
@Composable
private fun GenreMiniCatalogueRow(
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
        // Padding visuel pour les lignes incomplètes (1 ou 2 cards).
        repeat(3 - row.size) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}
