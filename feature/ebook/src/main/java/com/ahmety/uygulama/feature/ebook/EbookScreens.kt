package com.ahmety.uygulama.feature.ebook

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
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

/**
 * Okuma ekranı.
 *
 * Metne bir dokunuş arayüzü gizler (sadece kitap kalır), ikinci dokunuş altta
 * ilerleme çubuğunu ve "İndeks" düğmesini getirir. Kelimeyi işaretlemek için
 * **uzun bas** — okurken yanlışlıkla renk seçiciyi açmamak için.
 */
@Composable
fun BookReaderRoute(
    bookId: Long,
    modifier: Modifier = Modifier,
    viewModel: BookReaderViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(bookId) { viewModel.load(bookId) }

    val context = LocalContext.current
    val prefs = remember { ReaderPrefs(context) }
    var theme by remember { mutableStateOf(prefs.theme) }
    var fontSize by remember { mutableStateOf(prefs.fontSizeSp) }

    var pending by remember { mutableStateOf<PendingHighlight?>(null) }
    var chromeVisible by remember { mutableStateOf(true) }
    var showIndex by remember { mutableStateOf(false) }
    var showDisplay by remember { mutableStateOf(false) }

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
            val listState = rememberLazyListState()
            LaunchedEffect(state.chapterIndex) { listState.scrollToItem(0) }

            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(theme.background),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = 28.dp,
                        bottom = 96.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(chapter?.paragraphs.orEmpty()) { paragraph ->
                        HighlightableParagraph(
                            paragraph = paragraph,
                            colors = state.highlightColors,
                            textColor = theme.text,
                            fontSizeSp = fontSize,
                            onTap = { chromeVisible = !chromeVisible },
                            onWordLongPressed = { word, sentence ->
                                pending = PendingHighlight(word, sentence)
                            },
                        )
                    }
                }

                if (chromeVisible) {
                    ReaderBottomBar(
                        chapterIndex = state.chapterIndex,
                        chapterCount = book.chapters.size,
                        theme = theme,
                        onOpenIndex = { showIndex = true },
                        onOpenDisplay = { showDisplay = true },
                        onSeekChapter = { viewModel.selectChapter(it) },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }

            if (showIndex) {
                ChapterIndexDialog(
                    chapters = book.chapters,
                    current = state.chapterIndex,
                    onPick = {
                        viewModel.selectChapter(it)
                        showIndex = false
                    },
                    onDismiss = { showIndex = false },
                )
            }

            if (showDisplay) {
                DisplayOptionsDialog(
                    theme = theme,
                    fontSizeSp = fontSize,
                    onTheme = { theme = it; prefs.theme = it },
                    onFontSize = { fontSize = it; prefs.fontSizeSp = it },
                    onDismiss = { showDisplay = false },
                )
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
            onRemove = {
                viewModel.removeHighlight(request.word)
                pending = null
            },
        )
    }
}

/** Altta: nerede kaldığın + indeks + görünüm. */
@Composable
private fun ReaderBottomBar(
    chapterIndex: Int,
    chapterCount: Int,
    theme: ReaderTheme,
    onOpenIndex: () -> Unit,
    onOpenDisplay: () -> Unit,
    onSeekChapter: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = theme.background,
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            if (chapterCount > 1) {
                Slider(
                    value = chapterIndex.toFloat(),
                    onValueChange = { onSeekChapter(it.toInt()) },
                    valueRange = 0f..(chapterCount - 1).toFloat(),
                    steps = (chapterCount - 2).coerceAtLeast(0),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onOpenIndex) {
                    Text("İndeks", color = theme.text)
                }
                Text(
                    text = "${chapterIndex + 1} / $chapterCount",
                    style = MaterialTheme.typography.labelMedium,
                    color = theme.text.copy(alpha = 0.7f),
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onOpenDisplay) {
                    Text("Görünüm", color = theme.text)
                }
            }
        }
    }
}

@Composable
private fun ChapterIndexDialog(
    chapters: List<EpubChapter>,
    current: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("İndeks") },
        text = {
            LazyColumn(modifier = Modifier.height(400.dp)) {
                itemsIndexed(chapters) { index, chapter ->
                    Text(
                        text = chapter.title.ifBlank { "Bölüm ${index + 1}" },
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (index == current) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(index) }
                            .padding(vertical = 12.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Kapat") } },
    )
}

@Composable
private fun DisplayOptionsDialog(
    theme: ReaderTheme,
    fontSizeSp: Int,
    onTheme: (ReaderTheme) -> Unit,
    onFontSize: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Görünüm") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReaderTheme.entries.forEach { option ->
                        FilterChip(
                            selected = option == theme,
                            onClick = { onTheme(option) },
                            label = { Text(option.label) },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Yazı boyutu", modifier = Modifier.weight(1f))
                    TextButton(
                        enabled = fontSizeSp > 14,
                        onClick = { onFontSize(fontSizeSp - 1) },
                    ) { Text("−") }
                    Text("$fontSizeSp")
                    TextButton(
                        enabled = fontSizeSp < 28,
                        onClick = { onFontSize(fontSizeSp + 1) },
                    ) { Text("+") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Kapat") } },
    )
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
    textColor: Color,
    fontSizeSp: Int,
    onTap: () -> Unit,
    onWordLongPressed: (word: String, sentence: String) -> Unit,
) {
    var layout by remember(paragraph) { mutableStateOf<TextLayoutResult?>(null) }
    val haptic = LocalHapticFeedback.current

    val painted: AnnotatedString = remember(paragraph, colors) {
        buildAnnotatedString {
            append(paragraph)
            forEachWord(paragraph) { rawStart, rawEnd ->
                val bounds = trimBounds(paragraph, rawStart, rawEnd) ?: return@forEachWord
                val word = paragraph.substring(bounds.first, bounds.second).lowercase()
                colors[word]?.let { color ->
                    addStyle(SpanStyle(background = paintOf(color)), bounds.first, bounds.second)
                }
            }
        }
    }

    Text(
        text = painted,
        color = textColor,
        style = MaterialTheme.typography.bodyLarge.copy(
            fontSize = fontSizeSp.sp,
            lineHeight = (fontSizeSp * 1.65f).sp,
        ),
        onTextLayout = { layout = it },
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(paragraph) {
                detectTapGestures(
                    // Tek dokunuş okuma arayüzünü gizler/gösterir; işaretleme
                    // uzun basmayla, yoksa okurken sürekli renk seçici açılıyor.
                    onTap = { onTap() },
                    onLongPress = { position ->
                        val result = layout ?: return@detectTapGestures
                        val offset = result.getOffsetForPosition(position)
                        val bounds = wordBoundsAt(paragraph, offset) ?: return@detectTapGestures
                        val word = paragraph.substring(bounds.first, bounds.second)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onWordLongPressed(word, contextAround(paragraph, bounds.first, bounds.second))
                    },
                )
            },
    )
}

@Composable
private fun ColorPickerDialog(
    request: PendingHighlight,
    current: HighlightColor?,
    onDismiss: () -> Unit,
    onPick: (HighlightColor, Boolean) -> Unit,
    onRemove: () -> Unit,
) {
    var keepContext by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(request.word) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Renkler kendini anlatıyor; ad yazmaya gerek yok.
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    HighlightColor.entries.forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .background(highlightPaint(color), CircleShape)
                                .then(
                                    if (color == current) {
                                        Modifier.border(
                                            3.dp,
                                            MaterialTheme.colorScheme.onSurface,
                                            CircleShape,
                                        )
                                    } else {
                                        Modifier
                                    },
                                )
                                .clickable { onPick(color, keepContext) },
                        )
                    }
                }

                if (request.sentence.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = keepContext, onCheckedChange = { keepContext = it })
                        Text(
                            text = request.sentence,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Kapat") } },
        dismissButton = {
            if (current != null) {
                TextButton(onClick = onRemove) {
                    Text("Kaldır", color = MaterialTheme.colorScheme.error)
                }
            }
        },
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
    return trimBounds(text, start, end)
}

/**
 * Kelimeye yapışan tırnak/tireyi sınırların dışında bırakır. Yalnızca uzunluk
 * kontrolünde kırpmak yetmiyordu: `'quiet'` içindeki kelime `quiet'` olarak
 * kaydedilip boyama eşleşmesini de bozuyordu.
 */
private fun trimBounds(text: String, startIn: Int, endIn: Int): Pair<Int, Int>? {
    var start = startIn
    var end = endIn
    while (start < end && !text[start].isLetter()) start++
    while (end > start && !text[end - 1].isLetter()) end--
    return if (end - start < 2) null else start to end
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

/**
 * Kelimenin bağlamı.
 *
 * Cümle kısaysa (en fazla [MAX_SENTENCE_WORDS] kelime) cümlenin tamamı;
 * uzunsa kelimenin çevresinden [WINDOW_WORDS] kelimelik bir pencere alınır.
 * Uzun cümlelerin tamamını saklamak kartı okunmaz hâle getiriyordu.
 */
internal fun contextAround(text: String, wordStart: Int, wordEnd: Int): String {
    val enders = charArrayOf('.', '!', '?')
    var start = wordStart
    while (start > 0 && text[start - 1] !in enders) start--
    var end = wordEnd
    while (end < text.length && text[end] !in enders) end++
    if (end < text.length) end++
    val sentence = text.substring(start.coerceAtMost(end), end).trim()
    if (sentence.split(' ').count { it.isNotBlank() } <= MAX_SENTENCE_WORDS) return sentence

    // Uzun cümle: kelimenin önünden ve arkasından birkaç kelime.
    val before = text.substring(start, wordStart).split(' ').filter { it.isNotBlank() }
    val after = text.substring(wordEnd, end).split(' ').filter { it.isNotBlank() }
    val word = text.substring(wordStart, wordEnd)
    val left = before.takeLast(WINDOW_WORDS)
    val right = after.take(WINDOW_WORDS)
    return buildString {
        if (before.size > WINDOW_WORDS) append("… ")
        if (left.isNotEmpty()) append(left.joinToString(" ")).append(' ')
        append(word)
        if (right.isNotEmpty()) append(' ').append(right.joinToString(" "))
        if (after.size > WINDOW_WORDS) append(" …")
    }.trim()
}

private const val MAX_SENTENCE_WORDS = 10
private const val WINDOW_WORDS = 5

/** Composable olmayan yerlerden kullanılabilen renk eşlemesi. */
internal fun paintOf(color: HighlightColor): Color = when (color) {
    HighlightColor.YELLOW -> Color(0xFFFFE082)
    HighlightColor.BLUE -> Color(0xFF90CAF9)
    HighlightColor.GREEN -> Color(0xFFA5D6A7)
    HighlightColor.RED -> Color(0xFFEF9A9A)
}
