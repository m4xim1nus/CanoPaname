package app.arbre.ui.species

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.arbre.data.EcosystemServices
import app.arbre.ui.theme.ArbresTheme

/**
 * Card « Son rôle en ville » de la fiche espèce : proses de services
 * écosystémiques (climat, eau, biodiversité) issues des fiches-essences Ville de
 * Paris (bloc `ess.svc`). Remplace l'ancien `ServicesEcoBlock`, mais **repliée
 * par défaut** pour raccourcir la page — toute la card est cliquable pour
 * basculer l'état. L'expansion est une recomposition sèche, sans animation
 * (contrat device de Max). Le call-site ne monte le bloc que quand
 * [EcosystemServices] est non-`null` (au moins un item présent).
 */
@Composable
fun RoleEnVilleBlock(services: EcosystemServices) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Son rôle en ville",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) "Replier" else "Déplier",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (expanded) {
                services.climat?.let { ServiceItem("Climat", it) }
                services.eau?.let { ServiceItem("Eau", it) }
                services.biodiv?.let { ServiceItem("Biodiversité", it) }
            }
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
private fun RoleEnVilleBlockPreview() {
    ArbresTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            RoleEnVilleBlock(
                EcosystemServices(
                    climat = "Excellente capacité d'ombrage due au port arrondi et à la densité du feuillage.",
                    eau = "Système racinaire développé favorisant l'absorption et l'infiltration de l'eau.",
                    biodiv = "Floraison spectaculaire attirant de nombreux insectes.",
                ),
            )
        }
    }
}
