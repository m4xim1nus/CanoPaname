package app.arbre.ui.map

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.arbre.R
import app.arbre.data.SpeciesEntry
import app.arbre.data.rememberCaptureRepository
import app.arbre.data.rememberDatasetStats
import app.arbre.data.rememberOnboardingStore
import app.arbre.data.rememberSplashTipsRepository
import app.arbre.ui.theme.ArbresMotion
import app.arbre.ui.theme.arbresMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

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
 * - Cascade fade+scale à l'apparition du hero (0→1 alpha + 0.85→1 scale, 600 ms).
 * - Couronne de mini-platanes flottants en boucle continue (cf.
 *   `MiniArbreCrown`) — porte le sentiment de vie pendant les 3-25 s
 *   d'attente cold-start, plutôt qu'une barre de progression sans signal réel.
 */
@Composable
internal fun ColdStartSplash() {
    // Doit rester en sync avec @color/ic_launcher_background.
    val splashGreen = MaterialTheme.colorScheme.primary
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
    // `derivedStateOf` plutôt qu'une lecture brute : la rotation des tips ne
    // doit pas redémarrer à chaque frame du fade-in. Ne change qu'une fois,
    // au passage 0.99 → 1f.
    val canRotateTips by remember { derivedStateOf { intro.value >= 1f } }

    val splashTips = rememberSplashTipsRepository()
    val captureRepo = rememberCaptureRepository()
    val onboardingStore = rememberOnboardingStore()
    val tipText by rememberSplashTipText(
        repository = splashTips,
        captureRepository = captureRepo,
        onboardingStore = onboardingStore,
        canRotate = canRotateTips,
    )

    // Total réellement embarqué (arbres OpenData filtrés sur genre+espece
    // connus). Doit matcher le `point_count` du cluster MapLibre dezoomé à
    // fond — sinon le user croit qu'on lui en survend quelques milliers.
    val datasetStats = rememberDatasetStats()
    val totalFormatted = remember(datasetStats.totalArbres) {
        NumberFormat.getInstance(Locale.FRANCE).format(datasetStats.totalArbres)
    }

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
                text = "Réveil des $totalFormatted arbres parisiens",
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodyMedium,
            )
            // Tip rotatif sous le wordmark, hauteur fixe pour éviter les sauts
            // de layout entre une phrase courte et une longue (3 lignes max).
            Spacer(Modifier.height(32.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp)
                    .padding(horizontal = 28.dp),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    targetState = tipText,
                    transitionSpec = {
                        fadeIn(tween(220)) togetherWith fadeOut(tween(220))
                    },
                    label = "splash-tip",
                ) { text ->
                    if (text != null) {
                        Text(
                            text = text,
                            color = Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        // Couronne de mini-platanes flottants — apparition cascade puis
        // boucle douce désynchronisée. Posée après la Column dans le Box
        // pour que les platanes flottent au-dessus du hero (z-order).
        MiniArbreCrown(motion = motion, cream = Color(0xFFF5F1E6))
    }
}

/**
 * Couronne décorative de 7 silhouettes de platane miniatures qui flottent
 * autour du hero du splash cold-start. Pensée pour donner du « vivant » au
 * splash pendant les 3-25 s de chargement asset DB + parsing GeoJSON 32 Mo
 * (le hero seul, statique sauf sway, paraissait figé en device cold).
 *
 * Apparition séquentielle (delays 160→1000 ms, fade+scale `motion.short`),
 * puis boucle infinie sway rotatif + drift Y, désynchronisée par platane
 * via `phaseOffset = idx * 0.37f` (irrationnel-ish, évite les harmoniques).
 *
 * Disposition en arc semi-circulaire AU-DESSUS du hero (angles -88°→+88°,
 * rayons 115-155 dp). Pas de couronne 360° : la column descend (wordmark,
 * sous-titre, barre) et chevaucherait.
 */
private data class MiniArbre(
    val angleDeg: Float,
    val radiusDp: Float,
    val sizeDp: Float,
    val targetAlpha: Float,
    val delayMs: Int,
    val phaseOffset: Float,
)

// Cycle complet par platane : fade in 600 ms → plateau visible 1300 ms →
// fade out 600 ms → invisible 1000 ms = 3500 ms. Les `delayMs` sont étalés
// sur 0-3000 ms (pas de 500), de sorte qu'à tout instant il y ait toujours
// 1-2 platanes en train d'apparaître/disparaître et 2-3 en plateau visible.
// L'effet « cascade » est ainsi continu pendant les 3-25 s du cold start,
// pas réduit à 1 s d'intro suivie d'un état figé.
private val miniArbres = listOf(
    MiniArbre(angleDeg = -88f, radiusDp = 130f, sizeDp = 22f, targetAlpha = 0.50f, delayMs = 0, phaseOffset = 0.00f),
    MiniArbre(angleDeg = -55f, radiusDp = 115f, sizeDp = 30f, targetAlpha = 0.62f, delayMs = 500, phaseOffset = 0.37f),
    MiniArbre(angleDeg = -25f, radiusDp = 140f, sizeDp = 18f, targetAlpha = 0.45f, delayMs = 1000, phaseOffset = 0.74f),
    MiniArbre(angleDeg = 5f, radiusDp = 155f, sizeDp = 26f, targetAlpha = 0.55f, delayMs = 1500, phaseOffset = 1.11f),
    MiniArbre(angleDeg = 35f, radiusDp = 120f, sizeDp = 32f, targetAlpha = 0.65f, delayMs = 2000, phaseOffset = 1.48f),
    MiniArbre(angleDeg = 65f, radiusDp = 135f, sizeDp = 20f, targetAlpha = 0.48f, delayMs = 2500, phaseOffset = 1.85f),
    MiniArbre(angleDeg = 88f, radiusDp = 125f, sizeDp = 28f, targetAlpha = 0.58f, delayMs = 3000, phaseOffset = 2.22f),
)

@Composable
private fun MiniArbreCrown(motion: ArbresMotion, cream: Color) {
    val infinite = rememberInfiniteTransition(label = "crown")
    val sway by infinite.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = motion.sway, easing = motion.swayEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "crownSway",
    )
    val drift by infinite.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = motion.swayEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "crownDrift",
    )
    miniArbres.forEach { mini ->
        MiniArbreItem(mini = mini, sway = sway, drift = drift, motion = motion, cream = cream)
    }
}

@Composable
private fun MiniArbreItem(
    mini: MiniArbre,
    sway: Float,
    drift: Float,
    motion: ArbresMotion,
    cream: Color,
) {
    // Cycle infini en `Animatable` + `LaunchedEffect { while(true) ... }`.
    // L'`Animatable.value` est lu comme un `State` dans la lambda
    // `graphicsLayer { }`, ce qui invalide le draw layer à chaque frame —
    // contrairement à un `Float` plain passé en paramètre, qui serait
    // capturé à la composition et figerait l'animation (cf. tentative
    // précédente avec un `tick` global, abandonnée).
    val alpha = remember { Animatable(0f) }
    val scale = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) {
        delay(mini.delayMs.toLong())
        while (true) {
            // Fade in : alpha 0 → targetAlpha + scale 0.6 → 1, en parallèle.
            launch {
                alpha.animateTo(
                    targetValue = mini.targetAlpha,
                    animationSpec = tween(durationMillis = 600, easing = motion.swayEasing),
                )
            }
            scale.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 600, easing = motion.swayEasing),
            )
            // Plateau visible.
            delay(1300)
            // Fade out.
            launch {
                alpha.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 600, easing = motion.swayEasing),
                )
            }
            scale.animateTo(
                targetValue = 0.6f,
                animationSpec = tween(durationMillis = 600, easing = motion.swayEasing),
            )
            // Invisible : laisse respirer le cycle, et déphase
            // suffisamment les 7 platanes pour qu'il y ait toujours
            // 1-2 en transition + 2-3 en plateau (delays 0..3000 ms,
            // cycle 3500 ms par platane).
            delay(1000)
        }
    }
    // Angle 0° = vertical haut. Convention math : -90° = gauche horizontal,
    // +90° = droite. On translate via `Modifier.offset` depuis le centre du
    // Box parent (qui centre les enfants par `contentAlignment = Center`).
    val angleRad = Math.toRadians(mini.angleDeg.toDouble() - 90.0)
    val baseX = (cos(angleRad) * mini.radiusDp).toFloat().dp
    val baseY = (sin(angleRad) * mini.radiusDp).toFloat().dp
    // L'amplitude de rotation varie un peu par platane (5°-7°) pour casser
    // la régularité ; le `sin(phaseOffset)` donne un signe + magnitude
    // continus → certains platanes oscillent en phase opposée, naturel.
    val rotationAmplitude = 5f + (mini.phaseOffset.mod(2f)) * 2f
    Image(
        painter = painterResource(R.drawable.ic_arbre_canonical),
        contentDescription = null,
        colorFilter = ColorFilter.tint(cream),
        modifier = Modifier
            .size(mini.sizeDp.dp)
            .offset(baseX, baseY)
            .graphicsLayer {
                this.alpha = alpha.value
                val s = scale.value
                scaleX = s
                scaleY = s
                rotationZ = sway * rotationAmplitude * sin(mini.phaseOffset)
                translationY = drift * 3.dp.toPx() * cos(mini.phaseOffset * 1.3f)
            },
    )
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
