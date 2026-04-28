package app.arbre.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.arbre.ui.detail.ArbreDetailScreen
import app.arbre.ui.map.MapScreen

object Routes {
    const val MAP = "map"
    const val DETAIL = "arbre/{id}"
    fun detail(id: Long) = "arbre/$id"
}

@Composable
fun ArbresNavHost() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.MAP) {
        composable(Routes.MAP) {
            MapScreen(onArbreClick = { nav.navigate(Routes.detail(it)) })
        }
        composable(Routes.DETAIL) { backStack ->
            val id = backStack.arguments?.getString("id")?.toLongOrNull() ?: return@composable
            ArbreDetailScreen(arbreId = id, onBack = { nav.popBackStack() })
        }
    }
}
