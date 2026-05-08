package app.arbre.ui.common

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Affiche [msg] pendant exactement [ms] ms via la durée Indefinite + dismiss
 * programmé. Material 3 ne propose que Short (4 s) / Long (10 s) / Indefinite,
 * d'où ce helper pour les valeurs intermédiaires (5 s par défaut). Cancellable :
 * si le caller (LaunchedEffect ou autre) est annulé, la coroutine fille
 * `showSnackbar` l'est aussi et la snackbar disparaît.
 */
suspend fun showSnackbarFor(
    host: SnackbarHostState,
    msg: String,
    ms: Long = 5000,
) {
    coroutineScope {
        val job = launch {
            host.showSnackbar(msg, duration = SnackbarDuration.Indefinite)
        }
        delay(ms)
        host.currentSnackbarData?.dismiss()
        job.cancel()
    }
}
