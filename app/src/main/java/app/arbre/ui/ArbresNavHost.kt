package app.arbre.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.arbre.ui.map.MapScreen

object Routes {
    const val MAP = "map"
}

@Composable
fun ArbresNavHost() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.MAP) {
        composable(Routes.MAP) {
            MapScreen()
        }
    }
}
