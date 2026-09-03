package com.ahmety.uygulama.feature.ebook

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import com.ahmety.uygulama.core.designsystem.MerkezTopBar
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

    fun load(id: Long) {
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
        }
    }

    suspend fun render(index: Int, widthPx: Int): Bitmap? = pages?.render(index, widthPx)

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
@Composable
fun PdfReaderRoute(
    bookId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PdfReaderViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(bookId) { viewModel.load(bookId) }

    val context = LocalContext.current
    val prefs = remember { ReaderPrefs(context) }
    var chromeVisible by remember { mutableStateOf(true) }

    val widthPx = with(LocalDensity.current) {
        LocalConfiguration.current.screenWidthDp.dp.roundToPx()
    }

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

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        horizontal = 8.dp,
                        vertical = 12.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.sizes.size) { index ->
                        PdfPage(
                            index = index,
                            size = state.sizes[index],
                            widthPx = widthPx,
                            render = viewModel::render,
                            onTap = { chromeVisible = !chromeVisible },
                        )
                    }
                }

                if (chromeVisible) {
                    MerkezTopBar(
                        title = state.title,
                        onBack = onBack,
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                    val page by remember(listState) {
                        derivedStateOf { listState.firstVisibleItemIndex }
                    }
                    PdfBottomBar(
                        page = page + 1,
                        total = state.sizes.size,
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
    widthPx: Int,
    render: suspend (Int, Int) -> Bitmap?,
    onTap: () -> Unit,
) {
    val bitmap by produceState<ImageBitmap?>(null, index, widthPx) {
        value = render(index, widthPx)?.asImageBitmap()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            // Oran önceden biliniyor: sayfa çizilmeden de yerini tutuyor,
            // böylece kaydırırken liste zıplamıyor.
            .aspectRatio(size.ratio)
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
        Box(modifier = Modifier.navigationBarsPadding()) {
            LinearProgressIndicator(
                progress = { if (total > 0) page.toFloat() / total else 0f },
                color = textColor.copy(alpha = 0.75f),
                trackColor = textColor.copy(alpha = 0.12f),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
            )
            Text(
                text = "Sayfa $page/$total",
                style = MaterialTheme.typography.labelMedium,
                color = textColor.copy(alpha = 0.7f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(vertical = 10.dp),
            )
        }
    }
}
