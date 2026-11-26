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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.ahmety.uygulama.core.ai.AgentProvider
import com.ahmety.uygulama.core.ai.AiSettings
import com.ahmety.uygulama.core.ai.AgentUsageRow
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

    fun claudePlanner(): Boolean = settings.claudePlanner
    fun saveClaudePlanner(value: Boolean) { settings.claudePlanner = value }
    fun claudeEscalate(): Boolean = settings.claudeEscalate
    fun saveClaudeEscalate(value: Boolean) { settings.claudeEscalate = value }
    fun agentUsage(): List<AgentUsageRow> = settings.agentUsage()
    fun agentUsageSince(): Long = settings.agentUsageSince()
    fun resetAgentUsage() = settings.resetAgentUsage()
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

        var planner by remember { mutableStateOf(viewModel.claudePlanner()) }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("Opus planlasın", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Her turun başında Opus ekrana bir kez bakıp adım planı yazar; seçili model " +
                        "(Haiku) bu plana göre dokunur. Tur başına bir Opus çağrısı (~0,03 $).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = planner, onCheckedChange = {
                planner = it
                viewModel.saveClaudePlanner(it)
            })
        }

        var escalate by remember { mutableStateOf(viewModel.claudeEscalate()) }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("Takılınca güçlü model devralsın", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Haiku takılırsa (ekran değişmez, aynı hareketi tekrarlar, anlamsız yanıt verir) " +
                        "Sonnet en az 4 adım devralır; o da takılırsa Opus en az 3 adım. Sonra birer " +
                        "basamak iner. Turda Sonnet en çok 10 adım; Opus için sınır yok.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = escalate, onCheckedChange = {
                escalate = it
                viewModel.saveClaudeEscalate(it)
            })
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        AgentScheduleSection()

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        AgentBankSection()

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        AgentUsageSection(viewModel)

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

/** Ekran ajanının harcama sayacı: model başına adım, token ve yaklaşık dolar. */
@Composable
private fun AgentUsageSection(viewModel: AiSettingsViewModel) {
    var rows by remember { mutableStateOf(viewModel.agentUsage()) }
    val since = viewModel.agentUsageSince()
    val tr = java.util.Locale.forLanguageTag("tr")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Ekran ajanı harcaması", style = MaterialTheme.typography.titleSmall)
        if (rows.isEmpty()) {
            Text(
                "Henüz harcama yok.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        if (since > 0) {
            Text(
                java.text.SimpleDateFormat("d MMMM", tr).format(java.util.Date(since)) + " tarihinden beri",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                Text(row.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(
                    "${row.steps} adım · ${(row.inputTokens + row.outputTokens) / 1000} bin token" +
                        if (row.usd > 0) " · ~" + String.format(tr, "%.2f", row.usd) + " $" else "",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        val total = rows.sumOf { it.usd }
        if (total > 0) {
            Text(
                "Toplam ~" + String.format(tr, "%.2f", total) + " $ (liste fiyatıyla yaklaşık)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = {
            viewModel.resetAgentUsage()
            rows = emptyList()
        }) { Text("Sayacı sıfırla") }
    }
    AgentLogSection()
}

/** Ajanın son turlarının günlüğü: kopyalayıp sohbete yapıştırmak için. */
@Composable
private fun AgentLogSection() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var log by remember { mutableStateOf(com.ahmety.uygulama.feature.gestures.AgentLog.saved(context)) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Ekran ajanı günlüğü", style = MaterialTheme.typography.titleSmall)
        Text(
            if (log.isBlank()) {
                "Henüz kayıt yok. Ajan her turu adım adım buraya yazar."
            } else {
                "Son turlar adım adım: model, uygulama, süre, maliyet, düşünce ve hareket. " +
                    "Ekranın tam yazısı kaydedilmez."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (log.isNotBlank()) {
            Row {
                TextButton(onClick = {
                    val cm = context.getSystemService(android.content.ClipboardManager::class.java)
                    cm?.setPrimaryClip(android.content.ClipData.newPlainText("Ajan günlüğü", log))
                    android.widget.Toast.makeText(context, "Günlük kopyalandı", android.widget.Toast.LENGTH_SHORT).show()
                }) { Text("Günlüğü kopyala") }
                TextButton(onClick = {
                    com.ahmety.uygulama.feature.gestures.AgentLog.clear(context)
                    log = ""
                }) { Text("Sil") }
            }
        }
    }
}

/**
 * Ekran ajanının çalışabileceği bankalar. Varsayılan hepsi kapalı; açılan
 * bankada ajan okur, gezinir, form doldurur ama para çıkaran düğmeye basmaz.
 */
@Composable
private fun AgentBankSection() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val banks = remember { com.ahmety.uygulama.feature.gestures.BankAccess.installedBanks(context) }
    var allowed by remember { mutableStateOf(com.ahmety.uygulama.feature.gestures.BankAccess.allowed(context)) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Ajanın çalışabileceği bankalar", style = MaterialTheme.typography.titleSmall)
        Text(
            "Açtığın bankada ajan bakiye ve hareketleri okur, gezinir, form doldurur. Onayla, " +
                "Gönder, Öde gibi para çıkaran düğmelere asla kendisi basmaz; orada durur, son " +
                "dokunuşu sen yaparsın. Bankada Claude seçiliyse her adımı Opus yapar. Ekrandaki " +
                "yazılar (IBAN ve kart numarası maskelenmiş olarak) yapay zekâ sağlayıcısına gider.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (banks.isEmpty()) {
            Text(
                "Telefonda banka uygulaması bulunamadı.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        banks.forEach { (pkg, label) ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(
                    checked = pkg in allowed,
                    onCheckedChange = { on ->
                        com.ahmety.uygulama.feature.gestures.BankAccess.setAllowed(context, pkg, on)
                        allowed = com.ahmety.uygulama.feature.gestures.BankAccess.allowed(context)
                    },
                )
            }
        }
    }
}

/**
 * Ekran ajanının zamanlanmış görevleri. Kurmak için ajana "yarın 9'da şunu
 * yap" demek yetiyor; burada yalnız görülüp siliniyorlar.
 */
@Composable
private fun AgentScheduleSection() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var jobs by remember { mutableStateOf(com.ahmety.uygulama.feature.gestures.AgentScheduler.list(context)) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Zamanlanmış görevler", style = MaterialTheme.typography.titleSmall)
        Text(
            "Kurmak için ajana saatiyle söylemen yeter: \"yarın 9'da …\", \"her sabah 8.30'da …\", " +
                "\"hafta içi 18:00'de …\". Saat gelince ekran yanar, kaydırma kilidi kalkar ve görev başlar; " +
                "sonucu bildirimde de görürsün.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (jobs.isEmpty()) {
            Text(
                "Kurulu zamanlayıcı yok.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        jobs.forEach { job ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(job.task, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        com.ahmety.uygulama.feature.gestures.AgentScheduler.describe(job),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = {
                    com.ahmety.uygulama.feature.gestures.AgentScheduler.remove(context, job.id)
                    jobs = com.ahmety.uygulama.feature.gestures.AgentScheduler.list(context)
                }) { Text("Sil") }
            }
        }
        if (!com.ahmety.uygulama.feature.gestures.AgentScheduler.exactAllowed(context)) {
            TextButton(onClick = {
                runCatching {
                    context.startActivity(
                        android.content.Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                            .setData(android.net.Uri.parse("package:" + context.packageName)),
                    )
                }
            }) { Text("Tam saat iznini aç (yoksa birkaç dakika sapabilir)") }
        }
    }
}
