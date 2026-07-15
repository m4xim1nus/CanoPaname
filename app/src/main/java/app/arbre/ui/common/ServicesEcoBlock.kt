package app.arbre.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.arbre.data.EcosystemServices
import app.arbre.ui.theme.ArbresTheme

/**
 * Bloc « Services écosystémiques » de la fiche espèce : trois proses (régulation
 * du climat, gestion de l'eau, biodiversité) issues des fiches-essences Ville de
 * Paris (bloc `ess.svc`). Chaque item est omis si sa prose est `null` ; le
 * call-site ne monte le bloc que quand [EcosystemServices] est non-`null` (donc au
 * moins un item présent, garanti par le parsing qui réduit un groupe vide à `null`).
 */
@Composable
fun ServicesEcoBlock(services: EcosystemServices) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Services écosystémiques", style = MaterialTheme.typography.titleMedium)
            services.climat?.let { ServiceItem("Régulation du climat", it) }
            services.eau?.let { ServiceItem("Gestion de l'eau", it) }
            services.biodiv?.let { ServiceItem("Biodiversité", it) }
        }
    }
}

@Composable
private fun ServiceItem(label: String, prose: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = prose,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ServicesEcoBlockPreview() {
    ArbresTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            ServicesEcoBlock(
                EcosystemServices(
                    climat = "Excellente capacité d'ombrage due au port arrondi et à la densité du feuillage.",
                    eau = "Système racinaire développé favorisant l'absorption et l'infiltration de l'eau.",
                    biodiv = "Floraison spectaculaire attirant de nombreux insectes.",
                ),
            )
        }
    }
}
