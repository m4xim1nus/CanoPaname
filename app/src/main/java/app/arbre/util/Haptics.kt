package app.arbre.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Wrapper léger autour de [LocalHapticFeedback] pour ne pas mentionner le
 * type de feedback dans chaque appelant. `LongPress` est l'effet « tap+
 * confirmation » standard d'Android, plus marqué qu'un `TextHandleMove`.
 */
@Composable
fun rememberCaptureHaptic(): () -> Unit {
    val haptic = LocalHapticFeedback.current
    return remember(haptic) {
        { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
    }
}
