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
import app.arbre.ui.about.AboutScreen
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
    // `celebrate` en query param — compose-navigation n'autorise les
    // optionnels qu'après `?`.
    const val SPECIES = "species/{speciesIndex}?celebrate={celebrate}"
    // Destination distincte de MAP : MapViewModel propre + caméra Paris z11
    // + entrée séparée du backstack.
    const val MAP_FILTERED = "map_filtered/{speciesIndex}"
    const val ABOUT = "about"

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
    // `null` = round-trip DataStore en cours. Pendant ce délai (quelques ms),
    // le splash overlay du MapScreen masque déjà l'écran.
    val onboardingDone by onboardingStore.onboardingDone.collectAsState(initial = null)
    val start = when (onboardingDone) {
        null -> Routes.MAP
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
                onRemarquableClick = { id -> nav.navigate(Routes.remarquableDetail(id)) },
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
                onRemarquableDetail = { id -> nav.navigate(Routes.remarquableDetail(id)) },
                onBack = { nav.popBackStack() },
            )
        }
    }
}
