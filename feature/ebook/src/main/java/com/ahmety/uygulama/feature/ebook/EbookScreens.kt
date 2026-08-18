package com.ahmety.uygulama.feature.ebook

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahmety.uygulama.core.model.Entry
import com.ahmety.uygulama.core.model.HighlightColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

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
 * Sayfa ilerletme **dokunma bölgeleriyle**: sol çeyrek geri, sağ çeyrek ileri,
 * orta bölge arayüzü gizler/gösterir. Önceki tasarımda tek dokunuş arayüzü
 * tümüyle gizliyordu ve sayfa çevirmenin tek yolu olan şerit de onunla
 * kaybolduğu için ilerlemek imkânsız hâle geliyordu.
 *
 * Bölümün sonuna gelince ileri dokunuşu sonraki bölüme geçiyor; ayrıca
 * metnin sonunda açık bir "sonraki bölüm" kartı var.
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
            val scope = rememberCoroutineScope()

            // Geriye doğru bölüm değiştirince önceki bölümün başına değil
            // sonuna düşmeli: sayfa çevirir gibi okunuyor.
            var landAtEnd by remember { mutableStateOf(false) }

            // Bölüm değişimi ile listenin yeni içeriği ölçülmesi arasında
            // canScrollBackward/Forward hâlâ eski değeri veriyor; peş peşe iki
            // dokunuş bir bölümü atlıyordu.
            var switching by remember { mutableStateOf(false) }

            // Kitap ilk açıldığında kaldığın paragrafa dön; ileri doğru bölüm
            // değiştirdiğinde metnin başından başla.
            LaunchedEffect(state.chapterIndex, book) {
                val last = (chapter?.paragraphs?.lastIndex ?: 0).coerceAtLeast(0)
                val target = when {
                    landAtEnd -> last
                    else -> viewModel.lastParagraph()
                }
                landAtEnd = false
                runCatching { listState.scrollToItem(target.coerceIn(0, last)) }
                switching = false
            }

            // Nerede kaldığını sürekli değil, durulunca kaydediyoruz.
            LaunchedEffect(listState, state.chapterIndex) {
                snapshotFlow { listState.firstVisibleItemIndex }
                    .collectLatest { index ->
                        delay(400)
                        viewModel.savePosition(index)
                    }
            }

            fun turnPage(forward: Boolean) {
                if (switching) return
                scope.launch {
                    val viewport = listState.layoutInfo.viewportSize.height.toFloat()
                    val step = (viewport * 0.88f).coerceAtLeast(200f)
                    if (forward) {
                        if (listState.canScrollForward) {
                            listState.animateScrollBy(step)
                        } else if (state.chapterIndex < book.chapters.lastIndex) {
                            switching = true
                            viewModel.selectChapter(state.chapterIndex + 1)
                        }
                    } else {
                        if (listState.canScrollBackward) {
                            listState.animateScrollBy(-step)
                        } else if (state.chapterIndex > 0) {
                            switching = true
                            landAtEnd = true
                            viewModel.selectChapter(state.chapterIndex - 1)
                        }
                    }
                }
            }

            /** Dokunulan yerin yatay oranına göre: geri / arayüz / ileri. */
            fun handleZoneTap(fraction: Float) {
                when {
                    fraction < 0.25f -> turnPage(forward = false)
                    fraction > 0.75f -> turnPage(forward = true)
                    else -> chromeVisible = !chromeVisible
                }
            }

            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(theme.background)
                    // Paragraf aralarına ve boşluklara denk gelen dokunuşlar da
                    // aynı bölgelere göre çalışsın.
                    .pointerInput(state.chapterIndex) {
                        detectTapGestures { position ->
                            handleZoneTap(position.x / size.width.toFloat())
                        }
                    },
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = 28.dp,
                        bottom = 120.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(chapter?.paragraphs.orEmpty()) { paragraph ->
                        HighlightableParagraph(
                            paragraph = paragraph,
                            colors = state.highlightColors,
                            textColor = theme.text,
                            fontSizeSp = fontSize,
                            onZoneTap = { fraction -> handleZoneTap(fraction) },
                            onSelection = { text, sentence ->
                                pending = PendingHighlight(text, sentence)
                            },
                        )
                    }

                    // Bölüm sonunda ölü nokta bırakmıyoruz.
                    item {
                        ChapterEndCard(
                            theme = theme,
                            hasNext = state.chapterIndex < book.chapters.lastIndex,
                            nextTitle = book.chapters
                                .getOrNull(state.chapterIndex + 1)
                                ?.title
                                .orEmpty(),
                            onNext = { viewModel.selectChapter(state.chapterIndex + 1) },
                        )
                    }
                }

                if (chromeVisible) {
                    val percent by remember(state, listState) {
                        derivedStateOf { readingPercent(state, listState) }
                    }
                    ReaderBottomBar(
                        chapterIndex = state.chapterIndex,
                        chapterCount = book.chapters.size,
                        percent = percent,
                        theme = theme,
                        onOpenIndex = { showIndex = true },
                        onOpenDisplay = { showDisplay = true },
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

/**
 * Kitabın tamamına göre ilerleme; bölüm sayısı değil karakter sayısı esas.
 *
 * Sade bir fonksiyon: `@Composable` olsaydı `Int` döndürdüğü için kendi
 * yeniden başlatma kapsamı olmaz, `layoutInfo` okuması çağıranın kapsamına
 * yazılır ve her kaydırma karesinde bütün okuma ekranı yeniden bestelenirdi.
 *
 * Paragraf indeksine ek olarak ilk görünen paragrafın piksel kayması da
 * hesaba katılıyor: uzun paragraflı bölümlerde yüzde sayfa çevirdikçe
 * yerinde sayıp sonra sıçruyordu.
 */
private fun readingPercent(state: ReaderUiState, listState: LazyListState): Int {
    val info = listState.layoutInfo
    val total = info.totalItemsCount.coerceAtLeast(1)
    val first = info.visibleItemsInfo.firstOrNull()
    val partial = if (first != null && first.size > 0) {
        ((-first.offset).toFloat() / first.size).coerceIn(0f, 1f)
    } else {
        0f
    }
    val within = ((listState.firstVisibleItemIndex + partial) / total).coerceIn(0f, 1f)
    val chars = state.charsBefore + state.chapterChars * within
    return ((chars / state.totalChars.toFloat()) * 100f).toInt().coerceIn(0, 100)
}

@Composable
private fun ChapterEndCard(
    theme: ReaderTheme,
    hasNext: Boolean,
    nextTitle: String,
    onNext: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (hasNext) "Bölüm bitti" else "Kitap bitti",
            style = MaterialTheme.typography.titleMedium,
            color = theme.text.copy(alpha = 0.7f),
        )
        if (hasNext) {
            Button(onClick = onNext, modifier = Modifier.padding(top = 10.dp)) {
                Text(
                    text = if (nextTitle.isBlank()) "Sonraki bölüm" else "Sonraki: $nextTitle",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Altta: nerede kaldığın + indeks + görünüm. */
@Composable
private fun ReaderBottomBar(
    chapterIndex: Int,
    chapterCount: Int,
    percent: Int,
    theme: ReaderTheme,
    onOpenIndex: () -> Unit,
    onOpenDisplay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = theme.background,
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            // Zemin gezinme çubuğunun arkasına kadar uzanıyor; düğmeler
            // çubuğun altına düşmesin diye boşluğu içeride veriyoruz.
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            TextButton(onClick = onOpenIndex) {
                Text("İndeks", color = theme.text)
            }
            Text(
                text = "Bölüm ${chapterIndex + 1}/$chapterCount · %$percent",
                style = MaterialTheme.typography.labelMedium,
                color = theme.text.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onOpenDisplay) {
                Text("Görünüm", color = theme.text)
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
    onZoneTap: (Float) -> Unit,
    onSelection: (text: String, context: String) -> Unit,
) {
    var layout by remember(paragraph) { mutableStateOf<TextLayoutResult?>(null) }
    // Seçim: uzun basınca kelimede başlar, parmak sürüklendikçe genişler.
    var selection by remember(paragraph) { mutableStateOf<IntRange?>(null) }
    var anchor by remember(paragraph) { mutableStateOf<Pair<Int, Int>?>(null) }
    val haptic = LocalHapticFeedback.current
    val selectionTint = MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)

    val painted: AnnotatedString = remember(paragraph, colors, selection, selectionTint) {
        buildAnnotatedString {
            append(paragraph)

            // Önce çok kelimeli işaretlemeler: metinde geçtiği her yeri boya.
            colors.forEach { (text, color) ->
                if (!text.contains(' ')) return@forEach
                var index = paragraph.indexOf(text, ignoreCase = true)
                while (index >= 0) {
                    addStyle(SpanStyle(background = paintOf(color)), index, index + text.length)
                    index = paragraph.indexOf(text, index + text.length, ignoreCase = true)
                }
            }

            // Sonra tek kelimeler.
            forEachWord(paragraph) { rawStart, rawEnd ->
                val bounds = trimBounds(paragraph, rawStart, rawEnd) ?: return@forEachWord
                val word = paragraph.substring(bounds.first, bounds.second).lowercase()
                colors[word]?.let { color ->
                    addStyle(SpanStyle(background = paintOf(color)), bounds.first, bounds.second)
                }
            }

            // En üstte, sürüklenirken görünen seçim.
            selection?.let { range ->
                addStyle(SpanStyle(background = selectionTint), range.first, range.last + 1)
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
            // Dokunma bölgesi: solda geri, sağda ileri, ortada arayüz.
            // onLongPress boş bırakılıyor ki uzun basıp seçim yaparken
            // parmak kalkınca ayrıca "dokunuş" sayılmasın.
            .pointerInput(paragraph) {
                detectTapGestures(
                    onLongPress = {},
                    onTap = { position -> onZoneTap(position.x / size.width.toFloat()) },
                )
            }
            // Uzun bas + sürükle: birden çok kelime seçilebiliyor. Sarı ile
            // altı çizilecek yerler genelde tek kelime değil.
            .pointerInput(paragraph) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { position ->
                        val result = layout ?: return@detectDragGesturesAfterLongPress
                        val offset = result.getOffsetForPosition(position)
                        val bounds = wordBoundsAt(paragraph, offset)
                            ?: return@detectDragGesturesAfterLongPress
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        anchor = bounds
                        selection = bounds.first until bounds.second
                    },
                    onDrag = { change, _ ->
                        val result = layout ?: return@detectDragGesturesAfterLongPress
                        val start = anchor ?: return@detectDragGesturesAfterLongPress
                        val offset = result.getOffsetForPosition(change.position)
                        val bounds = wordBoundsAt(paragraph, offset)
                            ?: return@detectDragGesturesAfterLongPress
                        selection = minOf(start.first, bounds.first) until
                            maxOf(start.second, bounds.second)
                    },
                    onDragEnd = {
                        val range = selection
                        if (range != null && !range.isEmpty()) {
                            val text = paragraph.substring(range.first, range.last + 1).trim()
                            if (text.isNotEmpty()) {
                                onSelection(
                                    text,
                                    contextAround(paragraph, range.first, range.last + 1),
                                )
                            }
                        }
                        selection = null
                        anchor = null
                    },
                    onDragCancel = {
                        selection = null
                        anchor = null
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
        title = {
            Text(
                text = request.word,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                style = if (request.word.length > 40) {
                    MaterialTheme.typography.titleSmall
                } else {
                    MaterialTheme.typography.titleLarge
                },
            )
        },
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
