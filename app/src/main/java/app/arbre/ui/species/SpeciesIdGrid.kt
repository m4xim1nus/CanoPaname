package app.arbre.ui.species

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.arbre.data.SpeciesAttributes
import app.arbre.ui.theme.ArbresTheme
import java.util.Locale

/**
 * Card « Carte d'identité » de la fiche espèce : grille 2 colonnes de paires
 * label/valeur remplaçant les anciennes pills peu lisibles de `AttributesBlock`.
 *
 * Les paires sont construites dans un ordre fixe et chaque paire dont la valeur
 * est `null`/vide est omise. `taille` (catégorie floue « Moyen »/« Grand ») ne
 * sert que de repli quand ni `hauteur` ni `envergure` ne sont renseignées. Le
 * champ `fleurs` est volontairement absent (redondant avec la frise de floraison
 * du bloc « Reconnaître »). Si aucune paire n'est disponible, le composable ne
 * rend rien (garde de tête).
 */
@Composable
fun SpeciesIdGrid(attrs: SpeciesAttributes) {
    val pairs = buildList {
        attrs.port?.let { add("Silhouette" to it) }
        attrs.feuillage?.let { add("Feuillage" to it) }
        attrs.hauteur?.let { add("Hauteur" to it) }
        attrs.envergure?.let { add("Envergure" to it) }
        // Repli catégorie uniquement en l'absence de hauteur ET d'envergure.
        if (attrs.hauteur == null && attrs.envergure == null) {
            attrs.taille?.let { add("Taille" to it) }
        }
        attrs.croissance?.let { add("Croissance" to capitalizeFirst(it)) }
        attrs.longevite?.let { add("Longévité" to longeviteValue(it)) }
        val origine = listOfNotNull(attrs.origine, attrs.indigenat).joinToString(" · ")
        if (origine.isNotEmpty()) add("Origine" to origine)
        attrs.famille?.let { add("Famille" to it) }
        if (attrs.exposition.isNotEmpty()) {
            add("Exposition" to attrs.exposition.joinToString(" · ") { capitalizeFirst(it) })
        }
    }
    if (pairs.isEmpty()) return

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Carte d'identité", style = MaterialTheme.typography.titleMedium)
            pairs.chunked(2).forEach { rowPairs ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IdCell(rowPairs[0].first, rowPairs[0].second, Modifier.weight(1f))
                    if (rowPairs.size > 1) {
                        IdCell(rowPairs[1].first, rowPairs[1].second, Modifier.weight(1f))
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun IdCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Longévité affichable : si la valeur brute contient une parenthèse, ne garder
 * que son contenu (« Moyenne (100 à 200 ans) » → « 100 à 200 ans ») ; sinon
 * renvoyer la valeur telle quelle.
 */
private fun longeviteValue(raw: String): String {
    val open = raw.indexOf('(')
    val close = raw.indexOf(')', open + 1)
    return if (open >= 0 && close > open) raw.substring(open + 1, close).trim() else raw
}

private fun capitalizeFirst(s: String): String =
    s.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.FRENCH) else it.toString() }

@Preview(showBackground = true)
@Composable
private fun SpeciesIdGridPreview() {
    ArbresTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            SpeciesIdGrid(
                SpeciesAttributes(
                    port = "Ovoïde",
                    feuillage = "Caduc",
                    taille = null,
                    indigenat = "Exotique",
                    origine = "Asie",
                    fleurs = true,
                    exposition = listOf("soleil", "mi-ombre"),
                    besoinsEau = emptyList(),
                    sitePlantation = emptyList(),
                    floraison = null,
                    fructification = null,
                    atouts = emptyList(),
                    limites = emptyList(),
                    famille = "Magnoliacées",
                    hauteur = "10 m",
                    envergure = "8 m",
                    croissance = "moyenne",
                    longevite = "Moyenne (100 à 200 ans)",
                ),
            )
        }
    }
}
