package app.arbre.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/** Wrapper compact sur [LocalHapticFeedback]. `LongPress` = effet
 *  « tap + confirmation » standard, plus marqué que `TextHandleMove`.
 */
@Composable
fun rememberCaptureHaptic(): () -> Unit {
    val haptic = LocalHapticFeedback.current
    return remember(haptic) {
        { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
    }
}
