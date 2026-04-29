package app.arbre.ui.remarquables

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.arbre.data.Arbre
import app.arbre.data.rememberArbreRepository
import app.arbre.data.rememberCaptureRepository
import app.arbre.data.rememberRemarquableInfoRepository
import app.arbre.ui.detail.ArbreDetailContent

/**
 * Fiche plein-écran d'un arbre remarquable individuel, atteinte depuis le
 * Pokédex remarquables. On n'arrive ici qu'après découverte (le Pokédex ne
 * rend cliquable que les cellules découvertes), donc `isDiscovered = true`
 * d'office et pas de bouton Capturer.
 *
 * Réutilise `ArbreDetailContent` (le même rendu que la bottom sheet de la
 * carte), enveloppé dans un `Scaffold` avec back arrow pour la cohérence
 * avec `SpeciesDetailScreen`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemarquableDetailScreen(
    arbreId: Long,
    onBack: () -> Unit,
) {
    val arbreRepo = rememberArbreRepository()
    val captureRepo = rememberCaptureRepository()
    val remarquableInfoRepo = rememberRemarquableInfoRepository()

    var arbre by remember(arbreId) { mutableStateOf<Arbre?>(null) }
    LaunchedEffect(arbreId) {
        arbre = arbreRepo.arbreParId(arbreId)
    }

    // Flow de captures pour cet arbre — affiche le compteur « X photos de
    // capture » à mesure que de nouvelles captures arrivent (peu probable
    // pendant qu'on regarde la fiche, mais cohérent avec la bottom sheet).
    val captures by remember(arbreId) {
        captureRepo.capturesPourArbre(arbreId)
    }.collectAsState(initial = emptyList())

    val info = remarquableInfoRepo.get(arbreId)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(arbre?.nomAffichage ?: "Remarquable")
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Retour")
                    }
                },
            )
        },
    ) { padding ->
        val current = arbre
        if (current == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text("Chargement…", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState()),
            ) {
                ArbreDetailContent(
                    arbre = current,
                    isDiscovered = true,
                    nbPhotos = captures.size,
                    remarquableInfo = info,
                )
            }
        }
    }
}
