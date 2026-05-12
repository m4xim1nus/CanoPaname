package app.arbre.ui.onboarding

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
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
import app.arbre.ui.common.rememberFramePingPong
import app.arbre.ui.theme.arbresColors
import app.arbre.util.LocationProvider

/**
 * Bienvenue + explication minimale du jeu, montré une fois au 1er lancement
 * (`OnboardingStore.onboardingDone`). Replay possible depuis le Profil.
 *
 * Tap « Commencer » : demande `ACCESS_FINE_LOCATION` puis appelle
 * `onContinue` quel que soit le résultat — un refus laisse l'utilisateur
 * sur la carte centrée Paris, retentable via le FAB GPS.
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
        // Amorce `LocationProvider` immédiatement sur grant — sinon le 1er
        // sheet de capture (avant que `MapScreen.DisposableEffect` ait
        // re-amorcé) verrait `currentLocation == null` et afficherait
        // « Activer le GPS » pendant le TTFF.
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

/** Animation pédagogique : silhouette grise (« non capturé ») ↔ silhouette
 *  verte pleine taille (« capturé »), interpolation continue 4 s reverse.
 */
@Composable
private fun WelcomeAnimation() {
    // Frame-clock (cf. `ui/common/FrameClock.kt`) plutôt qu'`infiniteRepeatable` : la respiration
    // gris↔vert doit jouer même si l'échelle d'animation système est à 0. Période = aller-retour
    // complet (= 2 × les 4 s d'un leg de l'ancien `tween(... , Reverse)`).
    val progress by rememberFramePingPong(periodMs = 8_000, easing = FastOutSlowInEasing)
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
