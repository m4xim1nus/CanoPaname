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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.arbre.data.BadgeCatalog
import app.arbre.data.BadgeState
import app.arbre.data.rememberCaptureRepository
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(onBack: () -> Unit) {
    val captureRepo = rememberCaptureRepository()

    val firstCaptureTs by captureRepo.firstCaptureTimestamp()
        .collectAsState(initial = null)
    val capturedSpecies by captureRepo.capturedSpeciesIndices()
        .collectAsState(initial = emptySet())
    val capturedRemarquables by captureRepo.capturedRemarquableIds()
        .collectAsState(initial = emptySet())
    val captureCount by captureRepo.captureCount().collectAsState(initial = 0)

    // Le badge « 1re capture » se débloque dès qu'une capture existe ;
    // l'instant de déblocage = `firstCaptureTimestamp`. Calcul dérivé,
    // pas de table dédiée. Quand on ajoutera d'autres badges, chacun aura
    // sa propre fonction d'évaluation à brancher ici.
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
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
                StatsCard(
                    firstCaptureTs = firstCaptureTs,
                    nbSpecies = capturedSpecies.size,
                    nbRemarquables = capturedRemarquables.size,
                    nbCaptures = captureCount,
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

@Composable
private fun StatsCard(
    firstCaptureTs: Long?,
    nbSpecies: Int,
    nbRemarquables: Int,
    nbCaptures: Int,
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
                label = "Espèces capturées",
                value = nbSpecies.toString(),
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
                    imageVector = if (state.unlocked) Icons.Default.EmojiEvents else Icons.Default.Lock,
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
