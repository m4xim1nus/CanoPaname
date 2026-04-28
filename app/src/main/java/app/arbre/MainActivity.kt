package app.arbre

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import app.arbre.ui.ArbresNavHost
import app.arbre.ui.theme.ArbresTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // installSplashScreen() doit être appelé AVANT super.onCreate(), sinon
        // le theme post-splash n'est pas appliqué et on garde l'icône splash
        // visible au lieu de la carte. Le splash natif disparaît avec le 1er
        // frame Compose ; le splash overlay Compose prend ensuite le relais
        // jusqu'au chargement des layers d'arbres.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ArbresTheme {
                ArbresNavHost()
            }
        }
    }
}
