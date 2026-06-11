package app.arbre.ui.map

import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

/**
 * Holder **Activity-scopé** de la MapView principale (mode normal de `MapScreen`).
 *
 * Avant lui, la MapView était `remember`-ée dans `MapScreen` : chaque navigation
 * (Profil, Arboretum, fiche…) la détruisait, et le retour re-payait ré-init GL +
 * `setStyle` + re-push des 217 k features + re-clustering — splash perceptible à
 * chaque retour carte. Le holder garde l'instance (style et source comprises)
 * pendant toute la vie de l'Activity : `MapScreen` ne fait plus qu'attacher /
 * détacher la view et poser ses listeners d'interaction per-mount.
 *
 * Contrats :
 * - Créé par `remember {}` dans `ArbresNavHost` (contexte Activity), passé en
 *   paramètre aux entrées `Routes.MAP`. **Pas dans `ArbresApp`** : un View
 *   construit sur un contexte Activity et tenu par l'Application leakerait
 *   l'Activity. À la recréation d'Activity (rotation), le holder meurt
 *   proprement avec elle (= comportement pré-holder).
 * - Le cycle GL (`onStart`/`onResume`/…/`onDestroy`) est relayé depuis le
 *   lifecycle de l'**Activity** via [attachLifecycle], plus depuis le mount du
 *   composable. La view est créée lazy : si elle naît après `ON_START`/
 *   `ON_RESUME` (cas nominal), le getter rattrape les événements manqués.
 * - `MAP_FILTERED` n'utilise pas le holder : sa MapView jetable locale est
 *   volontairement conservée (partager l'instance forcerait un re-push des
 *   33 Mo au retour sur la carte principale).
 * - Le pipeline d'init contenu (style + GeoJSON) reste écrit dans `MapScreen`
 *   mais tourne dans [scope] et pose ses résultats ici ([map], [style],
 *   [pinsRendered]) : il survit à une nav pendant le chargement, et les
 *   remounts s'y raccordent au lieu de relancer.
 */
class MapHost(private val context: Context) {

    init {
        android.util.Log.i("MapHost", "créé @${System.identityHashCode(this)}")
    }

    /**
     * Scope des travaux liés au contenu de la carte (init one-shot ; observers
     * de découverte à l'étape B). Annulé à la destruction du holder.
     */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Posée par le callback `getMapAsync` du pipeline d'init de `MapScreen`. */
    var map: MapLibreMap? by mutableStateOf(null)

    /** Posé une fois `addArbresLayers` passé — déclenche les observers per-mount. */
    var style: Style? by mutableStateOf(null)

    /**
     * Vrai une fois `awaitArbresRendered` franchi la 1re fois. Les remounts de
     * `MapScreen` initialisent leur splash dessus : carte prête = zéro voile,
     * les clusters éventuellement stale rattrapent silencieusement.
     */
    var pinsRendered: Boolean by mutableStateOf(false)

    /**
     * Garde du pipeline d'init contenu : un seul run par vie du holder. Flag
     * explicite plutôt que `style != null` — entre `getMapAsync` et le callback
     * de `setStyle`, un remount relancerait tout le pipeline.
     */
    var contentInitStarted: Boolean = false

    /**
     * Dernière caméra connue (listener idle posé une fois à l'init du contenu).
     * La caméra persiste de toute façon dans la view entre deux mounts ; ce
     * champ remplace `MapViewModel.lastCamera` pour le mode normal.
     */
    var lastCamera: CameraPosition? = null

    /**
     * Le recadrage GPS auto au 1er fix ne tire qu'une fois par vie d'Activity
     * (remplace l'ancien `freshMount`) — jamais au remount : la caméra de
     * l'utilisateur est sacrée.
     */
    var autoRecenterDone: Boolean = false

    private var mapViewLazy: MapView? = null
    private var lifecycle: Lifecycle? = null
    private var viewStarted = false
    private var viewResumed = false
    private var destroyed = false
    private var activityStarted = false
    private var activityResumed = false

    /**
     * Nombre d'entrées MAP actuellement montées qui affichent la view (0 ou 1,
     * brièvement 2 pendant la crossfade `pulseArbreId` — compteur et pas
     * booléen pour que le dispose tardif de l'entrée sortante n'écrase pas le
     * mount de l'entrante). Incrémenté/décrémenté par `MapScreen`.
     */
    private var attachedScreens = 0

    /**
     * Gel de la carte pendant l'absence : la view n'est `onResume` que si
     * l'Activity est resumed ET qu'un `MapScreen` l'affiche. Détachée, elle
     * reste en pause — sinon le render loop MapLibre et le `ValueAnimator`
     * infini du pulse GPS continuent d'invalider en boucle et saccadent les
     * écrans affichés au-dessus. Effet de bord assumé : le render thread en
     * pause gèle aussi le callback de `setStyle` — un pipeline d'init encore
     * en cours au moment du detach (nav pendant le cold start, onboarding)
     * reprend simplement au resume suivant, sous le splash.
     */
    fun screenAttached() {
        attachedScreens++
        syncViewState()
    }

    fun screenDetached() {
        attachedScreens--
        syncViewState()
    }

    private fun syncViewState() {
        val view = mapViewLazy ?: return
        if (destroyed) return
        if (activityStarted && !viewStarted) {
            view.onStart()
            viewStarted = true
        }
        val wantResumed = activityResumed && attachedScreens > 0
        if (wantResumed && !viewResumed) {
            view.onResume()
            viewResumed = true
        } else if (!wantResumed && viewResumed) {
            view.onPause()
            viewResumed = false
        }
        if (!activityStarted && viewStarted) {
            view.onStop()
            viewStarted = false
        }
    }

    /** Relais `onLowMemory` — le Lifecycle Jetpack ne porte pas cet événement. */
    private val memoryCallbacks = object : ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) = Unit
        override fun onLowMemory() {
            mapViewLazy?.onLowMemory()
        }
    }

    private val lifecycleObserver = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_START -> {
                activityStarted = true
                syncViewState()
            }
            Lifecycle.Event.ON_RESUME -> {
                activityResumed = true
                syncViewState()
            }
            Lifecycle.Event.ON_PAUSE -> {
                activityResumed = false
                syncViewState()
            }
            Lifecycle.Event.ON_STOP -> {
                activityStarted = false
                syncViewState()
            }
            Lifecycle.Event.ON_DESTROY -> destroy()
            else -> Unit
        }
    }

    val mapView: MapView
        get() {
            mapViewLazy?.let { return it }
            check(!destroyed) { "MapHost déjà détruit" }
            val view = MapView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
            mapViewLazy = view
            view.onCreate(null)
            // Rattrapage : la view naît en général alors que l'Activity est
            // déjà STARTED/RESUMED — l'observer n'a relayé que ce qui précédait
            // sa création. Le resume effectif attend `screenAttached()`.
            val state = lifecycle?.currentState
            activityStarted = state?.isAtLeast(Lifecycle.State.STARTED) == true
            activityResumed = state?.isAtLeast(Lifecycle.State.RESUMED) == true
            syncViewState()
            context.registerComponentCallbacks(memoryCallbacks)
            return view
        }

    fun attachLifecycle(lifecycle: Lifecycle) {
        this.lifecycle = lifecycle
        lifecycle.addObserver(lifecycleObserver)
    }

    /**
     * Détache l'observer et détruit le holder. Appelé au dispose de
     * `ArbresNavHost` ; `ON_DESTROY` peut aussi arriver en premier selon
     * l'ordre de teardown — [destroy] est idempotent.
     */
    fun release(lifecycle: Lifecycle) {
        lifecycle.removeObserver(lifecycleObserver)
        this.lifecycle = null
        destroy()
    }

    private fun destroy() {
        if (destroyed) return
        android.util.Log.i("MapHost", "destroy @${System.identityHashCode(this)}")
        destroyed = true
        scope.cancel()
        mapViewLazy?.let { view ->
            context.unregisterComponentCallbacks(memoryCallbacks)
            (view.parent as? ViewGroup)?.removeView(view)
            if (viewResumed) view.onPause()
            if (viewStarted) view.onStop()
            view.onDestroy()
        }
        mapViewLazy = null
        map = null
        style = null
    }
}
