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
 * Pills neutres : `port`, `feuillage`, `taille`, `indigenat`, `famille`, hauteur,
 * envergure, croissance ; pills « soleil » : `exposition`. En prose (lignes
 * label:valeur) : `origine` puis `longevite`. Section « descriptions
 * d'identification » (`identDescriptions`) en fin de bloc : label + texte terrain,
 * ligne omise si le champ est `null`, section omise si le groupe est `null`.
 * `besoinsEau`/`sitePlantation` (listes longues) hors périmètre. Présent uniquement
 * quand l'espèce porte un bloc `ess` — sinon le call-site ne monte pas ce composable.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AttributesBlock(attrs: SpeciesAttributes) {
    val structural = listOfNotNull(attrs.port, attrs.feuillage, attrs.taille, attrs.indigenat)
    val neutralExtras = listOfNotNull(
        attrs.famille,
        attrs.hauteur?.let { "Hauteur $it" },
        attrs.envergure?.let { "Envergure $it" },
        attrs.croissance?.let { "Croissance ${it.lowercase(Locale.FRENCH)}" },
    )
    val showFleurs = attrs.fleurs == true
    val hasPills =
        structural.isNotEmpty() || neutralExtras.isNotEmpty() ||
            showFleurs || attrs.exposition.isNotEmpty()
    val hasProse = attrs.origine != null || attrs.longevite != null
    val idDesc = attrs.identDescriptions
    if (!hasPills && !hasProse && idDesc == null) return

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
                    neutralExtras.forEach { Pill(it, neutralContainer, neutralContent) }
                    if (showFleurs) Pill("À fleurs", neutralContainer, neutralContent)
                    attrs.exposition.forEach { Pill(capitalizeFirst(it), sunContainer, sunContent) }
                }
            }
            attrs.origine?.let { origine ->
                ProseLine("Origine : $origine")
            }
            attrs.longevite?.let { longevite ->
                ProseLine("Longévité : $longevite")
            }
            idDesc?.let { desc ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    desc.ecorce?.let { IdentDescRow("Écorce", it) }
                    desc.feuillage?.let { IdentDescRow("Feuillage", it) }
                    desc.floraison?.let { IdentDescRow("Floraison", it) }
                    desc.fructification?.let { IdentDescRow("Fructification", it) }
                }
            }
        }
    }
}

@Composable
private fun ProseLine(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun IdentDescRow(label: String, text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
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
