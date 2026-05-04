package app.arbre.ui.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import app.arbre.data.Arbre
import app.arbre.data.CaptureRepository
import app.arbre.data.Season
import app.arbre.data.SpeciesIndex
import app.arbre.util.LocationProvider
import app.arbre.util.ageMs
import app.arbre.util.rememberCaptureHaptic
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal const val MAX_GPS_AGE_MS = 30_000L
internal const val MAX_DISTANCE_M = 30f

/**
 * État du bouton « Capturer » du sheet, calculé une fois à l'ouverture pour ne
 * pas laisser un bouton cliquable qui ne fait rien (cf. Sprint D). Les mêmes
 * seuils sont vérifiés à nouveau dans [runCapture] comme garde-fou — si la
 * position devenue stale entre l'ouverture du sheet et le tap, on renvoie
 * simplement l'utilisateur via Snackbar.
 */
sealed class CaptureAvailability {
    object Ready : CaptureAvailability()
    object NoGps : CaptureAvailability()
    data class TooFar(val meters: Int) : CaptureAvailability()
    /** Saison sélectionnée ≠ saison vive : capture désactivée (cf. Sprint I). */
    object Archived : CaptureAvailability()
}

/**
 * Lecture pure du `currentLocation` — pas de fallback bloquant. Si notre
 * listener n'a pas encore reçu de fix (cold start post-onboarding,
 * permission tout juste accordée), retourne `NoGps` instantanément. Le
 * `LaunchedEffect(openedArbre.id, currentLocation)` côté `MapScreen` recompute
 * dès que `LocationProvider.currentLocation` émet, donc le bouton bascule de
 * « Active le GPS » à « Capturer » dans la seconde. Cf. Phase 10.5 sous-groupe
 * F : l'ancien fallback `currentOrLastKnown` faisait un `getCurrentLocation`
 * synchrone qui pouvait suspendre jusqu'à 10 s, gardant le bouton « Capturer »
 * non-cliquable pendant tout ce délai.
 */
fun captureAvailability(arbre: Arbre): CaptureAvailability {
    val loc = LocationProvider.currentLocation.value
        ?.takeIf { it.ageMs() <= MAX_GPS_AGE_MS }
        ?: return CaptureAvailability.NoGps
    val results = FloatArray(1)
    Location.distanceBetween(
        loc.latitude, loc.longitude,
        arbre.latitude, arbre.longitude,
        results,
    )
    val distance = results[0]
    return if (distance > MAX_DISTANCE_M) {
        CaptureAvailability.TooFar(distance.toInt())
    } else {
        CaptureAvailability.Ready
    }
}

/**
 * Pipeline de capture : demande la permission caméra si besoin, lit le GPS
 * frais, vérifie la proximité < 30 m, génère un fichier photo via FileProvider,
 * sauve l'état pendant en `SavedStateHandle`, lance l'intent caméra système.
 *
 * Au retour de l'intent (qui peut survenir après un process death), récupère
 * l'état pendant, contrôle que le fichier a bien été écrit (> 0 octet — certains
 * camera-apps OEM tirent un fichier vide même quand l'utilisateur a pris la
 * photo), puis INSERT la `Capture` en Room. Le Flow `capturedSpeciesIndices`
 * propage la bascule grise → verte sur la layer MapLibre.
 *
 * @return un callback à brancher sur le bouton « Capturer » du sheet.
 */
@Composable
fun rememberCaptureController(
    viewModel: MapViewModel,
    captureRepo: CaptureRepository,
    speciesIndex: SpeciesIndex,
    snackbar: SnackbarHostState,
    onCaptured: () -> Unit = {},
    onFirstSpeciesCapture: (Int) -> Unit = {},
): (Arbre) -> Unit {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val pendingArbre = remember { mutableStateOf<Arbre?>(null) }
    val captureHaptic = rememberCaptureHaptic()

    val takePictureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        scope.launch {
            val pending = viewModel.consumePending() ?: return@launch
            val file = File(pending.photoPath)
            if (!success) {
                file.delete()
                return@launch
            }
            if (file.length() == 0L) {
                file.delete()
                snackbar.showSnackbar("Photo vide — caméra a échoué")
                return@launch
            }
            // Snapshot AVANT insert : sinon on lit le set qui contient déjà
            // notre nouvelle espèce et on rate la transition « 1re capture ».
            // Scopé sur la saison de la capture pour le catalogue saisonnier
            // (cf. Sprint I) : la même espèce capturée 2 saisons compte 2 fois.
            val captureSeason = Season.fromTimestamp(pending.captureTimestamp)
            val previouslyCaptured = captureRepo.capturedSpeciesIndices(captureSeason).first()
            captureRepo.insertCapture(
                arbreId = pending.arbreId,
                speciesIndex = pending.speciesIndex,
                remarquable = pending.remarquable,
                latitudeDevice = pending.captureLatitude,
                longitudeDevice = pending.captureLongitude,
                photoPath = pending.photoPath,
                timestamp = pending.captureTimestamp,
            )
            captureHaptic()
            onCaptured()
            // Effet « waouh » : uniquement si l'espèce vient juste d'être
            // débloquée. On exclut les remarquables (le speciesIndex y est
            // technique mais le set capturedSpeciesIndices ne les compte pas
            // — cf. requête DAO `WHERE remarquable = 0`). Pour un remarquable
            // dont l'espèce était inconnue, l'utilisateur verra la fiche
            // standard et débloquera l'espèce à la prochaine capture normale.
            if (!pending.remarquable && pending.speciesIndex !in previouslyCaptured) {
                onFirstSpeciesCapture(pending.speciesIndex)
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val arbre = pendingArbre.value
        pendingArbre.value = null
        if (granted && arbre != null) {
            scope.launch {
                runCapture(ctx, arbre, viewModel, speciesIndex, snackbar, takePictureLauncher)
            }
        } else if (!granted) {
            scope.launch { snackbar.showSnackbar("Permission caméra requise") }
        }
    }

    return remember<(Arbre) -> Unit>(speciesIndex) {
        { arbre ->
            val perm = ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA)
            if (perm == PackageManager.PERMISSION_GRANTED) {
                scope.launch {
                    runCapture(ctx, arbre, viewModel, speciesIndex, snackbar, takePictureLauncher)
                }
            } else {
                pendingArbre.value = arbre
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }
}

private suspend fun runCapture(
    ctx: Context,
    arbre: Arbre,
    viewModel: MapViewModel,
    speciesIndex: SpeciesIndex,
    snackbar: SnackbarHostState,
    launcher: ActivityResultLauncher<android.net.Uri>,
) {
    val sk = speciesIndex.indexOf(arbre)
    if (sk == null) {
        snackbar.showSnackbar("Espèce inconnue, capture impossible")
        return
    }

    // Lecture pure du flow temps réel — cf. `captureAvailability`. Si on
    // arrive ici sans fix, c'est qu'un état de course très court a permis
    // au bouton d'être tappé avant la propagation. La snackbar reste un
    // garde-fou théorique.
    val loc = LocationProvider.currentLocation.value?.takeIf { it.ageMs() <= MAX_GPS_AGE_MS }
    if (loc == null) {
        snackbar.showSnackbar("Position indisponible (active le GPS)")
        return
    }
    val results = FloatArray(1)
    Location.distanceBetween(
        loc.latitude, loc.longitude,
        arbre.latitude, arbre.longitude,
        results,
    )
    val distance = results[0]
    if (distance > MAX_DISTANCE_M) {
        snackbar.showSnackbar("Trop loin de l'arbre (${distance.toInt()} m, max ${MAX_DISTANCE_M.toInt()})")
        return
    }

    val timestamp = System.currentTimeMillis()
    val capturesDir = File(ctx.getExternalFilesDir(null), "captures").apply { mkdirs() }
    val photoFile = File(capturesDir, "${UUID.randomUUID()}.jpg")
    photoFile.createNewFile()
    val photoUri = FileProvider.getUriForFile(
        ctx,
        "${ctx.packageName}.fileprovider",
        photoFile,
    )

    viewModel.savePending(
        PendingCapture(
            arbreId = arbre.id,
            speciesIndex = sk,
            remarquable = arbre.remarquable,
            photoPath = photoFile.absolutePath,
            captureLatitude = loc.latitude,
            captureLongitude = loc.longitude,
            captureTimestamp = timestamp,
        )
    )

    launcher.launch(photoUri)
}
