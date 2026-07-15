package app.arbre.ui.species

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.arbre.ui.theme.ArbresTheme
import app.arbre.ui.theme.arbresColors

/**
 * Card « Forces & faiblesses » de la fiche espèce : atouts puis limites issus des
 * fiches-essences Ville de Paris (bloc `ess`). Remplace l'ancien `ARetenirBlock`
 * en supprimant les sous-titres de section : chaque item est directement
 * introduit par un glyphe (« + » vert pour un atout, « − » écorce pour une
 * limite). Garde de tête : rien à afficher si les deux listes sont vides.
 */
@Composable
fun ForcesFaiblessesBlock(atouts: List<String>, limites: List<String>) {
    if (atouts.isEmpty() && limites.isEmpty()) return
    val arbresColors = MaterialTheme.arbresColors

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Forces & faiblesses", style = MaterialTheme.typography.titleMedium)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                atouts.forEach { GlyphRow("+", arbresColors.feuilleClaire, it) }
                limites.forEach { GlyphRow("−", arbresColors.ecorce, it) }
            }
        }
    }
}

@Composable
private fun GlyphRow(glyph: String, glyphColor: Color, text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(modifier = Modifier.width(16.dp)) {
            Text(
                glyph,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = glyphColor,
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ForcesFaiblessesBlockPreview() {
    ArbresTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            ForcesFaiblessesBlock(
                atouts = listOf("Mellifère", "Résistant à la sécheresse et à la pollution urbaine"),
                limites = listOf("Sensible au vent", "Racines superficielles"),
            )
        }
    }
}
