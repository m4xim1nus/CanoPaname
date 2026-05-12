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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PictureAsPdf
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
import app.arbre.data.SpeciesEntry
import app.arbre.data.SpeciesIndex
import app.arbre.data.SpeciesInfoRepository
import app.arbre.data.SpeciesStats
import app.arbre.data.catalogueRank
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
import app.arbre.ui.common.WikipediaBlock
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeciesDetailScreen(
    speciesIndex: Int,
    onBack: () -> Unit,
    /**
     * `Set<Int>` à filtrer sur la carte. S5 du cycle Catalogue passait un set
     * polymorphe (sp. + sks identifiés capturés du genre) ; S8 a déménagé ce
     * comportement vers `GenreDetailScreen`. Côté fiche espèce, le set est
     * désormais toujours singleton `setOf(entry.index)`.
     */
    onShowOnMap: (Set<Int>) -> Unit = {},
    onShowArbreOnMap: (Long) -> Unit = {},
    onRemarquableClick: (Long) -> Unit = {},
    onUnlockLost: () -> Unit = {},
    /**
     * S8 : la fiche `(G, sp.)` est remplacée par `GenreDetailScreen`. Ce
     * callback est invoqué quand un deep link historique (`Routes.species(sk)`
     * sur un `unknownSpecies`) atterrit ici — on redirige immédiatement vers
     * la fiche genre correspondante.
     */
    onRedirectToGenre: (String) -> Unit = {},
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
    if (entry.unknownSpecies) {
        // Deep link historique vers une fiche `(G, sp.)` : redirige vers la
        // fiche genre. `popUpTo(SPECIES) inclusive` côté NavHost évite la
        // boucle visuelle au back depuis la fiche genre.
        LaunchedEffect(entry.index, entry.genre) { onRedirectToGenre(entry.genre) }
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

    var lightboxIndex by remember(speciesIndex) { mutableStateOf<Int?>(null) }
    var pendingDeleteIndex by remember(speciesIndex) { mutableStateOf<Int?>(null) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val photoFiles = captures.map { it.resolvedFile(ctx) }

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

            item {
                WikipediaBlock(
                    summary = info?.summary,
                    wikipediaTitle = info?.wikipediaTitle,
                    emptyMessage = "Pas d'info encyclopédique disponible pour cette espèce.",
                )
            }

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
                // S8 : la fiche `(G, sp.)` a déménagé vers GenreDetailScreen,
                // qui porte désormais la logique de set polymorphe. Ici on
                // filtre sur le sk de l'espèce identifiée seul.
                ShowOnMapButton(onClick = { onShowOnMap(setOf(entry.index)) })
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
