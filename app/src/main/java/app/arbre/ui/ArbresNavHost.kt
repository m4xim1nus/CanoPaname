package app.arbre.ui

import android.net.Uri
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.arbre.data.rememberOnboardingStore
import app.arbre.ui.about.AboutScreen
import app.arbre.ui.about.PhotoCreditsScreen
import app.arbre.ui.arboretum.ArboretumScreen
import app.arbre.ui.badges.BadgesScreen
import app.arbre.ui.genre.GenreActions
import app.arbre.ui.genre.GenreDetailScreen
import app.arbre.ui.map.CAPTURE_CELEBRATION_HAPTIC_MS
import app.arbre.ui.map.CAPTURE_CELEBRATION_SEQUENCE_MS
import app.arbre.ui.map.CaptureTransitionSplash
import app.arbre.ui.map.MapHost
import app.arbre.ui.map.MapScreen
import app.arbre.ui.onboarding.WelcomeScreen
import app.arbre.ui.profile.ProfileScreen
import app.arbre.ui.remarquables.RemarquableDetailScreen
import app.arbre.ui.remarquables.RemarquablesScreen
import app.arbre.ui.species.SpeciesActions
import app.arbre.ui.species.SpeciesDetailScreen
import app.arbre.ui.theme.arbresMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

// Voile de transition capture → fiche espèce : plancher de lecture = la durée
// de la séquence de célébration jouée par le voile (source unique côté
// MapOverlays — le plancher suit automatiquement la timeline) et timeout filet
// (ne jamais rester coincé sous le voile si la nav n'aboutit pas — pattern
// analogue au `finally { filteredArbresPrets = true }` du mode filtré).
private const val CAPTURE_TRANSITION_FLOOR_MS = CAPTURE_CELEBRATION_SEQUENCE_MS
private const val CAPTURE_TRANSITION_TIMEOUT_MS = 6_000L

@Composable
fun ArbresNavHost() {
    val nav = rememberNavController()
    val onboardingStore = rememberOnboardingStore()
    val coScope = rememberCoroutineScope()
    // `null` tant que le round-trip DataStore initial n'a pas répondu (quelques
    // ms) ; `false` = onboarding pas fait ; `true` = fait.
    val onboardingDone by onboardingStore.onboardingDone.collectAsState(initial = null)

    // MapView persistante : holder Activity-scopé partagé par toutes les entrées
    // `Routes.MAP` (le mode filtré garde sa MapView jetable et ne le reçoit pas).
    // Le cycle GL est relayé depuis le lifecycle de l'Activity — il ne dépend
    // plus du mount de `MapScreen`. Cf. doc de tête de `MapHost`.
    val ctx = LocalContext.current
    val mapHost = remember { MapHost(ctx) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        mapHost.attachLifecycle(lifecycleOwner.lifecycle)
        onDispose { mapHost.release(lifecycleOwner.lifecycle) }
    }

    // Saut « voir cet arbre » depuis une fiche : intent one-shot sur le holder
    // (consommé par l'effet pulse de MapScreen) + retour à l'entrée MAP
    // existante. `launchSingleTop` est essentiel : un param de route ou une
    // 2e entrée MAP rejouerait le fly-to à chaque retour carte, et laisserait
    // un appui back « fantôme » entre deux cartes identiques.
    val showArbreOnMap: (Long) -> Unit = { id ->
        mapHost.pendingPulseArbreId = id
        nav.navigate(Routes.map()) {
            popUpTo(Routes.MAP) { inclusive = false }
            launchSingleTop = true
        }
    }

    // `startDestination` est une CONSTANTE — surtout PAS dérivé de `onboardingDone`.
    // S'il l'était, chaque changement de valeur (sur un install frais :
    // null → false → true via `markDone()`) reconstruirait le graphe via le
    // `remember(route, startDestination)` interne de `NavHost`, démontant/remontant
    // MapScreen 3× : une instance transiente (créée par `onContinue`, tuée 200 ms
    // plus tard par la reconstruction `true → map`) jouerait et « consommerait »
    // l'intro tips (`markSplashIntroSeen()`) avant que l'instance stable ne lise
    // le flag → l'intro ne joue jamais. La redirection vers le WelcomeScreen
    // passe donc par un `LaunchedEffect` (en fin de fonction), pas
    // par le `startDestination` ; le splash overlay du MapScreen couvre la
    // transition, donc pas de flicker.
    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(navController = nav, startDestination = Routes.map()) {
            composable(Routes.WELCOME) {
                WelcomeScreen(
                    onContinue = {
                        coScope.launch { onboardingStore.markDone() }
                        nav.navigate(Routes.map()) {
                            popUpTo(Routes.WELCOME) { inclusive = true }
                        }
                    },
                )
            }
            composable(Routes.WELCOME_REPLAY) {
                WelcomeScreen(
                    readOnly = true,
                    onContinue = {},
                    onClose = { nav.popBackStack() },
                )
            }
            composable(Routes.MAP) {
                MapScreen(
                    mapHost = mapHost,
                    onArboretumClick = { nav.navigate(Routes.ARBORETUM) },
                    onRemarquablesClick = { nav.navigate(Routes.REMARQUABLES) },
                    onProfileClick = { nav.navigate(Routes.PROFILE) },
                    onSpeciesClick = { sk -> nav.navigate(Routes.species(sk)) },
                    onGenreClick = { genre -> nav.navigate(Routes.genre(genre)) },
                    onRemarquableDetail = { id -> nav.navigate(Routes.remarquableDetail(id)) },
                    onFirstSpeciesCapture = { sk -> nav.navigate(Routes.species(sk)) },
                )
            }
            composable(Routes.PROFILE) {
                ProfileScreen(
                    onBack = { nav.popBackStack() },
                    onBadgesClick = { nav.navigate(Routes.BADGES) },
                    onHowToPlayClick = { nav.navigate(Routes.WELCOME_REPLAY) },
                    onAboutClick = { nav.navigate(Routes.ABOUT) },
                )
            }
            composable(Routes.ABOUT) {
                AboutScreen(
                    onBack = { nav.popBackStack() },
                    onPhotoCreditsClick = { nav.navigate(Routes.PHOTO_CREDITS) },
                )
            }
            composable(Routes.PHOTO_CREDITS) {
                PhotoCreditsScreen(onBack = { nav.popBackStack() })
            }
            composable(Routes.BADGES) {
                BadgesScreen(onBack = { nav.popBackStack() })
            }
            composable(Routes.ARBORETUM) {
                ArboretumScreen(
                    onBack = { nav.popBackStack() },
                    onSpeciesClick = { sk -> nav.navigate(Routes.species(sk)) },
                    onGenreClick = { genre -> nav.navigate(Routes.genre(genre)) },
                )
            }
            composable(Routes.REMARQUABLES) {
                RemarquablesScreen(
                    onBack = { nav.popBackStack() },
                    onRemarquableClick = { id -> nav.navigate(Routes.remarquableDetail(id)) },
                )
            }
            composable(
                Routes.REMARQUABLE_DETAIL,
                arguments = listOf(navArgument("arbreId") { type = NavType.LongType }),
            ) { entry ->
                val arbreId = entry.arguments?.getLong("arbreId") ?: return@composable
                RemarquableDetailScreen(
                    arbreId = arbreId,
                    onBack = { nav.popBackStack() },
                    onSpeciesClick = { sk -> nav.navigate(Routes.species(sk)) },
                    onShowOnMap = showArbreOnMap,
                    onUnlockLost = { nav.popBackStack(Routes.MAP, inclusive = false) },
                )
            }
            composable(
                Routes.SPECIES,
                arguments = listOf(
                    navArgument("speciesIndex") { type = NavType.IntType },
                ),
            ) { entry ->
                val sk = entry.arguments?.getInt("speciesIndex") ?: return@composable
                SpeciesDetailScreen(
                    speciesIndex = sk,
                    actions = SpeciesActions(
                        onBack = { nav.popBackStack() },
                        onShowOnMap = { sks -> nav.navigate(Routes.mapFiltered(sks)) },
                        onShowArbreOnMap = showArbreOnMap,
                        onRemarquableClick = { id -> nav.navigate(Routes.remarquableDetail(id)) },
                        onUnlockLost = { nav.popBackStack(Routes.MAP, inclusive = false) },
                        onRedirectToGenre = { genre ->
                            nav.navigate(Routes.genre(genre)) {
                                popUpTo(Routes.SPECIES) { inclusive = true }
                            }
                        },
                    ),
                )
            }
            composable(
                Routes.MAP_FILTERED,
                arguments = listOf(navArgument("speciesIndices") { type = NavType.StringType }),
            ) { entry ->
                // Parsing CSV → Set<Int>. Un sk seul (fiche-espèce) ou plusieurs
                // (fiche `(G, sp.)`). Les sks invalides sont ignorés silencieusement
                // (toIntOrNull) ; un set vide retombe sur le mode non-filtré côté
                // MapScreen.
                val sks = entry.arguments
                    ?.getString("speciesIndices")
                    ?.split(',')
                    ?.mapNotNull { it.trim().toIntOrNull() }
                    ?.toSet()
                    ?: emptySet()
                if (sks.isEmpty()) return@composable
                MapScreen(
                    filterSpecies = sks,
                    onArboretumClick = { nav.navigate(Routes.ARBORETUM) },
                    onSpeciesClick = { other -> nav.navigate(Routes.species(other)) },
                    onRemarquableDetail = { id -> nav.navigate(Routes.remarquableDetail(id)) },
                    onBack = { nav.popBackStack() },
                )
            }
            composable(
                Routes.GENRE,
                arguments = listOf(navArgument("genre") { type = NavType.StringType }),
            ) { entry ->
                val raw = entry.arguments?.getString("genre")
                val genre = raw?.let { Uri.decode(it) }?.takeIf { it.isNotBlank() }
                if (genre == null) return@composable
                GenreDetailScreen(
                    genre = genre,
                    actions = GenreActions(
                        onBack = { nav.popBackStack() },
                        onSpeciesClick = { sk -> nav.navigate(Routes.species(sk)) },
                        onShowOnMap = { sks -> nav.navigate(Routes.mapFiltered(sks)) },
                        onShowArbreOnMap = showArbreOnMap,
                        onUnlockLost = { nav.popBackStack(Routes.MAP, inclusive = false) },
                    ),
                )
            }
        }

        // --- Voile de transition 1re capture → fiche espèce ---
        // Au-dessus du NavHost (un overlay dans MapScreen fade-rait avec lui
        // pendant la transition de nav et laisserait entrevoir la carte).
        // Levé synchroniquement par le callback TakePicture (cf.
        // `MapHost.captureTransitionSk`), il PORTE la séquence de célébration
        // (cf. `CaptureTransitionSplash`) ; éteint quand l'entrée SPECIES est
        // RESUMED ET le plancher de lecture écoulé — ou au tap (skip) dès que
        // la fiche est prête. Timeout filet inchangé.
        val transitionSk = mapHost.captureTransitionSk
        // Dernier sk non-null : pendant le fadeOut d'exit (300 ms) le contenu
        // de l'AnimatedVisibility est encore composé alors que
        // `captureTransitionSk` est déjà repassé à null — sans ça le voile
        // perdrait son texte en plein fade.
        var lastTransitionSk by remember { mutableStateOf(0) }
        if (transitionSk != null) lastTransitionSk = transitionSk
        // Fiche prête (RESUMED) : condition d'armement du skip au tap.
        var sheetReady by remember { mutableStateOf(false) }
        val sheetReadyState = rememberUpdatedState(sheetReady)
        val haptic = LocalHapticFeedback.current
        LaunchedEffect(transitionSk) {
            sheetReady = false
            if (transitionSk == null) return@LaunchedEffect
            // Tic au climax visuel (le nom se révèle dans le voile). Vit dans
            // la coroutine du voile : un skip avant l'échéance l'annule — pas
            // de tic pendant le fadeOut, l'arrivée sur la fiche reste muette.
            launch {
                delay(CAPTURE_CELEBRATION_HAPTIC_MS)
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            val raisedAt = SystemClock.elapsedRealtime()
            withTimeoutOrNull(CAPTURE_TRANSITION_TIMEOUT_MS) {
                nav.currentBackStackEntryFlow
                    .first { it.destination.route == Routes.SPECIES }
                    .lifecycle.currentStateFlow
                    .first { it.isAtLeast(Lifecycle.State.RESUMED) }
                sheetReady = true
                val elapsed = SystemClock.elapsedRealtime() - raisedAt
                if (elapsed < CAPTURE_TRANSITION_FLOOR_MS) {
                    delay(CAPTURE_TRANSITION_FLOOR_MS - elapsed)
                }
            }
            mapHost.captureTransitionSk = null
        }
        // Back avalé pendant le voile (fenêtre ≈ 2,5 s, pire cas borné par le
        // timeout, porte de sortie volontaire via le skip au tap) : un pop
        // pendant la transition laisserait la fiche orpheline sous le voile.
        BackHandler(enabled = transitionSk != null) {}
        AnimatedVisibility(
            visible = transitionSk != null,
            // Couvre dès la 1re frame ; l'exit coupe sec à échelle d'animation
            // système 0 — précédent assumé des splashes de MapScreen (le
            // contenu du voile, lui, est frame-clock donc toujours vivant).
            enter = EnterTransition.None,
            exit = fadeOut(animationSpec = tween(durationMillis = MaterialTheme.arbresMotion.short)),
            modifier = Modifier
                .fillMaxSize()
                // Mur d'input inconditionnel : contrairement au
                // CaptureCelebrationOverlay (qui doit laisser passer les
                // gestes carte), ici on VEUT bloquer l'écran en dessous —
                // TOUS les events sont consommés, y compris multi-touch et
                // entre deux gestes (pas de `awaitEachGesture`, qui laisse
                // fuir les pointeurs secondaires après le up du primaire).
                // En parallèle, un tap mono-doigt propre (down/up ≤ slop)
                // alors que la fiche est prête skippe le plancher : poser
                // null re-keye le LaunchedEffect ci-dessus, dont le delay
                // (et l'haptique pas encore tirée) est annulé → dissipation
                // immédiate.
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        val slop = viewConfiguration.touchSlop
                        var tapStart: Offset? = null
                        while (true) {
                            val event = awaitPointerEvent()
                            event.changes.forEach { it.consume() }
                            val single = event.changes.singleOrNull()
                            val start = tapStart
                            when {
                                single == null -> tapStart = null // multi-touch : pas un tap
                                single.changedToDownIgnoreConsumed() -> tapStart = single.position
                                start != null &&
                                    (single.position - start).getDistance() > slop -> tapStart = null
                                single.changedToUpIgnoreConsumed() -> {
                                    if (start != null && sheetReadyState.value) {
                                        mapHost.captureTransitionSk = null
                                    }
                                    tapStart = null
                                }
                            }
                        }
                    }
                },
        ) {
            CaptureTransitionSplash(speciesIndex = lastTransitionSk)
        }
    }

    // Redirection one-shot vers l'onboarding. Tant que `onboardingDone == null`
    // (DataStore en cours) on reste sur la carte (couverte par le splash) ;
    // `false` → on bascule sur le WelcomeScreen en purgeant la carte du backstack ;
    // `true` → ne fait rien (l'utilisateur est déjà sur la carte, ou y revient via
    // `WelcomeScreen.onContinue`). Le key étant `onboardingDone`, le passage
    // `false → true` provoqué par `markDone()` re-exécute l'effet une fois mais
    // sans action — donc pas de reconstruction du graphe NavHost.
    LaunchedEffect(onboardingDone) {
        if (onboardingDone == false) {
            nav.navigate(Routes.WELCOME) {
                popUpTo(Routes.MAP) { inclusive = true }
            }
        }
    }
}
