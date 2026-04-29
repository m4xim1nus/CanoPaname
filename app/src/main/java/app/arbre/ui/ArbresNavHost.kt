package app.arbre.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.arbre.data.rememberOnboardingStore
import app.arbre.ui.arboretum.ArboretumScreen
import app.arbre.ui.badges.BadgesScreen
import app.arbre.ui.map.MapScreen
import app.arbre.ui.onboarding.WelcomeScreen
import app.arbre.ui.profile.ProfileScreen
import app.arbre.ui.remarquables.RemarquableDetailScreen
import app.arbre.ui.remarquables.RemarquablesScreen
import app.arbre.ui.species.SpeciesDetailScreen
import kotlinx.coroutines.launch

object Routes {
    const val WELCOME = "welcome"
    const val WELCOME_REPLAY = "welcome_replay"
    const val MAP = "map"
    const val ARBORETUM = "arboretum"
    const val PROFILE = "profile"
    const val BADGES = "badges"
    const val REMARQUABLES = "remarquables"
    const val REMARQUABLE_DETAIL = "remarquable_detail/{arbreId}"
    // Le flag `celebrate` est passé en query param (compose-navigation gère
    // les optionnels uniquement après `?`). Permet la transition « waouh »
    // depuis CaptureLauncher sans dupliquer la destination.
    const val SPECIES = "species/{speciesIndex}?celebrate={celebrate}"
    // Carte filtrée sur une espèce — destination distincte de MAP pour avoir
    // un MapViewModel propre (caméra à Paris z11, pas la dernière position
    // mémorisée par l'écran principal) et une entrée séparée sur le backstack.
    const val MAP_FILTERED = "map_filtered/{speciesIndex}"

    fun species(speciesIndex: Int, celebrate: Boolean = false): String =
        "species/$speciesIndex?celebrate=$celebrate"
    fun mapFiltered(speciesIndex: Int): String = "map_filtered/$speciesIndex"
    fun remarquableDetail(arbreId: Long): String = "remarquable_detail/$arbreId"
}

@Composable
fun ArbresNavHost() {
    val nav = rememberNavController()
    val onboardingStore = rememberOnboardingStore()
    val coScope = rememberCoroutineScope()
    // initial = null pendant le 1er round-trip DataStore (quelques ms). Pendant
    // ce délai, le splash overlay du MapScreen précédent couvre déjà l'écran —
    // pas de flicker visible.
    val onboardingDone by onboardingStore.onboardingDone.collectAsState(initial = null)
    val start = when (onboardingDone) {
        null -> Routes.MAP // fallback transitoire ; l'utilisateur arrivera sur la carte
        true -> Routes.MAP
        false -> Routes.WELCOME
    }

    NavHost(navController = nav, startDestination = start) {
        composable(Routes.WELCOME) {
            WelcomeScreen(
                onContinue = {
                    coScope.launch { onboardingStore.markDone() }
                    nav.navigate(Routes.MAP) {
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
                onArboretumClick = { nav.navigate(Routes.ARBORETUM) },
                onRemarquablesClick = { nav.navigate(Routes.REMARQUABLES) },
                onProfileClick = { nav.navigate(Routes.PROFILE) },
                onSpeciesClick = { sk -> nav.navigate(Routes.species(sk)) },
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
            )
        }
        composable(Routes.BADGES) {
            BadgesScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.ARBORETUM) {
            ArboretumScreen(
                onBack = { nav.popBackStack() },
                onSpeciesClick = { sk -> nav.navigate(Routes.species(sk)) },
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
                onBack = { nav.popBackStack() },
                onShowOnMap = { nav.navigate(Routes.mapFiltered(sk)) },
                celebrate = celebrate,
            )
        }
        composable(
            Routes.MAP_FILTERED,
            arguments = listOf(navArgument("speciesIndex") { type = NavType.IntType }),
        ) { entry ->
            val sk = entry.arguments?.getInt("speciesIndex") ?: return@composable
            MapScreen(
                filterSpecies = sk,
                onArboretumClick = { nav.navigate(Routes.ARBORETUM) },
                onSpeciesClick = { other -> nav.navigate(Routes.species(other)) },
                onBack = { nav.popBackStack() },
            )
        }
    }
}
