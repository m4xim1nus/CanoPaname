package app.arbre.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Géoloc native autour de `LocationManager`. Ne dépend pas de Google Play Services
 * — l'app cible GrapheneOS sans GMS.
 */
object LocationProvider {

    fun hasFineLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * Renvoie une position fraîche (API 30+) ou la dernière connue (API 26-29).
     * `null` si aucun provider disponible ou si l'appareil n'a jamais eu de fix.
     * À appeler après vérification de la permission.
     */
    @SuppressLint("MissingPermission")
    suspend fun currentOrLastKnown(context: Context): Location? {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null

        val provider = pickProvider(lm) ?: return null

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getCurrent(lm, provider) ?: lm.getLastKnownLocation(provider)
        } else {
            lm.getLastKnownLocation(provider)
        }
    }

    private fun pickProvider(lm: LocationManager): String? {
        // GPS d'abord (plus précis), réseau en fallback.
        val candidates = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        return candidates.firstOrNull { lm.isProviderEnabled(it) }
    }

    @SuppressLint("MissingPermission")
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.R)
    private suspend fun getCurrent(lm: LocationManager, provider: String): Location? =
        suspendCancellableCoroutine { cont ->
            val signal = CancellationSignal()
            cont.invokeOnCancellation { signal.cancel() }
            lm.getCurrentLocation(provider, signal, directExecutor) { loc ->
                if (cont.isActive) cont.resume(loc)
            }
        }

    // Executor minimal pour `getCurrentLocation` — exécute sur le thread courant.
    private val directExecutor = java.util.concurrent.Executor { it.run() }
}
