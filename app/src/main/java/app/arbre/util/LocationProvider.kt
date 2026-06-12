package app.arbre.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Géoloc native autour de `LocationManager` — pas de dépendance Google Play
 * Services (cible GrapheneOS sans GMS).
 *
 * API : [currentLocation], `StateFlow` continu — calculs de distance temps
 * réel (capture, FAB ★ remarquable proche) comme bootstrap caméra. Amorcé au
 * [start] par un last-known filtré sur fraîcheur ([isRecentFix]) puis un
 * one-shot `getCurrentLocation` (API ≥ R).
 */
object LocationProvider {

    private val _currentLocation = MutableStateFlow<Location?>(null)
    val currentLocation: StateFlow<Location?> = _currentLocation.asStateFlow()

    private var manager: LocationManager? = null
    private var listener: LocationListener? = null
    // Mémorisé pour pouvoir désenregistrer le receiver sans le contexte
    // d'origine (évite la fuite Activity et permet le rebind depuis le receiver).
    private var appContext: Context? = null
    private var providersReceiver: BroadcastReceiver? = null

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
        val app = context.applicationContext
        appContext = app
        val lm = app.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
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

        // Amorce le flow avec un last-known — sinon il reste à null pendant le
        // TTFF. Filtré sur fraîcheur : un last-known de la veille recadrerait
        // la carte sur la position d'hier (`computeInitialCamera` + recadrage
        // auto consommeraient ce fix périmé). Périmé → on laisse null, le
        // one-shot `getCurrentLocation` ci-dessous fournit un frais en ~1 s.
        if (_currentLocation.value == null) {
            val seed = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (seed != null && seed.isRecentFix()) _currentLocation.value = seed
        }

        // API ≥ R : `getCurrentLocation` retourne un fix frais en < 1 s,
        // non-bloquant, plus précis que le last-known. Sans ça, le cold-start
        // post-onboarding attendait le 1er natural update (~2 s).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            requestOneShotSeed(lm)
        }

        // Receiver système : si le user toggle GPS dans les paramètres pendant
        // que l'app est ouverte, on rebind notre listener au lieu de rester
        // muet. Idempotent via le check `listener != null` au-dessus —
        // `stop() + start()` repart proprement.
        if (providersReceiver == null) {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) {
                    if (i.action != LocationManager.PROVIDERS_CHANGED_ACTION) return
                    val mgr = c.getSystemService(LocationManager::class.java) ?: return
                    if (mgr.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                        mgr.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                    ) {
                        val ctx = appContext ?: return
                        stop()
                        start(ctx)
                    }
                }
            }
            ContextCompat.registerReceiver(
                app,
                receiver,
                IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            providersReceiver = receiver
        }
    }

    @SuppressLint("MissingPermission")
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.R)
    private fun requestOneShotSeed(lm: LocationManager) {
        val provider = pickProvider(lm) ?: return
        lm.getCurrentLocation(provider, CancellationSignal(), directExecutor) { loc ->
            if (loc != null) {
                val current = _currentLocation.value
                if (current == null || isBetterFix(loc, current)) {
                    _currentLocation.value = loc
                }
            }
        }
    }

    fun stop() {
        // Désenregistre le receiver d'abord — sinon un PROVIDERS_CHANGED qui
        // arrive entre les deux unbind redéclencherait `start()` orphelin.
        val receiver = providersReceiver
        val app = appContext
        if (receiver != null && app != null) {
            try {
                app.unregisterReceiver(receiver)
            } catch (_: IllegalArgumentException) {
                // Déjà désenregistré (cycles parallèles).
            }
        }
        providersReceiver = null
        listener?.let { manager?.removeUpdates(it) }
        listener = null
        manager = null
    }

    /**
     * Pousse un fix externe — typiquement le `LocationEngine` MapLibre bridge
     * par `MapScreen`. Couvre le cas du 1er lancement post-onboarding où
     * notre `LocationListener` ne reçoit rien pendant ~10 s alors que le
     * `LocationEngine` MapLibre a déjà des fix. Filtre [isBetterFix] : un
     * fix externe vieux ou imprécis n'écrasera pas un meilleur temps réel.
     */
    fun feedExternalFix(loc: Location) {
        val current = _currentLocation.value
        if (current == null || isBetterFix(loc, current)) {
            _currentLocation.value = loc
        }
    }

    private fun pickProvider(lm: LocationManager): String? {
        val candidates = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        return candidates.firstOrNull { lm.isProviderEnabled(it) }
    }

    private val directExecutor = java.util.concurrent.Executor { it.run() }

    // 500 ms aligné avec la cadence MapLibre. Au-dessus (2 000 ms testé), on
    // observe un drift ~100 m entre le pin MapLibre et notre flow.
    private const val MIN_INTERVAL_MS = 500L
    private const val MIN_DISTANCE_M = 0f

    /**
     * Âge maximal d'un fix utilisé pour cadrer la carte ([isRecentFix]) :
     * relance pendant la même balade → on repart de la position récente ;
     * relance le lendemain → Paris, puis recadrage auto au 1er fix frais.
     */
    const val MAX_BOOTSTRAP_FIX_AGE_MS = 15 * 60_000L
}

/**
 * Âge mesuré sur l'horloge monotonique — insensible aux changements d'heure
 * système et au caching douteux de `loc.time` qu'on a vu reporter un fix jeune
 * sur une position spatialement figée.
 */
fun Location.ageMs(): Long =
    (SystemClock.elapsedRealtimeNanos() - elapsedRealtimeNanos) / 1_000_000L

/**
 * « Ce fix est-il assez frais pour cadrer la carte ? » — borne basse 0
 * incluse : un fix d'avant reboot porte un `elapsedRealtimeNanos` plus grand
 * que l'horloge courante (âge négatif) et doit compter comme périmé, le
 * last-known survivant au reboot sur Android récent.
 */
fun Location.isRecentFix(maxAgeMs: Long = LocationProvider.MAX_BOOTSTRAP_FIX_AGE_MS): Boolean =
    ageMs() in 0..maxAgeMs

/**
 * Critère « ce nouveau fix est-il préférable au courant » :
 * - +10 s plus récent → on prend, même si moins précis ;
 * - −10 s plus ancien → on garde l'actuel ;
 * - sinon, prend si précision égale ou meilleure (NETWORK ne doit pas
 *   écraser un GPS plus précis fraîchement arrivé).
 */
private fun isBetterFix(candidate: Location, current: Location): Boolean {
    val dtMs = (candidate.elapsedRealtimeNanos - current.elapsedRealtimeNanos) / 1_000_000L
    if (dtMs > 10_000) return true
    if (dtMs < -10_000) return false
    return candidate.accuracy <= current.accuracy
}
