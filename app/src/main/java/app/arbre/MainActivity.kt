package app.arbre

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.arbre.ui.ArbresNavHost
import app.arbre.ui.theme.ArbresTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ArbresTheme {
                ArbresNavHost()
            }
        }
    }
}
