package com.ahmety.uygulama.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UpdateUiState(
    val currentVersion: String = "",
    val checking: Boolean = false,
    val downloadProgress: Float? = null,
    val available: UpdateInfo? = null,
    val canInstall: Boolean = true,
    val message: String? = null,
)

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val checker: UpdateChecker,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        UpdateUiState(
            currentVersion = checker.currentVersionName(),
            canInstall = checker.canInstallPackages(),
        ),
    )
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    fun refreshInstallPermission() {
        _uiState.value = _uiState.value.copy(canInstall = checker.canInstallPackages())
    }

    fun check() {
        if (_uiState.value.checking) return
        _uiState.value = _uiState.value.copy(checking = true, message = null)
        viewModelScope.launch {
            val info = checker.check()
            _uiState.value = _uiState.value.copy(
                checking = false,
                available = info,
                message = if (info == null) "En güncel sürümü kullanıyorsun." else null,
            )
        }
    }

    fun downloadAndInstall() {
        val info = _uiState.value.available ?: return
        _uiState.value = _uiState.value.copy(downloadProgress = 0f, message = null)
        viewModelScope.launch {
            val file = checker.download(info) { progress ->
                _uiState.value = _uiState.value.copy(downloadProgress = progress)
            }
            _uiState.value = _uiState.value.copy(downloadProgress = null)
            if (file == null) {
                _uiState.value = _uiState.value.copy(message = "İndirme başarısız oldu.")
            } else {
                checker.install(file)
            }
        }
    }

    fun openInstallPermissionSettings(open: (android.content.Intent) -> Unit) {
        open(checker.unknownSourcesSettingsIntent())
    }
}

@Composable
fun UpdateScreen(
    modifier: Modifier = Modifier,
    viewModel: UpdateViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Kurulum izni sistem ayarlarında verildikten sonra geri dönüşte tazeliyoruz.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshInstallPermission()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Güncellemeler", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = "Yüklü sürüm: ${state.currentVersion.ifBlank { "bilinmiyor" }}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (!state.canInstall) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Kurulum izni gerekiyor", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Android, bir uygulamanın başka bir uygulama kurmasına ancak " +
                            "açıkça izin verirsen müsaade ediyor. Bir kez vermen yeterli.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = {
                            viewModel.openInstallPermissionSettings(context::startActivity)
                        },
                    ) {
                        Text("İzin ver")
                    }
                }
            }
        }

        state.available?.let { info ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "${info.versionName} sürümü hazır",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (info.notes.isNotBlank()) {
                        Text(
                            text = info.notes.lineSequence().take(6).joinToString("\n"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(
                        onClick = viewModel::downloadAndInstall,
                        enabled = state.downloadProgress == null && state.canInstall,
                    ) {
                        Text("İndir ve kur")
                    }
                }
            }
        }

        state.downloadProgress?.let { progress ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "İndiriliyor… %${(progress * 100).toInt()}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        OutlinedButton(
            onClick = viewModel::check,
            enabled = !state.checking && state.downloadProgress == null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.checking) "Bakılıyor…" else "Güncelleme ara")
        }

        state.message?.let { message ->
            Text(text = message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
