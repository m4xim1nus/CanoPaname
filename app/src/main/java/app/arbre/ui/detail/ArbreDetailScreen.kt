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
import androidx.compose.material.icons.outlined.FilterAlt
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
import app.arbre.data.isEmpty
import app.arbre.ui.common.PhotoGallery
import app.arbre.ui.map.CaptureAvailability
import app.arbre.ui.theme.arbresColors
import java.io.File

/**
 * Données affichées par le sheet/fiche. Le `displayName` est précalculé côté
 * caller (préférence pour `displayNomCommun` du species-index, sinon fallback
 * `arbre.nomAffichage`). `captureAvailability` est en state : c'est le résultat
 * d'une évaluation GPS / distance, pas une action.
 */
data class ArbreDetailState(
    val arbre: Arbre,
    val isDiscovered: Boolean,
    val displayName: String,
    val photoFiles: List<File> = emptyList(),
    val medianHeightM: Int? = null,
    val medianCircCm: Int? = null,
    val remarquableInfo: RemarquableInfo? = null,
    val captureAvailability: CaptureAvailability? = null,
)

/**
 * Callbacks utilisateur. Tous nullables sauf `onPhotoClick` (le default `{}`
 * sert au mode lecture seule — fiche remarquable plein-écran qui rend la
 * gallery hors du composable).
 */
data class ArbreDetailActions(
    val onPhotoClick: (Int) -> Unit = {},
    val onPhotoLongClick: ((Int) -> Unit)? = null,
    val onCapturer: (() -> Unit)? = null,
    val onSpeciesClick: (() -> Unit)? = null,
    val onRemarquableClick: (() -> Unit)? = null,
    /**
     * Filtres rapides de la carte principale (« Toute l'espèce » / « Tout le
     * genre ») — posés uniquement depuis la sheet d'un pin non remarquable
     * découvert en mode carte normale ; `null` partout ailleurs (fiche
     * remarquable, mode MAP_FILTERED). `onFilterGenre` reste `null` quand le
     * set genre se réduirait au même singleton que l'espèce (bouton redondant).
     */
    val onFilterSpecies: (() -> Unit)? = null,
    val onFilterGenre: (() -> Unit)? = null,
)

/**
 * Rendu du sheet selon l'état de découverte. Un remarquable non capturé
 * tombe sur le rendu Inconnu même si son espèce est par ailleurs débloquée —
 * un remarquable reste gris jusqu'à sa capture personnelle.
 */
@Composable
fun ArbreDetailContent(
    state: ArbreDetailState,
    actions: ArbreDetailActions = ArbreDetailActions(),
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.isDiscovered) {
            DiscoveredContent(state, actions)
        } else {
            UnknownContent(state.arbre, actions.onCapturer, state.captureAvailability)
        }
    }
}

@Composable
private fun DiscoveredContent(
    state: ArbreDetailState,
    actions: ArbreDetailActions,
) {
    val arbre = state.arbre
    Text(
        state.displayName,
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
        state.remarquableInfo?.let { RemarquableBlock(it) }
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
        Text("Hauteur : $h m" + medianComparison(h, state.medianHeightM))
    }
    arbre.circonferenceCm?.let { c ->
        Text("Circonférence : $c cm" + medianComparison(c, state.medianCircCm))
    }
    arbre.adresse?.let { Text("Adresse : $it") }
    if (state.photoFiles.isNotEmpty()) {
        Spacer(Modifier.height(4.dp))
        PhotoGallery(
            photoFiles = state.photoFiles,
            onPhotoClick = actions.onPhotoClick,
            onPhotoLongClick = actions.onPhotoLongClick,
        )
    }
    Text("ID OpenData : ${arbre.id}", style = MaterialTheme.typography.bodySmall)

    DetailActionButtons(actions)
    actions.onCapturer?.let { capturer ->
        Spacer(Modifier.height(8.dp))
        CaptureButton(
            defaultLabel = "Recapturer",
            onCapturer = capturer,
            availability = state.captureAvailability,
        )
    }
}

/**
 * Pile des boutons d'action du contenu découvert : fiches (remarquable /
 * espèce) puis filtres rapides carte. Tout est conditionnel aux callbacks
 * non-null des [ArbreDetailActions].
 */
@Composable
private fun DetailActionButtons(actions: ArbreDetailActions) {
    val onRemarquableClick = actions.onRemarquableClick
    val onSpeciesClick = actions.onSpeciesClick
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
    val onFilterSpecies = actions.onFilterSpecies
    if (onFilterSpecies != null) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickFilterButton(
                label = "Toute l'espèce",
                onClick = onFilterSpecies,
                modifier = Modifier.weight(1f),
            )
            actions.onFilterGenre?.let { onFilterGenre ->
                QuickFilterButton(
                    label = "Tout le genre",
                    onClick = onFilterGenre,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * Bouton de filtre rapide carte (« Toute l'espèce » / « Tout le genre ») —
 * deux par Row, d'où le label compact et l'icône entonnoir partagée.
 */
@Composable
private fun QuickFilterButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(onClick = onClick, modifier = modifier) {
        Icon(
            Icons.Outlined.FilterAlt,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Text(
            label,
            modifier = Modifier.padding(start = 8.dp),
            maxLines = 1,
        )
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
    if (info.isEmpty()) return
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
            "Non capturé. Capture un arbre de cette espèce et tous les semblables se déverrouilleront."
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(Modifier.height(8.dp))
    CaptureButton(
        defaultLabel = "Capturer",
        onCapturer = onCapturer,
        availability = availability,
    )
}

@Composable
private fun CaptureButton(
    defaultLabel: String,
    onCapturer: (() -> Unit)?,
    availability: CaptureAvailability?,
) {
    val label = when (availability) {
        CaptureAvailability.Ready -> defaultLabel
        CaptureAvailability.NoGps -> "Active le GPS"
        is CaptureAvailability.TooFar -> "Trop loin (${availability.meters} m / max 30 m). Rapproche-toi."
        null -> defaultLabel
    }
    Button(
        onClick = { onCapturer?.invoke() },
        enabled = onCapturer != null && availability is CaptureAvailability.Ready,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label)
    }
}
