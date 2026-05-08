package app.arbre.ui.badges

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Height
import androidx.compose.material.icons.outlined.Hiking
import androidx.compose.material.icons.outlined.LocalFlorist
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MilitaryTech
import androidx.compose.material.icons.outlined.Park
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.Star
import androidx.compose.ui.graphics.vector.ImageVector
import app.arbre.data.BadgeCatalog
import app.arbre.data.BadgeDef

/**
 * Mapping id → icône. Outlined partout (cf. Phase 3 ROADMAP).
 * `EmojiEvents` en fallback générique si l'id n'est pas reconnu.
 */
fun BadgeDef.icon(): ImageVector = when (id) {
    BadgeCatalog.FIRST_CAPTURE.id -> Icons.Outlined.DirectionsWalk
    BadgeCatalog.PROMENADE.id -> Icons.Outlined.Hiking
    BadgeCatalog.MARCHEUR.id -> Icons.Outlined.DirectionsRun
    BadgeCatalog.CENTURION.id -> Icons.Outlined.MilitaryTech
    BadgeCatalog.BOTANISTE_AMATEUR.id -> Icons.Outlined.Spa
    BadgeCatalog.BOTANISTE_CONFIRME.id -> Icons.Outlined.LocalFlorist
    BadgeCatalog.ESPECE_RARE.id -> Icons.Outlined.AutoAwesome
    BadgeCatalog.TOURNEUR_DE_PARIS.id -> Icons.Outlined.Map
    BadgeCatalog.TOUR_COMPLET.id -> Icons.Outlined.Explore
    BadgeCatalog.CHASSEUR_REMARQUABLES.id -> Icons.Outlined.Star
    BadgeCatalog.LEGENDE.id -> Icons.Outlined.EmojiEvents
    BadgeCatalog.GEANT.id -> Icons.Outlined.Height
    BadgeCatalog.VIEUX_SAGE.id -> Icons.Outlined.Park
    else -> Icons.Outlined.EmojiEvents
}
