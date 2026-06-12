package app.arbre.ui.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.location.Location
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import app.arbre.data.Arbre
import app.arbre.data.CaptureRepository
import app.arbre.data.Season
import app.arbre.data.SpeciesIndex
import app.arbre.ui.common.showSnackbarFor
import app.arbre.util.LocationProvider
import app.arbre.util.ageMs
import app.arbre.util.rememberCaptureHaptic
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal const val MAX_GPS_AGE_MS = 30_000L
internal const val MAX_DISTANCE_M = 30f

// Une photo brute 12 MP / quality ~95 d'OEM pèse ~10 MB ; on la ramène à
// ~400-700 KB (×15-25) sans dégradation visible sur vignette ou lightbox HD.
private const val TARGET_LONG_EDGE = 1600
private const val JPEG_QUALITY = 85

/**
 * État du bouton « Capturer » du sheet. Les mêmes seuils sont revalidés dans
 * [runCapture] comme garde-fou : si la position est devenue stale entre
 * l'ouverture du sheet et le tap, on retombe sur une snackbar.
 */
sealed class CaptureAvailability {
    object Ready : CaptureAvailability()
    object NoGps : CaptureAvailability()
    data class TooFar(val meters: Int) : CaptureAvailability()
}

/**
 * Lecture pure du `currentLocation` — pas de fallback bloquant. Si notre
 * listener n'a pas encore reçu de fix (cold start post-onboarding, permission
 * tout juste accordée), retourne `NoGps` instantanément. Le
 * `LaunchedEffect(openedArbre.id, currentLocation)` côté `MapScreen` recompute
 * dès que `LocationProvider.currentLocation` émet, donc le bouton bascule de
 * « Active le GPS » à « Capturer » dans la seconde. Tout fallback synchrone
 * (`getCurrentLocation`) gèlerait le bouton jusqu'à 10 s post-permission.
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
 * Callbacks du pipeline de capture, groupés façon `SpeciesActions` /
 * `GenreActions`.
 */
data class CaptureCallbacks(
    val onCaptured: () -> Unit = {},
    /** INSERT fait et `willCelebrate` : naviguer vers la fiche espèce. */
    val onFirstSpeciesCapture: (Int) -> Unit = {},
    /**
     * Appelé SYNCHRONIQUEMENT au retour caméra si `success && willCelebrate`
     * — avant la 1re frame de retour : lever le voile de transition et fermer
     * la sheet sans animation (la carte n'est jamais rendue).
     */
    val onCelebrationTransitionStart: (Int) -> Unit = {},
    /** Échec après levée du voile (fichier vide, compression) — le retirer. */
    val onCelebrationTransitionAbort: () -> Unit = {},
)

/**
 * Pipeline de capture : permission caméra → GPS frais → proximité < 30 m →
 * fichier photo via FileProvider → état pendant en `SavedStateHandle` → intent
 * caméra système. Au retour (qui peut survenir après un process death),
 * relit l'état pendant, vérifie que le fichier > 0 octet (certains camera-apps
 * OEM écrivent un fichier vide même après prise réussie), puis INSERT la
 * `Capture`. Le Flow `capturedSpeciesIndices` propage gris → vert sur la carte.
 *
 * @return un callback à brancher sur le bouton « Capturer » du sheet.
 */
@Composable
fun rememberCaptureController(
    viewModel: MapViewModel,
    captureRepo: CaptureRepository,
    speciesIndex: SpeciesIndex,
    snackbar: SnackbarHostState,
    callbacks: CaptureCallbacks = CaptureCallbacks(),
): (Arbre) -> Unit {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val pendingArbre = remember { mutableStateOf<Arbre?>(null) }
    val captureHaptic = rememberCaptureHaptic()
    val haptic = LocalHapticFeedback.current

    val takePictureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        // SYNCHRONE — ce callback est dispatché avant `onResume`, donc avant
        // la 1re frame de retour de l'app caméra : consommer le pending et
        // lever le voile ICI garantit que la carte n'est jamais rendue entre
        // la validation photo et la fiche espèce. Rien de bloquant avant le
        // `scope.launch`.
        val pending = viewModel.consumePending()
            ?: return@rememberLauncherForActivityResult
        // Pas de voile sur annulation (`success == false`) : on reste sur la
        // carte, la snackbar du launch ci-dessous fait le feedback.
        if (success && pending.willCelebrate) {
            callbacks.onCelebrationTransitionStart(pending.speciesIndex)
        }
        scope.launch {
            val file = File(File(ctx.getExternalFilesDir(null), "captures"), pending.photoBasename)
            if (!success) {
                file.delete()
                // Tic discret + retour textuel : annuler depuis l'app caméra
                // n'avait aucun feedback in-app. TextHandleMove est l'haptic
                // « light » Material — alignement avec le geste « j'annule ».
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                showSnackbarFor(snackbar, "Capture annulée", 2500)
                return@launch
            }
            if (file.length() == 0L) {
                file.delete()
                if (pending.willCelebrate) callbacks.onCelebrationTransitionAbort()
                snackbar.showSnackbar("Photo vide — caméra a échoué")
                return@launch
            }
            // CPU-bound (~200-500 ms decode/scale/encode), donc IO pour ne pas
            // bloquer le main thread. Sans recompression chaque capture pèse
            // ~10 MB (résolution native OEM).
            val compressed = withContext(Dispatchers.IO) { compressCapture(file) }
            if (!compressed) {
                file.delete()
                if (pending.willCelebrate) callbacks.onCelebrationTransitionAbort()
                snackbar.showSnackbar("Erreur traitement photo")
                return@launch
            }
            captureRepo.insertCapture(
                arbreId = pending.arbreId,
                speciesIndex = pending.speciesIndex,
                remarquable = pending.remarquable,
                latitudeDevice = pending.captureLatitude,
                longitudeDevice = pending.captureLongitude,
                photoPath = pending.photoBasename,
                timestamp = pending.captureTimestamp,
            )
            // Note : `captureHaptic()` a été déplacé en amont (juste avant
            // `launcher.launch`) — le retour kinesthésique mérite d'arriver au
            // tap sur « Capturer », pas à la fin du pipeline d'INSERT.
            callbacks.onCaptured()
            // Même valeur que le voile (figée avant le launch caméra, cf.
            // `prepareCapture`) — la nav et le voile ne divergent jamais.
            if (pending.willCelebrate) {
                callbacks.onFirstSpeciesCapture(pending.speciesIndex)
            }
        }
    }

    // Préparation (checks, fichier, pending) puis déclenchement : le tic
    // haptique arrive au moment où on lance l'intent caméra (après tous les
    // checks) — auparavant joué post-INSERT, ce qui plaçait le retour
    // kinesthésique ~3 s après le geste.
    suspend fun startCapture(arbre: Arbre) {
        val photoUri = prepareCapture(ctx, arbre, viewModel, captureRepo, speciesIndex, snackbar)
            ?: return
        captureHaptic()
        takePictureLauncher.launch(photoUri)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val arbre = pendingArbre.value
        pendingArbre.value = null
        if (granted && arbre != null) {
            scope.launch { startCapture(arbre) }
        } else if (!granted) {
            scope.launch { snackbar.showSnackbar("Permission caméra requise") }
        }
    }

    return remember<(Arbre) -> Unit>(speciesIndex) {
        { arbre ->
            val perm = ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA)
            if (perm == PackageManager.PERMISSION_GRANTED) {
                scope.launch { startCapture(arbre) }
            } else {
                pendingArbre.value = arbre
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }
}

/**
 * Checks (espèce connue, GPS frais, proximité) + fichier photo + état pendant.
 * Retourne l'URI à passer à l'intent caméra, ou `null` si un check a échoué
 * (la snackbar a déjà fait le feedback) — le déclenchement (haptic + launch)
 * appartient à l'appelant.
 */
private suspend fun prepareCapture(
    ctx: Context,
    arbre: Arbre,
    viewModel: MapViewModel,
    captureRepo: CaptureRepository,
    speciesIndex: SpeciesIndex,
    snackbar: SnackbarHostState,
): android.net.Uri? {
    val sk = speciesIndex.indexOf(arbre)
    if (sk == null) {
        snackbar.showSnackbar("Espèce inconnue, capture impossible")
        return null
    }

    // Garde-fou : si une race condition entre la propagation de
    // `captureAvailability` et le tap a laissé passer un état stale, on
    // retombe sur la snackbar plutôt que d'INSERT une capture sans GPS.
    val loc = LocationProvider.currentLocation.value?.takeIf { it.ageMs() <= MAX_GPS_AGE_MS }
    if (loc == null) {
        snackbar.showSnackbar("Position indisponible (active le GPS)")
        return null
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
        return null
    }

    val timestamp = System.currentTimeMillis()
    // Décision « célébration » (voile de transition + nav fiche espèce) figée
    // AVANT le launch caméra : le snapshot précède strictement l'insert —
    // sinon le set contiendrait déjà la nouvelle espèce et on raterait la
    // transition « 1re capture » — et rien ne peut insérer une capture pendant
    // l'intent caméra (modal, single-player, l'import backup vit sur Profil).
    // Scopé sur la saison de la capture : la même espèce capturée 2 saisons
    // compte 2 fois. Les remarquables sont exclus : `capturedSpeciesIndices`
    // filtre déjà `WHERE remarquable = 0`, donc on ne ferait pas la
    // transition ; l'utilisateur débloquera l'espèce à la prochaine capture
    // normale. Coût : une query Room (~ms) avant l'ouverture de la caméra.
    val captureSeason = Season.fromTimestamp(timestamp)
    val willCelebrate = !arbre.remarquable &&
        sk !in captureRepo.capturedSpeciesIndices(captureSeason).first()
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
            photoBasename = photoFile.name,
            captureLatitude = loc.latitude,
            captureLongitude = loc.longitude,
            captureTimestamp = timestamp,
            willCelebrate = willCelebrate,
        )
    )

    return photoUri
}

/**
 * Décode, redimensionne et recompresse le JPEG écrit par la caméra OEM ;
 * overwrite le fichier d'origine. Retourne `false` si lecture impossible
 * (HEIC sur API < 28, JPEG corrompu, OOM) — l'appelant abort l'INSERT Room.
 *
 * Pipeline : `inJustDecodeBounds` pour les dims sans charger le bitmap,
 * `inSampleSize` (puissance de 2) à ~2× la cible (mitige OOM sur Pro mode
 * 50 MP), resize fin via `createScaledBitmap`, rotation EXIF pixel-side puis
 * EXIF normalisé (sinon les viewers pivoteraient en double).
 */
private fun compressCapture(file: File): Boolean {
    val path = file.absolutePath
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        val srcW = bounds.outWidth
        val srcH = bounds.outHeight
        if (srcW <= 0 || srcH <= 0) return false

        val orientation = ExifInterface(path).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )

        // Marge ×2 pour que le resize fin ne souffre pas de la quantization.
        var sampleSize = 1
        val margin = TARGET_LONG_EDGE * 2
        while (srcW / (sampleSize * 2) >= margin && srcH / (sampleSize * 2) >= margin) {
            sampleSize *= 2
        }

        val decoded = BitmapFactory.decodeFile(
            path,
            BitmapFactory.Options().apply { inSampleSize = sampleSize },
        ) ?: return false

        val maxEdge = maxOf(decoded.width, decoded.height)
        val scaled = if (maxEdge > TARGET_LONG_EDGE) {
            val scale = TARGET_LONG_EDGE.toFloat() / maxEdge
            val newW = (decoded.width * scale).toInt().coerceAtLeast(1)
            val newH = (decoded.height * scale).toInt().coerceAtLeast(1)
            val s = Bitmap.createScaledBitmap(decoded, newW, newH, true)
            if (s !== decoded) decoded.recycle()
            s
        } else {
            decoded
        }

        val rotated = applyExifRotation(scaled, orientation)
        if (rotated !== scaled) scaled.recycle()

        FileOutputStream(file).use { fos ->
            rotated.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, fos)
        }
        rotated.recycle()
        stripSensitiveExif(file)
        true
    } catch (t: Throwable) {
        Log.w("CaptureLauncher", "compressCapture failed for $path", t)
        false
    }
}

// `Bitmap.compress(JPEG)` ne ré-injecte aucun EXIF, mais on strip
// explicitement pour garantir le contrat « ni GPS ni signature matériel »
// si le pipeline d'encodage change un jour.
private fun stripSensitiveExif(file: File) {
    val exif = ExifInterface(file.absolutePath)
    listOf(
        ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LATITUDE_REF,
        ExifInterface.TAG_GPS_LONGITUDE, ExifInterface.TAG_GPS_LONGITUDE_REF,
        ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_GPS_ALTITUDE_REF,
        ExifInterface.TAG_GPS_TIMESTAMP, ExifInterface.TAG_GPS_DATESTAMP,
        ExifInterface.TAG_GPS_PROCESSING_METHOD,
        ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL,
        ExifInterface.TAG_SOFTWARE,
        ExifInterface.TAG_IMAGE_UNIQUE_ID,
        ExifInterface.TAG_BODY_SERIAL_NUMBER,
        ExifInterface.TAG_LENS_MAKE, ExifInterface.TAG_LENS_MODEL,
        ExifInterface.TAG_LENS_SERIAL_NUMBER,
    ).forEach { exif.setAttribute(it, null) }
    exif.saveAttributes()
}

private fun applyExifRotation(bitmap: Bitmap, orientation: Int): Bitmap {
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.postRotate(90f); matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.postRotate(270f); matrix.postScale(-1f, 1f)
        }
        else -> return bitmap
    }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}
