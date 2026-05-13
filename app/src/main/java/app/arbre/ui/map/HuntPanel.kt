package app.arbre.ui.map

import android.location.Location
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.arbre.data.Arbre
import app.arbre.ui.theme.arbresColors
import app.arbre.util.LocationProvider
import kotlin.math.roundToInt

/**
 * Durée d'une révolution du radar. C'est aussi la cadence de refresh de la
 * distance : le wrap du balayage (passage à midi) déclenche la nouvelle lecture
 * ET le pulse — une seule horloge, pas deux timers qui dérivent.
 */
private const val SWEEP_DURATION_MS = 5_000L
/** Durée du « ping » visuel après chaque refresh. */
private const val PULSE_DURATION_MS = 260f
private const val NEAR_THRESHOLD_M = 10f
private const val PANEL_HEIGHT_FRACTION = 0.28f

private sealed interface HuntReadout {
    /** Liste des remarquables pas encore chargée. */
    data object Loading : HuntReadout
    /** Plus aucun remarquable à découvrir. */
    data object AllDiscovered : HuntReadout
    /** On a une cible candidate mais pas de fix GPS exploitable. */
    data class NoFix(val target: Arbre) : HuntReadout
    /** Cible verrouillée + distance en mètres. */
    data class Locked(val target: Arbre, val distanceM: Float) : HuntReadout
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
    val results = FloatArray(1)
    val (nearest, dist) = candidates.map { rem ->
        Location.distanceBetween(loc.latitude, loc.longitude, rem.latitude, rem.longitude, results)
        rem to results[0]
    }.minBy { it.second }
    return HuntReadout.Locked(nearest, dist)
}

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
 * Panneau bas persistant du mode chasse aux remarquables (sprint S1 cycle
 * Progression). Affiche en continu le remarquable non découvert le plus proche,
 * sa qualification glosée, et la distance — rafraîchie toutes les 5 s, en phase
 * avec un balayage radar. Recalcule la cible à chaque tick (cible dynamique).
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
    var readout by remember { mutableStateOf<HuntReadout>(HuntReadout.Loading) }
    var sweep by remember { mutableFloatStateOf(0f) }   // 0..360, 0 = midi
    var pulse by remember { mutableFloatStateOf(0f) }   // 1 au wrap, décroît vers 0
    // Une seule boucle `withFrameNanos` (insensible à l'échelle d'animation
    // système — cf. RadarGlyph) : pilote l'angle du balayage, et au wrap (tour
    // complet = 5 s) recalcule la cible + déclenche le pulse. Keyé sur la liste
    // (devient non-null une fois chargée) + les remarquables capturés (re-scan
    // immédiat après une capture de cible).
    LaunchedEffect(remarquables, capturedIds) {
        if (remarquables == null) {
            readout = HuntReadout.Loading
            return@LaunchedEffect
        }
        readout = computeReadout(remarquables, capturedIds, LocationProvider.currentLocation.value)
        var startNanos = 0L
        var lastCycle = 0L
        var pulseStartNanos = 0L
        while (true) {
            withFrameNanos { now ->
                if (startNanos == 0L) {
                    startNanos = now
                    pulseStartNanos = now
                }
                val elapsedMs = (now - startNanos) / 1_000_000L
                sweep = (elapsedMs % SWEEP_DURATION_MS) * 360f / SWEEP_DURATION_MS
                val cycle = elapsedMs / SWEEP_DURATION_MS
                if (cycle != lastCycle) {
                    lastCycle = cycle
                    readout = computeReadout(remarquables, capturedIds, LocationProvider.currentLocation.value)
                    pulseStartNanos = now
                }
                val sincePulseMs = (now - pulseStartNanos) / 1_000_000L
                pulse = (1f - sincePulseMs.toFloat() / PULSE_DURATION_MS).coerceIn(0f, 1f)
            }
        }
    }

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
                    RadarGlyph(sweep = sweep, pulse = pulse, modifier = Modifier.size(76.dp))
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
    val (title, subtitle) = when (readout) {
        HuntReadout.Loading -> "Chasse aux remarquables" to "Chargement…"
        HuntReadout.AllDiscovered -> "Tous les remarquables découverts" to "Bravo 🎉"
        is HuntReadout.NoFix -> resolveName(readout.target) to "Localisation en cours…"
        is HuntReadout.Locked ->
            resolveName(readout.target) to glossQualification(resolveQualification(readout.target))
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
 * Rendu « bête » : `sweep` et `pulse` viennent de la boucle `withFrameNanos` de
 * [HuntPanel] (insensible à l'échelle d'animation système, contrairement aux API
 * d'animation Compose qui se figent quand elle vaut 0).
 */
@Composable
private fun RadarGlyph(sweep: Float, pulse: Float, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.arbresColors.remarquableOrange
    Canvas(modifier = modifier.graphicsLayer { rotationZ = sweep }) {
        val r = size.minDimension / 2f
        val baseLineW = 1.6.dp.toPx()
        listOf(1f to 0.45f, 0.66f to 0.3f, 0.33f to 0.2f).forEach { (frac, alpha) ->
            drawCircle(accent.copy(alpha = alpha), r * frac, center, style = Stroke(1.2.dp.toPx()))
        }
        drawCircle(accent, 2.dp.toPx(), center)
        // Comète : pie-slice de 70° côté anti-horaire de la ligne (= derrière,
        // puisque la rotation est horaire). La ligne pointe vers le haut = midi
        // à rotationZ = 0.
        drawArc(
            color = accent.copy(alpha = 0.16f),
            startAngle = -160f,
            sweepAngle = 70f,
            useCenter = true,
            topLeft = Offset(center.x - r, center.y - r),
            size = Size(r * 2f, r * 2f),
        )
        // Anneau « ping » : jaillit du centre (rayon r·(1−pulse)) en s'estompant.
        if (pulse > 0.01f) {
            drawCircle(
                accent.copy(alpha = pulse * 0.5f),
                radius = r * (1f - pulse),
                center = center,
                style = Stroke(1.4.dp.toPx()),
            )
        }
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
}
