package app.arbre.ui.remarquables

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.arbre.data.Arbre
import app.arbre.data.rememberArbreRepository
import app.arbre.data.rememberCaptureRepository
import app.arbre.data.rememberRemarquableInfoRepository
import app.arbre.data.rememberSpeciesIndex
import app.arbre.data.resolvedFile
import app.arbre.ui.common.PhotoGallery
import app.arbre.ui.common.PhotoLightbox
import app.arbre.ui.detail.ArbreDetailContent

/**
 * Fiche plein-écran d'un arbre remarquable, atteinte uniquement après
 * découverte — d'où `isDiscovered = true` d'office et pas de bouton Capturer.
 * Réutilise `ArbreDetailContent` (le rendu de la bottom sheet de la carte)
 * en y ajoutant une galerie + lightbox plein écran.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemarquableDetailScreen(
    arbreId: Long,
    onBack: () -> Unit,
    onSpeciesClick: (Int) -> Unit = {},
) {
    val arbreRepo = rememberArbreRepository()
    val captureRepo = rememberCaptureRepository()
    val remarquableInfoRepo = rememberRemarquableInfoRepository()
    val speciesIndex = rememberSpeciesIndex()

    var arbre by remember(arbreId) { mutableStateOf<Arbre?>(null) }
    LaunchedEffect(arbreId) {
        arbre = arbreRepo.arbreParId(arbreId)
    }

    val captures by remember(arbreId) {
        captureRepo.capturesPourArbre(arbreId)
    }.collectAsState(initial = emptyList())

    // Gating : pivot vers la fiche-espèce seulement si l'espèce basique a
    // été capturée (toutes saisons), cohérent avec le langage « ??? ».
    val capturedSpecies by captureRepo.capturedSpeciesIndices()
        .collectAsState(initial = emptySet())

    val info = remarquableInfoRepo.get(arbreId)

    var lightboxIndex by remember(arbreId) { mutableStateOf<Int?>(null) }

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
            val ctx = LocalContext.current
            val sk = speciesIndex.indexOf(current)
            val photoFiles = captures.map { it.resolvedFile(ctx) }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (photoFiles.isNotEmpty()) {
                    PhotoGallery(
                        photoFiles = photoFiles,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        onPhotoClick = { idx -> lightboxIndex = idx },
                    )
                    Spacer(Modifier.height(8.dp))
                }
                ArbreDetailContent(
                    arbre = current,
                    isDiscovered = true,
                    nbPhotos = captures.size,
                    onSpeciesClick = if (sk != null && sk in capturedSpecies) {
                        { onSpeciesClick(sk) }
                    } else null,
                    onRemarquableClick = null,
                    remarquableInfo = info,
                )
            }
            PhotoLightbox(
                photoFiles = photoFiles,
                selectedIndex = lightboxIndex,
                onDismiss = { lightboxIndex = null },
            )
        }
    }
}
