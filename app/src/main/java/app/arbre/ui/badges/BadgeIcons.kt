package app.arbre.ui.badges

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Forest
import androidx.compose.material.icons.outlined.Grass
import androidx.compose.material.icons.outlined.Height
import androidx.compose.material.icons.outlined.Looks3
import androidx.compose.material.icons.outlined.Looks4
import androidx.compose.material.icons.outlined.Looks5
import androidx.compose.material.icons.outlined.LooksOne
import androidx.compose.material.icons.outlined.LooksTwo
import androidx.compose.material.icons.outlined.Park
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Place
import androidx.compose.ui.graphics.vector.ImageVector
import app.arbre.data.BadgeCatalog
import app.arbre.data.BadgeDef

/**
 * Mapping id → icône. Outlined partout. `EmojiEvents` en fallback générique.
 *
 * Les familles dynamiques « Familier » partagent une icône :
 * - « Familier des … » (genre) → `Forest` (un grand arbre + des petits — l'idée
 *   « bosquet », tout le genre capturé). Placeholder commun ; un visuel dédié
 *   par genre n'est pas prévu.
 * - « Familier du … » (arrondissement) → `Place` en **placeholder S3** ; le
 *   logo final est le chiffre romain (I…XX) / « Boulogne »·« Vincennes » rendu
 *   en texte dans le cercle — tâche S5 côté `BadgesScreen` (cf. ROADMAP).
 */
fun BadgeDef.icon(): ImageVector = when {
    id == BadgeCatalog.PREMIERE_CAPTURE.id -> Icons.Outlined.PhotoCamera
    id == BadgeCatalog.ESPECE_UNIQUE.id -> Icons.Outlined.LooksOne
    id == BadgeCatalog.ESPECE_COUPLE.id -> Icons.Outlined.LooksTwo
    id == BadgeCatalog.ESPECE_TRINITE.id -> Icons.Outlined.Looks3
    id == BadgeCatalog.ESPECE_QUATUOR.id -> Icons.Outlined.Looks4
    id == BadgeCatalog.ESPECE_QUINTETTE.id -> Icons.Outlined.Looks5
    id == BadgeCatalog.GEANT.id -> Icons.Outlined.Height
    id == BadgeCatalog.BONSAI.id -> Icons.Outlined.Grass
    id == BadgeCatalog.VIEUX_SAGE.id -> Icons.Outlined.Park
    id == BadgeCatalog.JEUNE_POUSSE.id -> Icons.Outlined.Eco
    id.startsWith(BadgeCatalog.FAMILIER_GENRE_PREFIX) -> Icons.Outlined.Forest
    id.startsWith(BadgeCatalog.FAMILIER_ARR_PREFIX) -> Icons.Outlined.Place
    else -> Icons.Outlined.EmojiEvents
}
