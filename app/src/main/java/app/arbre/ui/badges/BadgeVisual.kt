package app.arbre.ui.badges

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Place
import androidx.compose.ui.graphics.vector.ImageVector
import app.arbre.data.ArrKey
import app.arbre.data.BadgeCatalog
import app.arbre.data.BadgeDef
import app.arbre.data.arrKeyFromSlug
import app.arbre.data.romanNumeral

/**
 * Visuel d'un badge dans le cercle de `BadgeCard` / `BadgeIconCircle` : soit une
 * icône vectorielle Outlined ([Vector]), soit du texte rendu dans le cercle
 * ([Label] — chiffre romain de l'arrondissement, ou « Boulogne »·« Vincennes »
 * pour les deux bois).
 */
sealed interface BadgeVisual {
    data class Vector(val image: ImageVector) : BadgeVisual
    data class Label(val text: String) : BadgeVisual
}

/**
 * Visuel du badge. Les badges « Familier d'arrondissement » (`familier_arr_*`)
 * sont des textes : chiffre romain I…XX pour les 20 arrondissements, nom court
 * pour les 2 bois. Tout le reste — y compris la famille « Familier de genre »
 * (`familier_genre_*` → `Forest`, l'idée « bosquet ») — passe par [icon].
 */
fun BadgeDef.visual(): BadgeVisual = when {
    id.startsWith(BadgeCatalog.FAMILIER_ARR_PREFIX) -> {
        when (val key = arrKeyFromSlug(id.removePrefix(BadgeCatalog.FAMILIER_ARR_PREFIX))) {
            is ArrKey.Paris -> BadgeVisual.Label(key.romanNumeral() ?: "?")
            ArrKey.BoisVincennes -> BadgeVisual.Label("Vincennes")
            ArrKey.BoisBoulogne -> BadgeVisual.Label("Boulogne")
            else -> BadgeVisual.Vector(Icons.Outlined.Place)
        }
    }
    else -> BadgeVisual.Vector(icon())
}
