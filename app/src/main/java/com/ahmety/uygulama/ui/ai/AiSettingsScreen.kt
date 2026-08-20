package com.ahmety.uygulama.ui.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.ahmety.uygulama.core.ai.AiSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AiSettingsViewModel @Inject constructor(
    private val settings: AiSettings,
) : ViewModel() {
    fun masked(): String = settings.maskedKey()

    fun save(key: String) {
        settings.apiKey = key
    }

    fun clear() = settings.clear()

    fun configured(): Boolean = settings.configured

    fun model(): String = settings.model

    fun saveModel(value: String) {
        settings.model = value.ifBlank { AiSettings.DEFAULT_MODEL }
    }

    val models: List<String> get() = AiSettings.MODELS
}

/**
 * OpenAI anahtarının girildiği ekran.
 *
 * Anahtar yalnızca bu cihazda, uygulamanın kendi özel alanında tutuluyor;
 * depoya veya yedeklere hiçbir şekilde yazılmıyor. Ekranda da tam hâli
 * gösterilmiyor.
 */
@Composable
fun AiSettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: AiSettingsViewModel = hiltViewModel(),
) {
    var input by remember { mutableStateOf("") }
    var configured by remember { mutableStateOf(viewModel.configured()) }
    var masked by remember { mutableStateOf(viewModel.masked()) }
    var saved by remember { mutableStateOf(false) }
    var model by remember { mutableStateOf(viewModel.model()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Yapay zekâ", style = MaterialTheme.typography.headlineMedium)

        Text(
            text = "Kitapta işaretlediğin ama sözlükte karşılığı olmayan " +
                "kelimelerin anlamını, tanımını, örneklerini ve öbeklerini " +
                "doldurmak için kullanılır.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = if (configured) "Anahtar kayıtlı: $masked" else "Anahtar girilmemiş.",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = "Anahtar yalnızca bu telefonda, uygulamanın kendi " +
                        "alanında saklanır. Depoya veya yedeğe yazılmaz.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        OutlinedTextField(
            value = input,
            onValueChange = {
                input = it
                saved = false
            },
            label = { Text("OpenAI anahtarı (sk-…)") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = input.isNotBlank(),
                onClick = {
                    viewModel.save(input)
                    input = ""
                    configured = viewModel.configured()
                    masked = viewModel.masked()
                    saved = true
                },
            ) {
                Text("Kaydet")
            }
            if (configured) {
                TextButton(
                    onClick = {
                        viewModel.clear()
                        configured = false
                        masked = ""
                        saved = false
                    },
                ) {
                    Text("Sil", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        // Model seçimi: ucuz olan çoğu kelimeye yetiyor, zor cümlelerde
        // büyük model gözle görülür biçimde daha iyi çözümlüyor. Liste kapalı
        // değil, yeni bir modelin adını elle de yazabiliyorsun.
        Text("Model", style = MaterialTheme.typography.titleSmall)
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        ) {
            viewModel.models.forEach { option ->
                FilterChip(
                    selected = model == option,
                    onClick = {
                        model = option
                        viewModel.saveModel(option)
                    },
                    label = { Text(option) },
                )
            }
        }
        OutlinedTextField(
            value = model,
            onValueChange = {
                model = it
                viewModel.saveModel(it)
            },
            label = { Text("Model adı") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        if (saved) {
            Text(
                text = "Kaydedildi.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Text(
            text = "Güvenlik: anahtarını bir sohbete veya mesaja yapıştırdıysan, " +
                "onu iptal edip yenisini üretmen daha doğru olur.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
