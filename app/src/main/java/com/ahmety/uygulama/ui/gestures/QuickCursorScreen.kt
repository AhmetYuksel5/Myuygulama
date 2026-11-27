package com.ahmety.uygulama.ui.gestures

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import com.ahmety.uygulama.core.designsystem.MerkezTopBar
import com.ahmety.uygulama.core.designsystem.longPressHint
import com.ahmety.uygulama.feature.gestures.AccessibilityGuard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ahmety.uygulama.feature.gestures.QuickCursorSettings
import com.ahmety.uygulama.feature.gestures.CursorMarker
import com.ahmety.uygulama.feature.gestures.VoiceTyping
import com.ahmety.uygulama.feature.gestures.CursorMarkerPreview
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.shape.RoundedCornerShape

@Composable
fun QuickCursorScreen(onBack: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val settings = remember { QuickCursorSettings(context) }

    var serviceEnabled by remember { mutableStateOf(QuickCursorSettings.isServiceEnabled(context)) }
    var enabled by remember { mutableStateOf(settings.enabled) }
    var barWidth by remember { mutableIntStateOf(settings.handleWidthDp) }
    var barHeight by remember { mutableIntStateOf(settings.handleHeightDp) }
    var opacity by remember { mutableIntStateOf(settings.opacityPercent) }
    var sensitivityTimes10 by remember { mutableIntStateOf((settings.sensitivity * 10).toInt()) }
    var offset by remember { mutableIntStateOf(settings.bottomOffsetDp) }
    var centerOffset by remember { mutableIntStateOf(settings.centerOffsetDp) }
    var izRengi by remember { mutableIntStateOf(settings.izRengi) }
    var marker by remember { mutableIntStateOf(settings.marker) }
    var edgeActions by remember { mutableStateOf(settings.edgeActions) }
    var edgeOnRelease by remember { mutableStateOf(settings.edgeOnRelease) }
    var carousel by remember { mutableIntStateOf(settings.carouselCount) }
    var quickSwitch by remember { mutableStateOf(settings.systemQuickSwitch) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                serviceEnabled = QuickCursorSettings.isServiceEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        MerkezTopBar(title = "Tek elle imleç", onBack = onBack)

        ServiceCard(
            enabled = serviceEnabled,
            explanation = "İmlecin durduğu yere gerçek dokunma gönderebilmek için jest " +
                "gönderme yetkisi ister. Ekran içeriğini yine okumaz.",
            onOpen = { context.startActivity(QuickCursorSettings.accessibilitySettingsIntent()) },
        )

        GuardSection()

        Toggle(
            label = "Tek elle imleci göster",
            checked = enabled,
            hint = "Ekranın dibindeki çubuğa basıp gezdirince sanal imleç çıkar; uzun basıp sürükleyerek çubuğu taşıyabilir, çift dokunarak kısayolları açabilirsin.",
        ) {
            enabled = it
            settings.enabled = it
        }

        Text("Çubuğun ölçüsü", style = MaterialTheme.typography.labelLarge)
        Stepper("En", barWidth, 60..320, step = 10, suffix = "dp") {
            barWidth = it; settings.handleWidthDp = it
        }
        Stepper("Kalınlık", barHeight, 8..56, step = 2, suffix = "dp") {
            barHeight = it; settings.handleHeightDp = it
        }

        Text(
            text = "Çubuğun yeri",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.longPressHint(
                title = "Çubuğun yeri",
                hint = "Alttan yukarı 0 = ekranın tam dibi. Yatay kayma 0 = ortada (eksi sola, artı sağa). Çubuğa uzun basıp sürükleyerek de taşınabilir.",
            ),
        )
        Stepper("Alttan yukarı", offset, 0..1200, step = 10, suffix = "dp") {
            offset = it; settings.bottomOffsetDp = it
        }
        Stepper("Yatay kayma", centerOffset, -300..300, step = 10, suffix = "dp") {
            centerOffset = it; settings.centerOffsetDp = it
        }

        Text("İmlecin biçimi", style = MaterialTheme.typography.labelLarge)
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 2.dp),
        ) {
            CursorMarker.NAMES.forEachIndexed { index, name ->
                val secili = index == marker
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { marker = index; settings.marker = index },
                ) {
                    // Önizleme koyu zeminde: imleç beyaz, açık zeminde
                    // seçilmiyor; gerçekte de çoğunlukla bir içeriğin üstünde.
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(Color(0xFF2B2F36), RoundedCornerShape(14.dp))
                            .then(
                                if (secili) {
                                    Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp))
                                } else {
                                    Modifier
                                },
                            ),
                    ) {
                        AndroidView(
                            factory = { CursorMarkerPreview(it) },
                            update = { it.style = index; it.accent = izRengi },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Text(
                        name,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (secili) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }

        Text("İzin rengi", style = MaterialTheme.typography.labelLarge)
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 2.dp),
        ) {
            QuickCursorSettings.IZ_RENKLERI.forEach { renk ->
                val secili = renk == izRengi
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(renk), CircleShape)
                        .then(
                            if (secili) {
                                Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                            } else {
                                Modifier
                            },
                        )
                        .clickable { izRengi = renk; settings.izRengi = renk },
                )
            }
        }

        Text("Üst köşeler", style = MaterialTheme.typography.labelLarge)
        Toggle(
            label = "Tepeye götürünce perde açılsın",
            checked = edgeActions,
            hint = "İmleci ekranın tepesinden dışarı it: sol yarıda bildirimler, sağ yarıda hızlı ayarlar açılır.",
        ) {
            edgeActions = it
            settings.edgeActions = it
        }
        if (edgeActions) {
            Toggle(
                label = "Parmağı bırakınca açılsın",
                checked = edgeOnRelease,
                hint = "Açıkken bölgede parmağı bırakınca açılır; kapalıyken bölgeye değer değmez açılır.",
            ) {
                edgeOnRelease = it
                settings.edgeOnRelease = it
            }
        }

        ShortcutsSection()
        Stepper(
            label = "Kaç uygulama arasında dolaşılsın",
            value = carousel,
            range = 2..4,
            step = 1,
            suffix = "",
            hint = "Önceki uygulama son bu kadar uygulama arasında döner; şeritte simgeye uzun basarak listeden çıkarabilirsin.",
        ) {
            carousel = it
            settings.carouselCount = it
        }
        Toggle(
            label = "Bir öncekine sistemin hızlı geçişiyle dön",
            checked = quickSwitch,
            hint = "Deneysel: açıkken uygulama içi pencereler bıraktığın yerden gelir; siyah ekran olursa kapalı tut.",
        ) {
            quickSwitch = it
            settings.systemQuickSwitch = it
        }

        Text(
            text = "Sesli yazma",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.longPressHint(
                title = "Sesli yazma",
                hint = "Klavye açılınca köşede mikrofon çıkar; dokun-konuş-dokun ile OpenAI üzerinden yazıya çevirir. Sürükleyerek taşıyabilir, kayıtta uzun basarak iptal edebilirsin.",
            ),
        )
        // Ayar servisin okuduğu dosyada; servis her klavye açılışında bakıyor.
        val voicePrefs = remember {
            context.getSharedPreferences(VoiceTyping.PREFS, android.content.Context.MODE_PRIVATE)
        }
        var voiceOn by remember { mutableStateOf(voicePrefs.getBoolean(VoiceTyping.KEY_ENABLED, true)) }
        Toggle("Klavyenin üstünde mikrofon", voiceOn) {
            voiceOn = it
            voicePrefs.edit().putBoolean(VoiceTyping.KEY_ENABLED, it).apply()
        }
        var voicePreview by remember { mutableStateOf(voicePrefs.getBoolean(VoiceTyping.KEY_PREVIEW, true)) }
        Toggle("Konuşurken yazıyı göster", voicePreview) {
            voicePreview = it
            voicePrefs.edit().putBoolean(VoiceTyping.KEY_PREVIEW, it).apply()
        }
        var voiceColor by remember { mutableIntStateOf(VoiceTyping.buttonColor(voicePrefs)) }
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 2.dp),
        ) {
            VoiceTyping.COLORS.forEach { renk ->
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(renk), CircleShape)
                        .then(
                            if (renk == voiceColor) {
                                Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                            } else {
                                Modifier
                            },
                        )
                        .clickable {
                            voiceColor = renk
                            voicePrefs.edit().putInt(VoiceTyping.KEY_COLOR, renk).apply()
                        },
                )
            }
        }

        Text("İnce ayarlar", style = MaterialTheme.typography.labelLarge)
        Stepper(
            label = "Saydamlık",
            value = opacity,
            range = 0..100,
            step = 1,
            suffix = "%",
            hint = "0'da çubuk görünmez ama yerinde çalışmaya devam eder. +/− düğmesine basılı tutunca hızlanır.",
        ) {
            opacity = it
            settings.opacityPercent = it
        }
        Stepper(
            label = "Hassasiyet",
            value = sensitivityTimes10,
            range = 10..40,
            step = 2,
            suffix = "×10",
            hint = "Parmak hareketinin kaç katı imleç hareketi olacağını belirler.",
        ) {
            sensitivityTimes10 = it
            settings.sensitivity = it / 10f
        }
    }
}

/**
 * Servisin kapanmasına karşı bekçinin ayarı, geri açma izni ve son çökme.
 * Çökme kaydı kopyalanıp gönderilebiliyor: sebebi kökten düzeltmek için.
 */
@Composable
private fun GuardSection() {
    val context = LocalContext.current
    var guarded by remember { mutableStateOf(AccessibilityGuard.isGuarded(context)) }
    var canRestore by remember { mutableStateOf(AccessibilityGuard.canRestore(context)) }
    var adbNoTimeout by remember { mutableStateOf(AccessibilityGuard.isAdbTimeoutDisabled(context)) }
    var wirelessAdbOn by remember { mutableStateOf(AccessibilityGuard.isWirelessAdbOn(context)) }
    var crash by remember { mutableStateOf(AccessibilityGuard.lastCrash(context)) }
    var exits by remember { mutableStateOf(AccessibilityGuard.exitHistory(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canRestore = AccessibilityGuard.canRestore(context)
                adbNoTimeout = AccessibilityGuard.isAdbTimeoutDisabled(context)
                wirelessAdbOn = AccessibilityGuard.isWirelessAdbOn(context)
                crash = AccessibilityGuard.lastCrash(context)
                exits = AccessibilityGuard.exitHistory(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    fun copy(label: String, text: String) {
        val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
        clipboard?.setPrimaryClip(android.content.ClipData.newPlainText(label, text))
        android.widget.Toast.makeText(context, "Kopyalandı", android.widget.Toast.LENGTH_SHORT).show()
    }

    Text("Kendiliğinden kapanırsa", style = MaterialTheme.typography.labelLarge)
    Toggle(
        label = "Uyar ya da geri aç",
        checked = guarded,
        hint = if (canRestore) {
            "Geri açma izni açık: servis kapanırsa kendiliğinden açılır (${AccessibilityGuard.restoredCount(context)} kez). Kasıtlı kapatmak için önce bu anahtarı kapat."
        } else {
            "Servis kapanırsa bildirim çıkarır. Kendiliğinden geri açması için bilgisayardan alttaki ADB komutunu bir kez çalıştır."
        },
    ) {
        guarded = it
        AccessibilityGuard.setGuarded(context, it)
    }
    if (canRestore) {
        Text("Kablosuz hata ayıklama (ADB)", style = MaterialTheme.typography.labelLarge)
        Toggle(
            label = "ADB yetki zaman aşımını kapat",
            checked = adbNoTimeout,
            hint = "adb_allowed_connection_time = 0 yazar; kablosuz ADB yetkisi 7 gün sonunda kendiliğinden düşmez.",
        ) { next ->
            if (AccessibilityGuard.setAdbTimeoutDisabled(context, next)) {
                adbNoTimeout = AccessibilityGuard.isAdbTimeoutDisabled(context)
            } else {
                android.widget.Toast.makeText(
                    context,
                    "Ayar yazılamadı; WRITE_SECURE_SETTINGS gerekli.",
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            }
        }
        Toggle("Kablosuz hata ayıklama", wirelessAdbOn) { next ->
            if (AccessibilityGuard.setWirelessAdb(context, next)) {
                wirelessAdbOn = AccessibilityGuard.isWirelessAdbOn(context)
            } else {
                android.widget.Toast.makeText(
                    context,
                    "Kablosuz hata ayıklama değiştirilemedi.",
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }
    if (!canRestore) {
        Text(
            text = AccessibilityGuard.ADB_COMMAND,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            ),
            modifier = Modifier.clickable { copy("Komut", AccessibilityGuard.ADB_COMMAND) },
        )
    }
    if (exits.isNotBlank()) {
        Text("Kapanma geçmişi", style = MaterialTheme.typography.labelLarge)
        Text(exits, style = MaterialTheme.typography.bodySmall)
        androidx.compose.material3.TextButton(onClick = {
            val report = exits + (crash?.let { "\n\nSon çökme:\n$it" } ?: "")
            copy("Kapanma geçmişi", report)
        }) { Text("Geçmişi kopyala") }
    }
    crash?.let { report ->
        Text("Son çökme", style = MaterialTheme.typography.labelLarge)
        Text(
            report.lineSequence().take(3).joinToString("\n"),
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.TextButton(onClick = { copy("Çökme kaydı", report) }) {
                Text("Kaydı kopyala")
            }
            androidx.compose.material3.TextButton(onClick = {
                AccessibilityGuard.clearCrash(context)
                crash = null
            }) { Text("Sil") }
        }
    }
}
