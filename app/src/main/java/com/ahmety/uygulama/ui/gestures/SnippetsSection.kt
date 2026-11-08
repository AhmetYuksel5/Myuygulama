package com.ahmety.uygulama.ui.gestures

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ahmety.uygulama.feature.gestures.Snippet
import com.ahmety.uygulama.feature.gestures.SnippetStore

/**
 * Klavyenin yanındaki düğmeden yazılan hazır metinler.
 *
 * Düzenleme bir pencerede: metinler (adres, fiyat listesi) çok satırlı
 * olabiliyor, satır içinde düzenlemek dar kalırdı.
 */
@Composable
internal fun SnippetsSection() {
    val context = LocalContext.current
    val store = remember { SnippetStore(context) }
    var items by remember { mutableStateOf(store.all()) }
    // Düzenlenen satırın sırası; -1 yeni kayıt, null pencere kapalı.
    var editing by remember { mutableStateOf<Int?>(null) }

    fun save(next: List<Snippet>) {
        items = next
        store.save(next)
    }

    Text("Hazır metinler", style = MaterialTheme.typography.labelLarge)
    Text(
        text = "Klavye açıkken mikrofonun solunda bir düğme çıkıyor; dokununca bu liste " +
            "açılıyor, birine dokunmak onu yazı kutusuna ekliyor. IBAN, adres, fiyat " +
            "listesi, \"yoldayım\" gibi sık yazdıkların için.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    items.forEachIndexed { index, item ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { editing = index }
                .padding(vertical = 6.dp),
        ) {
            Column(Modifier.weight(1f)) {
                if (item.title.isNotBlank()) {
                    Text(item.title, fontWeight = FontWeight.SemiBold)
                }
                Text(
                    item.text,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (index > 0) {
                TextButton(onClick = {
                    save(items.toMutableList().apply { add(index - 1, removeAt(index)) })
                }) { Text("Yukarı") }
            }
        }
    }
    OutlinedButton(onClick = { editing = -1 }) { Text("Hazır metin ekle") }

    val current = editing
    if (current != null) {
        val original = items.getOrNull(current)
        var title by remember(current) { mutableStateOf(original?.title.orEmpty()) }
        var text by remember(current) { mutableStateOf(original?.text.orEmpty()) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (original == null) "Yeni hazır metin" else "Hazır metni düzenle") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Kısa ad (listede görünür)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text("Yazılacak metin") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = text.isNotBlank(),
                    onClick = {
                        val entry = Snippet(title.trim(), text.trim())
                        save(
                            if (original == null) items + entry
                            else items.toMutableList().apply { set(current, entry) },
                        )
                        editing = null
                    },
                ) { Text("Kaydet") }
            },
            dismissButton = {
                Row {
                    if (original != null) {
                        TextButton(onClick = {
                            save(items.toMutableList().apply { removeAt(current) })
                            editing = null
                        }) { Text("Sil", color = MaterialTheme.colorScheme.error) }
                    }
                    TextButton(onClick = { editing = null }) { Text("Vazgeç") }
                }
            },
        )
    }
}
