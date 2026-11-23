package com.ahmety.uygulama.ui.gestures

import android.app.role.RoleManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ahmety.uygulama.core.designsystem.MerkezTopBar
import com.ahmety.uygulama.feature.gestures.CallFilterStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Arama süzgecinin ayarları: izin, engellenecek başlangıçlar ve son
 * engellenen aramalar.
 */
@Composable
fun CallFilterScreen(onBack: (() -> Unit)? = null) {
    val context = LocalContext.current
    val store = remember { CallFilterStore(context) }
    var enabled by remember { mutableStateOf(CallFilterStore.isEnabled(context)) }
    var on by remember { mutableStateOf(store.isOn) }
    var prefixes by remember { mutableStateOf(store.prefixes()) }
    var recent by remember { mutableStateOf(store.recent()) }
    var count by remember { mutableIntStateOf(store.blockedCount) }
    var draft by remember { mutableStateOf("") }

    fun refresh() {
        enabled = CallFilterStore.isEnabled(context)
        recent = store.recent()
        count = store.blockedCount
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Android'in "arama süzme uygulaması" sorusu; cevap gelince durum tazeleniyor.
    val askRole = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refresh()
    }

    fun save(next: List<String>) {
        prefixes = next
        store.savePrefixes(next)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        MerkezTopBar(title = "Arama süzgeci", onBack = onBack)
        Text(
            text = "Listedeki başlangıçlarla gelen aramalar telefon çalmadan reddedilir. " +
                "Arama kaydında görünmeye devam eder; önemli bir aramayı kaçırırsan oradan görürsün.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (enabled) "Merkez arama süzme uygulaması." else "Süzgeç kapalı.",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "Android gelen aramayı çalmadan önce Merkez'e sorar. Numara telefondan " +
                        "çıkmaz, hiçbir yere gönderilmez.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!enabled) {
                    Button(onClick = {
                        val roles = context.getSystemService(RoleManager::class.java)
                        if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
                            askRole.launch(roles.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
                        } else {
                            android.widget.Toast.makeText(
                                context,
                                "Bu telefon arama süzme uygulaması seçmeye izin vermiyor.",
                                android.widget.Toast.LENGTH_LONG,
                            ).show()
                        }
                    }) { Text("Süzgeci aç") }
                }
            }
        }

        if (enabled) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Engelleme açık", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = on, onCheckedChange = { on = it; store.isOn = it })
            }
        }

        Text("Engellenen başlangıçlar", style = MaterialTheme.typography.labelLarge)
        prefixes.forEach { prefix ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "${spaced(prefix)} ile başlayanlar",
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { save(prefixes - prefix) }) { Text("Sil") }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                label = { Text("Başlangıç, ör. 0850 ya da 0212 9*") },
                singleLine = true,
                // Telefon klavyesinde * var; ? için x de yazılabiliyor.
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.weight(1f),
            )
            TextButton(
                enabled = CallFilterStore.digitCount(CallFilterStore.normalizePrefix(draft)) >= 3,
                onClick = {
                    save(prefixes + CallFilterStore.normalizePrefix(draft))
                    draft = ""
                },
            ) { Text("Ekle") }
        }

        Text(
            "* istenen kadar rakam, ? (ya da x) tek rakam yerine geçer. Örnek: \"0212 9*\" 0212 9 ile " +
                "başlayan bütün numaraları, \"0212 xxx 00\" 0212'den sonra üç rakam ve 00 gelenleri engeller.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text("Son engellenenler", style = MaterialTheme.typography.labelLarge)
        Text(
            if (count == 0) "Henüz engellenen arama yok." else "Bugüne kadar $count arama engellendi.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val format = remember { SimpleDateFormat("d MMMM HH:mm", Locale.forLanguageTag("tr")) }
        recent.forEach { call ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(call.number, modifier = Modifier.weight(1f))
                Text(
                    format.format(Date(call.at)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Kalıbı okunur biçimde gösterir: "02129*" → "0212 9*". Türkiye'de alan
 * kodu dört hane; ondan sonrası ayrı yazılınca ne engellendiği hemen okunuyor.
 */
private fun spaced(prefix: String): String =
    if (prefix.length > 4) prefix.take(4) + " " + prefix.drop(4) else prefix
