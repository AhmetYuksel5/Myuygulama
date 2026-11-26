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
        Text(
            text = "Ekranın dibindeki çubuğa parmağını basıp gezdir; ekranda bir imleç " +
                "trackpad gibi dolaşır, parmağını kaldırınca oraya dokunur. Sol üst gibi " +
                "tek elle ulaşamadığın yerlere basmak için. Çubuğu taşımak için uzun " +
                "basıp sürükle; hızlıca iki kez dokununca kısayol çubuğu açılır. " +
                "Ekranı yana çevirince ve kilit ekranında çubuk " +
                "kayboluyor; video izlerken ya da telefonu cebinden çıkarınca " +
                "görüntünün üstünde durmasın diye.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        ServiceCard(
            enabled = serviceEnabled,
            explanation = "İmlecin durduğu yere gerçek dokunma gönderebilmek için jest " +
                "gönderme yetkisi ister. Ekran içeriğini yine okumaz.",
            onOpen = { context.startActivity(QuickCursorSettings.accessibilitySettingsIntent()) },
        )

        GuardSection()

        Toggle("Tek elle imleci göster", enabled) {
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

        Text("Çubuğun yeri", style = MaterialTheme.typography.labelLarge)
        Stepper("Alttan yukarı", offset, 0..1200, step = 10, suffix = "dp") {
            offset = it; settings.bottomOffsetDp = it
        }
        Stepper("Yatay kayma", centerOffset, -300..300, step = 10, suffix = "dp") {
            centerOffset = it; settings.centerOffsetDp = it
        }
        Text(
            text = "Alttan yukarı 0 = ekranın tam dibinde. Yatay kayma 0 = ortada, " +
                "eksi değer sola, artı değer sağa. Çubuğa uzun basıp sürükleyerek de " +
                "taşıyabilirsin; bıraktığın yer kaydedilir.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

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
        Text(
            text = "İmleç gezerken ardında bu renkte bir iz bırakıyor. " +
                "Renkler mat: parlak bir iz altındaki yazıyı okunmaz yapıyor.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text("Üst köşeler", style = MaterialTheme.typography.labelLarge)
        Toggle("Tepeye götürünce perde açılsın", edgeActions) {
            edgeActions = it
            settings.edgeActions = it
        }
        Text(
            text = "İmleci ekranın tepesinden biraz dışarı it: sol yarıda bildirimler, " +
                "sağ yarıda hızlı ayarlar açılır. Bölgeye girince tepede ince bir çizgi " +
                "belirir ve telefon kısa bir titrer.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (edgeActions) {
            Toggle("Parmağı bırakınca açılsın", edgeOnRelease) {
                edgeOnRelease = it
                settings.edgeOnRelease = it
            }
            Text(
                text = if (edgeOnRelease) {
                    "Bölgede parmağını bırakınca açılır; vazgeçersen aşağı çekip bırak."
                } else {
                    "Bölgeye değer değmez açılır, bırakmayı beklemez."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        ShortcutsSection()
        Stepper("Kaç uygulama arasında dolaşılsın", carousel, 2..4, step = 1, suffix = "") {
            carousel = it
            settings.carouselCount = it
        }
        Text(
            text = "Önceki uygulama her basışta son bu kadar uygulama arasında " +
                "sırayla döner; ortadaki şeritte görünürler, birine dokunursan oraya geçer. " +
                "Kapattığın bir uygulamayı listeden çıkarmak için şeritte simgesine uzun bas.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Toggle("Bir öncekine sistemin hızlı geçişiyle dön", quickSwitch) {
            quickSwitch = it
            settings.systemQuickSwitch = it
        }
        Text(
            text = "Deneysel. Açıkken Drive'daki PDF gibi uygulama içi pencereler " +
                "de bıraktığın yerden gelir; ama bazı telefonlarda arada siyah " +
                "ekran kalıyor. Öyle olursa kapalı tut.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text("Sesli yazma", style = MaterialTheme.typography.labelLarge)
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
        Text(
            text = "Klavye açılınca sağ üst köşesinde bir mikrofon çıkıyor. Dokun, konuş, " +
                "tekrar dokun: söylediğin OpenAI ile yazıya çevrilip kutuya ekleniyor. " +
                "Sürükleyerek yerini değiştirebilirsin; kayıttayken uzun basmak iptal ediyor. " +
                "Mikrofon izni ve Yapay zekâ ayarlarında OpenAI anahtarı gerekiyor.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text("İnce ayarlar", style = MaterialTheme.typography.labelLarge)
        // Birer birer: kenar şeridinde beşer beşer gitmek yetiyordu ama
        // burada çubuk küçük, iki komşu değer arasındaki fark önemli.
        // Uzak bir değere gitmek için düğmeyi basılı tutmak var.
        Stepper("Saydamlık", opacity, 0..100, step = 1, suffix = "%") {
            opacity = it
            settings.opacityPercent = it
        }
        Text(
            text = "Saydamlık, parmağını çekince çubuğun göründüğü hâl; " +
                "dokunduğun sürece biraz daha belirgin oluyor. 0'da çubuk hiç " +
                "görünmez ama yeri değişmez, oraya basınca yine çalışır. " +
                "Düğmeyi basılı tutarsan hızlanarak ilerler.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Stepper("Hassasiyet", sensitivityTimes10, 10..40, step = 2, suffix = "×10") {
            sensitivityTimes10 = it
            settings.sensitivity = it / 10f
        }
        Text(
            text = "Hassasiyet, parmak hareketinin kaç katı imleç hareketi olduğu. " +
                "Yüksek değer küçük hareketle ekranın öbür ucuna ulaştırır.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
    Toggle("Uyar ya da geri aç", guarded) {
        guarded = it
        AccessibilityGuard.setGuarded(context, it)
    }
    Text(
        text = if (canRestore) {
            "Geri açma izni verilmiş: servis kapanırsa hemen kendiliğinden açılıyor " +
                "(bugüne kadar ${AccessibilityGuard.restoredCount(context)} kez). Kasıtlı " +
                "kapatmak istersen önce yukarıdaki anahtarı kapat."
        } else {
            "Servis kapanırsa \"Merkez erişilebilirlik kapandı\" bildirimi çıkar; dokununca " +
                "ayar açılır. Bunun için bildirim süzgecinin izni açık olmalı.\n\n" +
                "Kendiliğinden geri açılması için bilgisayardan bir kez izin ver: telefonda " +
                "Geliştirici seçenekleri → USB hata ayıklama açık, telefonu bilgisayara " +
                "bağla ve şu komutu çalıştır:"
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (canRestore) {
        Text("Kablosuz hata ayıklama (ADB)", style = MaterialTheme.typography.labelLarge)
        Toggle("ADB yetki zaman aşımını kapat", adbNoTimeout) { next ->
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
        Text(
            text = "adb_allowed_connection_time = 0 yazar; bilgisayarla eşleştirilen " +
                "kablosuz hata ayıklama yetkisi 7 gün sonunda kendiliğinden iptal edilmez.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
        Text(
            "Komuta dokununca kopyalanır.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (exits.isNotBlank()) {
        Text("Kapanma geçmişi", style = MaterialTheme.typography.labelLarge)
        Text(
            "Android'in kaydı: Merkez'in son kapanışları, en yenisi üstte.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
