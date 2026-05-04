package app.arbre.ui.map

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.arbre.R
import app.arbre.data.SpeciesEntry
import app.arbre.ui.theme.arbresColors
import app.arbre.ui.theme.arbresMotion

/**
 * Bandeau retour + label espèce affiché en haut à gauche en mode `MAP_FILTERED`.
 * Remplace les FABs Profil/Arboretum/Remarquables qui n'ont pas de sens en mode
 * filtré (l'utilisateur chasse une espèce précise, pas le Catalogue global).
 */
@Composable
internal fun FilterBanner(
    entry: SpeciesEntry,
    count: Int?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.widthIn(max = 320.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        shadowElevation = 4.dp,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Retour à la fiche-espèce",
                )
            }
            Column(
                modifier = Modifier
                    .padding(start = 4.dp)
                    .fillMaxWidth(),
            ) {
                Text(
                    entry.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                )
                if (count != null) {
                    Text(
                        "$count arbre${if (count > 1) "s" else ""} dans Paris",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Splash overlay du cold start — superposé tant que `arbresPrets == false`.
 * Couleur de fond synchronisée avec `@color/ic_launcher_background` du splash
 * natif (themes.xml) pour ne pas faire flasher la transition.
 *
 * Animation pure Compose (pas de Lottie) :
 * - Sway sinusoïdal ±3° de la silhouette d'arbre (rotation via graphicsLayer).
 * - Cascade fade+scale à l'apparition (0→1 alpha + 0.85→1 scale, 600 ms).
 * - LinearProgressIndicator or `arbresColors.or` (token de marque) plutôt que
 *   le CircularProgressIndicator blanc générique.
 */
@Composable
internal fun ColdStartSplash() {
    // Doit rester en sync avec @color/ic_launcher_background.
    val splashGreen = MaterialTheme.colorScheme.primary
    val arbresColors = MaterialTheme.arbresColors
    val motion = MaterialTheme.arbresMotion

    val infinite = rememberInfiniteTransition(label = "sway")
    val sway by infinite.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = motion.sway, easing = motion.swayEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "swayAngle",
    )
    // `Animatable` plutôt que `animateFloatAsState(targetValue = 1f)` : le
    // second saute directement à 1f à la 1re composition (pas d'animation
    // visible) parce qu'il n'y a pas de valeur précédente vers laquelle
    // interpoler. L'Animatable part explicitement de 0f puis va à 1f.
    val intro = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        intro.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = motion.medium, easing = motion.swayEasing),
        )
    }
    val introValue = intro.value

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(splashGreen),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer { alpha = introValue },
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier
                    .size(168.dp)
                    .scale(0.85f + introValue * 0.15f)
                    .graphicsLayer { rotationZ = sway },
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = "CanoPaname",
                color = Color.White,
                style = MaterialTheme.typography.displayMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Réveil des 217 855 arbres parisiens",
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(28.dp))
            LinearProgressIndicator(
                color = arbresColors.or,
                trackColor = Color.White.copy(alpha = 0.18f),
                modifier = Modifier
                    .widthIn(max = 200.dp)
                    .fillMaxWidth(0.45f),
            )
        }
    }
}

/**
 * Splash overlay dédié au mode `MAP_FILTERED` : on ne charge pas 217k arbres,
 * juste une espèce filtrée (pré-filtre Kotlin < 1 s + setStyle MapLibre 1-3 s).
 * Pas de silhouette d'arbre (pas un boot), pas d'animation infinie — juste
 * un voile bref pour éviter le flash de carte vide.
 */
@Composable
internal fun FilterSplash(speciesLabel: String) {
    val splashGreen = MaterialTheme.colorScheme.primary
    val motion = MaterialTheme.arbresMotion
    val intro = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        intro.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = motion.short, easing = motion.swayEasing),
        )
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(splashGreen),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(horizontal = 48.dp)
                .graphicsLayer { alpha = intro.value },
        ) {
            Text(
                text = "Filtrage de $speciesLabel…",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.5.dp,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}
