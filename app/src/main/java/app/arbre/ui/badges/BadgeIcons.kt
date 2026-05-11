package app.arbre.ui.badges

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Forest
import androidx.compose.material.icons.outlined.Height
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Park
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.Star
import androidx.compose.ui.graphics.vector.ImageVector
import app.arbre.data.BadgeCatalog
import app.arbre.data.BadgeDef

/**
 * Mapping id → icône. Outlined partout. `EmojiEvents` en fallback générique
 * si l'id n'est pas reconnu.
 */
fun BadgeDef.icon(): ImageVector = when (id) {
    BadgeCatalog.MARCHEUR.id -> Icons.AutoMirrored.Outlined.DirectionsRun
    BadgeCatalog.BOTANISTE.id -> Icons.Outlined.Spa
    BadgeCatalog.ESPECE_RARE.id -> Icons.Outlined.AutoAwesome
    BadgeCatalog.MOSAIQUE_QUERCUS.id -> Icons.Outlined.Forest
    BadgeCatalog.TOURNEUR_DE_PARIS.id -> Icons.Outlined.Map
    BadgeCatalog.TOUR_COMPLET.id -> Icons.Outlined.Explore
    BadgeCatalog.CHASSEUR.id -> Icons.Outlined.Star
    BadgeCatalog.GEANT.id -> Icons.Outlined.Height
    BadgeCatalog.VIEUX_SAGE.id -> Icons.Outlined.Park
    else -> Icons.Outlined.EmojiEvents
}
