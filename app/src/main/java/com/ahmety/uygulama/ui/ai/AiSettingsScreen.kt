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
import androidx.compose.material3.OutlinedButton
import com.ahmety.uygulama.core.designsystem.MerkezTopBar
import com.ahmety.uygulama.core.designsystem.longPressHint
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.ahmety.uygulama.core.ai.AgentProvider
import com.ahmety.uygulama.core.ai.AiSettings
import com.ahmety.uygulama.core.ai.AgentUsageRow
import com.ahmety.uygulama.feature.gestures.ScreenAgentStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    fun driveBridgeUrl(): String = settings.driveBridgeUrl
    fun driveBridgeConfigured(): Boolean = settings.driveBridgeConfigured
    fun maskedDriveToken(): String = settings.maskedDriveToken()
    fun saveDriveBridge(url: String, token: String) {
        if (url.isNotBlank()) settings.driveBridgeUrl = url
        if (token.isNotBlank()) settings.driveBridgeToken = token
    }
    fun clearDriveBridge() = settings.clearDriveBridge()

    fun personalGmailBridgeUrl(): String = settings.personalGmailBridgeUrl
    fun personalGmailBridgeConfigured(): Boolean = settings.personalGmailBridgeConfigured
    fun hasOwnPersonalGmailToken(): Boolean = settings.hasOwnPersonalGmailToken
    fun maskedPersonalGmailToken(): String = settings.maskedPersonalGmailToken()
    fun savePersonalGmailBridge(url: String, token: String) {
        if (url.isNotBlank()) settings.personalGmailBridgeUrl = url
        if (token.isNotBlank()) settings.personalGmailBridgeToken = token
    }
    fun clearPersonalGmailBridge() = settings.clearPersonalGmailBridge()
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

        // ---- OpenAI (sözlük + ekrana sor + sesli yazma) ----
        ProviderKeySection(
            title = "OpenAI",
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
        Text(
            text = "Ekran ajanı",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.longPressHint(
                title = "Ekran ajanı",
                hint = "Görev verdiğinde ekranı okuyup senin yerine dokunarak ve yazarak uygular. Banka uygulamalarında güvenlik için kendiliğinden durur.",
            ),
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .longPressHint(
                    title = "Opus planlasın",
                    hint = "Her turun başında Opus ekrana bir kez bakıp adım planı yazar; seçili model bu plana göre uygular.",
                ),
        ) {
            Text("Opus planlasın", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(checked = planner, onCheckedChange = {
                planner = it
                viewModel.saveClaudePlanner(it)
            })
        }

        var escalate by remember { mutableStateOf(viewModel.claudeEscalate()) }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .longPressHint(
                    title = "Takılınca güçlü model devralsın",
                    hint = "Haiku takılırsa Sonnet (en az 4 adım), o da takılırsa Opus (en az 3 adım) devralır; sonra birer basamak iner.",
                ),
        ) {
            Text("Takılınca güçlü model devralsın", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(checked = escalate, onCheckedChange = {
                escalate = it
                viewModel.saveClaudeEscalate(it)
            })
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        DriveBridgeSection(viewModel)

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        PersonalGmailBridgeSection(viewModel)

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        AgentMemorySection()

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        AgentScheduleSection()

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        AgentBankSection()

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        AgentUsageSection(viewModel)
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
        Text(
            text = "Ekran ajanı günlüğü",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.longPressHint(
                title = "Ekran ajanı günlüğü",
                hint = "Son turların model, uygulama, süre, maliyet ve hareket kayıtları. Ekranın tam yazısı kaydedilmez.",
            ),
        )
        if (log.isBlank()) {
            Text(
                "Kayıt yok.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
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
        Text(
            text = "Ajanın çalışabileceği bankalar",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.longPressHint(
                title = "Ajanın çalışabileceği bankalar",
                hint = "Açtığın bankada ajan bilgi okur ve form doldurur ama Onayla/Gönder/Öde gibi para çıkaran düğmelere asla basmaz. IBAN ve kart numarası maskelenir.",
            ),
        )
        if (banks.isEmpty()) {
            Text(
                "Banka uygulaması yok.",
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
        Text(
            text = "Zamanlanmış görevler",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.longPressHint(
                title = "Zamanlanmış görevler",
                hint = "Ajana saatiyle görev verdiğinde (\"yarın 9'da…\", \"her sabah 8.30'da…\") burada listelenir ve saatinde kendiliğinden çalışır.",
            ),
        )
        if (jobs.isEmpty()) {
            Text(
                "Zamanlayıcı yok.",
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
            }) { Text("Tam saat iznini aç") }
        }
    }
}

/** Google Apps Script köprüsü: arka planda Drive arama, okuma, log ve hafıza eşitleme. */
@Composable
private fun DriveBridgeSection(viewModel: AiSettingsViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var urlInput by remember { mutableStateOf(viewModel.driveBridgeUrl()) }
    var tokenInput by remember { mutableStateOf("") }
    var configured by remember { mutableStateOf(viewModel.driveBridgeConfigured()) }
    var maskedToken by remember { mutableStateOf(viewModel.maskedDriveToken()) }
    var status by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "İş köprüsü (yesiltemizlik@gmail.com)",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.longPressHint(
                title = "İş köprüsü",
                hint = "Drive, Gmail (okuma + taslak), Takvim, günlük ve hafıza eşitleme için Apps Script köprüsü.",
            ),
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = if (configured) {
                        "Köprü kayıtlı · Jeton: $maskedToken"
                    } else {
                        "Kurulmamış."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        OutlinedTextField(
            value = urlInput,
            onValueChange = {
                urlInput = it
                status = null
            },
            label = { Text("Web uygulaması URL'si") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = tokenInput,
            onValueChange = {
                tokenInput = it
                status = null
            },
            label = { Text("MERKEZ_TOKEN") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        ) {
            Button(
                enabled = urlInput.isNotBlank() && (tokenInput.isNotBlank() || configured),
                onClick = {
                    viewModel.saveDriveBridge(urlInput, tokenInput)
                    tokenInput = ""
                    configured = viewModel.driveBridgeConfigured()
                    maskedToken = viewModel.maskedDriveToken()
                    status = "Kaydedildi."
                },
            ) {
                Text("Kaydet")
            }
            if (configured) {
                OutlinedButton(
                    enabled = !testing,
                    onClick = {
                        testing = true
                        status = "Sınanıyor…"
                        scope.launch {
                            val res = withContext(Dispatchers.IO) {
                                ScreenAgentStorage.pingBridge(context)
                            }
                            testing = false
                            status = res.getOrElse { "Hata: ${it.message}" }
                        }
                    },
                ) {
                    Text(if (testing) "Sınanıyor…" else "Bağlantıyı dene")
                }
                TextButton(
                    onClick = {
                        viewModel.clearDriveBridge()
                        urlInput = ""
                        tokenInput = ""
                        configured = false
                        maskedToken = ""
                        status = null
                    },
                ) {
                    Text("Sil", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        status?.let { msg ->
            Text(
                text = msg,
                style = MaterialTheme.typography.bodySmall,
                color = if (msg.startsWith("Hata")) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
        }
    }
}

/** Opsiyonel ikinci hesap: ahmetyuksel5@gmail.com köprüsü. */
@Composable
private fun PersonalGmailBridgeSection(viewModel: AiSettingsViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var urlInput by remember { mutableStateOf(viewModel.personalGmailBridgeUrl()) }
    var tokenInput by remember { mutableStateOf("") }
    var configured by remember { mutableStateOf(viewModel.personalGmailBridgeConfigured()) }
    var ownToken by remember { mutableStateOf(viewModel.hasOwnPersonalGmailToken()) }
    var maskedToken by remember { mutableStateOf(viewModel.maskedPersonalGmailToken()) }
    var status by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Kişisel köprü (ahmetyuksel5@gmail.com)",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.longPressHint(
                title = "Kişisel köprü",
                hint = "Kişisel Gmail (okuma + taslak), Drive ve Takvim için ikinci Apps Script köprüsü. Jeton boş bırakılırsa üstteki ana MERKEZ_TOKEN kullanılır.",
            ),
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = when {
                        configured && ownToken -> "Köprü kayıtlı · Özel jeton: $maskedToken"
                        configured -> "Köprü kayıtlı · Ana jeton"
                        else -> "Kurulmamış."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        OutlinedTextField(
            value = urlInput,
            onValueChange = {
                urlInput = it
                status = null
            },
            label = { Text("2. Web uygulaması URL'si") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = tokenInput,
            onValueChange = {
                tokenInput = it
                status = null
            },
            label = { Text("2. Jeton (opsiyonel)") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        ) {
            Button(
                enabled = urlInput.isNotBlank() &&
                    (tokenInput.isNotBlank() || viewModel.driveBridgeConfigured() || configured),
                onClick = {
                    viewModel.savePersonalGmailBridge(urlInput, tokenInput)
                    tokenInput = ""
                    configured = viewModel.personalGmailBridgeConfigured()
                    ownToken = viewModel.hasOwnPersonalGmailToken()
                    maskedToken = viewModel.maskedPersonalGmailToken()
                    status = "Kaydedildi."
                },
            ) {
                Text("Kaydet")
            }
            if (configured) {
                OutlinedButton(
                    enabled = !testing,
                    onClick = {
                        testing = true
                        status = "Sınanıyor…"
                        scope.launch {
                            val res = withContext(Dispatchers.IO) {
                                ScreenAgentStorage.pingPersonalGmailBridge(context)
                            }
                            testing = false
                            status = res.getOrElse { "Hata: ${it.message}" }
                        }
                    },
                ) {
                    Text(if (testing) "Sınanıyor…" else "Bağlantıyı dene")
                }
                TextButton(
                    onClick = {
                        viewModel.clearPersonalGmailBridge()
                        urlInput = ""
                        tokenInput = ""
                        configured = false
                        ownToken = false
                        maskedToken = ""
                        status = null
                    },
                ) {
                    Text("Sil", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        status?.let { msg ->
            Text(
                text = msg,
                style = MaterialTheme.typography.bodySmall,
                color = if (msg.startsWith("Hata")) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
        }
    }
}

/** Ajanın öğrendiği kısayollar ve engel aşma kuralları (Merkez_Ajan_Hafiza.md). */
@Composable
private fun AgentMemorySection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var memory by remember { mutableStateOf(ScreenAgentStorage.readLocalMemory(context)) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Ajan hafızası",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.longPressHint(
                title = "Ajan hafızası",
                hint = "Ekran ajanı her görev başında bu notlara bakar; Drive'daki Merkez_Ajan_Hafiza.md ile eşitlenir.",
            ),
        )

        OutlinedTextField(
            value = memory,
            onValueChange = {
                memory = it
                status = null
            },
            label = { Text("Merkez_Ajan_Hafiza.md") },
            minLines = 5,
            maxLines = 12,
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        ) {
            Button(
                enabled = !busy,
                onClick = {
                    busy = true
                    status = "Kaydediliyor…"
                    scope.launch {
                        val res = withContext(Dispatchers.IO) {
                            ScreenAgentStorage.saveMemory(context, memory)
                        }
                        busy = false
                        status = res.getOrElse { "Cihaza kaydedildi (Drive hatası: ${it.message})" }
                    }
                },
            ) {
                Text("Hafızayı kaydet")
            }
            if (ScreenAgentStorage.isBridgeConfigured(context)) {
                OutlinedButton(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        status = "Drive'dan çekiliyor…"
                        scope.launch {
                            val res = withContext(Dispatchers.IO) {
                                ScreenAgentStorage.syncMemoryFromDrive(context, force = true)
                            }
                            busy = false
                            res.onSuccess {
                                memory = it
                                status = "Drive'dan güncellendi."
                            }.onFailure {
                                status = "Hata: ${it.message}"
                            }
                        }
                    },
                ) {
                    Text("Drive'dan yenile")
                }
            }
        }

        status?.let { msg ->
            Text(
                text = msg,
                style = MaterialTheme.typography.bodySmall,
                color = if (msg.startsWith("Hata")) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
        }
    }
}
