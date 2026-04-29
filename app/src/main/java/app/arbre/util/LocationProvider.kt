package app.arbre.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Géoloc native autour de `LocationManager`. Ne dépend pas de Google Play Services
 * — l'app cible GrapheneOS sans GMS.
 *
 * Deux APIs :
 *  - [currentLocation] : `StateFlow` mis à jour en continu tant que [start] est
 *    actif. C'est la source à lire pour tout calcul de distance temps réel
 *    (capture, FAB ★ remarquable proche). Fix sur le bug observé Sprint D où
 *    `getCurrentLocation` rendait un fix figé alors que MapLibre voyait la vraie
 *    position : MapLibre souscrit en continu via son `LocationEngine`, on fait
 *    pareil ici plutôt que du one-shot.
 *  - [currentOrLastKnown] : one-shot pour le cold start (avant que la souscription
 *    n'ait reçu son premier fix), reste utilisé par `computeInitialCamera`.
 */
object LocationProvider {

    private val _currentLocation = MutableStateFlow<Location?>(null)
    val currentLocation: StateFlow<Location?> = _currentLocation.asStateFlow()

    private var manager: LocationManager? = null
    private var listener: LocationListener? = null

    fun hasFineLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * Démarre une souscription continue aux updates GPS + NETWORK. Idempotent :
     * un second appel sans [stop] préalable ne crée pas de doublon. Sans effet
     * si la permission n'est pas accordée — l'appelant doit redémarrer après
     * obtention de la permission.
     */
    @SuppressLint("MissingPermission")
    fun start(context: Context) {
        if (!hasFineLocationPermission(context)) return
        if (listener != null) return
        val lm = context.applicationContext
            .getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return

        val l = LocationListener { newLoc ->
            val current = _currentLocation.value
            if (current == null || isBetterFix(newLoc, current)) {
                _currentLocation.value = newLoc
            }
        }
        manager = lm
        listener = l

        if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, MIN_INTERVAL_MS, MIN_DISTANCE_M, l)
        }
        if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, MIN_INTERVAL_MS, MIN_DISTANCE_M, l)
        }

        // Amorce le flow avec le last-known pour ne pas démarrer à null pendant
        // la première seconde de TTFF.
        if (_currentLocation.value == null) {
            val seed = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (seed != null) _currentLocation.value = seed
        }
    }

    fun stop() {
        listener?.let { manager?.removeUpdates(it) }
        listener = null
        manager = null
    }

    /**
     * Renvoie une position fraîche (API 30+) ou la dernière connue (API 26-29).
     * Réservé au bootstrap (cold start, première caméra). Pour les calculs de
     * distance utiliser [currentLocation].
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

    private val directExecutor = java.util.concurrent.Executor { it.run() }

    private const val MIN_INTERVAL_MS = 2_000L
    private const val MIN_DISTANCE_M = 1f
}

/**
 * Âge d'un fix mesuré sur l'horloge monotonique (`elapsedRealtimeNanos`),
 * insensible aux changements d'heure système et à la mise en cache douteuse de
 * `loc.time` — le bug observé Sprint D venait justement d'un `loc.time` jeune
 * sur un fix spatialement figé.
 */
fun Location.ageMs(): Long =
    (SystemClock.elapsedRealtimeNanos() - elapsedRealtimeNanos) / 1_000_000L

/**
 * Vrai si [candidate] est plus utile que [current] :
 *  - significativement plus récent (> 10 s) → on prend, même si moins précis ;
 *  - significativement plus ancien (> 10 s) → on garde l'actuel ;
 *  - sinon, on prend si la précision est égale ou meilleure (NETWORK ne doit
 *    pas écraser un GPS plus précis qui vient d'arriver).
 */
private fun isBetterFix(candidate: Location, current: Location): Boolean {
    val dtMs = (candidate.elapsedRealtimeNanos - current.elapsedRealtimeNanos) / 1_000_000L
    if (dtMs > 10_000) return true
    if (dtMs < -10_000) return false
    return candidate.accuracy <= current.accuracy
}
