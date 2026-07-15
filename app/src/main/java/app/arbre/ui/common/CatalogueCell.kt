package app.arbre.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.arbre.data.CataloguePhotos
import app.arbre.data.SpeciesEntry

/**
 * Card carrée affichant une entrée du Catalogue : numéro Pokédex (ou rang),
 * photo de référence de l'espèce si elle existe (`photos.referencePath`,
 * asset WebP — même visuel que le hero de la fiche), sinon photo 1re capture,
 * sinon silhouette « ? » ; nom commun (ou « ??? »), binôme latin italique
 * en sous-titre (si `nv` a enrichi).
 *
 * Réutilisé par :
 * - `ArboretumScreen.CatalogueView` : grille 3 colonnes, partition
 *   identifiées / `unknownSpecies`.
 * - `GenreDetailScreen` mini-catalogue genre : section « espèces du genre »
 *   — donne la vue « j'ai 3/55 chênes » sans quitter l'écran.
 *
 * Le param `count` (count Paris) est optionnel : `null` côté Arboretum
 * (tri Pokédex/count décide déjà du contexte), affiché côté mini-catalogue
 * genre pour donner du grain.
 */
@Composable
fun CatalogueCell(
    displayLabel: String,
    entry: SpeciesEntry,
    photos: CataloguePhotos,
    discovered: Boolean,
    onClick: (() -> Unit)?,
    count: Int? = null,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
        colors = CardDefaults.cardColors(
            containerColor = if (discovered) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                displayLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                // Garde anti-spoiler : la photo de référence n'est rendue que
                // découverte, quoi que passe l'appelant — une espèce non
                // capturée reste silhouette (décision produit, cf. ROADMAP
                // Herbier ; les cellules « ??? » deviennent tappables en S12).
                val referencePath = photos.referencePath.takeIf { discovered }
                if (referencePath != null) {
                    AssetImage(
                        assetPath = referencePath,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else if (photos.captureFile != null) {
                    PhotoThumbnail(
                        photoFile = photos.captureFile,
                        sampleSize = 4,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    // Slot photo silhouette : couvre les non-découverts ET les
                    // `unknownSpecies` débloqués genre-based (titre `nv` montré
                    // mais photo seulement si capture explicite `sp.`).
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "?",
                            style = MaterialTheme.typography.displayMedium,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }
            Text(
                if (discovered) entry.displayNomCommun else "???",
                style = MaterialTheme.typography.bodySmall,
                color = if (discovered) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Start,
            )
            // Sous-titre binôme italique : seulement si `nv` a apporté une
            // valeur (sinon le titre EST déjà le binôme, redondance évitée).
            if (discovered && entry.nv != null) {
                Text(
                    entry.displayName,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontStyle = FontStyle.Italic,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (count != null && count > 0) {
                Text(
                    "$count à Paris",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
