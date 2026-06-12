package app.arbre.ui.map

import android.location.Location
import android.os.SystemClock
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.arbre.data.Arbre
import app.arbre.data.rememberRadarObscureStore
import app.arbre.ui.theme.arbresColors
import app.arbre.util.LocationProvider
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Durée d'une révolution du radar. C'est aussi la cadence de refresh de la
 * distance : le wrap du balayage (passage à midi) déclenche la nouvelle lecture
 * — une seule horloge, pas deux timers qui dérivent. L'AFFICHAGE de cette
 * lecture (pulse, chiffre, saut du blip) est lui théâtralisé : commité quand la
 * barre passe sur le blip, ou directement au wrap s'il n'y a pas de blip.
 */
private const val SWEEP_DURATION_MS = 5_000L
/** Durée du « ping » visuel après chaque refresh. */
private const val PULSE_DURATION_MS = 260f
private const val NEAR_THRESHOLD_M = 10f
private const val PANEL_HEIGHT_FRACTION = 0.28f
/**
 * Sous ce seuil, plus de blip directionnel : à courte distance le bearing GPS
 * est du bruit pur (précision urbaine 5-15 m), un blip qui saute d'un bord à
 * l'autre du radar n'aide personne. Pulse central à la place.
 */
private const val DIRECTION_HIDE_M = 25f
/**
 * Le blip ne réapparaît qu'au-delà de ce seuil — hystérésis contre le
 * flip-flop blip ↔ centre quand on stationne à ~25 m de la cible.
 */
private const val DIRECTION_SHOW_M = 35f
/** Distance projetée sur l'anneau extérieur du radar (au-delà, blip capé au bord). */
private const val RADAR_EDGE_M = 2_000f

private sealed interface HuntReadout {
    /** Liste des remarquables pas encore chargée. */
    data object Loading : HuntReadout
    /** Plus aucun remarquable à découvrir. */
    data object AllDiscovered : HuntReadout
    /** On a une cible candidate mais pas de fix GPS exploitable. */
    data class NoFix(val target: Arbre) : HuntReadout
    /** Cible verrouillée + distance en mètres + bearing géographique 0-360 (0 = nord). */
    data class Locked(val target: Arbre, val distanceM: Float, val bearingDeg: Float) : HuntReadout
}

private fun computeReadout(
    remarquables: List<Arbre>?,
    capturedIds: Set<Long>,
    loc: Location?,
): HuntReadout {
    if (remarquables == null) return HuntReadout.Loading
    val candidates = remarquables.filterNot { it.id in capturedIds }
    if (candidates.isEmpty()) return HuntReadout.AllDiscovered
    if (loc == null) return HuntReadout.NoFix(candidates.first())
    // results[1] = initial bearing (±180), rempli gratuitement par le même appel.
    val results = FloatArray(2)
    val (nearest, dist, bearing) = candidates.map { rem ->
        Location.distanceBetween(loc.latitude, loc.longitude, rem.latitude, rem.longitude, results)
        Triple(rem, results[0], results[1])
    }.minBy { it.second }
    return HuntReadout.Locked(nearest, dist, (bearing + 360f) % 360f)
}

/**
 * « Faut-il masquer le blip directionnel ? » — vrai sous [DIRECTION_HIDE_M],
 * ne redevient faux qu'au-delà de [DIRECTION_SHOW_M] (hystérésis).
 */
private fun updateNearMode(prev: Boolean, readout: HuntReadout): Boolean =
    when (readout) {
        is HuntReadout.Locked ->
            readout.distanceM < (if (prev) DIRECTION_SHOW_M else DIRECTION_HIDE_M)
        else -> false
    }

/** Blip cible sur le radar : angle north-up (0° = midi = nord) + rayon ∈ [0, 1]. */
private data class RadarBlip(val bearingDeg: Float, val radiusFrac: Float)

/**
 * La barre de balayage a-t-elle franchi [bearingDeg] entre deux frames ?
 * Intervalle ouvert-fermé `(prev, cur]`, wrap à 0/360 géré.
 */
private fun sweepCrossed(prev: Float, cur: Float, bearingDeg: Float): Boolean =
    when {
        prev == cur -> false
        prev < cur -> bearingDeg > prev && bearingDeg <= cur
        else -> bearingDeg > prev || bearingDeg <= cur
    }

/**
 * État vivant du radar, piloté par une seule boucle `withFrameNanos`
 * (insensible à l'échelle d'animation système — cf. [RadarGlyph]) : angle du
 * balayage en continu, et au wrap (tour complet = 5 s) nouvelle lecture de la
 * cible. Théâtre sonar : quand un blip est affiché, la lecture part en
 * `pending` et n'est commitée (readout affiché + pulse + saut du blip +
 * chiffre) qu'au passage de la barre sur le blip — l'utilisateur voit le
 * refresh « quand la barre balaie le contact », même si le calcul reste calé
 * sur le wrap. Sans blip (proche, NoFix…), commit direct au wrap, barre à
 * midi (pulse « nord »).
 */
private class HuntRadarState {
    var readout by mutableStateOf<HuntReadout>(HuntReadout.Loading)
        private set
    var sweep by mutableFloatStateOf(0f)   // 0..360, 0 = midi
        private set
    var pulse by mutableFloatStateOf(0f)   // 1 au commit, décroît vers 0
        private set
    var nearMode by mutableStateOf(false)  // hystérésis 25/35 m
        private set

    private var remarquables: List<Arbre> = emptyList()
    private var capturedIds: Set<Long> = emptySet()
    private var pending: HuntReadout? = null
    private var startNanos = 0L
    private var lastCycle = 0L
    private var pulseStartNanos = 0L

    /**
     * Boucle infinie — à lancer dans un `LaunchedEffect` keyé sur la liste
     * (devient non-null une fois chargée) + les remarquables capturés
     * (re-scan immédiat après une capture de cible).
     */
    suspend fun run(remarquables: List<Arbre>?, capturedIds: Set<Long>) {
        if (remarquables == null) {
            readout = HuntReadout.Loading
            nearMode = false
            return
        }
        this.remarquables = remarquables
        this.capturedIds = capturedIds
        pending = null
        startNanos = 0L
        lastCycle = 0L
        pulseStartNanos = 0L
        commit(scan())
        while (true) {
            withFrameNanos { onFrame(it) }
        }
    }

    private fun scan(): HuntReadout =
        computeReadout(remarquables, capturedIds, LocationProvider.currentLocation.value)

    private fun commit(r: HuntReadout) {
        readout = r
        nearMode = updateNearMode(nearMode, r)
    }

    private fun onFrame(now: Long) {
        if (startNanos == 0L) {
            startNanos = now
            pulseStartNanos = now
        }
        val elapsedMs = (now - startNanos) / 1_000_000L
        val prevSweep = sweep
        sweep = (elapsedMs % SWEEP_DURATION_MS) * 360f / SWEEP_DURATION_MS
        val cycle = elapsedMs / SWEEP_DURATION_MS
        if (cycle != lastCycle) {
            lastCycle = cycle
            val computed = scan()
            if (readout is HuntReadout.Locked && !nearMode) {
                pending = computed   // attendra le passage de la barre sur le blip
            } else {
                commit(computed)
                pulseStartNanos = now
            }
        }
        // Commit théâtral : la barre vient de passer sur le blip affiché.
        val displayedBearing = (readout as? HuntReadout.Locked)?.bearingDeg
        val p = pending
        if (p != null && displayedBearing != null &&
            sweepCrossed(prevSweep, sweep, displayedBearing)
        ) {
            commit(p)
            pending = null
            pulseStartNanos = now
        }
        val sincePulseMs = (now - pulseStartNanos) / 1_000_000L
        pulse = (1f - sincePulseMs.toFloat() / PULSE_DURATION_MS).coerceIn(0f, 1f)
    }
}

/**
 * Distance → rayon du blip, mapping log : 25 m → 0 (centre), ~105 m → anneau 1
 * (0.33), ~450 m → anneau 2 (0.66), ≥ 2 km → bord. Les trois anneaux du radar
 * portent ainsi une échelle, et le blip converge vers le centre en approchant.
 */
internal fun distanceToRadiusFrac(distanceM: Float): Float =
    (ln(distanceM / DIRECTION_HIDE_M) / ln(RADAR_EDGE_M / DIRECTION_HIDE_M)).coerceIn(0f, 1f)

/** > 50 m → arrondi 5 m ; ≤ 50 m → arrondi 1 m. */
private fun roundByBand(m: Float): Int =
    if (m > 50f) (m / 5f).roundToInt() * 5 else m.roundToInt()

/**
 * Rend la qualification OpenData lisible : « Paysager » → « Intérêt paysager ».
 * Les qualifications déjà rédigées en phrase (« Arbre de la liberté ») sont
 * laissées telles quelles.
 */
private fun glossQualification(q: String?): String = when {
    q.isNullOrBlank() -> "Arbre remarquable"
    ' ' in q -> q
    else -> "Intérêt ${q.lowercase()}"
}

/**
 * Panneau bas persistant du mode chasse aux remarquables. Affiche en continu
 * le remarquable non découvert le plus proche, sa qualification glosée, et la
 * distance — recalculée toutes les 5 s mais affichée au passage de la barre de
 * balayage sur le blip (théâtre sonar, cf. la boucle ci-dessous). Recalcule la
 * cible à chaque tick (cible dynamique). Le radar porte un blip directionnel
 * north-up (rayon = distance, mapping log), masqué sous 25 m (pulse central,
 * hystérésis 35 m) — voir [RadarGlyph].
 */
@Composable
fun HuntPanel(
    remarquables: List<Arbre>?,
    capturedIds: Set<Long>,
    resolveName: (Arbre) -> String,
    resolveQualification: (Arbre) -> String?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val radar = remember { HuntRadarState() }
    LaunchedEffect(remarquables, capturedIds) { radar.run(remarquables, capturedIds) }
    val readout = radar.readout
    // Dérivé du readout affiché : ne change qu'au commit → position stable
    // entre deux pulses par construction, pas de tremblement inter-pulse.
    val blip = (readout as? HuntReadout.Locked)
        ?.takeUnless { radar.nearMode }
        ?.let { RadarBlip(it.bearingDeg, distanceToRadiusFrac(it.distanceM)) }

    val contentMinHeight = (LocalConfiguration.current.screenHeightDp * PANEL_HEIGHT_FRACTION).dp
    val topLabel = when (readout) {
        is HuntReadout.Locked, is HuntReadout.NoFix -> "Arbre remarquable non capturé le plus proche"
        else -> null
    }

    Surface(
        // Pas de `windowInsetsPadding` sur la Surface : son fond doit couvrir
        // jusqu'au bas de l'écran (cache le bas de la carte + l'attribution
        // MapLibre). C'est le contenu qui est insetté.
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
    ) {
        Box(modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = contentMinHeight)
                    .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 12.dp),
                // 3 enfants (label / Row radar+infos / spacer bas). SpaceBetween
                // répartit le mou ; le spacer du bas réserve la place du ✕.
                // Pas de `weight` : un enfant pondéré ferait remplir tout l'écran.
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    topLabel ?: " ",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // « N » au-dessus du cadran (pas dedans) : repère north-up.
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "N",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        RadarGlyph(
                            sweep = radar.sweep,
                            pulse = radar.pulse,
                            blip = blip,
                            nearMode = radar.nearMode,
                            modifier = Modifier.size(76.dp),
                        )
                    }
                    HuntTargetText(
                        readout = readout,
                        resolveName = resolveName,
                        resolveQualification = resolveQualification,
                        modifier = Modifier.weight(1f),
                    )
                    (readout as? HuntReadout.Locked)?.let { HuntDistanceLabel(it.distanceM) }
                }
                Spacer(modifier = Modifier.height(56.dp))
            }
            // ✕ au même endroit que le FAB ★ (bas-gauche) : on tape au même
            // pixel pour entrer et sortir du mode.
            IconButton(
                onClick = onClose,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 10.dp, bottom = 6.dp),
            ) {
                Icon(Icons.Outlined.Close, contentDescription = "Fermer la chasse")
            }
        }
    }
}

@Composable
private fun HuntTargetText(
    readout: HuntReadout,
    resolveName: (Arbre) -> String,
    resolveQualification: (Arbre) -> String?,
    modifier: Modifier = Modifier,
) {
    val obscured by rememberRadarObscureStore().obscured.collectAsState(initial = false)
    val (title, subtitle) = when (readout) {
        HuntReadout.Loading -> "Chasse aux remarquables" to "Chargement…"
        HuntReadout.AllDiscovered -> "Tous les remarquables découverts" to "Bravo 🎉"
        is HuntReadout.NoFix ->
            (if (obscured) "???" else resolveName(readout.target)) to "Localisation en cours…"
        is HuntReadout.Locked ->
            if (obscured) "???" to "???"
            else resolveName(readout.target) to glossQualification(resolveQualification(readout.target))
    }
    Column(modifier = modifier) {
        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HuntDistanceLabel(distanceM: Float) {
    val accent = MaterialTheme.arbresColors.remarquableOrange
    if (distanceM < NEAR_THRESHOLD_M) {
        Text(
            "tout près !",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = accent,
        )
        return
    }
    val animated by animateIntAsState(
        targetValue = roundByBand(distanceM),
        animationSpec = tween(250),
        label = "huntDistance",
    )
    Row(verticalAlignment = Alignment.Bottom) {
        Text("$animated", style = MaterialTheme.typography.displaySmall)
        Text(
            " m",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp),
        )
    }
}

/**
 * Petit radar : anneaux concentriques + ligne de balayage qui tourne (0° =
 * midi, sens horaire) avec une trainée en comète derrière. Au moment du refresh
 * (`pulse` passe à 1 puis décroît), la ligne flashe (épaissit + halo) et un
 * anneau « ping » jaillit du centre.
 *
 * Le [blip] cible est north-up (0° = midi = nord, comme la carte) et dessiné
 * dans une couche statique SOUS la ligne de balayage : c'est le balayage qui le
 * « révèle » (alpha plein au passage, décroît sur le reste du tour). En
 * [nearMode] (cible < 25 m, bearing GPS = bruit), pas de blip : le point
 * central gonfle et bat au rythme du ping.
 *
 * Rendu « bête » : `sweep` et `pulse` viennent de la boucle `withFrameNanos` de
 * [HuntPanel] (insensible à l'échelle d'animation système, contrairement aux API
 * d'animation Compose qui se figent quand elle vaut 0).
 */
@Composable
private fun RadarGlyph(
    sweep: Float,
    pulse: Float,
    blip: RadarBlip?,
    nearMode: Boolean,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.arbresColors.remarquableOrange
    val store = rememberRadarObscureStore()
    val scope = rememberCoroutineScope()
    // Triple-tap easter egg : 3 taps avec < 600 ms entre chaque → bascule
    // l'obscurcissement. `pointerInput` posé sur le `Box` parent (statique)
    // plutôt que sur le Canvas pour que le hit-test ne tourne pas avec le
    // balayage (`graphicsLayer { rotationZ = sweep }` côté Canvas).
    var tapState by remember { mutableStateOf(0 to 0L) }
    Box(
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures(onTap = {
                val now = SystemClock.uptimeMillis()
                val (count, lastMs) = tapState
                val nextCount = if (now - lastMs > 600L) 1 else count + 1
                if (nextCount >= 3) {
                    scope.launch { store.toggle() }
                    tapState = 0 to 0L
                } else {
                    tapState = nextCount to now
                }
            })
        },
    ) {
        // Couche statique (référentiel north-up) : anneaux, centre, ping, blip.
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRadarBase(accent, pulse, nearMode)
            blip?.let { drawBlip(accent, it, sweep) }
        }
        // Couche rotative : ligne + comète + flash, au-dessus du blip.
        Canvas(modifier = Modifier.fillMaxSize().graphicsLayer { rotationZ = sweep }) {
            drawSweepLine(accent, pulse)
        }
    }
}

/** Anneaux concentriques, point central (gonflé et battant en mode proche), anneau « ping ». */
private fun DrawScope.drawRadarBase(accent: Color, pulse: Float, nearMode: Boolean) {
    val r = size.minDimension / 2f
    listOf(1f to 0.45f, 0.66f to 0.3f, 0.33f to 0.2f).forEach { (frac, alpha) ->
        drawCircle(accent.copy(alpha = alpha), r * frac, center, style = Stroke(1.2.dp.toPx()))
    }
    val centerR = if (nearMode) 4.dp.toPx() * (1f + pulse * 0.6f) else 2.dp.toPx()
    drawCircle(accent, centerR, center)
    // Anneau « ping » : jaillit du centre (rayon r·(1−pulse)) en s'estompant.
    if (pulse > 0.01f) {
        drawCircle(
            accent.copy(alpha = pulse * 0.5f),
            radius = r * (1f - pulse),
            center = center,
            style = Stroke(1.4.dp.toPx()),
        )
    }
}

/**
 * Blip cible à l'angle [RadarBlip.bearingDeg] (north-up) et au rayon
 * [RadarBlip.radiusFrac]. Révélation sonar : alpha plein juste après le passage
 * du balayage, décroissant vers un plancher 0.3 jusqu'au tour suivant.
 */
private fun DrawScope.drawBlip(accent: Color, blip: RadarBlip, sweep: Float) {
    val r = size.minDimension / 2f
    val rad = Math.toRadians(blip.bearingDeg.toDouble())
    val pos = Offset(
        center.x + (sin(rad) * r * blip.radiusFrac).toFloat(),
        center.y - (cos(rad) * r * blip.radiusFrac).toFloat(),
    )
    val sinceSweepFrac = (sweep - blip.bearingDeg).mod(360f) / 360f
    drawCircle(accent.copy(alpha = 1f - 0.7f * sinceSweepFrac), 3.5.dp.toPx(), pos)
}

/** Ligne de balayage (pointe midi à rotation 0) + comète derrière + flash au pulse. */
private fun DrawScope.drawSweepLine(accent: Color, pulse: Float) {
    val r = size.minDimension / 2f
    val baseLineW = 1.6.dp.toPx()
    // Comète : pie-slice de 70° côté anti-horaire de la ligne (= derrière,
    // puisque la rotation est horaire).
    drawArc(
        color = accent.copy(alpha = 0.16f),
        startAngle = -160f,
        sweepAngle = 70f,
        useCenter = true,
        topLeft = Offset(center.x - r, center.y - r),
        size = Size(r * 2f, r * 2f),
    )
    // Flash du trait : front-loaded (pulse²) → snap épais puis ré-affine.
    val flash = pulse * pulse
    val lineW = baseLineW * (1f + flash * 2.5f)
    if (flash > 0.01f) {
        drawLine(
            accent.copy(alpha = flash * 0.35f),
            center,
            Offset(center.x, center.y - r),
            strokeWidth = lineW * 2.2f,
        )
    }
    drawLine(accent, center, Offset(center.x, center.y - r), strokeWidth = lineW)
}
