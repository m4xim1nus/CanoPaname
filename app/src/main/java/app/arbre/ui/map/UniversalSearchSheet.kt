package app.arbre.ui.map

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun UniversalSearchSheet(
    data: SearchData,
    onSpeciesTap: (Int) -> Unit,
    onGenreTap: (String) -> Unit,
    onArrTap: (ArrSearchItem) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusRequester = remember { FocusRequester() }

    var q by remember { mutableStateOf("") }

    val filteredSpecies by remember(data) {
        derivedStateOf {
            val nq = normalizeQuery(q)
            if (nq.isEmpty()) data.species
            else data.species.filter { nq in it.haystack }
        }
    }
    val filteredGenres by remember(data) {
        derivedStateOf {
            val nq = normalizeQuery(q)
            if (nq.isEmpty()) data.genres
            else data.genres.filter { nq in it.haystack }
        }
    }
    val filteredArrs by remember(data) {
        derivedStateOf {
            val nq = normalizeQuery(q)
            val parsed = parseArrQuery(q)
            val matched = parsed?.let { p -> data.arrs.firstOrNull { it.key == p } }
            val lexical = if (nq.isEmpty()) data.arrs
            else data.arrs.filter { nq in it.haystack }
            if (matched != null) {
                listOf(matched) + lexical.filter { it.key != matched.key }
            } else {
                lexical
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        // `fillMaxSize()` sur le Column + `weight(1f)` sur la LazyColumn :
        // sans contrainte, le `ModalBottomSheet` s'adapte à la taille de son
        // contenu — qui était ici TextField + LazyColumn bornée à 520 dp =
        // ~600 dp, soit ~75 % de l'écran sur un téléphone moderne. Le user
        // voyait ce contenu compact comme une « ouverture à 3/4 », même avec
        // `skipPartiallyExpanded = true` (qui ne touche qu'aux anchors, pas
        // à la taille du contenu). En forçant le contenu à occuper toute la
        // hauteur disponible, le sheet est toujours plein écran, IME ou pas.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
        ) {
            OutlinedTextField(
                value = q,
                onValueChange = { q = it },
                singleLine = true,
                placeholder = { Text("espèce, genre ou arrondissement") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )

            LazyColumn(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .weight(1f),
            ) {
                if (filteredSpecies.isNotEmpty()) {
                    stickyHeader { SectionHeader("Espèces", filteredSpecies.size) }
                    items(
                        items = filteredSpecies,
                        key = { "sp-${it.sk}" },
                    ) { item ->
                        SpeciesRow(item, onClick = { onSpeciesTap(item.sk) })
                    }
                }
                if (filteredGenres.isNotEmpty()) {
                    stickyHeader { SectionHeader("Genres", filteredGenres.size) }
                    items(
                        items = filteredGenres,
                        key = { "g-${it.genre}" },
                    ) { item ->
                        GenreRow(item, onClick = { onGenreTap(item.genre) })
                    }
                }
                if (filteredArrs.isNotEmpty()) {
                    stickyHeader { SectionHeader("Arrondissements", filteredArrs.size) }
                    items(
                        items = filteredArrs,
                        key = { "a-${it.label}" },
                    ) { item ->
                        ArrRow(item, onClick = { onArrTap(item) })
                    }
                }
                if (filteredSpecies.isEmpty() && filteredGenres.isEmpty() && filteredArrs.isEmpty()) {
                    item {
                        Text(
                            text = "Aucun résultat",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    }
                }
            }
        }
    }

    // Focus auto sur le champ de recherche au mount. `focusRequester.requestFocus()`
    // déclenche automatiquement l'apparition de l'IME sur Android 14+
    // (`showSoftInputOnFocus = true`, pas opt-out exposé en Material 3). Compromis
    // accepté : quand l'IME est visible, Android route le 1er back-press vers
    // `ImeBackAnimationController` (priorité système, on l'a vérifié via logcat),
    // donc 2 back pour fermer (1 ferme l'IME, 1 ferme le sheet). C'est le
    // comportement Android natif universel (Google Maps, Apple Plans, formulaires
    // système…). Le bug visuel précédent — sheet « rabougri » à 3/4 quand l'IME
    // se fermait — est neutralisé par le `fillMaxSize()` du Column conteneur :
    // le sheet est toujours plein écran, IME ou pas.
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

@Composable
private fun SectionHeader(label: String, count: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Text(
            text = "$label ($count)",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
        )
        HorizontalDivider()
    }
}

@Composable
private fun SpeciesRow(item: SpeciesSearchItem, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        Text(
            text = item.display,
            style = MaterialTheme.typography.bodyLarge,
        )
        if (item.latin != item.display) {
            Text(
                text = item.latin,
                style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GenreRow(item: GenreSearchItem, onClick: () -> Unit) {
    Text(
        text = item.display,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    )
}

@Composable
private fun ArrRow(item: ArrSearchItem, onClick: () -> Unit) {
    Text(
        text = item.label,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    )
}
