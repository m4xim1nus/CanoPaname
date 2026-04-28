package app.arbre.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.arbre.ui.arboretum.ArboretumScreen
import app.arbre.ui.map.MapScreen
import app.arbre.ui.species.SpeciesDetailScreen

object Routes {
    const val MAP = "map"
    const val ARBORETUM = "arboretum"
    const val SPECIES = "species/{speciesIndex}"

    fun species(speciesIndex: Int): String = "species/$speciesIndex"
}

@Composable
fun ArbresNavHost() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.MAP) {
        composable(Routes.MAP) {
            MapScreen(onArboretumClick = { nav.navigate(Routes.ARBORETUM) })
        }
        composable(Routes.ARBORETUM) {
            ArboretumScreen(
                onBack = { nav.popBackStack() },
                onSpeciesClick = { sk -> nav.navigate(Routes.species(sk)) },
            )
        }
        composable(
            Routes.SPECIES,
            arguments = listOf(navArgument("speciesIndex") { type = NavType.IntType }),
        ) { entry ->
            val sk = entry.arguments?.getInt("speciesIndex") ?: return@composable
            SpeciesDetailScreen(speciesIndex = sk, onBack = { nav.popBackStack() })
        }
    }
}
