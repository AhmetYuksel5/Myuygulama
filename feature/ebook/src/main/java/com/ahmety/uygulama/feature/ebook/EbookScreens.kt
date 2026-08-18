package com.ahmety.uygulama.feature.ebook

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahmety.uygulama.core.model.Entry
import com.ahmety.uygulama.core.model.HighlightColor

/** Renklerin ekrandaki karşılığı. */
@Composable
fun highlightPaint(color: HighlightColor): Color = when (color) {
    HighlightColor.YELLOW -> Color(0xFFFFE082)
    HighlightColor.BLUE -> Color(0xFF90CAF9)
    HighlightColor.GREEN -> Color(0xFFA5D6A7)
    HighlightColor.RED -> Color(0xFFEF9A9A)
}

/** Kitaplık: EPUB yükle, kitapları listele. */
@Composable
fun BookShelfRoute(
    onOpenBook: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BookShelfViewModel = hiltViewModel(),
) {
    val books by viewModel.books.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Belge seçici: EPUB'ların MIME türü cihazdan cihaza değiştiği için
    // her dosyayı seçtirip doğrulamayı ayrıştırıcıya bırakıyoruz.
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) viewModel.import(uri) }

    LaunchedEffect(state.openBookId) {
        state.openBookId?.let {
            viewModel.consumeOpenRequest()
            onOpenBook(it)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Kitaplık", style = MaterialTheme.typography.headlineSmall)

        Button(
            onClick = { picker.launch(arrayOf("*/*")) },
            enabled = !state.importing,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.importing) "Ekleniyor…" else "EPUB yükle")
        }
        if (state.importing) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp))
        }
        state.message?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable { viewModel.clearMessage() },
            )
        }

        if (books.isEmpty()) {
            Text(
                text = "Henüz kitap yok. EPUB dosyanı yükle; okurken kelimelere " +
                    "dokunarak sarı, mavi, yeşil, kırmızı işaretleyebilirsin. " +
                    "Mavi işaretlediklerin kelime çalışmasına düşer.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(books, key = { it.id }) { book ->
                BookCard(
                    book = book,
                    onOpen = { onOpenBook(book.id) },
                    onDelete = { viewModel.delete(book) },
                )
            }
        }
    }
}

@Composable
private fun BookCard(book: Entry, onOpen: () -> Unit, onDelete: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = book.title.ifBlank { "(adsız kitap)" },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (book.body.isNotBlank()) {
                    Text(
                        text = book.body,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TextButton(onClick = { confirmDelete = true }) { Text("Sil") }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Kitap silinsin mi?") },
            text = { Text("Kitap ve metni silinir. İşaretlemelerin kalır.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("Sil") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Vazgeç") }
            },
        )
    }
}

/** Okuma ekranı: bölüm seçimi + kelimeye dokunup renkle işaretleme. */
@Composable
fun BookReaderRoute(
    bookId: Long,
    modifier: Modifier = Modifier,
    viewModel: BookReaderViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(bookId) { viewModel.load(bookId) }

    var pending by remember { mutableStateOf<PendingHighlight?>(null) }

    when {
        state.loading -> Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        state.book == null -> Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Kitap açılamadı. Dosya silinmiş olabilir; yeniden yükle.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(24.dp),
            )
        }

        else -> {
            val book = state.book!!
            val chapter = book.chapters.getOrNull(state.chapterIndex)

            Column(modifier = modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    book.chapters.forEachIndexed { index, item ->
                        FilterChip(
                            selected = index == state.chapterIndex,
                            onClick = { viewModel.selectChapter(index) },
                            label = {
                                Text(
                                    text = item.title.ifBlank { "Bölüm ${index + 1}" },
                                    maxLines = 1,
                                )
                            },
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Text(
                            text = "Bir kelimeye dokun → rengini seç.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(chapter?.paragraphs.orEmpty()) { paragraph ->
                        HighlightableParagraph(
                            paragraph = paragraph,
                            colors = state.highlightColors,
                            onWordTapped = { word, sentence ->
                                pending = PendingHighlight(word, sentence)
                            },
                        )
                    }
                }
            }
        }
    }

    pending?.let { request ->
        ColorPickerDialog(
            request = request,
            current = state.highlightColors[request.word.lowercase()],
            onDismiss = { pending = null },
            onPick = { color, keepContext ->
                viewModel.highlight(
                    word = request.word,
                    contextSentence = if (keepContext) request.sentence else "",
                    color = color,
                )
                pending = null
            },
        )
    }
}

data class PendingHighlight(val word: String, val sentence: String)

/**
 * Paragrafı çizer ve dokunulan kelimeyi bulur.
 *
 * Metin tek bir [Text] olarak çiziliyor; dokunma noktası, yerleşim sonucundan
 * karakter konumuna çevrilip kelime sınırları bulunuyor. Böylece kelimeleri
 * ayrı ayrı bileşenlere bölmeden, akıcı bir okuma metni korunuyor.
 */
@Composable
private fun HighlightableParagraph(
    paragraph: String,
    colors: Map<String, HighlightColor>,
    onWordTapped: (word: String, sentence: String) -> Unit,
) {
    var layout by remember(paragraph) { mutableStateOf<TextLayoutResult?>(null) }

    val painted: AnnotatedString = remember(paragraph, colors) {
        buildAnnotatedString {
            append(paragraph)
            forEachWord(paragraph) { start, end ->
                val word = paragraph.substring(start, end).lowercase()
                colors[word]?.let { color ->
                    addStyle(SpanStyle(background = paintOf(color)), start, end)
                }
            }
        }
    }

    Text(
        text = painted,
        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 30.sp),
        onTextLayout = { layout = it },
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(paragraph) {
                detectTapGestures { position ->
                    val result = layout ?: return@detectTapGestures
                    val offset = result.getOffsetForPosition(position)
                    val bounds = wordBoundsAt(paragraph, offset) ?: return@detectTapGestures
                    val word = paragraph.substring(bounds.first, bounds.second)
                    onWordTapped(word, sentenceAround(paragraph, bounds.first))
                }
            },
    )
}

@Composable
private fun ColorPickerDialog(
    request: PendingHighlight,
    current: HighlightColor?,
    onDismiss: () -> Unit,
    onPick: (HighlightColor, Boolean) -> Unit,
) {
    // Bağlam varsayılan olarak açık: kullanıcı bir kelimenin birden çok
    // anlamı olabildiği için cümlesiyle birlikte saklamak istiyor.
    var keepContext by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(request.word) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (current != null) {
                    Text(
                        text = "Şu an: ${current.label}. Aynı renge dokunmak işareti kaldırır.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HighlightColor.entries.forEach { color ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(color, keepContext) }
                            .padding(vertical = 6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .background(highlightPaint(color), CircleShape),
                        )
                        Spacer(Modifier.size(12.dp))
                        Text(color.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }

                if (request.sentence.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = keepContext, onCheckedChange = { keepContext = it })
                        Text(
                            text = "Cümleyi de sakla",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Text(
                        text = request.sentence,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(8.dp),
                            )
                            .padding(8.dp),
                    )
                }
                Spacer(Modifier.height(2.dp))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Kapat") } },
    )
}

// --- Metin yardımcıları (saf fonksiyonlar) ---

private fun isWordChar(c: Char): Boolean = c.isLetter() || c == '\'' || c == '-' || c == '’'

/** [offset] konumundaki kelimenin [start, end) sınırları; kelime değilse null. */
internal fun wordBoundsAt(text: String, offset: Int): Pair<Int, Int>? {
    if (text.isEmpty()) return null
    var index = offset.coerceIn(0, text.length - 1)
    if (!isWordChar(text[index])) {
        // Kelimenin hemen sonrasına dokunulmuş olabilir.
        if (index > 0 && isWordChar(text[index - 1])) index -= 1 else return null
    }
    var start = index
    while (start > 0 && isWordChar(text[start - 1])) start--
    var end = index + 1
    while (end < text.length && isWordChar(text[end])) end++
    val word = text.substring(start, end).trim('\'', '-', '’')
    return if (word.length < 2) null else start to end
}

/** Paragraf içinde her kelimeyi dolaşır. */
internal inline fun forEachWord(text: String, action: (start: Int, end: Int) -> Unit) {
    var i = 0
    while (i < text.length) {
        if (isWordChar(text[i])) {
            val start = i
            while (i < text.length && isWordChar(text[i])) i++
            action(start, i)
        } else {
            i++
        }
    }
}

/** Kelimenin içinde geçtiği cümle — bağlamı ayrıca saklayabilmek için. */
internal fun sentenceAround(text: String, offset: Int): String {
    val enders = charArrayOf('.', '!', '?')
    var start = offset
    while (start > 0 && text[start - 1] !in enders) start--
    var end = offset
    while (end < text.length && text[end] !in enders) end++
    if (end < text.length) end++
    return text.substring(start.coerceAtMost(end), end).trim()
}

/** Composable olmayan yerlerden kullanılabilen renk eşlemesi. */
internal fun paintOf(color: HighlightColor): Color = when (color) {
    HighlightColor.YELLOW -> Color(0xFFFFE082)
    HighlightColor.BLUE -> Color(0xFF90CAF9)
    HighlightColor.GREEN -> Color(0xFFA5D6A7)
    HighlightColor.RED -> Color(0xFFEF9A9A)
}
