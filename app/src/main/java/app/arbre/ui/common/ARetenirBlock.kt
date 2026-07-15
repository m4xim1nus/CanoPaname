package app.arbre.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.arbre.ui.theme.ArbresTheme
import app.arbre.ui.theme.arbresColors

/**
 * Bloc « À retenir » de la fiche espèce : atouts et limites (listes de puces)
 * issus des fiches-essences Ville de Paris (bloc `ess`). Chaque sous-section est
 * omise si sa liste est vide ; le composant entier ne s'affiche pas si les deux
 * listes sont vides (garde en tête). Le call-site ne monte le bloc que quand au
 * moins une liste est non-vide.
 */
@Composable
fun ARetenirBlock(atouts: List<String>, limites: List<String>) {
    if (atouts.isEmpty() && limites.isEmpty()) return
    val arbresColors = MaterialTheme.arbresColors

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("À retenir", style = MaterialTheme.typography.titleMedium)
            if (atouts.isNotEmpty()) {
                BulletSection("Atouts", atouts, arbresColors.feuilleClaire)
            }
            if (limites.isNotEmpty()) {
                BulletSection("Limites et contraintes", limites, arbresColors.ecorce)
            }
        }
    }
}

@Composable
private fun BulletSection(title: String, items: List<String>, bulletColor: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        items.forEach { item ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text("•", color = bulletColor)
                Text(
                    text = item,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ARetenirBlockPreview() {
    ArbresTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            ARetenirBlock(
                atouts = listOf("Mellifère", "Résistant à la sécheresse et à la pollution urbaine"),
                limites = listOf("Sensible au vent", "Racines superficielles"),
            )
        }
    }
}
