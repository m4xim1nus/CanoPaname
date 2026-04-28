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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.arbre.data.Arbre
import app.arbre.data.rememberArbreRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArbreDetailScreen(arbreId: Long, onBack: () -> Unit) {
    val repo = rememberArbreRepository()
    val arbre: Arbre? by produceState<Arbre?>(initialValue = null, key1 = arbreId) {
        value = repo.arbreParId(arbreId)
    }

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
            val a = arbre
            if (a == null) {
                Text("Chargement…")
                return@Scaffold
            }

            if (a.remarquable) {
                Text(
                    "★ Arbre remarquable",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            val taxonomie = listOfNotNull(a.genre, a.espece, a.varieteCultivar)
                .joinToString(" ")
                .ifBlank { null }
            taxonomie?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            a.hauteurM?.let { Text("Hauteur : $it m") }
            a.circonferenceCm?.let { Text("Circonférence : $it cm") }
            a.adresse?.let { Text("Adresse : $it") }
            Text("ID OpenData : ${a.id}", style = MaterialTheme.typography.bodySmall)
        }
    }
}
