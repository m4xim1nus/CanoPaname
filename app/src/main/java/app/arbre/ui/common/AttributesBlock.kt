package app.arbre.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.arbre.data.SpeciesAttributes
import app.arbre.ui.theme.arbresColors
import java.util.Locale

/**
 * Bloc « Caractéristiques » de la fiche espèce : attributs structurés des
 * fiches-essences Ville de Paris (bloc `ess`) rendus en pills Material 3.
 *
 * Champs courts seulement (`port`, `feuillage`, `taille`, `indigenat`, `fleurs`,
 * `exposition`) ; `origine` (prose) en ligne dédiée. `besoinsEau`/`sitePlantation`
 * sont des listes longues, hors périmètre ici. Présent uniquement quand l'espèce
 * porte un bloc `ess` (~169/934) — sinon le call-site ne monte pas ce composable.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AttributesBlock(attrs: SpeciesAttributes) {
    val structural = listOfNotNull(attrs.port, attrs.feuillage, attrs.taille, attrs.indigenat)
    val showFleurs = attrs.fleurs == true
    val hasPills = structural.isNotEmpty() || showFleurs || attrs.exposition.isNotEmpty()
    if (!hasPills && attrs.origine == null) return

    val arbresColors = MaterialTheme.arbresColors
    val neutralContainer = MaterialTheme.colorScheme.secondaryContainer
    val neutralContent = MaterialTheme.colorScheme.onSecondaryContainer
    val sunContainer = arbresColors.or.copy(alpha = 0.20f)
    val sunContent = MaterialTheme.colorScheme.onSurface

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Caractéristiques", style = MaterialTheme.typography.titleMedium)
            if (hasPills) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    structural.forEach { Pill(it, neutralContainer, neutralContent) }
                    if (showFleurs) Pill("À fleurs", neutralContainer, neutralContent)
                    attrs.exposition.forEach { Pill(capitalizeFirst(it), sunContainer, sunContent) }
                }
            }
            attrs.origine?.let { origine ->
                Text(
                    "Origine : $origine",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Pill(text: String, container: Color, content: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = content,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(container)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

private fun capitalizeFirst(s: String): String =
    s.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.FRENCH) else it.toString() }
