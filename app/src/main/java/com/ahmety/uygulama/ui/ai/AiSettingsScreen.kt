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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import com.ahmety.uygulama.core.designsystem.MerkezTopBar
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
import com.ahmety.uygulama.core.ai.AgentProvider
import com.ahmety.uygulama.core.ai.AiSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AiSettingsViewModel @Inject constructor(
    private val settings: AiSettings,
) : ViewModel() {
    fun masked(): String = settings.maskedKey()
    fun save(key: String) { settings.apiKey = key }
    fun clear() = settings.clear()
    fun configured(): Boolean = settings.configured
    fun model(): String = settings.model
    fun saveModel(value: String) { settings.model = value.ifBlank { AiSettings.DEFAULT_MODEL } }
    val models: List<String> get() = AiSettings.MODELS

    fun agentProvider(): AgentProvider = settings.agentProvider
    fun saveAgentProvider(provider: AgentProvider) { settings.agentProvider = provider }

    fun maskedGemini(): String = settings.maskedGeminiKey()
    fun saveGemini(key: String) { settings.geminiApiKey = key }
    fun clearGemini() = settings.clearGemini()
    fun geminiConfigured(): Boolean = settings.geminiConfigured
    fun geminiModel(): String = settings.geminiModel
    fun saveGeminiModel(value: String) {
        settings.geminiModel = value.ifBlank { AiSettings.DEFAULT_GEMINI_MODEL }
    }
    val geminiModels: List<String> get() = AiSettings.GEMINI_MODELS

    fun maskedClaude(): String = settings.maskedClaudeKey()
    fun saveClaude(key: String) { settings.claudeApiKey = key }
    fun clearClaude() = settings.clearClaude()
    fun claudeConfigured(): Boolean = settings.claudeConfigured
    fun claudeModel(): String = settings.claudeModel
    fun saveClaudeModel(value: String) {
        settings.claudeModel = value.ifBlank { AiSettings.DEFAULT_CLAUDE_MODEL }
    }
    val claudeModels: List<String> get() = AiSettings.CLAUDE_MODELS
}

/**
 * Yapay zekâ anahtarlarının girildiği ekran.
 *
 * Anahtarlar yalnızca bu cihazda, uygulamanın kendi özel alanında tutuluyor;
 * depoya veya yedeklere hiçbir şekilde yazılmıyor. Ekranda da tam hâlleri
 * gösterilmiyor.
 */
@Composable
fun AiSettingsScreen(
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: AiSettingsViewModel = hiltViewModel(),
) {
    var provider by remember { mutableStateOf(viewModel.agentProvider()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        MerkezTopBar(title = "Yapay zekâ", onBack = onBack)

        Text(
            text = "Sözlük, ekrana sor ve ekran ajanı anahtarları yalnızca bu " +
                "telefonda, uygulamanın kendi alanında saklanır. Depoya veya " +
                "yedeğe yazılmaz.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // ---- OpenAI (sözlük + ekrana sor + sesli yazma) ----
        ProviderKeySection(
            title = "OpenAI (sözlük ve ekrana sor)",
            fieldLabel = "OpenAI anahtarı (sk-…)",
            initialConfigured = viewModel.configured(),
            initialMasked = viewModel.masked(),
            initialModel = viewModel.model(),
            presetModels = viewModel.models,
            onSaveKey = {
                viewModel.save(it)
                viewModel.configured() to viewModel.masked()
            },
            onClearKey = { viewModel.clear() },
            onSaveModel = { viewModel.saveModel(it) },
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // ---- Ekran ajanı sağlayıcı seçimi ----
        Text("Ekran ajanı", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Görev verdiğinde ekranı okuyup senin yerine dokunarak ve " +
                "yazarak adımları uygular. Banka ve ödeme uygulamalarında " +
                "kendiliğinden durur.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        ) {
            AgentProvider.entries.forEach { option ->
                FilterChip(
                    selected = provider == option,
                    onClick = {
                        provider = option
                        viewModel.saveAgentProvider(option)
                    },
                    label = { Text(option.label) },
                )
            }
        }

        // ---- Gemini Flash ----
        ProviderKeySection(
            title = "Gemini API",
            fieldLabel = "Gemini anahtarı (AIza…)",
            initialConfigured = viewModel.geminiConfigured(),
            initialMasked = viewModel.maskedGemini(),
            initialModel = viewModel.geminiModel(),
            presetModels = viewModel.geminiModels,
            onSaveKey = {
                viewModel.saveGemini(it)
                viewModel.geminiConfigured() to viewModel.maskedGemini()
            },
            onClearKey = { viewModel.clearGemini() },
            onSaveModel = { viewModel.saveGeminiModel(it) },
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // ---- Anthropic Claude Opus ----
        ProviderKeySection(
            title = "Anthropic Claude API",
            fieldLabel = "Claude anahtarı (sk-ant-…)",
            initialConfigured = viewModel.claudeConfigured(),
            initialMasked = viewModel.maskedClaude(),
            initialModel = viewModel.claudeModel(),
            presetModels = viewModel.claudeModels,
            onSaveKey = {
                viewModel.saveClaude(it)
                viewModel.claudeConfigured() to viewModel.maskedClaude()
            },
            onClearKey = { viewModel.clearClaude() },
            onSaveModel = { viewModel.saveClaudeModel(it) },
        )

        Text(
            text = "Güvenlik: anahtarını bir sohbete veya mesaja yapıştırdıysan, " +
                "onu iptal edip yenisini üretmen daha doğru olur.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Tek bir yapay zekâ sağlayıcısının anahtar ve model bloğu. */
@Composable
private fun ProviderKeySection(
    title: String,
    fieldLabel: String,
    initialConfigured: Boolean,
    initialMasked: String,
    initialModel: String,
    presetModels: List<String>,
    onSaveKey: (String) -> Pair<Boolean, String>,
    onClearKey: () -> Unit,
    onSaveModel: (String) -> Unit,
) {
    var input by remember { mutableStateOf("") }
    var configured by remember { mutableStateOf(initialConfigured) }
    var masked by remember { mutableStateOf(initialMasked) }
    var saved by remember { mutableStateOf(false) }
    var model by remember { mutableStateOf(initialModel) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = if (configured) "Anahtar kayıtlı: $masked" else "Anahtar girilmemiş.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        OutlinedTextField(
            value = input,
            onValueChange = {
                input = it
                saved = false
            },
            label = { Text(fieldLabel) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = input.isNotBlank(),
                onClick = {
                    val (isConfigured, nextMasked) = onSaveKey(input)
                    input = ""
                    configured = isConfigured
                    masked = nextMasked
                    saved = true
                },
            ) {
                Text("Kaydet")
            }
            if (configured) {
                TextButton(
                    onClick = {
                        onClearKey()
                        configured = false
                        masked = ""
                        saved = false
                    },
                ) {
                    Text("Sil", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        if (saved) {
            Text(
                text = "Kaydedildi.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        // Liste kapalı değil: sağlayıcı yeni bir model çıkardığında adını elle yazabilsin.
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        ) {
            presetModels.forEach { option ->
                FilterChip(
                    selected = model == option,
                    onClick = {
                        model = option
                        onSaveModel(option)
                    },
                    label = { Text(option) },
                )
            }
        }

        OutlinedTextField(
            value = model,
            onValueChange = {
                model = it
                onSaveModel(it)
            },
            label = { Text("Model adı") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
