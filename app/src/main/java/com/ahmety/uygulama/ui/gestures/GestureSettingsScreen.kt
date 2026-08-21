package com.ahmety.uygulama.ui.gestures

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ahmety.uygulama.feature.gestures.GestureAction
import com.ahmety.uygulama.feature.gestures.GestureFeedback
import com.ahmety.uygulama.feature.gestures.GestureSettings

@Composable
fun GestureSettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val settings = remember { GestureSettings(context) }

    var serviceEnabled by remember { mutableStateOf(GestureSettings.isServiceEnabled(context)) }
    var enabled by remember { mutableStateOf(settings.enabled) }
    var showLeft by remember { mutableStateOf(settings.showLeft) }
    var showRight by remember { mutableStateOf(settings.showRight) }
    var widthDp by remember { mutableIntStateOf(settings.widthDp) }
    var heightDp by remember { mutableIntStateOf(settings.heightDp) }
    var offsetDp by remember { mutableIntStateOf(settings.verticalOffsetDp) }
    var opacity by remember { mutableIntStateOf(settings.opacityPercent) }
    var vibrate by remember { mutableStateOf(settings.vibrateEnabled) }
    var sound by remember { mutableStateOf(settings.soundEnabled) }
    var soundVolume by remember { mutableIntStateOf(settings.soundVolume) }
    var vibrateStrength by remember { mutableIntStateOf(settings.vibrateStrength) }
    var backAngle by remember { mutableIntStateOf(settings.backAngleDegrees) }
    var up by remember { mutableStateOf(settings.swipeUpAction) }
    var down by remember { mutableStateOf(settings.swipeDownAction) }
    var inward by remember { mutableStateOf(settings.swipeInAction) }
    var longPress by remember { mutableStateOf(settings.longPressAction) }
    var doubleTap by remember { mutableStateOf(settings.doubleTapAction) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                serviceEnabled = GestureSettings.isServiceEnabled(context)
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
        Text("Kenar hareketleri", style = MaterialTheme.typography.headlineMedium)

        ServiceCard(
            enabled = serviceEnabled,
            explanation = "\"Son uygulamalar\", \"bildirimler\" gibi komutları verebilmenin " +
                "tek yolu erişilebilirlik servisi. Farkımız: bu servis ekran içeriğini " +
                "okuma bayrağı kapalı — şifreni, bakiyeni göremez, yalnızca jesti alır.",
            onOpen = { context.startActivity(GestureSettings.accessibilitySettingsIntent()) },
        )

        Toggle("Şeridi göster", enabled) {
            enabled = it
            settings.enabled = it
        }

        Text("Hangi kenarlar", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = showLeft,
                onClick = {
                    showLeft = !showLeft
                    settings.showLeft = showLeft
                },
                label = { Text("Sol") },
            )
            FilterChip(
                selected = showRight,
                onClick = {
                    showRight = !showRight
                    settings.showRight = showRight
                },
                label = { Text("Sağ") },
            )
        }
        Text(
            text = "İkisini birden açabilirsin. Telefonu hareketlerle kullanıyorsan, " +
                "şeridin kapladığı alanda sistemin kendi \"kenardan çek = geri\" hareketinin " +
                "devre dışı kalması için gereken istek gönderiliyor; şeridin dışında sistem " +
                "normal çalışır. Bazı üretici arayüzlerinde bu istek yok sayılabilir — " +
                "çakışma sürerse şeridi biraz kısaltmayı veya yerini kaydırmayı dene.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text("Yön ayrımı", style = MaterialTheme.typography.labelLarge)
        Text(
            text = "Parmağın yatayla yaptığı açı bu çizginin altındaysa \"içeri\" " +
                "(geri), üstündeyse yukarı/aşağı sayılır. Oku sürükleyerek ayarla.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AnglePicker(
            angle = backAngle,
            onAngleChange = {
                backAngle = it
                settings.backAngleDegrees = it
            },
        )

        Text("Jestlere atanan eylemler", style = MaterialTheme.typography.labelLarge)
        ActionPicker("Yukarı kaydır", up, GestureSettings.GESTURE_UP, settings) {
            up = it; settings.swipeUpAction = it
        }
        ActionPicker("Aşağı kaydır", down, GestureSettings.GESTURE_DOWN, settings) {
            down = it; settings.swipeDownAction = it
        }
        ActionPicker("İçeri kaydır", inward, GestureSettings.GESTURE_IN, settings) {
            inward = it; settings.swipeInAction = it
        }
        ActionPicker("Uzun bas", longPress, GestureSettings.GESTURE_LONG, settings) {
            longPress = it; settings.longPressAction = it
        }
        ActionPicker("Çift dokun", doubleTap, GestureSettings.GESTURE_DOUBLE, settings) {
            doubleTap = it; settings.doubleTapAction = it
        }

        Text("Boyut ve konum", style = MaterialTheme.typography.labelLarge)
        Stepper("Kalınlık", widthDp, 2..16, suffix = "dp") { widthDp = it; settings.widthDp = it }
        Stepper("Uzunluk", heightDp, 60..500, step = 20, suffix = "dp") {
            heightDp = it; settings.heightDp = it
        }
        if (heightDp > 200) {
            Text(
                text = "Not: Android, kenar başına en fazla 200dp'lik alanda sistem geri " +
                    "hareketini bize bırakıyor. Şerit daha uzunsa 200dp'yi aşan kısımda " +
                    "telefonun kendi geri hareketi devreye girebilir.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Stepper("Dikey konum", offsetDp, -300..300, step = 20, suffix = "dp") {
            offsetDp = it; settings.verticalOffsetDp = it
        }
        Stepper("Saydamlık", opacity, 0..100, step = 5, suffix = "%") {
            opacity = it; settings.opacityPercent = it
        }
        Text(
            text = "Negatif yukarı, pozitif aşağı taşır. Saydamlık 0'da şerit görünmez " +
                "olur ama dokunmayı yine alır. Değişiklikler anında uygulanır.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Toggle("Titreşim", vibrate) {
            vibrate = it
            settings.vibrateEnabled = it
            if (it) GestureFeedback.vibrate(context, settings)
        }
        if (vibrate) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Hafif", "Orta", "Güçlü").forEachIndexed { index, label ->
                    FilterChip(
                        selected = vibrateStrength == index,
                        onClick = {
                            vibrateStrength = index
                            settings.vibrateStrength = index
                            GestureFeedback.vibrate(context, settings)
                        },
                        label = { Text(label) },
                    )
                }
            }
        }

        Toggle("Bip sesi", sound) {
            sound = it
            settings.soundEnabled = it
            if (it) GestureFeedback.beep(settings)
        }
        if (sound) {
            Stepper("Ses yüksekliği", soundVolume, 5..100, step = 5, suffix = "%") {
                soundVolume = it
                settings.soundVolume = it
                // Değişikliği duyabilmek için hemen çal.
                GestureFeedback.beep(settings)
            }
        }
        Text(
            text = "Jest başarıyla algılanınca kısa bir \"bip\" çalar. Yükseklik " +
                "ayarı sistem ses seviyesinden bağımsızdır.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Çeyrek daire üzerinde sürüklenen ok: geri ile dikey kaydırmayı ayıran açı.
 * Sayı yerine açıyı göstermek, hangi parmak hareketinin neye karşılık geldiğini
 * doğrudan anlatıyor.
 */
@Composable
private fun AnglePicker(
    angle: Int,
    onAngleChange: (Int) -> Unit,
) {
    val outline = MaterialTheme.colorScheme.outlineVariant
    val accent = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurfaceVariant

    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(
            modifier = Modifier
                .size(150.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        // Sol üst köşe merkez: sağa doğru yatay, aşağı doğru dikey.
                        val dx = change.position.x.coerceAtLeast(1f)
                        val dy = change.position.y.coerceAtLeast(0f)
                        val degrees = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble()))
                        onAngleChange(degrees.toInt().coerceIn(15, 75))
                    }
                },
        ) {
            val radius = size.minDimension
            // Çeyrek daire: yatay (içeri) ve dikey (aşağı) kenarlar.
            drawLine(outline, Offset.Zero, Offset(radius, 0f), strokeWidth = 3f)
            drawLine(outline, Offset.Zero, Offset(0f, radius), strokeWidth = 3f)
            drawArc(
                color = outline,
                startAngle = 0f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(-radius, -radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = 3f),
            )

            // Ayrım çizgisi ve ok.
            val radians = Math.toRadians(angle.toDouble())
            val endX = (radius * kotlin.math.cos(radians)).toFloat()
            val endY = (radius * kotlin.math.sin(radians)).toFloat()
            drawLine(accent, Offset.Zero, Offset(endX, endY), strokeWidth = 8f)
            drawCircle(accent, radius = 14f, center = Offset(endX, endY))
        }

        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text("$angle°", style = MaterialTheme.typography.headlineSmall)
            Text(
                text = "altı: içeri",
                style = MaterialTheme.typography.labelSmall,
                color = onSurface,
            )
            Text(
                text = "üstü: yukarı/aşağı",
                style = MaterialTheme.typography.labelSmall,
                color = onSurface,
            )
        }
    }
}

@Composable
internal fun ServiceCard(
    enabled: Boolean,
    explanation: String,
    onOpen: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (enabled) "Servis açık." else "Servis kapalı.",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = explanation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onOpen) {
                Text(if (enabled) "Erişilebilirlik ayarları" else "Servisi aç")
            }
        }
    }
}

/**
 * Bir jestin eylemi. "Uygulama aç" seçilirse hemen altında hangi uygulamanın
 * açılacağı da seçiliyor; her jest kendi uygulamasını taşıyor.
 */
@Composable
private fun ActionPicker(
    label: String,
    value: GestureAction,
    gestureKey: String,
    settings: GestureSettings,
    onChange: (GestureAction) -> Unit,
) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    var packageName by remember { mutableStateOf(settings.appFor(gestureKey)) }

    val appLabel = remember(packageName) {
        if (packageName.isBlank()) {
            null
        } else {
            runCatching {
                val manager = context.packageManager
                manager.getApplicationLabel(manager.getApplicationInfo(packageName, 0)).toString()
            }.getOrNull()
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Box {
                OutlinedButton(onClick = { open = true }) { Text(value.label) }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    GestureAction.entries.forEach { action ->
                        DropdownMenuItem(
                            text = { Text(action.label) },
                            onClick = {
                                open = false
                                onChange(action)
                            },
                        )
                    }
                }
            }
        }

        if (value == GestureAction.OPEN_APP) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = { picking = true }) {
                    Text(appLabel ?: "Uygulama seç")
                }
            }
        }
    }

    if (picking) {
        // Liste yalnızca kutu açılınca hazırlanıyor: paket sorgusu birkaç yüz
        // uygulamada yavaş, ayar ekranını her açılışta bekletmesin.
        val apps = remember { loadLaunchableApps(context) }
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text("Uygulama seç") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(apps) { app ->
                        Text(
                            text = app.label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    settings.setAppFor(gestureKey, app.packageName)
                                    packageName = app.packageName
                                    picking = false
                                }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { picking = false }) { Text("Vazgeç") }
            },
        )
    }
}

@Composable
internal fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
internal fun Stepper(
    label: String,
    value: Int,
    range: IntRange,
    step: Int = 1,
    suffix: String,
    onChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        TextButton(enabled = value > range.first, onClick = { onChange((value - step).coerceIn(range)) }) {
            Text("−")
        }
        Text("$value $suffix", style = MaterialTheme.typography.bodyLarge)
        TextButton(enabled = value < range.last, onClick = { onChange((value + step).coerceIn(range)) }) {
            Text("+")
        }
    }
}
