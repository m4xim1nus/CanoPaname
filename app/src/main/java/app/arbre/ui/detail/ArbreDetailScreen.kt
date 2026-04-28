package app.arbre.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.arbre.data.ArbreRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArbreDetailScreen(arbreId: Long, onBack: () -> Unit) {
    val repo = remember { ArbreRepository() }
    val arbre = repo.arbreParId(arbreId)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(arbre?.nomAffichage ?: "Arbre") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (arbre == null) {
                Text("Arbre introuvable.")
                return@Scaffold
            }
            Text("Genre : ${arbre.genre ?: "—"}")
            Text("Espèce : ${arbre.espece ?: "—"}")
            arbre.hauteurM?.let { Text("Hauteur : $it m") }
            arbre.circonferenceCm?.let { Text("Circonférence : $it cm") }
            if (arbre.remarquable) Text("★ Arbre remarquable")
            arbre.adresse?.let { Text("Adresse : $it") }
        }
    }
}
