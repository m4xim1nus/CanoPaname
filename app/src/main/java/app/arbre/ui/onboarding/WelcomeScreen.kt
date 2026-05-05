package app.arbre.ui.onboarding

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.arbre.R
import app.arbre.ui.theme.arbresColors
import app.arbre.util.LocationProvider

/**
 * Écran de bienvenue + explication minimale du jeu, montré une seule fois
 * au premier lancement (cf. `OnboardingStore.onboardingDone`). Aussi
 * accessible en mode replay depuis le Profil (« Comment jouer »).
 *
 * Au tap « Commencer » :
 * - demande la permission `ACCESS_FINE_LOCATION` avec rationale juste
 *   au-dessus dans la dernière bullet ;
 * - quel que soit le résultat (granted/denied), `onContinue` est appelé.
 *   Refus → carte centrée sur Paris, retentable plus tard via le FAB GPS.
 */
@Composable
fun WelcomeScreen(
    onContinue: () -> Unit,
    readOnly: Boolean = false,
    onClose: (() -> Unit)? = null,
) {
    val ctx = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        // L'onboarding est validé que la permission soit accordée ou non,
        // mais on amorce le `LocationProvider` immédiatement si grant : sinon
        // le 1er sheet de capture (avant que `MapScreen.DisposableEffect`
        // n'ait re-amorcé) trouve `currentLocation == null` et affiche
        // « Activer le GPS » pendant le TTFF (Phase 10.5 sous-groupe F).
        if (granted) LocationProvider.start(ctx)
        onContinue()
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.Top),
        ) {
            Spacer24()

            HeroLogo()

            Text(
                stringResource(R.string.welcome_title),
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            Text(
                stringResource(R.string.welcome_tagline),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )

            WelcomeAnimation()

            Text(
                stringResource(R.string.welcome_caption),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )

            Text(
                stringResource(R.string.welcome_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp),
            )

            Spacer24()

            Button(
                onClick = {
                    if (readOnly) {
                        onClose?.invoke()
                    } else {
                        val granted = ContextCompat.checkSelfPermission(
                            ctx, Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) {
                            onContinue()
                        } else {
                            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
            ) {
                Text(
                    stringResource(
                        if (readOnly) R.string.welcome_cta_close else R.string.welcome_cta_start
                    )
                )
            }
        }
    }
}

@Composable
private fun HeroLogo() {
    Box(
        modifier = Modifier
            .size(96.dp)
            .clip(CircleShape)
            .background(MaterialTheme.arbresColors.feuilleSombre),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_arbre_canonical),
            contentDescription = null,
            tint = MaterialTheme.arbresColors.remarquableOrange,
            modifier = Modifier.size(56.dp),
        )
    }
}

/**
 * Boucle Compose pure qui montre la mécanique : silhouette grise petite
 * (« pas encore capturé ») → silhouette verte pleine grandeur (« capturé »),
 * avec interpolation continue. 4 s par cycle, repeat infini en reverse.
 */
@Composable
private fun WelcomeAnimation() {
    val infinite = rememberInfiniteTransition(label = "welcome")
    val progress by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "welcomeProgress",
    )
    // 0 = silhouette grise petite (pas encore capturé), 1 = vert plein scale 1.0 (capturé)
    val grey = MaterialTheme.arbresColors.ecorce.copy(alpha = 0.5f)
    val green = MaterialTheme.arbresColors.feuilleSombre
    val tint = lerp(grey, green, progress)
    val scale = 0.85f + 0.15f * progress
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_arbre_canonical),
            contentDescription = null,
            colorFilter = ColorFilter.tint(tint),
            modifier = Modifier
                .size(180.dp)
                .scale(scale),
        )
    }
}

@Composable
private fun Spacer24() {
    Box(modifier = Modifier.size(0.dp, 8.dp))
}
