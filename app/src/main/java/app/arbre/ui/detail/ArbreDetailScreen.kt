package app.arbre.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.arbre.data.Arbre
import app.arbre.ui.map.CaptureAvailability

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
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (isDiscovered) {
            DiscoveredContent(arbre, nbPhotos)
        } else {
            UnknownContent(arbre, onCapturer, captureAvailability)
        }
    }
}

@Composable
private fun DiscoveredContent(arbre: Arbre, nbPhotos: Int) {
    Text(
        arbre.nomAffichage,
        style = MaterialTheme.typography.headlineSmall,
    )

    if (arbre.remarquable) {
        Text(
            "★ Arbre remarquable",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
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

    arbre.hauteurM?.let { Text("Hauteur : $it m") }
    arbre.circonferenceCm?.let { Text("Circonférence : $it cm") }
    arbre.adresse?.let { Text("Adresse : $it") }
    if (nbPhotos > 0) {
        Text(
            "$nbPhotos photo${if (nbPhotos > 1) "s" else ""} de capture",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    Text("ID OpenData : ${arbre.id}", style = MaterialTheme.typography.bodySmall)
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
