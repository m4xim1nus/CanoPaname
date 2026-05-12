package app.arbre.ui.badges

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Grass
import androidx.compose.material.icons.outlined.Height
import androidx.compose.material.icons.outlined.Looks3
import androidx.compose.material.icons.outlined.Looks4
import androidx.compose.material.icons.outlined.Looks5
import androidx.compose.material.icons.outlined.LooksOne
import androidx.compose.material.icons.outlined.LooksTwo
import androidx.compose.material.icons.outlined.Park
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.ui.graphics.vector.ImageVector
import app.arbre.data.BadgeCatalog
import app.arbre.data.BadgeDef

/**
 * Mapping id → icône. Outlined partout. `EmojiEvents` en fallback générique
 * si l'id n'est pas reconnu.
 */
fun BadgeDef.icon(): ImageVector = when (id) {
    BadgeCatalog.PREMIERE_CAPTURE.id -> Icons.Outlined.PhotoCamera
    BadgeCatalog.ESPECE_UNIQUE.id -> Icons.Outlined.LooksOne
    BadgeCatalog.ESPECE_COUPLE.id -> Icons.Outlined.LooksTwo
    BadgeCatalog.ESPECE_TRINITE.id -> Icons.Outlined.Looks3
    BadgeCatalog.ESPECE_QUATUOR.id -> Icons.Outlined.Looks4
    BadgeCatalog.ESPECE_QUINTETTE.id -> Icons.Outlined.Looks5
    BadgeCatalog.GEANT.id -> Icons.Outlined.Height
    BadgeCatalog.BONSAI.id -> Icons.Outlined.Grass
    BadgeCatalog.VIEUX_SAGE.id -> Icons.Outlined.Park
    BadgeCatalog.JEUNE_POUSSE.id -> Icons.Outlined.Eco
    else -> Icons.Outlined.EmojiEvents
}
