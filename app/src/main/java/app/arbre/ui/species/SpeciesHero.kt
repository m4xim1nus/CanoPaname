package app.arbre.ui.species

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import app.arbre.data.Arbre
import app.arbre.data.HeroPhotos
import app.arbre.data.SpeciesEntry
import app.arbre.data.SpeciesPhotos
import app.arbre.ui.common.AssetImage

/**
 * Hero de la fiche espèce. Deux modes selon la couverture photo (S9/S10) :
 * - `hero == null` (espèce non illustrée) → `IdentityBlock` texte historique
 *   inchangé (Card primaryContainer, eyebrow rang + nom + binôme).
 * - `hero != null` → une seule Card : mosaïque de référence (220 dp) façon
 *   collage Guide des essences, puis le même bloc identité, puis une ligne
 *   d'attribution discrète (obligation CC-BY). L'identité vit **sous** la photo
 *   (pas de scrim sur l'image, lisibilité Fraunces). Les index de tuile sont
 *   alignés sur `photos.all` (0 = principale) pour la lightbox.
 */
@Composable
fun SpeciesHero(
    entry: SpeciesEntry,
    sample: Arbre?,
    catalogueRank: Int?,
    catalogueTotal: Int,
    hero: HeroPhotos?,
    onPhotoClick: (Int) -> Unit,
    onOpenSource: (String) -> Unit,
) {
    if (hero == null) {
        IdentityBlock(entry, sample, catalogueRank, catalogueTotal)
        return
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        PhotoMosaic(hero.photos, entry.displayNomCommun, onPhotoClick)
        IdentityContent(entry, sample, catalogueRank, catalogueTotal)
        HeroAttributionLine(hero.photos, hero.licenseName, onOpenSource)
    }
}

/**
 * Mosaïque de référence (gouttières 2 dp) dans un Box de 220 dp : N=1 →
 * principale pleine largeur ; N≥2 → principale `weight(2f)` à gauche + colonne
 * `weight(1f)` de 1-3 détails à droite. Pas de coins arrondis par tuile — la
 * Card parente clippe déjà. contentDescription : nom de l'espèce sur la
 * principale, `null` sur les détails (décoratifs).
 */
@Composable
private fun PhotoMosaic(
    photos: SpeciesPhotos,
    speciesName: String,
    onPhotoClick: (Int) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
    ) {
        if (photos.details.isEmpty()) {
            MosaicTile(photos.principal.assetPath, speciesName, 0, onPhotoClick, Modifier.fillMaxSize())
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                MosaicTile(
                    photos.principal.assetPath,
                    speciesName,
                    index = 0,
                    onPhotoClick = onPhotoClick,
                    modifier = Modifier
                        .weight(2f)
                        .fillMaxHeight(),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    photos.details.forEachIndexed { i, detail ->
                        MosaicTile(
                            detail.assetPath,
                            contentDescription = null,
                            index = i + 1,
                            onPhotoClick = onPhotoClick,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MosaicTile(
    assetPath: String,
    contentDescription: String?,
    index: Int,
    onPhotoClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    AssetImage(
        assetPath = assetPath,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        sampleSize = 2,
        modifier = modifier.clickable { onPhotoClick(index) },
    )
}

/**
 * Bloc identité texte sur fond primaryContainer, réutilisé tel quel pour les
 * espèces non illustrées. Le contenu (eyebrow rang + nom + binôme) est mutualisé
 * avec le mode photo via `IdentityContent`.
 */
@Composable
private fun IdentityBlock(
    entry: SpeciesEntry,
    sample: Arbre?,
    catalogueRank: Int?,
    catalogueTotal: Int,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        IdentityContent(entry, sample, catalogueRank, catalogueTotal)
    }
}

/**
 * Colonne de textes identité, sans Card ni fond propre : suppose un parent
 * primaryContainer (les deux modes du hero le sont). Eyebrow rang `#N / M`
 * (masqué pour les `unknownSpecies` sans rang), nom commun titleLarge, binôme
 * latin italique quand le titre ne l'est pas déjà.
 */
@Composable
private fun IdentityContent(
    entry: SpeciesEntry,
    sample: Arbre?,
    catalogueRank: Int?,
    catalogueTotal: Int,
) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (catalogueRank != null) {
            // Eyebrow : rang 1-based partagé avec `ArboretumScreen.CatalogueView`.
            Text(
                "#$catalogueRank / $catalogueTotal",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
            )
        }
        Text(
            entry.displayNomCommun,
            style = MaterialTheme.typography.titleLarge,
        )
        // Sous-titre binôme italique : seulement quand le titre vient de
        // `nv` ou `nomCommun` (sinon le titre EST le binôme, redondance).
        if (entry.nv != null || entry.nomCommun != null || sample?.nomCommun != null) {
            Text(
                entry.displayName,
                style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

/**
 * Ligne d'attribution discrète sous l'identité (obligation CC-BY : crédit
 * visible). « Photo : {auteur} · {licence} » (ou « Photos : … » si plusieurs),
 * en onPrimaryContainer atténué (le fond de la Card est primaryContainer), plus
 * un OpenInNew cliquable vers la source quand une URL existe. Modèle : la ligne
 * source de `WikipediaBlock`.
 */
@Composable
private fun HeroAttributionLine(
    photos: SpeciesPhotos,
    licenseName: String?,
    onOpenSource: (String) -> Unit,
) {
    val prefix = if (photos.all.size > 1) "Photos" else "Photo"
    val label = buildString {
        append(prefix).append(" : ").append(photos.author)
        if (licenseName != null) append(" · ").append(licenseName)
    }
    val tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
    val url = photos.sourceUrl
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (url != null) Modifier.clickable { onOpenSource(url) } else Modifier)
            .padding(horizontal = 16.dp)
            .padding(bottom = 12.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = tint,
            modifier = Modifier.weight(1f),
        )
        if (url != null) {
            Icon(
                Icons.AutoMirrored.Outlined.OpenInNew,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}
