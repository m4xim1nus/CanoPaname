package app.arbre.ui.onboarding

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Park
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.arbre.R
import app.arbre.ui.theme.arbresColors
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition

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
    ) { _ ->
        // L'onboarding est validé que la permission soit accordée ou non.
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
            imageVector = Icons.Outlined.Park,
            contentDescription = null,
            tint = MaterialTheme.arbresColors.or,
            modifier = Modifier.size(56.dp),
        )
    }
}

/**
 * Boucle Lottie qui montre la mécanique : silhouette grise → coloration verte
 * → reset (4 s, repeat infini). Le composable charge l'asset et masque
 * gracieusement si la composition échoue (placeholder vide même hauteur).
 */
@Composable
private fun WelcomeAnimation() {
    val composition by rememberLottieComposition(
        LottieCompositionSpec.Asset("animations/welcome_loop.json")
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (composition != null) {
            LottieAnimation(
                composition = composition,
                iterations = LottieConstants.IterateForever,
                modifier = Modifier.size(220.dp),
            )
        }
    }
}

@Composable
private fun Spacer24() {
    Box(modifier = Modifier.size(0.dp, 8.dp))
}
