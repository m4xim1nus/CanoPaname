package app.arbre.ui.map

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.arbre.R
import app.arbre.data.SpeciesEntry
import app.arbre.data.rememberCaptureRepository
import app.arbre.data.rememberDatasetStats
import app.arbre.data.rememberOnboardingStore
import app.arbre.data.rememberSpeciesIndex
import app.arbre.data.rememberSplashTipsRepository
import app.arbre.ui.common.rememberFrameMillis
import app.arbre.ui.common.rememberFramePingPong
import app.arbre.ui.common.rememberFrameProgress
import app.arbre.ui.theme.ArbresMotion
import app.arbre.ui.theme.arbresColors
import app.arbre.ui.theme.arbresMotion
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/** Période de rotation du mini-platane « filtrage en cours » du [QuickFilterBanner]. */
private const val SPIN_PERIOD_MS = 1400L

/** Crème des textes secondaires et mini-platanes sur le fond vert des splashes. */
private val SplashCream = Color(0xFFF5F1E6)

/** Bandeau retour + label espèce, affiché en mode `MAP_FILTERED` à la place
 *  des FABs Profil/Arboretum/Remarquables.
 */
@Composable
internal fun FilterBanner(
    entry: SpeciesEntry,
    count: Int?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Sous-titre additionnel pour le mode genre :
     * « 3 / 12 espèces du genre ». `null` en mode normal singleton.
     */
    genreSubtitle: String? = null,
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
                    entry.displayNomCommun,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                )
                // Sous-titre binôme italique uniquement si `nv`/`nc` ont
                // apporté un nom différent du binôme latin.
                if (entry.nv != null || entry.nomCommun != null) {
                    Text(
                        entry.displayName,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontStyle = FontStyle.Italic,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                if (count != null) {
                    Text(
                        "$count arbre${if (count > 1) "s" else ""} dans Paris",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (genreSubtitle != null) {
                    Text(
                        genreSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Bandeau du filtre rapide (boutons sheet « Toute l'espèce » / « Tout le
 * genre ») sur la carte principale : ✕ de défiltrage + label figé au tap.
 * Même habillage que [FilterBanner], mais le ✕ retire le filtre in-place
 * (pas de navigation) et le label est une string libre (nv espèce ou nom de
 * genre). Prend le slot TopStart du FAB Recherche tant que le filtre est actif.
 * `busy` (filtrage/défiltrage en cours, source pas encore basculée) remplace
 * le ✕ par un mini-platane en rotation — feedback minimal pendant les ~1-3 s
 * du swap, pas de défiltrage cliquable tant que la source n'est pas
 * stabilisée. Rotation pilotée `withFrameNanos` (cf. `ui/common/FrameClock.kt`),
 * PAS un `CircularProgressIndicator` indéterminé : ce spinner est le seul
 * retour visuel du swap et doit rester vivant à échelle d'animation système 0.
 */
@Composable
internal fun QuickFilterBanner(
    label: String,
    count: Int?,
    busy: Boolean,
    onClear: () -> Unit,
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
            if (busy) {
                // Même empreinte 48 dp que l'IconButton : pas de saut de
                // layout au flip spinner ↔ ✕.
                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    SpinningMiniArbre(
                        contentDescription = "Filtrage en cours",
                        modifier = Modifier.size(22.dp),
                    )
                }
            } else {
                IconButton(onClick = onClear) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "Retirer le filtre",
                    )
                }
            }
            Column(modifier = Modifier.padding(start = 4.dp)) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
 * Spinner indéterminé maison : un mini-platane en rotation continue, piloté `withFrameNanos`
 * (cf. `ui/common/FrameClock.kt`) — vivant même à échelle d'animation système 0, là où un
 * `CircularProgressIndicator` se fige. À préférer partout où le retour de chargement doit
 * survivre au réglage accessibilité. La lecture du `State` se fait dans le draw layer : invalide
 * le dessin à chaque frame sans recomposer.
 */
@Composable
private fun SpinningMiniArbre(
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    val elapsed = rememberFrameMillis()
    Image(
        painter = painterResource(R.drawable.ic_arbre_canonical),
        contentDescription = contentDescription,
        colorFilter = ColorFilter.tint(tint),
        modifier = modifier.graphicsLayer {
            rotationZ = (elapsed.value % SPIN_PERIOD_MS) / SPIN_PERIOD_MS.toFloat() * 360f
        },
    )
}

/**
 * Squelette commun aux deux splash plein écran (cold start global + mode filtré) :
 * fond vert `colorScheme.primary` (à garder en sync avec `@color/ic_launcher_background`,
 * sans quoi la transition depuis le splash natif flashe), hero platane qui se balance,
 * fondu d'entrée, couronne décorative de mini-platanes flottants par-dessus.
 *
 * Pilotage `withFrameNanos` (cf. `ui/common/FrameClock.kt`) : ces animations doivent rester
 * vivantes même si l'échelle d'animation système est à 0 — le splash est le seul retour
 * visuel pendant le chargement DB + GeoJSON (cold start) ou le pré-filtre + setStyle MapLibre
 * (mode filtré).
 *
 * `content` reçoit `introDone` (le fondu d'entrée est terminé) : le `ColdStartSplash` s'en
 * sert pour n'autoriser la rotation des tips qu'après le fade-in.
 */
@Composable
internal fun SplashScaffold(content: @Composable ColumnScope.(introDone: Boolean) -> Unit) {
    val splashGreen = MaterialTheme.colorScheme.primary
    val motion = MaterialTheme.arbresMotion

    val swayP by rememberFramePingPong(periodMs = motion.sway * 2, easing = motion.swayEasing)
    val sway = -3f + swayP * 6f   // -3° → +3° → -3°
    val introState = rememberFrameProgress(durationMs = motion.medium, easing = motion.swayEasing)
    val introValue by introState
    // `derivedStateOf` pour ne déclencher qu'au passage 0.99 → 1f — sinon la
    // rotation des tips redémarrerait à chaque frame du fade-in.
    val introDone by remember { derivedStateOf { introState.value >= 1f } }

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
            content(introDone)
        }
        // Posée après la Column pour que les platanes passent au-dessus
        // du hero (z-order).
        MiniArbreCrown(motion = motion, cream = SplashCream)
    }
}

/** Durée de la séquence de révélation du voile de célébration — c'est aussi le
 *  plancher de lecture côté `ArbresNavHost` (source unique, le plancher suit). */
internal const val CAPTURE_CELEBRATION_SEQUENCE_MS = 2200L

/** Instant du tic haptique, calé sur l'apparition du nom vernaculaire (climax).
 *  Consommé par `ArbresNavHost` (l'haptique vit dans la coroutine du voile,
 *  annulée avec elle au skip — un tic pendant le fadeOut serait du bruit). */
internal const val CAPTURE_CELEBRATION_HAPTIC_MS = 950L

/**
 * Voile de transition « 1re capture d'espèce » : couvre la bascule validation
 * photo → fiche espèce pour ne jamais repasser visuellement par la carte, ET
 * porte le climax de célébration (kicker → nom vernaculaire → binôme latin) —
 * la fiche en dessous reste ainsi la fiche normale, sans bloc « succès ».
 * Rendu par `ArbresNavHost` AU-DESSUS du NavHost (piloté par
 * `MapHost.captureTransitionSk`), levé synchroniquement au retour de l'intent
 * caméra, éteint une fois la fiche RESUMED et le plancher
 * [CAPTURE_CELEBRATION_SEQUENCE_MS] écoulé (skippable au tap, cf. NavHost —
 * qui tire aussi le tic haptique à [CAPTURE_CELEBRATION_HAPTIC_MS]).
 * Séquence et scaffold sont frame-clock — vivants même à échelle d'animation
 * système 0.
 */
@Composable
internal fun CaptureTransitionSplash(speciesIndex: Int) {
    val entry = rememberSpeciesIndex().get(speciesIndex)
    SplashScaffold {
        if (entry != null) CelebrationReveal(entry)
    }
}

/**
 * Cascade de révélation sous le platane du voile : kicker « Nouvelle espèce »
 * → nom vernaculaire (climax) → binôme latin → filet or, sur
 * [CAPTURE_CELEBRATION_SEQUENCE_MS]. Une seule horloge frame-clock partagée ;
 * alphas/translations calculés dans les `graphicsLayer` (invalide le dessin à
 * chaque frame sans recomposer, cf. `ui/common/FrameClock.kt`). La hiérarchie
 * typo (vernaculaire dominant, binôme italique secondaire) est celle du
 * `SpeciesHero` : le voile parle la même langue que la fiche qu'il révèle.
 */
@Composable
private fun ColumnScope.CelebrationReveal(entry: SpeciesEntry) {
    val motion = MaterialTheme.arbresMotion
    val or = MaterialTheme.arbresColors.or
    val elapsed = rememberFrameMillis()
    // Enveloppe temporelle easée : 0 avant startMs, 1 après endMs. À n'appeler
    // que depuis un bloc `graphicsLayer` (lecture du State frame par frame).
    fun window(startMs: Long, endMs: Long): Float {
        val t = ((elapsed.value - startMs).toFloat() / (endMs - startMs)).coerceIn(0f, 1f)
        return motion.swayEasing.transform(t)
    }
    Text(
        text = "NOUVELLE ESPÈCE",
        color = SplashCream.copy(alpha = 0.9f),
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.sp),
        modifier = Modifier.graphicsLayer {
            val p = window(350L, 850L)
            alpha = p
            translationY = (1f - p) * 10.dp.toPx()
        },
    )
    Spacer(Modifier.height(12.dp))
    Text(
        text = entry.displayNomCommun,
        color = Color.White,
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .padding(horizontal = 28.dp)
            .graphicsLayer {
                val p = window(750L, 1350L)
                alpha = p
                translationY = (1f - p) * 14.dp.toPx()
                scaleX = 0.97f + p * 0.03f
                scaleY = 0.97f + p * 0.03f
            },
    )
    // Pas de doublon quand le vernaculaire est déjà retombé sur le binôme
    // (espèce sans nom commun dans le dataset).
    if (entry.displayNomCommun != entry.displayName) {
        Spacer(Modifier.height(6.dp))
        Text(
            text = entry.displayName,
            color = SplashCream.copy(alpha = 0.85f),
            style = MaterialTheme.typography.titleMedium.copy(fontStyle = FontStyle.Italic),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(horizontal = 28.dp)
                .graphicsLayer { alpha = window(1300L, 1800L) },
        )
    }
    Spacer(Modifier.height(20.dp))
    // Filet or : la respiration finale, sobre — pas de confetti. Largeur fixe
    // pour un layout stable, révélé par scaleX depuis le centre.
    Box(
        modifier = Modifier
            .width(64.dp)
            .height(1.5.dp)
            .graphicsLayer {
                val p = window(1650L, 2050L)
                scaleX = p
                alpha = p * 0.7f
            }
            .background(or),
    )
}

/**
 * Splash overlay du cold start, superposé tant que `arbresPrets == false`.
 */
@Composable
internal fun ColdStartSplash() {
    val splashTips = rememberSplashTipsRepository()
    val captureRepo = rememberCaptureRepository()
    val onboardingStore = rememberOnboardingStore()

    // DOIT matcher le `point_count` du cluster MapLibre dezoomé à fond — sinon
    // l'utilisateur croit qu'on lui survend quelques milliers d'arbres.
    val datasetStats = rememberDatasetStats()
    val totalFormatted = remember(datasetStats.totalArbres) {
        NumberFormat.getInstance(Locale.FRANCE).format(datasetStats.totalArbres)
    }

    SplashScaffold { introDone ->
        val tipText by rememberSplashTipText(
            repository = splashTips,
            captureRepository = captureRepo,
            onboardingStore = onboardingStore,
            canRotate = introDone,
        )
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
        // Hauteur fixe pour éviter les sauts de layout entre une phrase
        // courte et une longue.
        Spacer(Modifier.height(32.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp)
                .padding(horizontal = 28.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Laissé en `AnimatedContent` (≠ frame-clock) : à échelle d'animation = 0 le
            // tip change sec, ce qui est acceptable — un crossfade manuel devrait tenir les
            // deux textes en parallèle pour un gain quasi nul.
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
}

/**
 * Couronne décorative de 7 silhouettes de platane miniatures qui flottent
 * autour du hero. Sert à animer le splash pendant les 3-25 s de chargement
 * (asset DB + GeoJSON 32 Mo) en lieu et place d'une barre de progression
 * sans signal réel.
 *
 * Apparition séquentielle puis boucle infinie sway+drift, désynchronisée par
 * platane via `phaseOffset` (multiples irrationnels, évite les harmoniques).
 * Arc semi-circulaire AU-DESSUS du hero — pas de couronne 360°, la column
 * descendante (wordmark, sous-titre, tip) chevaucherait.
 */
private data class MiniArbre(
    val angleDeg: Float,
    val radiusDp: Float,
    val sizeDp: Float,
    val targetAlpha: Float,
    val delayMs: Int,
    val phaseOffset: Float,
)

// Cycle complet 3500 ms par platane (fade in 600 + plateau 1300 + fade out
// 600 + invisible 1000). `delayMs` étalés 0-3000 ms : à tout instant 1-2
// platanes en transition + 2-3 en plateau, donc cascade continue pendant
// tout le cold start.
private const val MINI_FADE_MS = 600L
private const val MINI_HOLD_MS = 1300L
private const val MINI_GONE_MS = 1000L
private const val MINI_CYCLE_MS = MINI_FADE_MS * 2 + MINI_HOLD_MS + MINI_GONE_MS // 3500

/** Position `(alpha, scale)` d'un mini-platane à `localMs` ms après son `delayMs` propre.
 *  alpha ∈ [0, targetAlpha], scale ∈ [0.6, 1]. Pendant inerte du state-machine
 *  `Animatable` d'avant, jouable depuis une horloge frame-clock partagée. */
private fun miniArbrePhase(localMs: Long, targetAlpha: Float, easing: Easing): Pair<Float, Float> {
    if (localMs < 0L) return 0f to 0.6f
    val pos = localMs % MINI_CYCLE_MS
    return when {
        pos < MINI_FADE_MS -> {
            val t = easing.transform(pos.toFloat() / MINI_FADE_MS)
            (targetAlpha * t) to (0.6f + 0.4f * t)
        }
        pos < MINI_FADE_MS + MINI_HOLD_MS -> targetAlpha to 1f
        pos < MINI_FADE_MS * 2 + MINI_HOLD_MS -> {
            val t = easing.transform((pos - MINI_FADE_MS - MINI_HOLD_MS).toFloat() / MINI_FADE_MS)
            (targetAlpha * (1f - t)) to (1f - 0.4f * t)
        }
        else -> 0f to 0.6f
    }
}

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
    // Horloges frame-clock partagées (cf. `ui/common/FrameClock.kt`) : insensibles à l'échelle
    // d'animation système. `elapsed`/`swayP`/`driftP` sont des `State` passés tels quels — lus dans
    // le `graphicsLayer` des items, donc cette couronne ne recompose pas par frame (l'ancien
    // `infiniteTransition` re-rendait les 7 items chaque frame).
    val elapsed = rememberFrameMillis()
    val swayP = rememberFramePingPong(periodMs = motion.sway * 2, easing = motion.swayEasing)
    val driftP = rememberFramePingPong(periodMs = 3600 * 2, easing = motion.swayEasing)
    miniArbres.forEach { mini ->
        MiniArbreItem(
            mini = mini,
            elapsed = elapsed,
            swayP = swayP,
            driftP = driftP,
            easing = motion.swayEasing,
            cream = cream,
        )
    }
}

@Composable
private fun MiniArbreItem(
    mini: MiniArbre,
    elapsed: State<Long>,
    swayP: State<Float>,
    driftP: State<Float>,
    easing: Easing,
    cream: Color,
) {
    // Convention : 0° = vertical haut, -90° = gauche, +90° = droite. Le
    // `Modifier.offset` translate depuis le centre du Box parent.
    val angleRad = Math.toRadians(mini.angleDeg.toDouble() - 90.0)
    val baseX = (cos(angleRad) * mini.radiusDp).toFloat().dp
    val baseY = (sin(angleRad) * mini.radiusDp).toFloat().dp
    // Amplitude variable 5°-7° par platane + signe via `sin(phaseOffset)`
    // pour que certains oscillent en phase opposée.
    val rotationAmplitude = 5f + (mini.phaseOffset.mod(2f)) * 2f
    Image(
        painter = painterResource(R.drawable.ic_arbre_canonical),
        contentDescription = null,
        colorFilter = ColorFilter.tint(cream),
        modifier = Modifier
            .size(mini.sizeDp.dp)
            .offset(baseX, baseY)
            // Lecture des `State` ici (pas dans le corps du composable) : invalide le draw layer
            // à chaque frame sans recomposer. `swayP`/`driftP` sont des ping-pongs 0→1→0, remappés
            // en -1→1→-1 par `* 2f - 1f` (les amplitudes héritées étaient en [-1, 1]).
            .graphicsLayer {
                val local = elapsed.value - mini.delayMs
                val (a, s) = miniArbrePhase(local, mini.targetAlpha, easing)
                alpha = a
                scaleX = s
                scaleY = s
                rotationZ = (swayP.value * 2f - 1f) * rotationAmplitude * sin(mini.phaseOffset)
                translationY = (driftP.value * 2f - 1f) * 3.dp.toPx() * cos(mini.phaseOffset * 1.3f)
            },
    )
}

/**
 * Splash du mode `MAP_FILTERED` (pré-filtre Kotlin < 1 s + setStyle MapLibre 1-3 s).
 * Même disposition / mêmes animations que le `ColdStartSplash` (hero platane qui se balance
 * + couronne de mini-platanes, via `SplashScaffold`), mais sans zone de tips : à la place du
 * sous-titre compteur, « Réveil des <nv> parisiens » avec le nom vernaculaire mis en avant
 * (plus gros + gras). `speciesLabel` est le nv au singulier — la mise au pluriel se fait ici.
 */
@Composable
internal fun FilterSplash(speciesLabel: String) {
    SplashScaffold {
        val phrase = buildAnnotatedString {
            withStyle(SpanStyle(color = Color.White.copy(alpha = 0.85f))) {
                append("Réveil des ")
            }
            withStyle(
                SpanStyle(color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
            ) {
                append(pluralizeHead(speciesLabel))
            }
            withStyle(SpanStyle(color = Color.White.copy(alpha = 0.85f))) {
                append(" parisiens")
            }
        }
        Text(
            text = phrase,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
        Spacer(Modifier.height(20.dp))
        SpinningMiniArbre(
            contentDescription = "Réveil en cours",
            modifier = Modifier.size(28.dp),
            tint = Color.White.copy(alpha = 0.7f),
        )
    }
}

/** Pluralise le 1er mot d'un nom vernaculaire : « Platane » → « Platanes », « Bouleau » →
 *  « Bouleaux », « Cyprès » → « Cyprès » (invariant), « Tilleul à petites feuilles » →
 *  « Tilleuls à petites feuilles ». L'accord d'adjectif n'est pas géré (« Chêne vert » →
 *  « Chênes vert ») — assumé, le bandeau qui suit donne le nom exact. */
private fun pluralizeHead(label: String): String {
    val space = label.indexOf(' ')
    val head = if (space == -1) label else label.substring(0, space)
    val rest = if (space == -1) "" else label.substring(space)
    val lower = head.lowercase(Locale.FRANCE)
    val pluralHead = when {
        lower.endsWith("s") || lower.endsWith("x") || lower.endsWith("z") -> head
        lower.endsWith("eau") || lower.endsWith("eu") -> head + "x"
        else -> head + "s"
    }
    return pluralHead + rest
}
