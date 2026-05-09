package app.arbre.ui.badges

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
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
import app.arbre.data.BadgeTier
import app.arbre.data.rememberBadgeRepository
import app.arbre.ui.common.EmptyState
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import java.text.DateFormat
import java.util.Date

/**
 * Vue d'ensemble des badges (débloqués au-dessus, verrouillés en dessous).
 * Les badges progressifs prennent la pleine largeur (barre + jalons + score
 * absolu). Les badges binaires gardent la grille 3 colonnes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BadgesScreen(onBack: () -> Unit) {
    val badgeRepo = rememberBadgeRepository()
    val badges by badgeRepo.badges().collectAsState(initial = emptyList())
    val unlocked = badges.filter { it.unlocked }.sortedByDescending(::lastUnlockedAt)
    val locked = badges.filter { !it.unlocked }
    val unlockedTiers = badges.sumOf { it.unlockedTierCount() }

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
                    "$unlockedTiers / ${BadgeCatalog.totalTierCount} débloqués",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (unlocked.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        title = "Aucun badge débloqué",
                        body = "Capture ton premier arbre pour démarrer Marcheur, Botaniste et Chasseur. Plusieurs paliers t'attendent au fil des arrondissements.",
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
                items(
                    items = unlocked,
                    key = { it.def.id },
                    span = { state ->
                        if (state is BadgeState.Progressive) GridItemSpan(maxLineSpan)
                        else GridItemSpan(1)
                    },
                ) { state ->
                    BadgeCard(state = state)
                }
            }
            if (locked.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SectionTitle("À débloquer")
                }
                items(
                    items = locked,
                    key = { it.def.id },
                    span = { state ->
                        if (state is BadgeState.Progressive) GridItemSpan(maxLineSpan)
                        else GridItemSpan(1)
                    },
                ) { state ->
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
    when (state) {
        is BadgeState.Binary -> BinaryBadgeCard(state)
        is BadgeState.Progressive -> ProgressiveBadgeCard(state)
    }
}

@Composable
private fun BinaryBadgeCard(state: BadgeState.Binary) {
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
private fun ProgressiveBadgeCard(state: BadgeState.Progressive) {
    val anyUnlocked = state.unlockedTierCount > 0
    val nextTier = state.nextTier
    val targetThreshold = nextTier?.threshold ?: state.tiers.last().threshold

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (anyUnlocked) {
                MaterialTheme.colorScheme.tertiaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BadgeIconCircle(state = state, size = 44)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        state.def.label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (anyUnlocked) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    val subtitle = when {
                        state.isFullyUnlocked -> "Tous les paliers débloqués"
                        nextTier != null -> "Prochain palier : ${nextTier.label}"
                        else -> state.def.description
                    }
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ScoreBadge(
                    currentCount = state.currentCount,
                    target = targetThreshold,
                    unitLabel = state.def.unitLabel,
                    completed = state.isFullyUnlocked,
                )
            }
            TierProgressBar(tiers = state.tiers, currentCount = state.currentCount)
            TierLabels(tiers = state.tiers)
            val lastTier = state.lastUnlockedTier
            if (lastTier?.unlockedAt != null) {
                Text(
                    "Dernier palier : ${lastTier.label} · ${formatDate(lastTier.unlockedAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ScoreBadge(
    currentCount: Int,
    target: Int,
    unitLabel: String?,
    completed: Boolean,
) {
    val labelColor = if (completed) {
        MaterialTheme.colorScheme.onTertiary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (completed) MaterialTheme.colorScheme.tertiary
                else MaterialTheme.colorScheme.surface
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            "$currentCount / $target",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = labelColor,
        )
        if (unitLabel != null) {
            Text(
                unitLabel,
                style = MaterialTheme.typography.labelMedium,
                color = labelColor,
            )
        }
        if (completed) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiary,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun TierProgressBar(tiers: List<BadgeTier>, currentCount: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tiers.forEachIndexed { index, tier ->
            val unlocked = tier.unlockedAt != null
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(
                        if (unlocked) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.outlineVariant
                    ),
            )
            if (index < tiers.size - 1) {
                val next = tiers[index + 1]
                val nextUnlocked = next.unlockedAt != null
                val span = (next.threshold - tier.threshold).toFloat().coerceAtLeast(1f)
                val segmentFill = when {
                    nextUnlocked -> 1f
                    unlocked -> ((currentCount - tier.threshold).toFloat() / span)
                        .coerceIn(0f, 1f)
                    else -> 0f
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                ) {
                    if (segmentFill > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(segmentFill)
                                .background(MaterialTheme.colorScheme.tertiary),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TierLabels(tiers: List<BadgeTier>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        tiers.forEach { tier ->
            Text(
                tier.threshold.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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

private fun lastUnlockedAt(state: BadgeState): Long = when (state) {
    is BadgeState.Binary -> state.unlockedAt ?: Long.MIN_VALUE
    is BadgeState.Progressive -> state.lastUnlockedAt ?: Long.MIN_VALUE
}

private fun BadgeState.unlockedTierCount(): Int = when (this) {
    is BadgeState.Binary -> if (unlocked) 1 else 0
    is BadgeState.Progressive -> unlockedTierCount
}

private val DATE_FORMAT: DateFormat = DateFormat.getDateInstance(DateFormat.SHORT)

private fun formatDate(epochMillis: Long): String =
    DATE_FORMAT.format(Date(epochMillis))
