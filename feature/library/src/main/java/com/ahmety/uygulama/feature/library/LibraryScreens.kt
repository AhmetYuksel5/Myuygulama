package com.ahmety.uygulama.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahmety.uygulama.core.model.Entry
import com.ahmety.uygulama.core.model.EntryType

@Composable
fun NotesRoute(
    onOpenNote: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val createdNoteId by viewModel.createdNoteId.collectAsStateWithLifecycle()

    // Yeni not oluşturulunca doğrudan editöre geçiyoruz; boş bir satır
    // listeye düşüp kullanıcının ayrıca dokunmasını beklemek fazladan adım olurdu.
    LaunchedEffect(createdNoteId) {
        createdNoteId?.let {
            viewModel.consumeCreatedNote()
            onOpenNote(it)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 16.dp,
                bottom = 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text("Notlar", style = MaterialTheme.typography.headlineSmall)
            }

            if (state.loaded && state.notes.isEmpty()) {
                item {
                    Text(
                        text = "Henüz not yok. Sağ alttaki düğmeyle ilk notunu yazabilirsin.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(state.notes, key = { it.id }) { entry ->
                EntryCard(
                    entry = entry,
                    onClick = { onOpenNote(entry.id) },
                    onArchive = { viewModel.archive(entry) },
                    onDelete = { viewModel.delete(entry) },
                )
            }
        }

        FloatingActionButton(
            onClick = { viewModel.createNote() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Not yaz")
        }
    }
}

/**
 * Pocket muadili: kaydedilen makaleler (oku-sonra). Arşivdeki notlardan ayrı
 * bir sekmede yaşıyor. URL yapıştırıp ya da başka bir uygulamadan paylaşıp
 * kaydedersin; sayfa okunabilir hâle getirilip çevrimdışı saklanır.
 */
@Composable
fun PocketRoute(
    onOpenArticle: (Long) -> Unit,
    onAddArticle: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 16.dp,
                bottom = 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text("Pocket", style = MaterialTheme.typography.headlineSmall)
            }

            if (state.loaded && state.articles.isEmpty()) {
                item {
                    Text(
                        text = "Henüz makale yok. Sağ alttaki düğmeden URL yapıştırabilir " +
                            "ya da herhangi bir uygulamadan bu uygulamaya paylaşabilirsin — " +
                            "sayfa okunabilir hâle getirilip çevrimdışı saklanır.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(state.articles, key = { it.id }) { entry ->
                EntryCard(
                    entry = entry,
                    onClick = { onOpenArticle(entry.id) },
                    onArchive = { viewModel.archive(entry) },
                    onDelete = { viewModel.delete(entry) },
                )
            }
        }

        FloatingActionButton(
            onClick = onAddArticle,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Makale kaydet")
        }
    }
}

@Composable
fun NoteEditorRoute(
    noteId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NoteEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var tagInput by remember { mutableStateOf("") }

    LaunchedEffect(noteId) { viewModel.load(noteId) }

    // Ekrandan çıkarken kaydediyoruz; not defterinde "kaydet" düğmesi olmamalı.
    androidx.compose.runtime.DisposableEffect(noteId) {
        onDispose { viewModel.save() }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = state.title,
            onValueChange = viewModel::onTitleChange,
            label = { Text("Başlık") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.body,
            onValueChange = viewModel::onBodyChange,
            label = { Text("Not") },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            state.tags.forEach { tag -> AssistChip(onClick = {}, label = { Text("#$tag") }) }
        }

        OutlinedTextField(
            value = tagInput,
            onValueChange = { tagInput = it },
            label = { Text("Etiket ekle") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(
                onClick = {
                    viewModel.addTag(tagInput)
                    tagInput = ""
                },
                label = { Text("Etiketle") },
            )
            AssistChip(onClick = onBack, label = { Text("Bitti") })
        }
    }
}

@Composable
fun SearchRoute(
    onOpenNote: (Long) -> Unit,
    onOpenArticle: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                viewModel.onQueryChange(it)
            },
            label = { Text("Her şeyde ara") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        when {
            !state.searched -> Text(
                text = "Notlar, makaleler, alıntılar, kelimeler ve görevler tek " +
                    "indeksten aranıyor.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            state.results.isEmpty() -> Text(
                text = "Sonuç yok.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.results, key = { it.id }) { entry ->
                    EntryCard(
                        entry = entry,
                        onClick = {
                            when (entry.type) {
                                EntryType.NOTE -> onOpenNote(entry.id)
                                EntryType.ARTICLE -> onOpenArticle(entry.id)
                                else -> Unit
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun EntryCard(
    entry: Entry,
    onClick: () -> Unit,
    onArchive: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.title.ifBlank { "(başlıksız)" },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (entry.body.isNotBlank()) {
                    Text(
                        text = entry.body,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val meta = buildList {
                    add(typeLabel(entry.type))
                    entry.tags.forEach { add("#${it.name}") }
                }.joinToString(" · ")
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            if (onArchive != null || onDelete != null) {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "Seçenekler")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        onArchive?.let { action ->
                            DropdownMenuItem(
                                text = { Text(if (entry.archived) "Arşivden çıkar" else "Arşivle") },
                                onClick = {
                                    menuOpen = false
                                    action()
                                },
                            )
                        }
                        onDelete?.let { action ->
                            DropdownMenuItem(
                                text = { Text("Sil") },
                                onClick = {
                                    menuOpen = false
                                    action()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun typeLabel(type: EntryType): String = when (type) {
    EntryType.NOTE -> "Not"
    EntryType.ARTICLE -> "Makale"
    EntryType.DOCUMENT -> "Doküman"
    EntryType.HIGHLIGHT -> "Alıntı"
    EntryType.WORD -> "Kelime"
    EntryType.TASK -> "Görev"
    EntryType.NEWS -> "Haber"
}
