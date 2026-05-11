package app.arbre.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ShowOnMapButton(
    onClick: () -> Unit,
    label: String = "Voir sur la carte",
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
    ) {
        Icon(
            Icons.Outlined.Map,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Text(
            label,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}
