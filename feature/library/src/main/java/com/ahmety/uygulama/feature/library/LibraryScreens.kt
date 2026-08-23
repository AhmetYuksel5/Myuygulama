@file:OptIn(ExperimentalFoundationApi::class)

package com.ahmety.uygulama.feature.library

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.ahmety.uygulama.core.designsystem.MerkezIcons
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import com.ahmety.uygulama.core.model.Entry
import com.ahmety.uygulama.core.model.EntryType
import kotlinx.coroutines.launch

/**
 * Keep benzeri not panosu: renkli kartlar, iki sütunlu serbest yükseklikli
 * dizilim, karttan doğrudan işaretlenebilen liste maddeleri ve fotoğraflar.
 */
@Composable
fun NotesRoute(
    onOpenNote: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val createdNoteId by viewModel.createdNoteId.collectAsStateWithLifecycle()
    var showArchive by remember { mutableStateOf(false) }

    // Yeni not oluşturulunca doğrudan editöre geçiyoruz; boş bir satır
    // listeye düşüp kullanıcının ayrıca dokunmasını beklemek fazladan adım olurdu.
    LaunchedEffect(createdNoteId) {
        createdNoteId?.let {
            viewModel.consumeCreatedNote()
            onOpenNote(it)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 96.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, bottom = 12.dp),
            ) {
                Text(
                    text = if (showArchive) "Arşiv" else "Notlar",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                // Arşiv, gösterilecek bir yer olmadan sessiz silmeye dönüşüyordu.
                if (state.archivedNotes.isNotEmpty() || showArchive) {
                    OutlinedButton(onClick = { showArchive = !showArchive }) {
                        Text(
                            if (showArchive) {
                                "Notlara dön"
                            } else {
                                "Arşiv (${state.archivedNotes.size})"
                            },
                        )
                    }
                }
            }

            if (showArchive && state.archivedNotes.isEmpty()) {
                Text(
                    text = "Arşivde not yok.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(4.dp),
                )
            }

            if (!showArchive && state.loaded && state.notes.isEmpty()) {
                Text(
                    text = "Henüz not yok. Sağ alttaki düğmeyle ilk notunu yazabilirsin.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(4.dp),
                )
            }

            // İki sütunlu duvar dizilimi: kartlar kendi yüksekliklerinde kalıyor.
            // (Lazy bir ızgara yerine düz Column; kişisel not sayısı için fazlasıyla yeterli
            // ve sürüm farkı olan deneysel API'lere bağımlılık getirmiyor.)
            val visible = if (showArchive) state.archivedNotes else state.notes
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(0, 1).forEach { column ->
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        visible.filterIndexed { index, _ -> index % 2 == column }
                            .forEach { note ->
                                NoteCard(
                                    note = note,
                                    archived = showArchive,
                                    onOpen = { onOpenNote(note.id) },
                                    onTogglePin = { viewModel.togglePinned(note) },
                                    onToggleArchive = { viewModel.archive(note) },
                                    onDelete = { viewModel.delete(note) },
                                    onToggleItem = { index -> viewModel.toggleChecklist(note, index) },
                                )
                            }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { viewModel.createNote() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            Icon(MerkezIcons.Add, contentDescription = "Not yaz")
        }
    }
}

@Composable
private fun NoteCard(
    note: Entry,
    archived: Boolean,
    onOpen: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleArchive: () -> Unit,
    onDelete: () -> Unit,
    onToggleItem: (Int) -> Unit,
) {
    val style = remember(note.source) { NoteStyle.decode(note.source) }
    val background = noteColor(style.colorIndex)
    val textColor = noteTextColor(style.colorIndex)
    var menuOpen by remember { mutableStateOf(false) }

    val items = remember(note.body) { parseChecklist(note.body) }
    val images = remember(note.body) { parseImagePaths(note.body) }
    val text = remember(note.body) { plainBody(note.body) }

    Surface(
        color = background,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onOpen, onLongClick = { menuOpen = true }),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            images.firstOrNull()?.let { path ->
                NoteImage(
                    path = path,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                )
            }

            if (note.title.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = note.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = textColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (style.pinned) {
                        Text(
                            text = " 📌",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
            }

            if (text.isNotBlank()) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.85f),
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis,
                )
                if (items.isNotEmpty()) Spacer(Modifier.height(6.dp))
            }

            // Liste maddeleri karttan doğrudan işaretlenebiliyor — Keep'te olduğu gibi.
            items.take(8).forEachIndexed { index, item ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleItem(index) },
                ) {
                    Checkbox(
                        checked = item.checked,
                        onCheckedChange = { onToggleItem(index) },
                        modifier = Modifier.size(28.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = item.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (item.checked) textColor.copy(alpha = 0.5f) else textColor,
                        textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (items.size > 8) {
                Text(
                    text = "+${items.size - 8} madde daha",
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.7f),
                )
            }

            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (!archived) {
                    DropdownMenuItem(
                        text = { Text(if (style.pinned) "Sabitlemeyi kaldır" else "Üste sabitle") },
                        onClick = {
                            menuOpen = false
                            onTogglePin()
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text(if (archived) "Arşivden çıkar" else "Arşivle") },
                    onClick = {
                        menuOpen = false
                        onToggleArchive()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Sil") },
                    onClick = {
                        menuOpen = false
                        onDelete()
                    },
                )
            }
        }
    }
}

@Composable
private fun NoteImage(path: String, modifier: Modifier = Modifier) {
    val bitmap = rememberNoteImage(path)
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .height(120.dp)
                .clip(RoundedCornerShape(10.dp)),
        )
    }
}

/** Pocket muadili: kaydedilen makaleler (oku-sonra). */
@Composable
fun PocketRoute(
    onOpenArticle: (Long) -> Unit,
    onAddArticle: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showHighlights by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = if (showHighlights) "Alıntılar" else "Pocket",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f),
                    )
                    // Alıntılar aksi hâlde yalnızca makalenin içinde görünür,
                    // "kaydettim ama nerede?" hissi doğuyordu.
                    if (state.highlights.isNotEmpty() || showHighlights) {
                        OutlinedButton(onClick = { showHighlights = !showHighlights }) {
                            Text(
                                if (showHighlights) {
                                    "Makaleler"
                                } else {
                                    "Alıntılar (${state.highlights.size})"
                                },
                            )
                        }
                    }
                }
            }

            if (showHighlights) {
                if (state.highlights.isEmpty()) {
                    item {
                        Text(
                            text = "Alıntı yok. Bir makalede paragrafa uzun basarak alıntıla.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(state.highlights, key = { it.id }) { entry ->
                    EntryCard(
                        entry = entry,
                        onClick = {
                            entry.source
                                ?.removePrefix("article:")
                                ?.toLongOrNull()
                                ?.let(onOpenArticle)
                        },
                        onDelete = { viewModel.delete(entry) },
                    )
                }
            } else {
                if (state.loaded && state.articles.isEmpty()) {
                    item {
                        Text(
                            text = "Henüz makale yok. Sağ alttaki düğmeden bir URL yapıştır — " +
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
                        onDelete = { viewModel.delete(entry) },
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = onAddArticle,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            Icon(MerkezIcons.Add, contentDescription = "Makale kaydet")
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val path = copyImageToNotes(context, uri)
                if (path != null) {
                    viewModel.addImage(path)
                } else {
                    Toast.makeText(context, "Fotoğraf eklenemedi", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LaunchedEffect(noteId) { viewModel.load(noteId) }

    // Ekrandan çıkarken kaydediyoruz; not defterinde "kaydet" düğmesi olmamalı.
    androidx.compose.runtime.DisposableEffect(noteId) {
        onDispose { viewModel.save() }
    }

    // Düz kaydırılabilir sütun (tembel liste değil): madde ekranın dışına
    // kayınca yok edilip yazdığın alanın odağı kaybolmasın. Not başına madde
    // sayısı az olduğu için geri dönüşüme gerek yok.
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(noteColor(state.colorIndex))
            // imePadding kaydırmadan önce: klavye alanı kaydırılabilir
            // içeriğin içine değil, kaydırma penceresinin kendisine
            // uygulanmalı; aksi hâlde son maddeye odaklanınca zıplıyor.
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        run {
            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::onTitleChange,
                label = { Text("Başlık") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (state.images.isNotEmpty()) {
            run {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.images.forEach { path ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            NoteImage(path = path, modifier = Modifier.width(150.dp))
                            TextButton(onClick = { viewModel.removeImage(path) }) {
                                Text("Kaldır", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }

        run {
            OutlinedTextField(
                value = state.plain,
                onValueChange = viewModel::onPlainChange,
                label = { Text("Not") },
                minLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Liste maddeleri: ham "[ ] madde" metni yerine gerçek kutucuklar.
        state.items.forEachIndexed { index, item ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Checkbox(
                    checked = item.checked,
                    onCheckedChange = { viewModel.toggleItem(index) },
                )
                OutlinedTextField(
                    value = item.text,
                    onValueChange = { viewModel.onItemTextChange(index, it) },
                    placeholder = { Text("Madde") },
                    singleLine = true,
                    textStyle = if (item.checked) {
                        LocalTextStyle.current.copy(textDecoration = TextDecoration.LineThrough)
                    } else {
                        LocalTextStyle.current
                    },
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { viewModel.removeItem(index) }) {
                    Icon(MerkezIcons.Close, contentDescription = "Maddeyi sil")
                }
            }
        }

        run {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { viewModel.addChecklistItem() }) {
                    Text("Madde ekle")
                }
                OutlinedButton(onClick = { pickImage.launch("image/*") }) {
                    Text("Fotoğraf")
                }
            }
        }

        Text("Renk", style = MaterialTheme.typography.labelLarge)
        run {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (0 until NOTE_COLOR_COUNT).forEach { index ->
                    val selected = index == state.colorIndex
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .background(noteColor(index), CircleShape)
                            .then(
                                if (selected) {
                                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                } else {
                                    Modifier
                                },
                            )
                            .clickable { viewModel.setColor(index) },
                    )
                }
            }
        }

        if (state.tags.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.tags.forEach { tag -> AssistChip(onClick = {}, label = { Text("#$tag") }) }
            }
        }

        run {
            OutlinedTextField(
                value = tagInput,
                onValueChange = { tagInput = it },
                label = { Text("Etiket ekle") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
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
                                // Alıntı, alındığı makaleyi açar; aksi hâlde
                                // sonuca dokunmak hiçbir şey yapmıyordu.
                                EntryType.HIGHLIGHT -> entry.source
                                    ?.removePrefix("article:")
                                    ?.toLongOrNull()
                                    ?.let(onOpenArticle)
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

            if (onDelete != null) {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(MerkezIcons.MoreVert, contentDescription = "Seçenekler")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Sil") },
                            onClick = {
                                menuOpen = false
                                onDelete()
                            },
                        )
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
