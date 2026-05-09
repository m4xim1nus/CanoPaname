package app.arbre.ui.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * Confirmation de suppression d'une capture. Le wording bascule en mode
 * « dernier » quand `isLastOfEntity` est vrai — la suppression va re-verrouiller
 * l'espèce ou l'arbre remarquable concerné, donc on prévient explicitement.
 */
@Composable
fun DeleteCaptureDialog(
    isLastOfEntity: Boolean,
    entityKindLabel: String,
    entityName: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val displayName = entityName ?: entityKindLabel
    val body = if (isLastOfEntity) {
        "C'est ta dernière capture de $displayName. La supprimer va re-verrouiller son entrée dans ton Arboretum."
    } else {
        "Cette capture sera retirée de ta galerie. Action définitive."
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Supprimer cette photo ?") },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    "Supprimer",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        },
    )
}
