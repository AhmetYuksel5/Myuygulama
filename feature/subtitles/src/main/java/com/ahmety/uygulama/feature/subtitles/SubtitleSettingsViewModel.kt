package com.ahmety.uygulama.feature.subtitles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Ayar ekranındaki "bağlantıyı sına" düğmesi.
 *
 * Hatanın anahtardan mı, hesaptan mı, sunucudan mı geldiğini ayırt etmek
 * için giriş ve arama ayrı ayrı deneniyor; film ararken tek bir "503"
 * görmek yerine hangi adımın düştüğü yazılıyor.
 */
@HiltViewModel
class SubtitleSettingsViewModel @Inject constructor(
    private val client: OpenSubtitlesClient,
    private val settings: SubtitleSettings,
) : ViewModel() {

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun test() {
        if (!settings.configured) {
            _message.value = "Önce API anahtarını gir."
            return
        }
        _message.value = "Deneniyor…"
        viewModelScope.launch {
            if (settings.username.isNotBlank()) {
                val login = client.login()
                if (login is SubtitleResult.Failed) {
                    _message.value = "Giriş başarısız: ${login.reason}"
                    return@launch
                }
            }
            _message.value = when (val result = client.search("matrix", 1999)) {
                is SubtitleResult.Failed -> "Arama başarısız: ${result.reason}"
                is SubtitleResult.Ok ->
                    "Çalışıyor. Deneme aramasında ${result.value.size} altyazı bulundu."
            }
        }
    }
}
