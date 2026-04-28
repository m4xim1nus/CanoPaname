package app.arbre.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.arbre.data.Arbre

@Composable
fun ArbreDetailContent(arbre: Arbre) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            arbre.nomAffichage,
            style = MaterialTheme.typography.headlineSmall,
        )

        if (arbre.remarquable) {
            Text(
                "★ Arbre remarquable",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        val taxonomie = listOfNotNull(arbre.genre, arbre.espece, arbre.varieteCultivar)
            .joinToString(" ")
            .ifBlank { null }
        taxonomie?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        arbre.hauteurM?.let { Text("Hauteur : $it m") }
        arbre.circonferenceCm?.let { Text("Circonférence : $it cm") }
        arbre.adresse?.let { Text("Adresse : $it") }
        Text("ID OpenData : ${arbre.id}", style = MaterialTheme.typography.bodySmall)
    }
}
