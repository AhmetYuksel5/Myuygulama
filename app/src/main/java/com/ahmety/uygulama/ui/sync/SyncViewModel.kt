package com.ahmety.uygulama.ui.sync

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahmety.uygulama.core.database.sync.ActiveTransport
import com.ahmety.uygulama.core.database.sync.GitHubTransport
import com.ahmety.uygulama.core.database.sync.LanPeer
import com.ahmety.uygulama.core.database.sync.SyncMode
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
    val mode: SyncMode = SyncMode.LAN,
    /** Bu cihazın ağda görünen adı. */
    val deviceName: String = "",
    /** Ağda bulunan Merkez cihazları. */
    val peers: List<String> = emptyList(),
    val listening: Boolean = false,
    /** GitHub yolu: "kullanıcı/depo" ve maskelenmiş anahtar. */
    val repository: String = "",
    val maskedToken: String = "",
)

@HiltViewModel
class SyncViewModel @Inject constructor(
    private val engine: SyncEngine,
    private val transport: DocumentTreeTransport,
    private val active: ActiveTransport,
    private val peer: LanPeer,
    private val github: GitHubTransport,
    private val crypto: SyncCrypto,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SyncUiState())
    val uiState: StateFlow<SyncUiState> = _uiState.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            peer.peers.collect { found ->
                _uiState.value = _uiState.value.copy(peers = found.map { it.name })
            }
        }
        viewModelScope.launch {
            peer.running.collect { on ->
                _uiState.value = _uiState.value.copy(listening = on)
            }
        }
    }

    /**
     * Ağ yolunda cihazın duyurusu ve sunucusu ekran açıkken çalışıyor:
     * arka planda sürekli açık tutmak pili yiyor ve iki telefon da bu
     * ekrandayken zaten yetiyor.
     */
    fun startPeering() {
        if (active.mode == SyncMode.LAN) peer.start()
    }

    fun stopPeering() = peer.stop()

    fun setMode(mode: SyncMode) {
        active.mode = mode
        if (mode == SyncMode.LAN) peer.start() else peer.stop()
        refresh()
    }

    /** Hazır değil uyarısının yol başına değişen karşılığı. */
    private fun notReady(): String = when (active.mode) {
        SyncMode.LAN ->
            "İkinci telefon görünmüyor. İkisi de aynı Wi-Fi'de ve bu ekranda açık mı?"

        SyncMode.GITHUB -> "Önce GitHub deposunu ve erişim anahtarını gir."
        SyncMode.FOLDER -> "Önce paylaşılan klasörü seç."
    }

    private fun refresh() {
        _uiState.value = _uiState.value.copy(
            folderLabel = transport.folderUri()?.lastPathSegment,
            recoveryKey = crypto.currentKey(),
            mode = active.mode,
            deviceName = peer.deviceName,
            repository = github.repository,
            maskedToken = github.maskedToken(),
        )
    }

    /**
     * GitHub deposunu ve anahtarını kaydeder.
     *
     * Anahtar ekranda bir daha tam hâliyle görünmüyor; yalnızca maskesi.
     */
    fun setGitHub(repository: String, token: String) {
        github.repository = repository
        if (token.isNotBlank()) github.token = token
        _uiState.value = _uiState.value.copy(
            message = if (github.configured) {
                "GitHub ayarlandı."
            } else {
                "Depo \"kullanıcı/depo\" biçiminde olmalı ve anahtar boş kalmamalı."
            },
        )
        refresh()
    }

    fun clearGitHub() {
        github.clear()
        refresh()
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
                message = if (outcome.error == SyncError.NO_FOLDER) {
                    notReady()
                } else {
                    describe(outcome)
                },
            )
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}

private fun describe(outcome: SyncOutcome): String = when (outcome.error) {
    SyncError.NO_FOLDER ->
        "Hazır değil: klasör yolunda klasörü seç, ağ yolunda ikinci telefonun " +
            "da bu ekranda açık olması gerekiyor."
    SyncError.NO_KEY -> "Önce kurtarma anahtarını oluştur veya gir."
    SyncError.WRITE_FAILED ->
        "Yazılamadı. Klasör yolunda erişim izni, GitHub yolunda depo adı ve " +
            "anahtar hâlâ geçerli mi?"
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
        if (outcome.receivedFiles > 0) {
            append(" ${outcome.receivedFiles} kitap/altyazı dosyası alındı.")
        }
        if (outcome.sentParts > 0) {
            append(" ${outcome.sentParts} dosya parçası gönderildi.")
        }
    }
}
