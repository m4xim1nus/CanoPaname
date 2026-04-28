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
import app.arbre.data.SpeciesIndex
import app.arbre.util.LocationProvider
import java.io.File
import java.util.UUID
import kotlinx.coroutines.launch

private const val MAX_GPS_AGE_MS = 30_000L
private const val MAX_DISTANCE_M = 30f

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
): (Arbre) -> Unit {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val pendingArbre = remember { mutableStateOf<Arbre?>(null) }

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
            captureRepo.insertCapture(
                arbreId = pending.arbreId,
                speciesIndex = pending.speciesIndex,
                remarquable = pending.remarquable,
                latitudeDevice = pending.captureLatitude,
                longitudeDevice = pending.captureLongitude,
                photoPath = pending.photoPath,
                timestamp = pending.captureTimestamp,
            )
            onCaptured()
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

    val loc = LocationProvider.currentOrLastKnown(ctx)
    if (loc == null) {
        snackbar.showSnackbar("Position indisponible (active le GPS)")
        return
    }
    val ageMs = System.currentTimeMillis() - loc.time
    if (ageMs > MAX_GPS_AGE_MS) {
        snackbar.showSnackbar("Position trop ancienne, attends un nouveau fix")
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
