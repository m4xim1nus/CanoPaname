package app.arbre.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.arbre.ui.arboretum.ArboretumScreen
import app.arbre.ui.map.MapScreen

object Routes {
    const val MAP = "map"
    const val ARBORETUM = "arboretum"
}

@Composable
fun ArbresNavHost() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.MAP) {
        composable(Routes.MAP) {
            MapScreen(onArboretumClick = { nav.navigate(Routes.ARBORETUM) })
        }
        composable(Routes.ARBORETUM) {
            ArboretumScreen(onBack = { nav.popBackStack() })
        }
    }
}
