package app.arbre.ui.badges

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.arbre.data.BadgeState
import app.arbre.ui.theme.ArbresFraunces

/**
 * Cercle plein d'un badge : `tertiary` si débloqué (icône ou texte selon
 * [BadgeDef.visual]), `outline` + silhouette `Lock` si verrouillé. Partagé entre
 * `BadgesScreen` (catalogue complet) et `ProfileScreen` (3 derniers déblocages).
 */
@Composable
fun BadgeIconCircle(state: BadgeState, size: Dp = 48.dp) {
    val unlocked = state.unlocked
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                if (unlocked) MaterialTheme.colorScheme.tertiary
                else MaterialTheme.colorScheme.outline
            ),
        contentAlignment = Alignment.Center,
    ) {
        val contentTint =
            if (unlocked) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.surface
        when (val visual = if (unlocked) state.def.visual() else BadgeVisual.Vector(Icons.Outlined.Lock)) {
            is BadgeVisual.Vector -> Icon(
                imageVector = visual.image,
                contentDescription = null,
                tint = contentTint,
            )
            is BadgeVisual.Label -> Text(
                text = visual.text,
                fontFamily = ArbresFraunces,
                fontSize = labelFontSize(visual.text.length),
                color = contentTint,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
            is BadgeVisual.VectorWithBadge -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                Icon(
                    imageVector = visual.image,
                    contentDescription = null,
                    tint = contentTint,
                    modifier = Modifier.size(size * 0.45f),
                )
                Text(
                    text = visual.badge,
                    fontFamily = ArbresFraunces,
                    fontSize = pokedexBadgeFontSize(visual.badge.length, size),
                    color = contentTint,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Taille de police d'un logo texte pour tenir dans le cercle 48 dp sans ellipsis. */
private fun labelFontSize(length: Int) = when {
    length <= 3 -> 18.sp   // I … VII (et X, XV…)
    length == 4 -> 16.sp   // VIII, XIII, XVII…
    length == 5 -> 13.sp   // XVIII
    length <= 8 -> 10.sp   // « Boulogne »
    else -> 9.sp           // « Vincennes »
}

/** Police du nombre overlay (10/20/50/100/200/500) sous l'icône MenuBook. */
private fun pokedexBadgeFontSize(length: Int, size: Dp) = when {
    size <= 32.dp -> if (length <= 2) 9.sp else 8.sp
    length <= 2 -> 13.sp   // « 10 », « 20 », « 50 »
    else -> 11.sp          // « 100 », « 200 », « 500 »
}
