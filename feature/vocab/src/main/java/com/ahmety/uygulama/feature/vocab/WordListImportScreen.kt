package com.ahmety.uygulama.feature.vocab

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WordListImportUiState(
    val busy: Boolean = false,
    val message: String? = null,
    val failed: Boolean = false,
)

@HiltViewModel
class WordListImportViewModel @Inject constructor(
    private val repository: WordListRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(WordListImportUiState())
    val state: StateFlow<WordListImportUiState> = _state.asStateFlow()

    fun load(uri: Uri) {
        if (_state.value.busy) return
        _state.value = WordListImportUiState(busy = true)
        viewModelScope.launch {
            val text = repository.read(uri)
            if (text.isNullOrBlank()) {
                _state.value = WordListImportUiState(
                    message = "Dosya okunamadı ya da boş.",
                    failed = true,
                )
                return@launch
            }
            val parsed = WordListFile.parse(text)
            if (parsed == null) {
                _state.value = WordListImportUiState(
                    message = "Dosyada liste bulunamadı. İlk satır listenin adı, " +
                        "sonraki satırlar maddeler olmalı.",
                    failed = true,
                )
                return@launch
            }
            val result = runCatching { repository.import(parsed) }.getOrNull()
            _state.value = if (result == null) {
                WordListImportUiState(message = "Liste kaydedilemedi.", failed = true)
            } else {
                val skipped = if (result.skipped > 0) {
                    " ${result.skipped} madde zaten listendeydi, atlandı."
                } else {
                    ""
                }
                WordListImportUiState(
                    message = "“${result.name}” eklendi: ${result.added} madde.$skipped",
                )
            }
        }
    }
}

/**
 * Kelime listesi yükleme.
 *
 * Kendi topladığın listeyi kitaba ya da filme bağlamadan çalışabilmek için.
 * Kaynak olarak kendi adıyla görünüyor; kelime listesinde "Listeden" diye
 * süzülebiliyor.
 */
@Composable
fun WordListImportRoute(
    modifier: Modifier = Modifier,
    viewModel: WordListImportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Dosya türü serbest bırakılıyor: .csv dosyaları cihazdan cihaza farklı
    // türlerle geliyor (text/csv, application/octet-stream…) ve daraltmak
    // kullanıcının kendi dosyasını seçememesine yol açıyor.
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::load) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Kelime listesi yükle",
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = "Düz metin (.txt) ya da .csv dosyası. Her satır bir madde; " +
                "ilk satır listenin adı. Tırnaklı da olur tırnaksız da.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = "Tek kelimeler mavi (kelime kartı), boşluk içeren maddeler " +
                "kırmızı (ifade kartı) olarak ekleniyor. Satır virgülden " +
                "bölünmüyor, yani cümle içindeki virgül maddeyi parçalamıyor.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Button(
            enabled = !state.busy,
            onClick = { picker.launch(arrayOf("*/*")) },
        ) { Text("Dosya seç") }

        if (state.busy) CircularProgressIndicator()

        state.message?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = if (state.failed) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
        }
    }
}
