package app.arbre.ui.common

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.arbre.data.isMonthInBitfield
import app.arbre.ui.theme.ArbresTheme
import app.arbre.ui.theme.arbresColors

/** Initiales des 12 mois (janvier → décembre), alignées sur les bitfields. */
private val MONTH_LETTERS = listOf("J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D")

/**
 * Bloc « Saisonnalité » de la fiche espèce : deux frises de 12 mois (floraison /
 * fructification) rendues depuis les bitfields de [SpeciesAttributes]. bit 0 =
 * janvier ; le mois courant est mis en évidence (en-tête gras + bordure sur les
 * cases). Purement statique — aucune animation (contrat device de Max).
 *
 * Le call-site ne monte ce composable que si au moins un des deux bitfields est
 * non-null ; le `return` de garde couvre l'appel défensif.
 */
@Composable
fun SeasonalityCalendar(
    floraison: Int?,
    fructification: Int?,
    currentMonth: Int = java.time.LocalDate.now().monthValue,
) {
    if (floraison == null && fructification == null) return
    val arbresColors = MaterialTheme.arbresColors

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Saisonnalité", style = MaterialTheme.typography.titleMedium)

            // En-tête : 12 lettres de mois, celle du mois courant en évidence.
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

            floraison?.let {
                MonthStrip("Floraison", it, arbresColors.or, currentMonth)
            }
            fructification?.let {
                MonthStrip("Fructification", it, arbresColors.ecorce, currentMonth)
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

@Composable
private fun MonthStrip(
    label: String,
    bits: Int,
    activeColor: Color,
    currentMonth: Int,
) {
    val cellShape = RoundedCornerShape(4.dp)
    val inactiveColor = MaterialTheme.colorScheme.surfaceVariant
    val currentBorder = MaterialTheme.colorScheme.primary
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
}

@Preview(showBackground = true)
@Composable
private fun SeasonalityCalendarPreview() {
    ArbresTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            // Floraison avril→juin (bits 3,4,5), fructification sept→nov (bits 8,9,10).
            SeasonalityCalendar(
                floraison = 0b0000_0011_1000,
                fructification = 0b0111_0000_0000,
                currentMonth = 5,
            )
        }
    }
}
