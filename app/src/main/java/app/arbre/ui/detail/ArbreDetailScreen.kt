package app.arbre.ui.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.arbre.R
import app.arbre.data.Arbre
import app.arbre.data.RemarquableInfo
import app.arbre.ui.map.CaptureAvailability
import app.arbre.ui.theme.arbresColors

/**
 * Rendu du sheet selon l'état de découverte. Un remarquable non capturé
 * tombe sur le rendu Inconnu même si son espèce est par ailleurs débloquée —
 * un remarquable reste gris jusqu'à sa capture personnelle.
 */
@Composable
fun ArbreDetailContent(
    arbre: Arbre,
    isDiscovered: Boolean,
    nbPhotos: Int = 0,
    onCapturer: (() -> Unit)? = null,
    captureAvailability: CaptureAvailability? = null,
    onSpeciesClick: (() -> Unit)? = null,
    onRemarquableClick: (() -> Unit)? = null,
    medianHeightM: Int? = null,
    medianCircCm: Int? = null,
    remarquableInfo: RemarquableInfo? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (isDiscovered) {
            DiscoveredContent(
                arbre = arbre,
                nbPhotos = nbPhotos,
                medianHeightM = medianHeightM,
                medianCircCm = medianCircCm,
                onSpeciesClick = onSpeciesClick,
                onRemarquableClick = onRemarquableClick,
                remarquableInfo = remarquableInfo,
            )
        } else {
            UnknownContent(arbre, onCapturer, captureAvailability)
        }
    }
}

@Composable
private fun DiscoveredContent(
    arbre: Arbre,
    nbPhotos: Int,
    medianHeightM: Int?,
    medianCircCm: Int?,
    onSpeciesClick: (() -> Unit)?,
    onRemarquableClick: (() -> Unit)?,
    remarquableInfo: RemarquableInfo?,
) {
    Text(
        arbre.nomAffichage,
        style = MaterialTheme.typography.headlineSmall,
    )

    if (arbre.remarquable) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_remarquable_badge),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Text(
                "Arbre remarquable",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.arbresColors.remarquableOrange,
            )
        }
        remarquableInfo?.let { RemarquableBlock(it) }
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

    arbre.hauteurM?.let { h ->
        Text("Hauteur : $h m" + medianComparison(h, medianHeightM))
    }
    arbre.circonferenceCm?.let { c ->
        Text("Circonférence : $c cm" + medianComparison(c, medianCircCm))
    }
    arbre.adresse?.let { Text("Adresse : $it") }
    if (nbPhotos > 0) {
        Text(
            "$nbPhotos photo${if (nbPhotos > 1) "s" else ""} de capture",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    Text("ID OpenData : ${arbre.id}", style = MaterialTheme.typography.bodySmall)

    if (onRemarquableClick != null || onSpeciesClick != null) {
        Spacer(Modifier.height(8.dp))
    }
    if (onRemarquableClick != null) {
        FilledTonalButton(
            onClick = onRemarquableClick,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_remarquable_badge),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(20.dp),
            )
            Text(
                "Fiche remarquable",
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
    if (onSpeciesClick != null) {
        if (onRemarquableClick != null) Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onSpeciesClick,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.MenuBook,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Text(
                "Fiche espèce",
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

/** « (médiane X · au-dessus/en-dessous) » — repère compact, pas de percentile. */
private fun medianComparison(value: Int, median: Int?): String {
    if (median == null) return ""
    val ratio = value.toDouble() / median
    val tag = when {
        ratio >= 1.5 -> "bien au-dessus"
        ratio >= 1.15 -> "au-dessus"
        ratio <= 0.66 -> "bien en dessous"
        ratio <= 0.85 -> "en dessous"
        else -> "proche"
    }
    return " · médiane $median ($tag)"
}

@Composable
private fun RemarquableBlock(info: RemarquableInfo) {
    if (info.qualification == null && info.resume == null && info.description == null &&
        info.datePlantation == null && info.cultivar == null) return
    val title = info.qualification?.let { "Classement : $it" } ?: "Pourquoi cet arbre est remarquable"
    val gold = MaterialTheme.arbresColors.or
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.5.dp, gold),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            info.resume?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            info.description?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            info.datePlantation?.let {
                Text(
                    "Planté en $it",
                    style = MaterialTheme.typography.labelSmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            info.cultivar?.let {
                Text(
                    "Variété : $it",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun UnknownContent(
    arbre: Arbre,
    onCapturer: (() -> Unit)?,
    availability: CaptureAvailability?,
) {
    Text(
        "Arbre inconnu",
        style = MaterialTheme.typography.headlineSmall,
    )
    Text(
        if (arbre.remarquable) {
            "Remarquable non découvert. Capture-le pour révéler sa fiche."
        } else {
            "Capture cet arbre pour révéler son espèce."
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(Modifier.height(8.dp))
    val label = when (availability) {
        CaptureAvailability.Ready -> "Capturer"
        CaptureAvailability.NoGps -> "Active le GPS"
        is CaptureAvailability.TooFar -> "Trop loin (${availability.meters} m)"
        CaptureAvailability.Archived -> "Saison archivée"
        null -> "Capturer"
    }
    Button(
        onClick = { onCapturer?.invoke() },
        enabled = onCapturer != null && availability is CaptureAvailability.Ready,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label)
    }
}
