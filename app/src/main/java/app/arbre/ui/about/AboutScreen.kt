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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.arbre.BuildConfig

private const val REPO_URL = "https://github.com/m4xim1nus/CanoPaname"
private const val NOTICE_URL = "https://github.com/m4xim1nus/CanoPaname/blob/main/NOTICE.md"

private data class Attribution(
    val label: String,
    val detail: String,
    val licenseLabel: String,
    val licenseUrl: String,
)

private val attributions = listOf(
    Attribution(
        label = "Données arbres",
        detail = "Ville de Paris — datasets « les-arbres » et « arbres remarquables » via OpenData Paris.",
        licenseLabel = "ODbL v1.0",
        licenseUrl = "https://opendatacommons.org/licenses/odbl/1-0/",
    ),
    Attribution(
        label = "Tuiles cartographiques",
        detail = "OpenFreeMap (MIT) sur fond OpenStreetMap contributors (ODbL).",
        licenseLabel = "OpenStreetMap copyright",
        licenseUrl = "https://www.openstreetmap.org/copyright",
    ),
    Attribution(
        label = "Résumés d'espèces",
        detail = "Wikipédia francophone — contributeurs Wikipédia.",
        licenseLabel = "CC BY-SA 4.0",
        licenseUrl = "https://creativecommons.org/licenses/by-sa/4.0/",
    ),
    Attribution(
        label = "Police Fraunces",
        detail = "Undercase Type — Phaedra Charles & Flavia Zimbardi.",
        licenseLabel = "SIL Open Font License 1.1",
        licenseUrl = "https://openfontlicense.org/",
    ),
    Attribution(
        label = "MapLibre Native Android",
        detail = "Rendu vectoriel de la carte plein-écran.",
        licenseLabel = "BSD-2-Clause",
        licenseUrl = "https://github.com/maplibre/maplibre-native/blob/main/LICENSE.md",
    ),
    Attribution(
        label = "AndroidX, Compose, Material 3, Kotlin, Room",
        detail = "Briques applicatives Google et JetBrains.",
        licenseLabel = "Apache 2.0",
        licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val openUrl: (String) -> Unit = { url ->
        runCatching {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("À propos") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Retour",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(PaddingValues(horizontal = 16.dp, vertical = 12.dp)),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            IdentityBlock()
            LinkCard(
                title = "Code source",
                subtitle = "github.com/m4xim1nus/CanoPaname",
                icon = Icons.Outlined.Code,
                onClick = { openUrl(REPO_URL) },
            )
            DisclaimerBlock()
            AttributionsBlock(onOpenLicense = openUrl)
            LinkCard(
                title = "Toutes les attributions et licences",
                subtitle = "NOTICE.md sur GitHub",
                icon = Icons.Outlined.Description,
                onClick = { openUrl(NOTICE_URL) },
            )
        }
    }
}

@Composable
private fun IdentityBlock() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "CanoPaname",
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            "Pokédex botanique des arbres de Paris.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DisclaimerBlock() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Text(
            "CanoPaname est un projet indépendant, non affilié à la Ville de Paris.",
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun AttributionsBlock(onOpenLicense: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Attributions",
            style = MaterialTheme.typography.titleLarge,
        )
        attributions.forEach { item ->
            AttributionCard(item, onOpenLicense = onOpenLicense)
        }
    }
}

@Composable
private fun AttributionCard(item: Attribution, onOpenLicense: (String) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(item.label, style = MaterialTheme.typography.titleSmall)
            Text(
                item.detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenLicense(item.licenseUrl) }
                    .padding(vertical = 4.dp),
            ) {
                Text(
                    item.licenseLabel,
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
    }
}

@Composable
private fun LinkCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(icon, contentDescription = null)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Outlined.OpenInNew,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
