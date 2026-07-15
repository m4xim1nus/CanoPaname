package app.arbre.ui.species

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.arbre.data.IdentDescriptions
import app.arbre.data.isMonthInBitfield
import app.arbre.ui.theme.ArbresTheme
import app.arbre.ui.theme.arbresColors
import java.util.Locale

/** Initiales des 12 mois (janvier → décembre), alignées sur les bitfields. */
private val MONTH_LETTERS = listOf("J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D")

/**
 * Card « Reconnaître » de la fiche espèce : fusionne les anciennes descriptions
 * d'identification terrain (`AttributesBlock`) et la saisonnalité
 * (`SeasonalityCalendar`). Rassemble en un seul bloc les repères visuels
 * (écorce, feuillage) et les frises floraison / fructification.
 *
 * Anti-redite : quand un texte descriptif commence par le mot du label (ex.
 * « Écorce grise lisse » sous le label « Écorce »), le préfixe est retiré et la
 * première lettre re-capitalisée (« Grise lisse »). Purement statique — aucune
 * animation (contrat device de Max). Garde de tête : rien à afficher si
 * `idDesc`, `floraison` et `fructification` sont tous `null`.
 */
@Composable
fun ReconnaitreBlock(
    idDesc: IdentDescriptions?,
    floraison: Int?,
    fructification: Int?,
    currentMonth: Int = java.time.LocalDate.now().monthValue,
) {
    if (idDesc == null && floraison == null && fructification == null) return
    val arbresColors = MaterialTheme.arbresColors

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Reconnaître", style = MaterialTheme.typography.titleMedium)

            idDesc?.ecorce?.let { LabelledLine("Écorce", stripLabelPrefix(it, "Écorce")) }
            idDesc?.feuillage?.let { LabelledLine("Feuillage", stripLabelPrefix(it, "Feuillage")) }

            // En-tête des 12 lettres de mois (mois courant en évidence), dès qu'une
            // frise (floraison ou fructification) sera rendue.
            if (floraison != null || fructification != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    MONTH_LETTERS.forEachIndexed { i, letter ->
                        val isCurrent = i + 1 == currentMonth
                        Box(modifier = Modifier.weight(1f)) {
                            Text(
                                text = letter,
                                style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Center,
                                fontWeight = if (isCurrent) FontWeight.Bold else null,
                                color = if (isCurrent) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            if (floraison != null || idDesc?.floraison != null) {
                SeasonGroup(
                    label = "Floraison",
                    desc = idDesc?.floraison?.let { stripLabelPrefix(it, "Floraison") },
                    bits = floraison,
                    activeColor = arbresColors.or,
                    currentMonth = currentMonth,
                )
            }
            if (fructification != null || idDesc?.fructification != null) {
                SeasonGroup(
                    label = "Fructification",
                    desc = idDesc?.fructification?.let { stripLabelPrefix(it, "Fructification") },
                    bits = fructification,
                    activeColor = arbresColors.ecorce,
                    currentMonth = currentMonth,
                )
            }

            if (floraison != null && isMonthInBitfield(floraison, currentMonth)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(arbresColors.or),
                    )
                    Text(
                        "En floraison ce mois-ci",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Groupe « label annoté (+ description) » suivi de sa frise 12 mois si le bitfield existe. */
@Composable
private fun SeasonGroup(
    label: String,
    desc: String?,
    bits: Int?,
    activeColor: Color,
    currentMonth: Int,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LabelledLine(label, desc)
        if (bits != null) {
            MonthStripCells(bits, activeColor, currentMonth)
        }
    }
}

/**
 * Une ligne « label — description » en un seul [Text] : label en SemiBold, texte
 * descriptif en poids normal. Le texte est optionnel (frise seule sans prose).
 */
@Composable
private fun LabelledLine(label: String, text: String?) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(label) }
            if (!text.isNullOrBlank()) {
                append("  —  ")
                append(text)
            }
        },
        style = MaterialTheme.typography.bodyMedium,
        color = onSurface,
    )
}

/** Les 12 cases de la frise sans label interne (le label vit dans [LabelledLine]). */
@Composable
private fun MonthStripCells(bits: Int, activeColor: Color, currentMonth: Int) {
    val cellShape = RoundedCornerShape(4.dp)
    val inactiveColor = MaterialTheme.colorScheme.surfaceVariant
    val currentBorder = MaterialTheme.colorScheme.primary
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        for (month in 1..12) {
            val active = isMonthInBitfield(bits, month)
            val isCurrent = month == currentMonth
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(20.dp)
                    .clip(cellShape)
                    .background(if (active) activeColor else inactiveColor)
                    .then(
                        if (isCurrent) {
                            Modifier.border(1.5.dp, currentBorder, cellShape)
                        } else {
                            Modifier
                        },
                    ),
            )
        }
    }
}

/**
 * Retire le préfixe redondant : si `text` commence par `label` (insensible à la
 * casse et aux diacritiques du label lui-même), le supprime, nettoie la
 * ponctuation/espaces de tête et re-capitalise la première lettre. Sinon renvoie
 * `text` inchangé.
 */
private fun stripLabelPrefix(text: String, label: String): String {
    if (!text.startsWith(label, ignoreCase = true)) return text
    val rest = text.drop(label.length).trimStart(' ', ':', '-', '—', ' ', ',', '.')
    if (rest.isEmpty()) return text
    return rest.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.FRENCH) else it.toString() }
}

@Preview(showBackground = true)
@Composable
private fun ReconnaitreBlockPreview() {
    ArbresTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            ReconnaitreBlock(
                idDesc = IdentDescriptions(
                    ecorce = "Écorce grise et lisse, se desquamant en plaques.",
                    feuillage = "Feuilles palmées à cinq lobes.",
                    floraison = "Grappes dressées blanches.",
                    fructification = null,
                ),
                // Floraison avril→juin (bits 3,4,5), fructification sept→nov (bits 8,9,10).
                floraison = 0b0000_0011_1000,
                fructification = 0b0111_0000_0000,
                currentMonth = 5,
            )
        }
    }
}
