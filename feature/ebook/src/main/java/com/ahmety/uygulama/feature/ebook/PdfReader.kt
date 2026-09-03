package com.ahmety.uygulama.feature.ebook

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.FlowPreview
import com.ahmety.uygulama.core.designsystem.pinchToZoom
import com.ahmety.uygulama.core.designsystem.MerkezIcons
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
    /** Açılışta gidilecek sayfa; kaldığın yer. */
    val startPage: Int = 0,
    val loading: Boolean = true,
    val error: String? = null,
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
        }
    }

    suspend fun render(index: Int, widthPx: Int, crop: PdfCrop): Bitmap? =
        pages?.render(index, widthPx, crop)

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
 * Sayfalar resim olarak çiziliyor: Android'in kendi motoru metni vermiyor,
 * metin çıkaran kütüphaneler ise uygulamanın tamamından büyük. Bu yüzden
 * PDF'te kelime işaretleme yok — okuma ve kaldığın yer var.
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

                val horizontal = rememberScrollState()
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        // Yakınlaştırınca sayfa ekrandan geniş oluyor; yana
                        // kaydırma buradan. Dikey kaydırma listenin kendi
                        // işi, iki eksen birbirine karışmıyor.
                        .horizontalScroll(horizontal)
                        .pinchToZoom(enabled = !zoomLocked) { change ->
                            zoom = (zoom * change).coerceIn(
                                ReaderPrefs.MIN_ZOOM,
                                ReaderPrefs.MAX_ZOOM,
                            )
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
                                size = state.sizes[index],
                                crop = state.crop,
                                widthPx = renderWidthPx,
                                render = viewModel::render,
                                onTap = { chromeVisible = !chromeVisible },
                            )
                        }
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
}

@Composable
private fun PdfPage(
    index: Int,
    size: PdfPageSize,
    crop: PdfCrop,
    widthPx: Int,
    render: suspend (Int, Int, PdfCrop) -> Bitmap?,
    onTap: () -> Unit,
) {
    val bitmap by produceState<ImageBitmap?>(null, index, widthPx, crop) {
        value = render(index, widthPx, crop)?.asImageBitmap()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            // Oran önceden biliniyor: sayfa çizilmeden de yerini tutuyor,
            // böylece kaydırırken liste zıplamıyor.
            // Kırpılmış çerçevenin oranı: yerleşim sayfa çizilmeden de
            // ne kadar yer tutacağını biliyor.
            .aspectRatio(size.ratio * crop.height / crop.width)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onTap),
    ) {
        bitmap?.let { image ->
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
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
