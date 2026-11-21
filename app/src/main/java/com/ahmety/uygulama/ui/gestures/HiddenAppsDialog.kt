package com.ahmety.uygulama.ui.gestures

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ahmety.uygulama.feature.gestures.HiddenApps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Son uygulamalarda gösterilmeyecek uygulamaları seçme düğmesi ve penceresi.
 */
@Composable
internal fun HiddenAppsButton() {
    val context = LocalContext.current
    var hidden by remember { mutableStateOf(HiddenApps.all(context)) }
    var open by remember { mutableStateOf(false) }

    OutlinedButton(onClick = { open = true }) {
        Text(if (hidden.isEmpty()) "Gösterilmeyecek uygulamalar" else "Gösterilmeyecek uygulamalar (${hidden.size})")
    }
    if (!open) return

    var apps by remember { mutableStateOf<List<AppInfo>?>(null) }
    var draft by remember { mutableStateOf(hidden) }
    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { loadLaunchableApps(context) }
    }
    AlertDialog(
        onDismissRequest = { open = false },
        title = { Text("Gösterilmeyecek uygulamalar") },
        text = {
            val list = apps
            if (list == null) {
                Text("Uygulamalar yükleniyor…")
            } else {
                // İşaretliler başta: hangilerinin gizli olduğu hemen görülsün.
                val sorted = list.sortedByDescending { it.packageName in draft }
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(sorted, key = { it.packageName }) { app ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    draft = if (app.packageName in draft) draft - app.packageName else draft + app.packageName
                                }
                                .padding(vertical = 2.dp),
                        ) {
                            Checkbox(
                                checked = app.packageName in draft,
                                onCheckedChange = { checked ->
                                    draft = if (checked) draft + app.packageName else draft - app.packageName
                                },
                            )
                            Text(app.label)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                HiddenApps.set(context, draft)
                hidden = draft
                open = false
            }) { Text("Kaydet") }
        },
        dismissButton = { TextButton(onClick = { open = false }) { Text("Vazgeç") } },
    )
}
