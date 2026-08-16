package com.ahmety.uygulama.ui.sync

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahmety.uygulama.core.database.sync.DocumentTreeTransport
import com.ahmety.uygulama.core.database.sync.SyncCrypto
import com.ahmety.uygulama.core.database.sync.SyncEngine
import com.ahmety.uygulama.core.database.sync.SyncError
import com.ahmety.uygulama.core.database.sync.SyncOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SyncUiState(
    val folderLabel: String? = null,
    val recoveryKey: String? = null,
    val running: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class SyncViewModel @Inject constructor(
    private val engine: SyncEngine,
    private val transport: DocumentTreeTransport,
    private val crypto: SyncCrypto,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SyncUiState())
    val uiState: StateFlow<SyncUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    private fun refresh() {
        _uiState.value = _uiState.value.copy(
            folderLabel = transport.folderUri()?.lastPathSegment,
            recoveryKey = crypto.currentKey(),
        )
    }

    fun setFolder(uri: Uri) {
        val ok = transport.setFolder(uri)
        _uiState.value = _uiState.value.copy(
            message = if (ok) null else "Klasör erişimi alınamadı.",
        )
        refresh()
    }

    fun clearFolder() {
        transport.clearFolder()
        refresh()
    }

    /** İlk cihazda anahtarı üretir; ikinci cihaza elle taşınacak. */
    fun generateKey() {
        crypto.ensureKey()
        refresh()
    }

    fun importKey(value: String) {
        val ok = crypto.importKey(value)
        _uiState.value = _uiState.value.copy(
            message = if (ok) "Anahtar alındı." else "Anahtar geçersiz görünüyor.",
        )
        refresh()
    }

    fun syncNow() {
        if (_uiState.value.running) return
        _uiState.value = _uiState.value.copy(running = true, message = null)
        viewModelScope.launch {
            val outcome = engine.sync()
            _uiState.value = _uiState.value.copy(
                running = false,
                message = describe(outcome),
            )
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}

private fun describe(outcome: SyncOutcome): String = when (outcome.error) {
    SyncError.NO_FOLDER -> "Önce paylaşılan klasörü seç."
    SyncError.NO_KEY -> "Önce kurtarma anahtarını oluştur veya gir."
    SyncError.WRITE_FAILED -> "Klasöre yazılamadı. Erişim izni hâlâ geçerli mi?"
    SyncError.DECRYPT_FAILED ->
        "Karşı cihazın dosyaları çözülemedi — iki cihazda aynı kurtarma anahtarı var mı?"

    null -> buildString {
        append("${outcome.exportedChanges} değişiklik gönderildi")
        append(", ${outcome.importedChanges} değişiklik alındı")
        if (outcome.skippedChanges > 0) {
            // Atlananlar hata değil: yereldeki kayıt daha yeniyse gelen uygulanmaz.
            append(" (${outcome.skippedChanges} eski kayıt atlandı)")
        }
        append(".")
    }
}
