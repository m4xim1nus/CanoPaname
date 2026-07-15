package app.arbre.ui.about

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.arbre.data.SpeciesIndex
import app.arbre.data.SpeciesPhotoRepository
import app.arbre.data.rememberSpeciesIndex
import app.arbre.data.rememberSpeciesPhotoRepository

/** Une ligne de crédit par photo cascade (Wikimedia / iNaturalist). */
data class CreditRow(
    val speciesName: String,
    val author: String,
    val licenseName: String,
    val url: String?,
)

/**
 * Modèle de l'écran crédits, dérivé une fois du repository photos. La source
 * Ville de Paris est repliée en bloc collectif (compteurs) — attribution ODbL
 * unique partagée — tandis que les cascades Wikimedia/iNaturalist sont listées
 * photo par photo (obligation d'attribution par auteur).
 */
data class PhotoCreditsModel(
    val parisPhotoCount: Int,
    val parisSpeciesCount: Int,
    val wikimediaRows: List<CreditRow>,
    val inatRows: List<CreditRow>,
)

private const val SRC_PARIS = "paris"
private const val SRC_WIKIMEDIA = "wikimedia-commons"
private const val SRC_INATURALIST = "inaturalist"
private const val LICENSE_ODBL = "odbl-1.0"

/**
 * Construit le modèle crédits (fonction pure, testable JVM). Paris → compteurs
 * collectifs (toutes photos, principales + détails). Cascades → une `CreditRow`
 * par photo, nom d'espèce résolu via l'index (`displayNomCommun` : nv → nom
 * commun → binôme), licence résolue via `licenseFor` (fallback = clé brute).
 * Tri final par (nom casse-insensible, sk) pour un ordre stable et déterministe.
 */
internal fun buildPhotoCreditsModel(
    photoRepo: SpeciesPhotoRepository,
    speciesIndex: SpeciesIndex,
): PhotoCreditsModel {
    var parisPhotoCount = 0
    var parisSpeciesCount = 0
    val wikimedia = mutableListOf<Pair<Int, CreditRow>>()
    val inat = mutableListOf<Pair<Int, CreditRow>>()

    for ((sk, photos) in photoRepo.all()) {
        if (photos.source == SRC_PARIS) {
            parisSpeciesCount++
            parisPhotoCount += photos.all.size
            continue
        }
        val name = speciesIndex.get(sk)?.displayNomCommun ?: "Espèce #$sk"
        for (photo in photos.all) {
            val licenseName = photoRepo.licenseFor(photo.license)?.name ?: photo.license
            val row = CreditRow(name, photo.author, licenseName, photo.sourceUrl)
            when (photo.source) {
                SRC_WIKIMEDIA -> wikimedia += sk to row
                SRC_INATURALIST -> inat += sk to row
            }
        }
    }
    return PhotoCreditsModel(
        parisPhotoCount = parisPhotoCount,
        parisSpeciesCount = parisSpeciesCount,
        wikimediaRows = wikimedia.sortedRows(),
        inatRows = inat.sortedRows(),
    )
}

/** Tri (nom casse-insensible, sk) puis projection sur la `CreditRow`. */
private fun List<Pair<Int, CreditRow>>.sortedRows(): List<CreditRow> =
    sortedWith(compareBy({ it.second.speciesName.lowercase() }, { it.first }))
        .map { it.second }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoCreditsScreen(onBack: () -> Unit) {
    val photoRepo = rememberSpeciesPhotoRepository()
    val speciesIndex = rememberSpeciesIndex()
    val model = remember(photoRepo, speciesIndex) { buildPhotoCreditsModel(photoRepo, speciesIndex) }
    val ctx = LocalContext.current
    val openUrl: (String) -> Unit = { url ->
        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Crédits photos") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Retour")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { ParisCollectiveCard(model, photoRepo, openUrl) }
            item { SectionHeader("Wikimedia Commons (${model.wikimediaRows.size})") }
            items(model.wikimediaRows) { CreditRowItem(it, openUrl) }
            item { SectionHeader("iNaturalist (${model.inatRows.size})") }
            items(model.inatRows) { CreditRowItem(it, openUrl) }
        }
    }
}

/**
 * Bloc collectif Ville de Paris : compteur global + auteurs du Guide des
 * essences (lus depuis `sourceFor("paris")`, jamais hardcodés) + ligne licence
 * ODbL cliquable (url depuis `licenseFor`).
 */
@Composable
private fun ParisCollectiveCard(
    model: PhotoCreditsModel,
    photoRepo: SpeciesPhotoRepository,
    onOpenUrl: (String) -> Unit,
) {
    val authors = photoRepo.sourceFor(SRC_PARIS)?.authors.orEmpty()
    val odblUrl = photoRepo.licenseFor(LICENSE_ODBL)?.url
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Ville de Paris — Guide des essences 2024", style = MaterialTheme.typography.titleSmall)
            Text(
                buildString {
                    append("${model.parisPhotoCount} photos")
                    if (authors.isNotEmpty()) append(" · Crédits ").append(authors)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LicenseLink("ODbL v1.0", odblUrl, onOpenUrl)
        }
    }
}

/** En-tête de section (Wikimedia / iNaturalist), style titleLarge. */
@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(top = 8.dp),
    )
}

/**
 * Ligne de crédit compacte (une par photo cascade) : « espèce — auteur » puis
 * lien licence cliquable, séparateur fin en bas. Plus léger qu'une Card pour
 * scroller ~350 lignes sans fatigue visuelle.
 */
@Composable
private fun CreditRowItem(row: CreditRow, onOpenUrl: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            "${row.speciesName} — ${row.author}",
            style = MaterialTheme.typography.bodyMedium,
        )
        LicenseLink(row.licenseName, row.url, onOpenUrl)
        HorizontalDivider(
            modifier = Modifier.padding(top = 6.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

/**
 * Libellé de licence : cliquable + OpenInNew (couleur primary) quand une URL
 * existe, sinon texte discret onSurfaceVariant. Modèle : la ligne licence de
 * `AttributionCard`.
 */
@Composable
private fun LicenseLink(licenseName: String, url: String?, onOpenUrl: (String) -> Unit) {
    if (url == null) {
        Text(
            licenseName,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clickable { onOpenUrl(url) }
            .padding(vertical = 4.dp),
    ) {
        Text(
            licenseName,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Icon(
            Icons.AutoMirrored.Outlined.OpenInNew,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp),
        )
    }
}
