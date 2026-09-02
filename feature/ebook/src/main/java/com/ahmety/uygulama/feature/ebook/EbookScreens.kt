package com.ahmety.uygulama.feature.ebook

import android.graphics.BitmapFactory
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahmety.uygulama.core.designsystem.ColorPickerDialog
import com.ahmety.uygulama.core.designsystem.HighlightableParagraph
import com.ahmety.uygulama.core.designsystem.MerkezEmptyState
import com.ahmety.uygulama.core.designsystem.ReaderDisplayDialog
import com.ahmety.uygulama.core.designsystem.ReaderPrefs
import com.ahmety.uygulama.core.designsystem.ReaderTheme
import com.ahmety.uygulama.core.designsystem.MerkezGlyphs
import com.ahmety.uygulama.core.designsystem.MerkezIcons
import com.ahmety.uygulama.core.designsystem.MonogramTile
import com.ahmety.uygulama.core.designsystem.PendingHighlight
import com.ahmety.uygulama.core.designsystem.pressable
import com.ahmety.uygulama.core.designsystem.SelectionPreview
import com.ahmety.uygulama.core.model.Entry
import com.ahmety.uygulama.core.model.HighlightColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/** Kitaplık: EPUB yükle, kitapları listele. */
@Composable
fun BookShelfRoute(
    onOpenBook: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BookShelfViewModel = hiltViewModel(),
) {
    val books by viewModel.books.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var shelf by rememberSaveable { mutableStateOf(Shelf.ALL) }
    val brief by viewModel.brief.collectAsStateWithLifecycle()

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

        // Filmler de burada: altyazı da okunacak bir metin. Karışmasınlar
        // diye süzgeç, ikisi birden varken görünüyor.
        val films = books.count { viewModel.isFilm(it) }
        if (films > 0 && films < books.size) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ShelfChip("Tümü", shelf == Shelf.ALL) { shelf = Shelf.ALL }
                ShelfChip("Kitaplar", shelf == Shelf.BOOKS) { shelf = Shelf.BOOKS }
                ShelfChip("Filmler", shelf == Shelf.FILMS) { shelf = Shelf.FILMS }
            }
        }

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
            MerkezEmptyState(
                title = "Raf boş",
                description = "Bir EPUB yükle ya da Altyazı aramadan bir film ekle. " +
                    "Okurken kelimelere dokunup işaretlediklerin kelime destene düşer.",
                glyph = { MerkezGlyphs.Shelf() },
                actionLabel = "EPUB yükle",
                onAction = { picker.launch(arrayOf("*/*")) },
            )
        }

        val shown = books.filter { book ->
            when (shelf) {
                Shelf.ALL -> true
                Shelf.BOOKS -> !viewModel.isFilm(book)
                Shelf.FILMS -> viewModel.isFilm(book)
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(shown, key = { it.id }) { book ->
                BookCard(
                    modifier = Modifier.animateItem(),
                    book = book,
                    film = viewModel.isFilm(book),
                    percent = remember(book.id) { viewModel.progressOf(book.id) },
                    cover = viewModel::coverOf,
                    onOpen = { onOpenBook(book.id) },
                    onBrief = { viewModel.openBrief(book) },
                    onDelete = { withWords -> viewModel.delete(book, withWords) },
                    countWords = { viewModel.highlightCount(book) },
                )
            }
        }
    }

    brief?.let { state ->
        BriefDialog(
            state = state,
            onGenerate = viewModel::generateBrief,
            onDismiss = viewModel::closeBrief,
        )
    }
}

/**
 * Eser künyesi kutusu.
 *
 * Künye kelime sorgularına sessizce ekleniyor; modelin eseri nasıl
 * tanıdığını görebilmek gerekiyor, çünkü yanlış tanırsa o eserden gelen
 * bütün kartlar ondan etkileniyor.
 */
@Composable
private fun BriefDialog(
    state: BriefUiState,
    onGenerate: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(state.work) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when {
                    state.busy -> Text("Künye çıkarılıyor…")
                    state.text.isBlank() -> Text(
                        "Bu eserin künyesi yok. Çıkarırsan bu kitaptan ya da " +
                            "filmden gelen her kelime sorgusuna eklenir.",
                    )

                    else -> Text(state.text)
                }
                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !state.busy, onClick = onGenerate) {
                Text(if (state.text.isBlank()) "Çıkar" else "Yenile")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Kapat") } },
    )
}

/** Kitaplık süzgeci. */
private enum class Shelf { ALL, BOOKS, FILMS }

@Composable
private fun ShelfChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
private fun BookCard(
    book: Entry,
    film: Boolean,
    percent: Int,
    cover: (Long) -> File?,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
    onBrief: () -> Unit,
    onDelete: (withWords: Boolean) -> Unit,
    countWords: suspend () -> Int,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    // Kutu açılırken sayılıyor: kaç kelimenin gideceğini görmeden seçim
    // yapmak körlemesine oluyor. -1 "henüz sayılmadı" demek.
    var wordCount by remember { mutableIntStateOf(-1) }

    var menuOpen by remember { mutableStateOf(false) }

    Card(modifier = modifier.fillMaxWidth().pressable(onClick = onOpen)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Kitabın kendi kapağı; yoksa adından türeyen renkli bir sırt.
            // Renk kitabın adından geldiği için her açılışta aynı: kapağı
            // olmayan kitaplar da rafta tanınıyor.
            MonogramTile(
                seed = book.title,
                label = book.title.ifBlank { "?" },
                image = bookCover(book.id, cover),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .padding(end = 14.dp)
                    .size(width = 46.dp, height = 66.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                if (film) {
                    Text(
                        text = "FİLM",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    text = book.title.ifBlank { "(adsız kitap)" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (book.body.isNotBlank()) {
                    Text(
                        text = book.body,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // Nerede kaldığın. Sayı okurken zaten hesaplanıyordu ama
                // rafta hiç görünmüyordu.
                if (percent > 0) {
                    LinearProgressIndicator(
                        progress = { percent / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .height(3.dp),
                    )
                    Text(
                        text = "%$percent",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            // "Künye" ve "Sil" başlıkla aynı ağırlıkta iki yazıydı; silme
            // gibi geri dönüşü olmayan bir şeyin orada durması doğru değil.
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(MerkezIcons.MoreVert, contentDescription = "Seçenekler")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Künye") },
                        onClick = {
                            menuOpen = false
                            onBrief()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Sil") },
                        onClick = {
                            menuOpen = false
                            wordCount = -1
                            confirmDelete = true
                        },
                    )
                }
            }
        }
    }

    if (confirmDelete) {
        LaunchedEffect(book.id) { wordCount = countWords() }
        val nesne = if (film) "Film" else "Kitap"
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("$nesne silinsin mi?") },
            // İki ayrı silme var. Üçüncü bir düğme alt sıraya sığmadığı için
            // seçenekler gövdede duruyor: her biri ne yaptığını yazıyor.
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = when {
                            wordCount < 0 -> "İşaretlemeler sayılıyor…"
                            wordCount == 0 -> "Bu eserde işaretlenmiş kelime yok."
                            else -> "Bu eserden $wordCount işaretlenmiş kelime var."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    DeleteChoice(
                        label = "Yalnız eseri sil",
                        detail = if (wordCount > 0) {
                            "Kelimeler kalır ama kaynaksız kalırlar."
                        } else {
                            "Eser ve metni silinir."
                        },
                    ) {
                        confirmDelete = false
                        onDelete(false)
                    }
                    if (wordCount > 0) {
                        DeleteChoice(
                            label = "Kelimeleriyle birlikte sil",
                            detail = "$wordCount kelime, işaretleri ve tekrar " +
                                "geçmişleriyle birlikte gider. Geri alınamaz.",
                        ) {
                            confirmDelete = false
                            onDelete(true)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Vazgeç") }
            },
        )
    }
}

/** Silme kutusundaki tek seçenek: üstte ne yaptığı, altında sonucu. */
@Composable
private fun DeleteChoice(
    label: String,
    detail: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    var marginDp by remember { mutableStateOf(prefs.marginDp) }

    var pending by remember { mutableStateOf<PendingHighlight?>(null) }
    // Sürüklerken seçtiğin metin. Ekranın üstünde duruyor: parmağın altında
    // kalanı göremiyordun.
    var preview by remember { mutableStateOf<String?>(null) }
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

            // Kitap ilk açıldığında kaldığın paragrafa dön; ileri doğru bölüm
            // değiştirdiğinde metnin başından başla.
            LaunchedEffect(state.chapterIndex, book) {
                val last = (chapter?.paragraphs?.lastIndex ?: 0).coerceAtLeast(0)
                val target = viewModel.lastParagraph()
                runCatching { listState.scrollToItem(target.coerceIn(0, last)) }
            }

            // Nerede kaldığını sürekli değil, durulunca kaydediyoruz.
            LaunchedEffect(listState, state.chapterIndex) {
                snapshotFlow { listState.firstVisibleItemIndex }
                    .collectLatest { index ->
                        delay(400)
                        viewModel.savePosition(index)
                    }
            }

            // Sayfa çevirme kaldırıldı: metin zaten kaydırılarak okunuyor,
            // dokunma bölgeleri okurken yanlışlıkla tetikleniyordu. Dokunuş
            // artık yalnızca okuma arayüzünü açıp kapatıyor.
            fun handleZoneTap(@Suppress("UNUSED_PARAMETER") fraction: Float) {
                chromeVisible = !chromeVisible
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
                // Arapça metin sağdan sola okunuyor: satırların hizası da,
                // paragrafların başlangıcı da o yöne göre. Yalnız okuma
                // alanını çeviriyoruz — alttaki çubuk ve ilerleme göstergesi
                // arayüzün parçası, onlar yerinde kalıyor.
                val rtl = remember(book) {
                    book.chapters.firstOrNull()?.paragraphs.orEmpty()
                        .any { paragraph -> paragraph.any { it in '\u0600'..'\u06FF' } }
                }
                CompositionLocalProvider(
                    LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = marginDp.dp,
                        end = marginDp.dp,
                        top = 28.dp,
                        bottom = 120.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(chapter?.paragraphs.orEmpty()) { paragraph ->
                        HighlightableParagraph(
                            raw = paragraph,
                            colors = state.highlightColors,
                            textColor = theme.text,
                            fontSizeSp = fontSize,
                            onZoneTap = { fraction -> handleZoneTap(fraction) },
                            onSelection = { text, sentence ->
                                pending = PendingHighlight(text, sentence)
                            },
                            onPreview = { preview = it },
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
                }

                preview?.let { text ->
                    SelectionPreview(
                        text = text,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }

                val percent by remember(state, listState) {
                    derivedStateOf { readingPercent(state, listState) }
                }
                // Kitaplıktaki ilerleme çizgisi için saklanıyor: orada
                // yeniden hesaplamak bütün bölümleri ayrıştırmak demek.
                LaunchedEffect(percent) { viewModel.saveProgress(percent) }

                if (chromeVisible) {
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
                ReaderDisplayDialog(
                    theme = theme,
                    fontSizeSp = fontSize,
                    marginDp = marginDp,
                    onTheme = { theme = it; prefs.theme = it },
                    onFontSize = { fontSize = it; prefs.fontSizeSp = it },
                    onMargin = { marginDp = it; prefs.marginDp = it },
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
/**
 * Kapağı dosyadan okur. Ana iş parçacığında değil: rafta on kitap varsa on
 * JPEG açılıyor ve kaydırma takılıyor.
 */
@Composable
private fun bookCover(bookId: Long, cover: (Long) -> File?): ImageBitmap? {
    val state = produceState<ImageBitmap?>(null, bookId) {
        value = withContext(Dispatchers.IO) {
            cover(bookId)
                ?.let { runCatching { BitmapFactory.decodeFile(it.absolutePath) }.getOrNull() }
                ?.asImageBitmap()
        }
    }
    return state.value
}

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
        Column(modifier = Modifier.navigationBarsPadding()) {
            // İlerleme bir sayı olarak yazıyordu. Okuyucu çubuğunda
            // yüzdeyi rakamla vermek, çizgisi olmayan bir okuyucunun en
            // belirgin işareti.
            LinearProgressIndicator(
                progress = { percent / 100f },
                color = theme.text.copy(alpha = 0.75f),
                trackColor = theme.text.copy(alpha = 0.12f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                IconButton(onClick = onOpenIndex) {
                    Icon(MerkezIcons.ListLines, contentDescription = "Bölümler", tint = theme.text)
                }
                Text(
                    text = "Bölüm ${chapterIndex + 1}/$chapterCount",
                    style = MaterialTheme.typography.labelMedium,
                    color = theme.text.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onOpenDisplay) {
                    Icon(MerkezIcons.TextSize, contentDescription = "Görünüm", tint = theme.text)
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
            // Liste okuduğun bölümde açılıyor. Kırk bölümlük bir kitapta
            // her seferinde birinci bölümden başlıyor, kaldığın yeri
            // bulmak için elle kaydırman gerekiyordu.
            val listState = rememberLazyListState()
            LaunchedEffect(current) {
                listState.scrollToItem(current.coerceAtLeast(0))
            }
            LazyColumn(state = listState, modifier = Modifier.height(400.dp)) {
                itemsIndexed(chapters) { index, chapter ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(index) }
                            .padding(vertical = 12.dp),
                    ) {
                        // Okuduğun bölümü renkten başka bir şey de söylesin.
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(20.dp)
                                .background(
                                    if (index == current) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        Color.Transparent
                                    },
                                ),
                        )
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(start = 10.dp)
                                .width(26.dp),
                        )
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
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Kapat") } },
    )
}

