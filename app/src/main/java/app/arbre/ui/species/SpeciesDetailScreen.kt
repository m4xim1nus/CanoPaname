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
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Park
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
import app.arbre.data.SpeciesEntry
import app.arbre.data.SpeciesIndex
import app.arbre.data.SpeciesInfo
import app.arbre.data.SpeciesStats
import app.arbre.data.label
import app.arbre.data.parseArrKey
import app.arbre.data.rememberArbreRepository
import app.arbre.data.rememberCaptureRepository
import app.arbre.data.rememberSpeciesIndex
import app.arbre.data.rememberSpeciesInfoRepository
import app.arbre.ui.common.PhotoGallery
import app.arbre.ui.common.PhotoLightbox
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeciesDetailScreen(
    speciesIndex: Int,
    onBack: () -> Unit,
    onShowOnMap: () -> Unit = {},
    onRemarquableClick: (Long) -> Unit = {},
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

    // Liste statique en mémoire (~200 arbres remarquables au total). Chargée
    // une fois au montage de l'écran et filtrée côté Kotlin par speciesIndex.
    var remarquablesEspece by remember(speciesIndex) {
        mutableStateOf<List<Arbre>>(emptyList())
    }
    LaunchedEffect(speciesIndex) {
        remarquablesEspece = loadRemarquablesPourEspece(arbreRepo, speciesIndexRepo, speciesIndex)
    }

    // Set des remarquables capturés (toutes saisons confondues) pour décider
    // ligne par ligne si on dévoile l'adresse + cliquabilité, ou si on rend
    // une silhouette « ??? + arrondissement ». Réveil de la chasse cf. Phase
    // 10.5 sous-groupe E.
    val capturedRemarquables by captureRepo.capturedRemarquableIds()
        .collectAsState(initial = emptySet())

    var lightboxIndex by remember(speciesIndex) { mutableStateOf<Int?>(null) }
    val photoPaths = captures.map { it.photoPath }

    val title = arbreSample?.nomCommun ?: entry.displayName

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Retour")
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
                item { CelebrationHero(entry) }
            }

            item { IdentityBlock(entry, arbreSample) }

            if (photoPaths.isNotEmpty()) {
                item {
                    PhotoGallery(
                        photoPaths = photoPaths,
                        onPhotoClick = { idx -> lightboxIndex = idx },
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

            item { ShowOnMapButton(onShowOnMap) }
        }
        PhotoLightbox(
            photoPaths = photoPaths,
            selectedIndex = lightboxIndex,
            onDismiss = { lightboxIndex = null },
        )
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

@Composable
private fun ShowOnMapButton(onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(
            Icons.Outlined.Map,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Text(
            "Voir sur la carte",
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/**
 * Climax « 1re capture » sur la fiche-espèce. Cascade fade+scale sur
 * 1.8 s : fond → silhouette espèce → nom binomial → label de confirmation.
 * Réutilise la grammaire du splash cold-start (sway sinusoïdal léger).
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
                    imageVector = Icons.Outlined.Park,
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

/**
 * Wikipedia accepte les espaces et la plupart des caractères dans son URL ;
 * la convention est de remplacer les espaces par `_`. Les apostrophes et
 * accents passent tels quels (Android URI les encode si besoin via Uri.parse).
 */
private fun wikipediaUrlPath(title: String): String =
    title.replace(' ', '_')
