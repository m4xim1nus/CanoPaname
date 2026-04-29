package app.arbre.ui.profile

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Lock
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.arbre.data.BadgeCatalog
import app.arbre.data.BadgeState
import app.arbre.data.Season
import app.arbre.data.rememberCaptureRepository
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(onBack: () -> Unit) {
    val captureRepo = rememberCaptureRepository()
    // Saison vive seule comptée pour la stat « Saison courante » : pas de
    // sélecteur de saison ici, un toggle binaire suffit (cf. ROADMAP I).
    val currentSeason = Season.current()

    var scope by rememberSaveable { mutableStateOf(ProfileScope.GLOBAL) }

    val firstCaptureTs by captureRepo.firstCaptureTimestamp()
        .collectAsState(initial = null)
    // Stat espèces / remarquables selon le scope : tout l'historique ou la
    // saison vive uniquement. Les Flows sans scope sont conservés pour le
    // mode GLOBAL — pas de re-collect au switch.
    val capturedSpeciesGlobal by captureRepo.capturedSpeciesIndices()
        .collectAsState(initial = emptySet())
    val capturedRemarquablesGlobal by captureRepo.capturedRemarquableIds()
        .collectAsState(initial = emptySet())
    val capturedSpeciesSeason by captureRepo.capturedSpeciesIndices(currentSeason)
        .collectAsState(initial = emptySet())
    val capturedRemarquablesSeason by captureRepo.capturedRemarquableIds(currentSeason)
        .collectAsState(initial = emptySet())
    val captureCount by captureRepo.captureCount().collectAsState(initial = 0)

    val nbSpecies = if (scope == ProfileScope.GLOBAL) capturedSpeciesGlobal.size else capturedSpeciesSeason.size
    val nbRemarquables = if (scope == ProfileScope.GLOBAL) capturedRemarquablesGlobal.size else capturedRemarquablesSeason.size

    // Le badge « 1re capture » reste global — la 1re capture est unique
    // dans la vie du joueur, indépendante de la saison.
    val badges = remember(firstCaptureTs) {
        BadgeCatalog.ALL.map { def ->
            BadgeState(
                def = def,
                unlockedAt = when (def.id) {
                    BadgeCatalog.FIRST_CAPTURE.id -> firstCaptureTs
                    else -> null
                },
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
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                ScopeSelector(
                    current = scope,
                    onSelect = { scope = it },
                    currentSeason = currentSeason,
                )
            }
            item {
                StatsCard(
                    firstCaptureTs = firstCaptureTs,
                    nbSpecies = nbSpecies,
                    nbRemarquables = nbRemarquables,
                    nbCaptures = captureCount,
                    scope = scope,
                    currentSeason = currentSeason,
                )
            }
            item {
                Text(
                    "Badges",
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            item {
                BadgeGrid(badges = badges)
            }
        }
    }
}

private enum class ProfileScope { GLOBAL, SEASON }

@Composable
private fun ScopeSelector(
    current: ProfileScope,
    onSelect: (ProfileScope) -> Unit,
    currentSeason: Season,
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = current == ProfileScope.GLOBAL,
            onClick = { onSelect(ProfileScope.GLOBAL) },
            shape = SegmentedButtonDefaults.itemShape(0, 2),
        ) { Text("Global") }
        SegmentedButton(
            selected = current == ProfileScope.SEASON,
            onClick = { onSelect(ProfileScope.SEASON) },
            shape = SegmentedButtonDefaults.itemShape(1, 2),
        ) { Text(currentSeason.label) }
    }
}

@Composable
private fun StatsCard(
    firstCaptureTs: Long?,
    nbSpecies: Int,
    nbRemarquables: Int,
    nbCaptures: Int,
    scope: ProfileScope,
    currentSeason: Season,
) {
    val seasonSuffix = if (scope == ProfileScope.SEASON) " (${currentSeason.label.lowercase()})" else ""
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
            // Première capture et captures totales restent globales — la
            // notion « 1re capture » est unique, et le total cumule tout.
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
                label = "Espèces capturées$seasonSuffix",
                value = nbSpecies.toString(),
            )
            StatLine(
                label = "Arbres remarquables$seasonSuffix",
                value = nbRemarquables.toString(),
            )
            StatLine(
                label = "Captures totales",
                value = nbCaptures.toString(),
            )
        }
    }
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

@Composable
private fun BadgeGrid(badges: List<BadgeState>) {
    // Grille fixe pour homogénéiser la taille des cellules ; aujourd'hui
    // un seul badge, demain on en aura plus — la grille se remplit
    // naturellement.
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier
            .fillMaxWidth()
            .heightForBadges(badges.size),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(badges, key = { it.def.id }) { state ->
            BadgeCell(state = state)
        }
    }
}

/**
 * Calcul de hauteur statique pour la grille — `LazyVerticalGrid` à
 * l'intérieur d'un `LazyColumn` ne sait pas se mesurer tout seul. On
 * dimensionne pour que toutes les rangées soient visibles sans scroll
 * interne.
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
private fun BadgeCell(state: BadgeState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (state.unlocked) {
                MaterialTheme.colorScheme.tertiaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
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
                    .background(
                        if (state.unlocked) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.outline
                        }
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (state.unlocked) Icons.Outlined.EmojiEvents else Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = if (state.unlocked) {
                        MaterialTheme.colorScheme.onTertiary
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                )
            }
            Text(
                if (state.unlocked) state.def.label else "???",
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
            )
            if (state.unlocked && state.unlockedAt != null) {
                Text(
                    formatDate(state.unlockedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private fun daysSince(epochMillis: Long): Long {
    val delta = System.currentTimeMillis() - epochMillis
    return TimeUnit.MILLISECONDS.toDays(delta).coerceAtLeast(0L)
}

private val DATE_FORMAT: DateFormat = DateFormat.getDateInstance(DateFormat.SHORT)

private fun formatDate(epochMillis: Long): String =
    DATE_FORMAT.format(Date(epochMillis))
