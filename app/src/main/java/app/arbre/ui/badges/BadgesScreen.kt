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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.arbre.R
import app.arbre.data.BadgeCatalog
import app.arbre.data.BadgeState
import app.arbre.data.rememberBadgeRepository
import app.arbre.ui.common.EmptyState
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import java.text.DateFormat
import java.util.Date

/**
 * Vue d'ensemble des badges (débloqués au-dessus, verrouillés en dessous),
 * grille 3 colonnes. Tous les badges sont binaires.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BadgesScreen(onBack: () -> Unit) {
    val badgeRepo = rememberBadgeRepository()
    val badges by badgeRepo.badges().collectAsState(initial = emptyList())
    val unlocked = badges.filter { it.unlocked }.sortedByDescending { it.unlockedAt ?: Long.MIN_VALUE }
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
                        body = "Capture tes premiers arbres. Espèces rares, arrondissements parcourus, géants de plus de 30 m : chaque badge se gagne d'un coup.",
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
                items(items = unlocked, key = { it.def.id }) { state ->
                    BadgeCard(state = state)
                }
            }
            if (locked.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SectionTitle("À débloquer")
                }
                items(items = locked, key = { it.def.id }) { state ->
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
    val unlocked = state.unlocked
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (unlocked) {
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
            BadgeIconCircle(state = state, size = 48)
            Text(
                state.def.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = if (unlocked) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Text(
                state.def.description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (unlocked && state.unlockedAt != null) {
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

@Composable
private fun BadgeIconCircle(state: BadgeState, size: Int) {
    val unlocked = state.unlocked
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(
                if (unlocked) MaterialTheme.colorScheme.tertiary
                else MaterialTheme.colorScheme.outline
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (unlocked) state.def.icon() else Icons.Outlined.Lock,
            contentDescription = null,
            tint = if (unlocked) {
                MaterialTheme.colorScheme.onTertiary
            } else {
                MaterialTheme.colorScheme.surface
            },
        )
    }
}

private val DATE_FORMAT: DateFormat = DateFormat.getDateInstance(DateFormat.SHORT)

private fun formatDate(epochMillis: Long): String =
    DATE_FORMAT.format(Date(epochMillis))
