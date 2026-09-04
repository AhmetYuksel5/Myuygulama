package com.ahmety.uygulama.feature.ebook

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import com.ahmety.uygulama.core.designsystem.PendingHighlight
import com.ahmety.uygulama.core.designsystem.ColorPickerDialog
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.Canvas
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.FlowPreview
import com.ahmety.uygulama.core.designsystem.pinchToZoom
import com.ahmety.uygulama.core.designsystem.MerkezIcons
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import kotlin.math.roundToInt
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ahmety.uygulama.core.model.HighlightColor
import com.ahmety.uygulama.core.model.HighlightRef
import com.ahmety.uygulama.core.model.PdfSpot
import com.ahmety.uygulama.core.designsystem.ReaderPrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PdfUiState(
    val title: String = "",
    val sizes: List<PdfPageSize> = emptyList(),
    /** Belgenin yazı çerçevesi; kırpma kapalıyken tam sayfa. */
    val crop: PdfCrop = PdfCrop.FULL,
    /** Sayfa numarasına göre işaretler; sayfanın üstüne çiziliyorlar. */
    val marks: Map<Int, List<PdfMark>> = emptyMap(),
    /** Açılışta gidilecek sayfa; kaldığın yer. */
    val startPage: Int = 0,
    val loading: Boolean = true,
    val error: String? = null,
)

/** Sayfaya çizilecek bir işaret. */
data class PdfMark(
    val word: String,
    val spot: PdfSpot,
    val color: HighlightColor,
)

@HiltViewModel
class PdfReaderViewModel @Inject constructor(
    private val repository: BookRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PdfUiState())
    val state: StateFlow<PdfUiState> = _state.asStateFlow()

    private var pages: PdfPages? = null
    private var bookId: Long = 0L

    fun load(id: Long, crop: Boolean) {
        if (bookId == id && pages != null) return
        bookId = id
        viewModelScope.launch {
            val title = repository.titleOf(id)
            val opened = repository.openPdf(id)
            if (opened == null) {
                _state.value = PdfUiState(
                    title = title,
                    loading = false,
                    error = "PDF açılamadı; dosya taşınmış ya da bozulmuş olabilir.",
                )
                return@launch
            }
            pages = opened
            _state.value = PdfUiState(
                title = title,
                sizes = opened.sizes(),
                startPage = repository.lastPage(id),
                loading = false,
            )
            // Çerçeve sayfalar göründükten sonra hesaplanıyor: birkaç
            // sayfa çizmek yarım saniye sürebiliyor ve okumanın önünde
            // beklemek anlamsız.
            if (crop) {
                _state.value = _state.value.copy(crop = opened.contentBox())
            }
            refreshMarks()
        }
    }

    suspend fun render(index: Int, widthPx: Int, crop: PdfCrop): Bitmap? =
        pages?.render(index, widthPx, crop)

    /** İki nokta arasındaki metin; noktalar sayfanın oranı olarak. */
    suspend fun selection(
        index: Int,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
    ): PdfWord? = pages?.selection(index, startX, startY, endX, endY)

    /**
     * Taranmış sayfada seçimi görüntüden okur.
     *
     * PDF'in kendi metni boş dönünce buraya düşülüyor; tanıma bir saniyeye
     * yakın sürdüğü için ayrı bir çağrı olarak duruyor, her seçimde
     * peşinen çalıştırılmıyor.
     */
    suspend fun ocrSelection(
        index: Int,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
    ): OcrOutcome = pages?.ocrSelection(index, startX, startY, endX, endY)
        ?: OcrOutcome.Failed("PDF açık değil.")

    /** Kelimeyi işaretler ve sayfaya çizer. */
    fun mark(word: PdfWord, page: Int, color: HighlightColor) {
        viewModelScope.launch {
            repository.setPdfHighlight(
                bookId = bookId,
                word = word.text,
                context = word.context,
                color = color,
                page = page,
                left = word.left,
                top = word.top,
                right = word.right,
                bottom = word.bottom,
            )
            refreshMarks()
        }
    }

    fun removeMark(word: String) {
        viewModelScope.launch {
            repository.removeHighlight(bookId, word)
            refreshMarks()
        }
    }

    private suspend fun refreshMarks() {
        val grouped = repository.highlightsFor(bookId)
            .mapNotNull { entry ->
                val spot = HighlightRef.spot(entry.source) ?: return@mapNotNull null
                val color = HighlightRef.color(entry.source) ?: return@mapNotNull null
                PdfMark(entry.title, spot, color)
            }
            .groupBy { it.spot.page }
        _state.value = _state.value.copy(marks = grouped)
    }

    /** Kırpmayı açıp kapatır; kapalıyken çerçeve tam sayfaya dönüyor. */
    fun setCrop(enabled: Boolean) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                crop = if (enabled) pages?.contentBox() ?: PdfCrop.FULL else PdfCrop.FULL,
            )
        }
    }

    /**
     * Kaldığın yer. Sayfa numarası hem burada saklanıyor hem de kitaplık
     * satırındaki çizgi için yüzdeye çevriliyor.
     */
    fun rememberPage(index: Int) {
        if (bookId == 0L) return
        repository.saveLastPage(bookId, index)
        val total = _state.value.sizes.size
        if (total > 0) {
            repository.saveReadingPercent(bookId, (index + 1) * 100 / total)
        }
    }

    override fun onCleared() {
        pages?.close()
        pages = null
        super.onCleared()
    }
}

/**
 * PDF okuyucusu.
 *
 * Sayfalar resim olarak çiziliyor. Kelime işaretlemek için metne iki ayrı
 * yoldan varılıyor: PDF'in kendi metin katmanı (Android 15 ve üstü) ya da
 * o yoksa sayfanın görüntüsünden yazı tanıma.
 *
 * Sayfalar önceden değil, göründükçe çiziliyor; sekiz yüz sayfalık bir
 * belgeyi baştan çizmek hem beklemek hem belleği doldurmak olurdu. Ölçüler
 * bir kez okunuyor ki liste kaydırırken zıplamasın.
 */
@OptIn(FlowPreview::class)
@Composable
fun PdfReaderRoute(
    bookId: Long,
    modifier: Modifier = Modifier,
    viewModel: PdfReaderViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val prefs = remember { ReaderPrefs(context) }
    var chromeVisible by remember { mutableStateOf(true) }
    var cropOn by remember { mutableStateOf(prefs.pdfCrop) }
    // Basılı tutup bırakılan aralık; metni okumak askıya alınmış bir iş
    // olduğu için hareket ile sonuç arasında bir adım var.
    //
    // Seçim etkinin içinde sıfırlanmıyor: anahtarı etkinin kendisi
    // değiştirirse yeniden derleme çalışan işi iptal ediyor ve metin hiç
    // okunmuyor. Onun yerine her seçime bir sıra numarası veriliyor.
    var tapped by remember { mutableStateOf<TapSpot?>(null) }
    var selectionSerial by remember { mutableLongStateOf(0L) }
    // Görüntüden yazı tanınırken; parmak kalktıktan sonra bir saniye
    // kadar sürüyor ve hiçbir şey olmuyormuş gibi görünmesin diye.
    var reading by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf<Pick?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    // Parmak basılıyken seçilen aralık; sayfanın üstünde canlı gösteriliyor.
    var dragging by remember { mutableStateOf<DragBox?>(null) }
    val scope = rememberCoroutineScope()

    // İki yol var ve sırası önemli. Önce PDF'in kendi metni deneniyor:
    // varsa anında ve harfi harfine doğru. Yoksa — taranmış belgede ya da
    // Android 15'in altındaki telefonlarda — sayfanın görüntüsünden yazı
    // tanınıyor. İkincisi bir saniyeye yakın sürdüğü için peşinen değil,
    // ancak birincisi boş dönünce çalıştırılıyor.
    LaunchedEffect(tapped) {
        val spot = tapped ?: return@LaunchedEffect
        val word = viewModel.selection(spot.page, spot.x, spot.y, spot.endX, spot.endY)
        if (word != null) {
            picking = Pick(spot.page, word)
            return@LaunchedEffect
        }
        reading = true
        val outcome = viewModel.ocrSelection(spot.page, spot.x, spot.y, spot.endX, spot.endY)
        reading = false
        when (outcome) {
            is OcrOutcome.Word -> picking = Pick(spot.page, outcome.word)
            is OcrOutcome.Empty -> {
                notice = "Burada bir kelime tanınamadı. " +
                    "Biraz daha geniş seçmeyi ya da sayfayı yakınlaştırmayı dene."
            }
            is OcrOutcome.Failed -> {
                notice = "Yazı tanıma çalıştırılamadı. Model telefona ilk " +
                    "kullanımda iniyor; internete bağlanıp birkaç dakika " +
                    "sonra tekrar dene.\n\n${outcome.message}"
            }
        }
    }

    LaunchedEffect(bookId) { viewModel.load(bookId, cropOn) }

    // Yakınlaştırma. İki değer var: [zoom] parmak altında anında değişiyor
    // ve yerleşimi büyütüyor; [renderZoom] parmak durunca ona yetişip
    // sayfayı o çözünürlükte yeniden çizdiriyor. Tek değerle yapılsaydı
    // her karede sayfa yeniden çizilirdi.
    var zoom by remember { mutableFloatStateOf(prefs.pdfZoom) }
    var renderZoom by remember { mutableFloatStateOf(prefs.pdfZoom) }
    var zoomLocked by remember { mutableStateOf(prefs.pdfZoomLocked) }

    LaunchedEffect(Unit) {
        snapshotFlow { zoom }
            .debounce(220)
            .collect { settled ->
                renderZoom = settled
                prefs.pdfZoom = settled
            }
    }

    val screenWidthDp = LocalConfiguration.current.screenWidthDp.dp
    val density = LocalDensity.current
    val renderWidthPx = with(density) { (screenWidthDp * renderZoom).roundToPx() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(prefs.theme.background),
    ) {
        when {
            state.loading -> {
                Text(
                    text = "Açılıyor…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = prefs.theme.text,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            state.error != null -> {
                Text(
                    text = state.error.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                )
            }

            else -> {
                val listState = rememberLazyListState()
                val horizontal = rememberScrollState()
                // Kaldığın sayfaya bir kez gidiliyor; ölçüler geldikten
                // sonra, yoksa liste henüz boşken atlama kayboluyor.
                LaunchedEffect(state.sizes.size, state.startPage) {
                    if (state.sizes.isNotEmpty() && state.startPage > 0) {
                        listState.scrollToItem(state.startPage.coerceAtMost(state.sizes.lastIndex))
                    }
                }
                // Görünen sayfa değiştikçe saklanıyor. Uygulamayı kapatınca
                // değil anında: telefon uygulamayı arka planda öldürürse
                // kaldığın yer kaybolmasın.
                LaunchedEffect(listState, state.sizes.size) {
                    snapshotFlow { listState.firstVisibleItemIndex }
                        .distinctUntilChanged()
                        .collect { viewModel.rememberPage(it) }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        // Yakınlaştırınca sayfa ekrandan geniş oluyor; yana
                        // kaydırma buradan. Dikey kaydırma listenin kendi
                        // işi, iki eksen birbirine karışmıyor.
                        .horizontalScroll(horizontal)
                        .pinchToZoom(enabled = !zoomLocked) { change, centroid ->
                            val next = (zoom * change).coerceIn(
                                ReaderPrefs.MIN_ZOOM,
                                ReaderPrefs.MAX_ZOOM,
                            )
                            val step = next / zoom
                            zoom = next
                            // Parmakların ortasındaki nokta yerinde kalsın:
                            // her şey [step] katı büyüdüğü için o noktanın
                            // kenara uzaklığı da aynı katta artıyor, iki
                            // kaydırma da o kadar ilerletiliyor. Bu olmadan
                            // sayfa hep sol üstünden büyüyordu.
                            if (step != 1f) {
                                scope.launch {
                                    horizontal.scrollTo(
                                        ((horizontal.value + centroid.x) * step - centroid.x)
                                            .roundToInt()
                                            .coerceAtLeast(0),
                                    )
                                    listState.scrollToItem(
                                        listState.firstVisibleItemIndex,
                                        ((listState.firstVisibleItemScrollOffset + centroid.y) *
                                            step - centroid.y)
                                            .roundToInt()
                                            .coerceAtLeast(0),
                                    )
                                }
                            }
                        },
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .width(screenWidthDp * zoom)
                            .fillMaxHeight(),
                        contentPadding = PaddingValues(
                            horizontal = if (zoom > 1f) 0.dp else 8.dp,
                            vertical = 12.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(state.sizes.size) { index ->
                            PdfPage(
                                index = index,
                                pageSize = state.sizes[index],
                                crop = state.crop,
                                marks = state.marks[index].orEmpty(),
                                widthPx = renderWidthPx,
                                render = viewModel::render,
                                onTap = { chromeVisible = !chromeVisible },
                                selecting = if (dragging?.page == index) dragging else null,
                                onSelecting = { start, end ->
                                    dragging = if (start == null || end == null) {
                                        null
                                    } else {
                                        DragBox(index, start, end)
                                    }
                                },
                                onSelected = { start, end ->
                                    selectionSerial++
                                    tapped = TapSpot(
                                        page = index,
                                        x = start.x,
                                        y = start.y,
                                        endX = end.x,
                                        endY = end.y,
                                        serial = selectionSerial,
                                    )
                                },
                            )
                        }
                    }
                }

                // Tanıma sürerken. Sayfanın üstünde küçük bir şerit:
                // parmak kalktıktan sonra bir saniye kadar bir şey
                // olmuyormuş gibi görünüyordu.
                if (reading) {
                    Surface(
                        color = MaterialTheme.colorScheme.inverseSurface,
                        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                    ) {
                        Text(
                            text = "Yazı okunuyor…",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        )
                    }
                }

                if (chromeVisible) {
                    val page by remember(listState) {
                        derivedStateOf { listState.firstVisibleItemIndex }
                    }
                    PdfBottomBar(
                        page = page + 1,
                        total = state.sizes.size,
                        zoomPercent = (zoom * 100).toInt(),
                        cropOn = cropOn,
                        zoomLocked = zoomLocked,
                        onToggleCrop = {
                            cropOn = !cropOn
                            prefs.pdfCrop = cropOn
                            viewModel.setCrop(cropOn)
                        },
                        onToggleLock = {
                            zoomLocked = !zoomLocked
                            prefs.pdfZoomLocked = zoomLocked
                        },
                        background = prefs.theme.background,
                        textColor = prefs.theme.text,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
    }

    picking?.let { pick ->
        // Kitaptaki renk kutusunun aynısı: aynı kalemler, aynı anlamlar.
        ColorPickerDialog(
            request = PendingHighlight(pick.word.text, pick.word.context),
            current = null,
            onDismiss = { picking = null },
            onPick = { color, keepContext ->
                viewModel.mark(
                    word = if (keepContext) pick.word else pick.word.copy(context = ""),
                    page = pick.page,
                    color = color,
                )
                picking = null
            },
            onRemove = {
                viewModel.removeMark(pick.word.text)
                picking = null
            },
        )
    }

    notice?.let { message ->
        AlertDialog(
            onDismissRequest = { notice = null },
            title = { Text("Kelime bulunamadı") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { notice = null }) { Text("Tamam") } },
        )
    }
}


/** Parmak basılıyken sürüklenen aralık; sayfanın üstünde çiziliyor. */
private data class DragBox(val page: Int, val start: Offset, val end: Offset)

/** Seçilen aralık: hangi sayfa, nereden nereye (sayfanın oranı olarak). */
private data class TapSpot(
    val page: Int,
    val x: Float,
    val y: Float,
    val endX: Float,
    val endY: Float,
    /**
     * Her seçim için artan sıra numarası.
     *
     * Bu olmadan aynı yeri iki kez seçmek işe yaramıyordu: değer eşit
     * olduğu için etkinin anahtarı değişmiyor ve etki yeniden
     * başlamıyordu.
     */
    val serial: Long,
)

/** Renk kutusunda bekleyen seçim. */
private data class Pick(val page: Int, val word: PdfWord)

@Composable
private fun PdfPage(
    index: Int,
    pageSize: PdfPageSize,
    crop: PdfCrop,
    marks: List<PdfMark>,
    widthPx: Int,
    render: suspend (Int, Int, PdfCrop) -> Bitmap?,
    onTap: () -> Unit,
    /** Parmak basılıyken sürüklenen aralık; bu sayfada değilse null. */
    selecting: DragBox?,
    /** Sürüklerken: aralık ekranda gösterilsin diye. Bitince ikisi de null. */
    onSelecting: (start: Offset?, end: Offset?) -> Unit,
    onSelected: (start: Offset, end: Offset) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val bitmap by produceState<ImageBitmap?>(null, index, widthPx, crop) {
        value = render(index, widthPx, crop)?.asImageBitmap()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            // Kırpılmış çerçevenin oranı (en / boy).
            //
            // Bölme bir süre ters yazılıydı ve kırpmanın bütün belirtisi
            // oradan geliyordu: yanlardan ne kadar çok kırpılırsa kutu o
            // kadar kısalıyor, resim ortalanıp üstten ve alttan
            // kesiliyordu. Kırpma kutusunun kendisiyle ilgisi yoktu.
            //
            // Kırpılmış sayfanın eni = sayfa_eni × kırpma_eni, boyu da
            // sayfa_boyu × kırpma_boyu; oran ikisinin bölümü.
            .aspectRatio(pageSize.ratio * crop.width / crop.height)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .pointerInput(index, crop) {
                detectTapGestures(onTap = { onTap() })
            }
            // Metin seçme: parmağı basılı tut, istersen sürükle, bırak.
            // Okurken beklenen hareket bu; çift dokunuş metin kutularının
            // yöntemi, sayfa okurken kimse denemiyor.
            .pointerInput(index, crop) {
                // Ölçü **hareket anında** okunuyor, blok başında değil.
                // Blok bileşen ekrana eklenirken çalışıyor ve o sırada
                // ölçüm henüz yapılmamış oluyor: sıfıra bölmeyi önleyen
                // alt sınır yüzünden her dokunuş sayfanın aynı köşesine
                // düşüyor, seçim de hep boş dönüyordu. Bir önceki
                // sürümde ölçü geri çağrının içinde okunduğu için sorun
                // görünmemişti.
                //
                // Buradaki ölçü bileşenin piksel ölçüsü, sayfanın kendi
                // ölçüsü değil; adları çakışmasın diye parametre pageSize.
                val scope = this
                fun fractionOf(offset: Offset): Offset {
                    val boxWidth = scope.size.width.toFloat().coerceAtLeast(1f)
                    val boxHeight = scope.size.height.toFloat().coerceAtLeast(1f)
                    return Offset(
                        crop.left + (offset.x / boxWidth) * crop.width,
                        crop.top + (offset.y / boxHeight) * crop.height,
                    )
                }

                var start = Offset.Zero
                var end = Offset.Zero
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        start = fractionOf(offset)
                        end = start
                        onSelecting(start, end)
                    },
                    onDrag = { change, _ ->
                        end = fractionOf(change.position)
                        onSelecting(start, end)
                    },
                    onDragEnd = {
                        onSelected(start, end)
                        onSelecting(null, null)
                    },
                    onDragCancel = { onSelecting(null, null) },
                )
            },
    ) {
        bitmap?.let { image ->
            Image(
                bitmap = image,
                contentDescription = null,
                // Fit, FillWidth değil: oran bir kıl payı şaşsa bile
                // sayfanın bir parçası kesilmiyor, en fazla kenarda
                // birkaç piksel boşluk kalıyor.
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // İşaretler sayfanın üstüne çiziliyor. Yerleri sayfanın oranı
        // olarak saklandığı için yakınlaştırma ve kırpma değişse de
        // kelimenin üstünde duruyorlar.
        if (marks.isNotEmpty() || selecting != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val boxWidth = this.size.width
                val boxHeight = this.size.height

                // Parmak altındaki aralık: ne seçtiğini bırakmadan görmek
                // için. Gerçek kelime sınırları bırakınca belli oluyor.
                selecting?.let { drag ->
                    val left = (minOf(drag.start.x, drag.end.x) - crop.left) /
                        crop.width * boxWidth
                    val right = (maxOf(drag.start.x, drag.end.x) - crop.left) /
                        crop.width * boxWidth
                    val top = (minOf(drag.start.y, drag.end.y) - crop.top) /
                        crop.height * boxHeight
                    val bottom = (maxOf(drag.start.y, drag.end.y) - crop.top) /
                        crop.height * boxHeight
                    drawRect(
                        color = Color(0xFF4FA3F7).copy(alpha = 0.25f),
                        topLeft = Offset(left, top),
                        size = Size(
                            (right - left).coerceAtLeast(6f),
                            (bottom - top).coerceAtLeast(6f),
                        ),
                    )
                }
                marks.forEach { mark ->
                    val left = (mark.spot.left - crop.left) / crop.width * boxWidth
                    val top = (mark.spot.top - crop.top) / crop.height * boxHeight
                    val right = (mark.spot.right - crop.left) / crop.width * boxWidth
                    val bottom = (mark.spot.bottom - crop.top) / crop.height * boxHeight
                    if (right <= left || bottom <= top) return@forEach
                    drawRect(
                        color = markPaint(mark.color).copy(alpha = 0.38f),
                        topLeft = Offset(left, top),
                        size = Size(right - left, bottom - top),
                    )
                }
            }
        }
    }
}

/** İşaret rengi; kitaptaki kalemlerle aynı tonlar. */
private fun markPaint(color: HighlightColor): Color = when (color) {
    HighlightColor.YELLOW -> Color(0xFFFFC93C)
    HighlightColor.BLUE -> Color(0xFF4FA3F7)
    HighlightColor.GREEN -> Color(0xFF5FC46B)
    HighlightColor.RED -> Color(0xFFF2708A)
}

@Composable
private fun PdfBottomBar(
    page: Int,
    total: Int,
    zoomPercent: Int,
    cropOn: Boolean,
    zoomLocked: Boolean,
    onToggleCrop: () -> Unit,
    onToggleLock: () -> Unit,
    background: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = background,
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
    ) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            LinearProgressIndicator(
                progress = { if (total > 0) page.toFloat() / total else 0f },
                color = textColor.copy(alpha = 0.75f),
                trackColor = textColor.copy(alpha = 0.12f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
            ) {
                Text(
                    text = if (zoomPercent > 100) {
                        "Sayfa $page/$total · %$zoomPercent"
                    } else {
                        "Sayfa $page/$total"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = textColor.copy(alpha = 0.7f),
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                )
                // İki küçük düğme sayının yanında: okurken lazım olan tek
                // şey bunlar. Üstte kitabın adını yazan bir çubuk vardı,
                // okurken hiçbir işe yaramıyordu.
                IconButton(onClick = onToggleCrop) {
                    Icon(
                        imageVector = MerkezIcons.Crop,
                        contentDescription = if (cropOn) {
                            "Kenar kırpma açık"
                        } else {
                            "Kenarları kırp"
                        },
                        tint = if (cropOn) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            textColor.copy(alpha = 0.55f)
                        },
                    )
                }
                IconButton(onClick = onToggleLock) {
                    Icon(
                        imageVector = if (zoomLocked) MerkezIcons.Lock else MerkezIcons.LockOpen,
                        contentDescription = if (zoomLocked) {
                            "Yakınlaştırma kilitli"
                        } else {
                            "Yakınlaştırmayı kilitle"
                        },
                        tint = if (zoomLocked) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            textColor.copy(alpha = 0.55f)
                        },
                    )
                }
            }
        }
    }
}
