package com.ahmety.uygulama.ui.gestures

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ahmety.uygulama.feature.gestures.Shortcut
import com.ahmety.uygulama.feature.gestures.ShortcutKind
import com.ahmety.uygulama.feature.gestures.ShortcutStore

/**
 * Kısayol çubuğunun içeriğini düzenleyen bölüm.
 *
 * Ayrı bir ekran değil, imleç ayarlarının içinde: çubuğu açan hareket de
 * (tutamağa çift dokunma) orada anlatılıyor, ikisini ayırmak insanı iki
 * ekran arasında gezdirirdi.
 */
@Composable
internal fun ShortcutsSection() {
    val context = LocalContext.current
    val store = remember { ShortcutStore(context) }
    var list by remember { mutableStateOf(store.items) }
    var pickingApp by remember { mutableStateOf(false) }
    var pickingSystem by remember { mutableStateOf(false) }
    // Rehberden gelen kişi; aramak mı WhatsApp mı diye sorulacak.
    var contact by remember { mutableStateOf<Pair<String, String>?>(null) }

    val contactPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val uri = result.data?.data
        if (uri != null) contact = readContact(context, uri)
    }

    fun add(item: Shortcut) {
        store.add(item)
        list = store.items
    }

    Text("Kısayollar", style = MaterialTheme.typography.labelLarge)
    Text(
        text = "Dipteki çubuğa hızlıca iki kez dokununca kısayol çubuğu açılır; " +
            "uygulamanın üstünde durur, dışına dokununca kapanır. Uygulama, " +
            "arama, WhatsApp ve sistem kısayolu karışık durabilir.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    list.forEachIndexed { index, item ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.label, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = kindLabel(item),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (index > 0) {
                TextButton(
                    onClick = {
                        store.move(index, index - 1)
                        list = store.items
                    },
                ) { Text("Yukarı") }
            }
            TextButton(
                onClick = {
                    store.removeAt(index)
                    list = store.items
                },
            ) { Text("Sil") }
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { pickingApp = true }) { Text("Uygulama") }
        OutlinedButton(
            onClick = {
                // Sistemin kendi seçicisi: rehber izni istemeden yalnız
                // seçilen kişinin numarası okunabiliyor.
                contactPicker.launch(
                    Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI),
                )
            },
        ) { Text("Kişi") }
        OutlinedButton(onClick = { pickingSystem = true }) { Text("Sistem") }
    }

    if (pickingApp) {
        val apps = remember { loadLaunchableApps(context) }
        AlertDialog(
            onDismissRequest = { pickingApp = false },
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
                                    add(Shortcut(ShortcutKind.APP, app.packageName, app.label))
                                    pickingApp = false
                                }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickingApp = false }) { Text("Vazgeç") } },
        )
    }

    if (pickingSystem) {
        AlertDialog(
            onDismissRequest = { pickingSystem = false },
            title = { Text("Sistem kısayolu") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(ShortcutStore.SYSTEM.entries.toList()) { entry ->
                        Text(
                            text = entry.value,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    add(Shortcut(ShortcutKind.SYSTEM, entry.key, entry.value))
                                    pickingSystem = false
                                }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickingSystem = false }) { Text("Vazgeç") } },
        )
    }

    contact?.let { (name, number) ->
        AlertDialog(
            onDismissRequest = { contact = null },
            title = { Text(name) },
            text = { Text("Bu kişi için hangisi olsun?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        add(Shortcut(ShortcutKind.CALL, number, name))
                        contact = null
                    },
                ) { Text("Ara") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        add(Shortcut(ShortcutKind.WHATSAPP, number, name))
                        contact = null
                    },
                ) { Text("WhatsApp") }
            },
        )
    }
}

private fun kindLabel(item: Shortcut): String = when (item.kind) {
    ShortcutKind.APP -> "Uygulama"
    ShortcutKind.CALL -> "Ara · ${item.value}"
    ShortcutKind.WHATSAPP -> "WhatsApp · ${item.value}"
    ShortcutKind.SYSTEM -> "Sistem"
}

/**
 * Seçilen kişinin adı ve numarası.
 *
 * Okuma izni yok; seçici yalnız seçilen satırı okumaya geçici izin
 * veriyor, o yüzden sorgu tam o adrese yapılıyor.
 */
private fun readContact(context: Context, uri: Uri): Pair<String, String>? = runCatching {
    context.contentResolver.query(
        uri,
        arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
        ),
        null,
        null,
        null,
    )?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        val name = cursor.getString(0).orEmpty()
        val number = cursor.getString(1).orEmpty()
        if (number.isBlank()) null else name.ifBlank { number } to number
    }
}.getOrNull()
