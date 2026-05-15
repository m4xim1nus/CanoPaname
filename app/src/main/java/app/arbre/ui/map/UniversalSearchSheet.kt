package app.arbre.ui.map

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
    val keyboard = LocalSoftwareKeyboardController.current

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
        Column(
            modifier = Modifier
                .fillMaxWidth()
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
                    .heightIn(max = 520.dp),
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

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
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
