package app.arbre.ui.badges

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Height
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Park
import androidx.compose.ui.graphics.vector.ImageVector
import app.arbre.data.BadgeCatalog
import app.arbre.data.BadgeDef

/**
 * Mapping id → icône. Outlined partout. `EmojiEvents` en fallback générique
 * si l'id n'est pas reconnu.
 */
fun BadgeDef.icon(): ImageVector = when (id) {
    BadgeCatalog.ESPECE_RARE.id -> Icons.Outlined.AutoAwesome
    BadgeCatalog.TOURNEUR_DE_PARIS.id -> Icons.Outlined.Map
    BadgeCatalog.TOUR_COMPLET.id -> Icons.Outlined.Explore
    BadgeCatalog.GEANT.id -> Icons.Outlined.Height
    BadgeCatalog.VIEUX_SAGE.id -> Icons.Outlined.Park
    else -> Icons.Outlined.EmojiEvents
}
