package app.arbre.ui.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.arbre.backup.ExportResult
import app.arbre.backup.ImportError
import app.arbre.backup.ImportResult
import app.arbre.backup.defaultExportFilename
import app.arbre.data.BadgeDef
import app.arbre.data.BadgeState
import app.arbre.data.rememberArbreRepository
import app.arbre.data.rememberBackupExporter
import app.arbre.data.rememberBackupImporter
import app.arbre.data.rememberBadgeRepository
import app.arbre.data.rememberCaptureRepository
import app.arbre.data.rememberDatasetStats
import app.arbre.data.rememberSpeciesIndex
import app.arbre.R
import app.arbre.ui.badges.icon
import app.arbre.ui.common.EmptyState
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onBadgesClick: () -> Unit = {},
    onHowToPlayClick: () -> Unit = {},
    onAboutClick: () -> Unit = {},
) {
    val captureRepo = rememberCaptureRepository()
    val arbreRepo = rememberArbreRepository()
    val badgeRepo = rememberBadgeRepository()
    val datasetStats = rememberDatasetStats()
    val speciesIndex = rememberSpeciesIndex()
    val backupExporter = rememberBackupExporter()
    val backupImporter = rememberBackupImporter()
    val coScope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var backupBusy by remember { mutableStateOf<BackupBusy>(BackupBusy.Idle) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        backupBusy = BackupBusy.Exporting
        coScope.launch {
            val result = backupExporter.export(uri)
            backupBusy = BackupBusy.Idle
            val msg = when (result) {
                is ExportResult.Success -> "Sauvegarde exportée (${result.captureCount} captures)"
                is ExportResult.Failure -> "Échec de l'export"
            }
            snackbar.showSnackbar(msg)
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        backupBusy = BackupBusy.Importing
        coScope.launch {
            val result = backupImporter.import(uri)
            backupBusy = BackupBusy.Idle
            val msg = when (result) {
                is ImportResult.Success -> buildString {
                    append("${result.imported} ajoutée${if (result.imported > 1) "s" else ""}, ")
                    append("${result.skipped} déjà présente${if (result.skipped > 1) "s" else ""}")
                    if (result.photosMissing > 0) {
                        append(", ${result.photosMissing} photo")
                        if (result.photosMissing > 1) append("s")
                        append(" manquante")
                        if (result.photosMissing > 1) append("s")
                    }
                }
                is ImportResult.Failure -> when (result.reason) {
                    ImportError.CORRUPT_ZIP -> "Fichier illisible"
                    ImportError.META_MISSING -> "meta.json absent — fichier non reconnu"
                    ImportError.CAPTURES_MISSING -> "captures.json absent — fichier non reconnu"
                    ImportError.SCHEMA_TOO_NEW -> "Backup créé avec une version plus récente de l'app"
                    ImportError.TOO_LARGE -> "Backup trop volumineux, refusé"
                    ImportError.IO_ERROR -> "Erreur de lecture, réessaie"
                }
            }
            snackbar.showSnackbar(msg)
        }
    }

    val firstCaptureTs by captureRepo.firstCaptureTimestamp()
        .collectAsState(initial = null)
    val capturedSpecies by captureRepo.capturedSpeciesIndices()
        .collectAsState(initial = emptySet())
    val capturedRemarquables by captureRepo.capturedRemarquableIds()
        .collectAsState(initial = emptySet())
    val captureCount by captureRepo.captureCount().collectAsState(initial = 0)
    val allBadges by badgeRepo.badges().collectAsState(initial = emptyList())

    val nbSpecies = capturedSpecies.size
    val nbRemarquables = capturedRemarquables.size

    var arbresDecouverts by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(capturedSpecies, capturedRemarquables) {
        arbresDecouverts = arbreRepo.nombreArbresDecouverts(
            capturedSpecies,
            capturedRemarquables,
            speciesIndex,
        )
    }

    val recentUnlocks = remember(allBadges) {
        allBadges.flatMap { state ->
            when (state) {
                is BadgeState.Binary -> if (state.unlockedAt != null) {
                    listOf(
                        BadgeUnlock(
                            def = state.def,
                            displayLabel = state.def.label,
                            unlockedAt = state.unlockedAt,
                        )
                    )
                } else emptyList()
                is BadgeState.Progressive -> state.tiers
                    .mapNotNull { tier ->
                        tier.unlockedAt?.let { ts ->
                            BadgeUnlock(
                                def = state.def,
                                displayLabel = "${state.def.label} · ${tier.label}",
                                unlockedAt = ts,
                            )
                        }
                    }
            }
        }.sortedByDescending { it.unlockedAt }.take(3)
    }

    LaunchedEffect(backupBusy) {
        if (backupBusy !is BackupBusy.Idle) {
            delay(60_000)
            backupBusy = BackupBusy.Idle
            snackbar.showSnackbar(
                "L'opération prend plus de 60 s — réessaie ou vérifie l'app système"
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profil") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Retour")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (firstCaptureTs == null) {
                item {
                    EmptyState(
                        title = "Ton aventure commence ici",
                        body = "Approche-toi d'un arbre, capture-le pour révéler son espèce. Tes stats et tes badges s'écriront ici au fil des captures.",
                        illustration = {
                            Image(
                                painter = painterResource(R.drawable.illus_empty_profile),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                            )
                        },
                    )
                }
            }
            item {
                StatsCard(
                    firstCaptureTs = firstCaptureTs,
                    nbSpecies = nbSpecies,
                    totalEspeces = datasetStats.totalEspeces,
                    nbRemarquables = nbRemarquables,
                    nbCaptures = captureCount,
                    arbresDecouverts = arbresDecouverts,
                    totalArbres = datasetStats.totalArbres,
                )
            }
            item {
                Text(
                    "Badges",
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            if (recentUnlocks.isNotEmpty()) {
                item {
                    BadgeGrid(unlocks = recentUnlocks)
                }
            }
            item {
                AllBadgesEntry(onClick = onBadgesClick)
            }
            item {
                HowToPlayEntry(onClick = onHowToPlayClick)
            }
            item {
                AboutEntry(onClick = onAboutClick)
            }
            item {
                Text(
                    "Sauvegarde",
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            item {
                BackupActionCard(
                    title = "Exporter mes captures",
                    icon = Icons.Outlined.Upload,
                    busy = backupBusy is BackupBusy.Exporting,
                    enabled = backupBusy is BackupBusy.Idle,
                    progressLabel = "Export en cours…",
                    onClick = { exportLauncher.launch(defaultExportFilename()) },
                )
            }
            item {
                BackupActionCard(
                    title = "Importer un backup",
                    icon = Icons.Outlined.Download,
                    busy = backupBusy is BackupBusy.Importing,
                    enabled = backupBusy is BackupBusy.Idle,
                    progressLabel = "Import en cours…",
                    onClick = {
                        importLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                    },
                )
            }
        }
    }
}

@Composable
private fun HowToPlayEntry(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(Icons.Outlined.HelpOutline, contentDescription = null)
            Text(
                "Comment jouer",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
            )
        }
    }
}

@Composable
private fun AboutEntry(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(Icons.Outlined.Info, contentDescription = null)
            Text(
                "À propos",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
            )
        }
    }
}

private sealed class BackupBusy {
    object Idle : BackupBusy()
    object Exporting : BackupBusy()
    object Importing : BackupBusy()
}

@Composable
private fun BackupActionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    busy: Boolean,
    enabled: Boolean,
    progressLabel: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(icon, contentDescription = null)
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
            }
            if (busy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    progressLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun StatsCard(
    firstCaptureTs: Long?,
    nbSpecies: Int,
    totalEspeces: Int,
    nbRemarquables: Int,
    nbCaptures: Int,
    arbresDecouverts: Int?,
    totalArbres: Int,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Statistiques",
                style = MaterialTheme.typography.titleMedium,
            )
            StatLine(
                label = if (firstCaptureTs != null) "Première capture" else "Aucune capture",
                value = if (firstCaptureTs != null) {
                    val days = daysSince(firstCaptureTs)
                    when (days) {
                        0L -> "aujourd'hui"
                        1L -> "il y a 1 jour"
                        else -> "il y a $days jours"
                    }
                } else "—",
            )
            StatLine(
                label = "Espèces du Catalogue",
                value = formatProgress(nbSpecies, totalEspeces),
            )
            StatLine(
                label = "Arbres déverrouillés",
                value = arbresDecouverts?.let { formatProgress(it, totalArbres) } ?: "…",
            )
            StatLine(
                label = "Arbres remarquables",
                value = nbRemarquables.toString(),
            )
            StatLine(
                label = "Captures totales",
                value = nbCaptures.toString(),
            )
        }
    }
}

private fun formatProgress(value: Int, total: Int): String {
    if (total <= 0) return value.toString()
    val pct = (value.toLong() * 100 / total).toInt().coerceIn(0, 100)
    return "$value / $total ($pct %)"
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

private data class BadgeUnlock(
    val def: BadgeDef,
    val displayLabel: String,
    val unlockedAt: Long,
)

@Composable
private fun BadgeGrid(unlocks: List<BadgeUnlock>) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier
            .fillMaxWidth()
            .heightForBadges(unlocks.size),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(
            items = unlocks,
            key = { "${it.def.id}-${it.unlockedAt}" },
        ) { unlock ->
            BadgeCell(unlock = unlock)
        }
    }
}

/**
 * Hauteur fixe imposée — un `LazyVerticalGrid` dans un `LazyColumn` ne sait
 * pas se mesurer tout seul.
 */
private fun Modifier.heightForBadges(count: Int): Modifier {
    val rows = (count + 2) / 3
    val rowHeightDp = 140
    val gapDp = 12
    return this.height(
        (rows * rowHeightDp + (rows - 1).coerceAtLeast(0) * gapDp).dp
    )
}

@Composable
private fun BadgeCell(unlock: BadgeUnlock) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = unlock.def.icon(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiary,
                )
            }
            Text(
                unlock.displayLabel,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                formatDate(unlock.unlockedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun AllBadgesEntry(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "Voir tous les badges",
                style = MaterialTheme.typography.titleSmall,
            )
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
            )
        }
    }
}

private fun daysSince(epochMillis: Long): Long {
    val zone = ZoneId.of("Europe/Paris")
    val captureDate = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    return ChronoUnit.DAYS.between(captureDate, today).coerceAtLeast(0L)
}

private val DATE_FORMAT: DateFormat = DateFormat.getDateInstance(DateFormat.SHORT)

private fun formatDate(epochMillis: Long): String =
    DATE_FORMAT.format(Date(epochMillis))
