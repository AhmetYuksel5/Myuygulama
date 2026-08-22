package com.ahmety.uygulama.ui.update

import androidx.lifecycle.ViewModel
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
