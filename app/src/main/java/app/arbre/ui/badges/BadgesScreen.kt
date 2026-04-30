package app.arbre.ui.badges

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Lock
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.arbre.data.Arbre
import app.arbre.data.BadgeCatalog
import app.arbre.data.BadgeEvaluator
import app.arbre.data.BadgeState
import app.arbre.data.rememberArbreRepository
import app.arbre.data.rememberCaptureRepository
import app.arbre.data.rememberSpeciesInfoRepository
import app.arbre.R
import app.arbre.ui.common.EmptyState
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import java.text.DateFormat
import java.util.Date

/**
 * Écran dédié — vue d'ensemble des 15 badges, débloqués au-dessus, à
 * débloquer en dessous (cf. ROADMAP Phase 4). L'évaluation est
 * recalculée à chaque changement de captures via `BadgeEvaluator`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BadgesScreen(onBack: () -> Unit) {
    val captureRepo = rememberCaptureRepository()
    val arbreRepo = rememberArbreRepository()
    val speciesInfoRepo = rememberSpeciesInfoRepository()

    val captures by captureRepo.toutesLesCaptures().collectAsState(initial = emptyList())

    // Batch-fetch des arbres référencés par les captures (pour Géant /
    // Vieux sage / arrondissements / espèce rare). Re-déclenché dès que
    // le set d'ids change — pas à chaque tick.
    val arbreIds = remember(captures) { captures.map { it.arbreId }.toSet() }
    val arbresById by produceState(
        initialValue = emptyMap<Long, Arbre>(),
        key1 = arbreIds,
    ) {
        value = arbreRepo.arbresParIds(arbreIds)
    }

    val badges = remember(captures, arbresById) {
        BadgeEvaluator.evaluate(captures, arbresById, speciesInfoRepo)
    }
    val unlocked = badges.filter { it.unlocked }
        .sortedByDescending { it.unlockedAt }
    val locked = badges.filter { !it.unlocked }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Badges & succès") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Retour")
                    }
                },
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "${unlocked.size} / ${BadgeCatalog.ALL.size} débloqués",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (unlocked.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        title = "Aucun badge débloqué",
                        body = "Capture ton premier arbre pour débloquer ton premier badge. Quinze succès t'attendent au fil des saisons et des arrondissements.",
                        illustration = {
                            Image(
                                painter = painterResource(R.drawable.illus_empty_badges),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                            )
                        },
                    )
                }
            }
            if (unlocked.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SectionTitle("Débloqués")
                }
                items(unlocked, key = { it.def.id }) { state ->
                    BadgeCard(state = state)
                }
            }
            if (locked.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SectionTitle("À débloquer")
                }
                items(locked, key = { it.def.id }) { state ->
                    BadgeCard(state = state)
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun BadgeCard(state: BadgeState) {
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
                .padding(10.dp),
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
                    imageVector = if (state.unlocked) state.def.icon() else Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = if (state.unlocked) {
                        MaterialTheme.colorScheme.onTertiary
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                )
            }
            Text(
                state.def.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = if (state.unlocked) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            // Critère toujours visible — l'utilisateur sait ce qu'il vise
            // même quand le badge est verrouillé.
            Text(
                state.def.description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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

private val DATE_FORMAT: DateFormat = DateFormat.getDateInstance(DateFormat.SHORT)

private fun formatDate(epochMillis: Long): String =
    DATE_FORMAT.format(Date(epochMillis))
