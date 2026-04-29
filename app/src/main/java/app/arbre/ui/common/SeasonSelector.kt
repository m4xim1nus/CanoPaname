package app.arbre.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.arbre.data.Season

/**
 * Sélecteur de saison discret : pill `Surface` cliquable (icône + label de
 * la saison sélectionnée) qui ouvre un `DropdownMenu` avec les 4 saisons.
 *
 * Volontairement compact pour ne pas dominer la top-bar (cf. ROADMAP
 * Sprint I — « ne pas charger la top-bar avec 4 segments side-by-side »).
 */
@Composable
fun SeasonSelector(
    selected: Season,
    onSelect: (Season) -> Unit,
    modifier: Modifier = Modifier,
    isCurrent: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(20.dp),
            color = if (isCurrent) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.tertiaryContainer
            },
            tonalElevation = 2.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = seasonIcon(selected),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    selected.label,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            Season.entries.forEach { season ->
                DropdownMenuItem(
                    text = { Text(season.label) },
                    onClick = {
                        onSelect(season)
                        expanded = false
                    },
                    leadingIcon = {
                        Icon(seasonIcon(season), contentDescription = null)
                    },
                )
            }
        }
    }
}

fun seasonIcon(season: Season): ImageVector = when (season) {
    Season.WINTER -> Icons.Default.AcUnit
    Season.SPRING -> Icons.Default.LocalFlorist
    Season.SUMMER -> Icons.Default.WbSunny
    Season.AUTUMN -> Icons.Default.Park
}
