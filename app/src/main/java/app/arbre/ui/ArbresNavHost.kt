package app.arbre.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.arbre.data.rememberOnboardingStore
import app.arbre.ui.about.AboutScreen
import app.arbre.ui.arboretum.ArboretumScreen
import app.arbre.ui.badges.BadgesScreen
import app.arbre.ui.genre.GenreActions
import app.arbre.ui.genre.GenreDetailScreen
import app.arbre.ui.map.MapHost
import app.arbre.ui.map.MapScreen
import app.arbre.ui.onboarding.WelcomeScreen
import app.arbre.ui.profile.ProfileScreen
import app.arbre.ui.remarquables.RemarquableDetailScreen
import app.arbre.ui.remarquables.RemarquablesScreen
import app.arbre.ui.species.SpeciesActions
import app.arbre.ui.species.SpeciesDetailScreen
import kotlinx.coroutines.launch

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
                onFirstSpeciesCapture = { sk ->
                    nav.navigate(Routes.species(sk, celebrate = true))
                },
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
            AboutScreen(onBack = { nav.popBackStack() })
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
                navArgument("celebrate") {
                    type = NavType.BoolType
                    defaultValue = false
                },
            ),
        ) { entry ->
            val sk = entry.arguments?.getInt("speciesIndex") ?: return@composable
            val celebrate = entry.arguments?.getBoolean("celebrate") ?: false
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
                celebrate = celebrate,
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
