package com.ahmety.uygulama.feature.vocab

import android.net.Uri
import androidx.lifecycle.ViewModel
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
