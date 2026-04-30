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
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
 * Trois rendus possibles du sheet en fonction de l'état de découverte :
 *  - **Inconnu** : pin gris, fiche neutre + bouton Capturer.
 *  - **Découvert (espèce)** : pin vert, fiche complète. Pas de bouton Capturer
 *    (l'espèce est déjà débloquée — recapturer n'apporte rien côté progression).
 *  - **Découvert (remarquable)** : idem, fiche complète.
 *
 * Pour un remarquable non découvert, on est bien sur le rendu Inconnu — un
 * remarquable reste gris jusqu'à sa capture personnelle, même si son espèce
 * est par ailleurs découverte (cf. vision-jeu.md §5.2).
 */
@Composable
fun ArbreDetailContent(
    arbre: Arbre,
    isDiscovered: Boolean,
    nbPhotos: Int = 0,
    onCapturer: (() -> Unit)? = null,
    captureAvailability: CaptureAvailability? = null,
    onSpeciesClick: (() -> Unit)? = null,
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
                painter = painterResource(R.drawable.ic_remarquable_plaque),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Text(
                "Arbre remarquable",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
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

    if (onSpeciesClick != null) {
        Spacer(Modifier.height(8.dp))
        FilledTonalButton(
            onClick = onSpeciesClick,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.MenuBook,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Text(
                "En savoir plus sur l'espèce",
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

/**
 * « (médiane X) » + indication relative. Volontairement court pour garder la
 * fiche dense — pas de percentile ni d'intervalle, juste un repère.
 */
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

// Or token (`arbresColors.or`) : signal visuel d'exceptionnalité pour les
// ~180 arbres remarquables. Cohérent avec l'orange vif des pins remarquables
// capturés et avec l'accent or du splash.
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
