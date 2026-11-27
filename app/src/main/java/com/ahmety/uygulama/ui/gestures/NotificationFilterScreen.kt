package com.ahmety.uygulama.ui.gestures

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ahmety.uygulama.core.designsystem.MerkezTopBar
import com.ahmety.uygulama.feature.gestures.NotificationFilterService
import com.ahmety.uygulama.feature.gestures.NotificationFilterStore
import com.ahmety.uygulama.feature.gestures.NotificationRule
import com.ahmety.uygulama.feature.gestures.SeenNotification

/**
 * Bildirim süzgecinin ayarları.
 *
 * Kural yazmanın kolay yolu son gelen bildirimlerden seçmek: hangi
 * uygulamadan geldiğini ve tam metnini kullanıcının bilmesi gerekmiyor.
 */
@Composable
fun NotificationFilterScreen(onBack: (() -> Unit)? = null) {
    val context = LocalContext.current
    val store = remember { NotificationFilterStore(context) }
    var enabled by remember { mutableStateOf(NotificationFilterStore.isEnabled(context)) }
    var rules by remember { mutableStateOf(store.rules()) }
    var recent by remember { mutableStateOf(NotificationFilterService.recent()) }
    var hidden by remember { mutableIntStateOf(store.hiddenCount) }
    // Düzenlenen kural taslağı; null ise pencere kapalı.
    var draft by remember { mutableStateOf<NotificationRule?>(null) }

    fun refresh() {
        enabled = NotificationFilterStore.isEnabled(context)
        recent = NotificationFilterService.recent()
        hidden = store.hiddenCount
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun save(next: List<NotificationRule>) {
        rules = next
        store.save(next)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        MerkezTopBar(title = "Bildirim süzgeci", onBack = onBack)

        ServiceCard(
            enabled = enabled,
            explanation = "Bildirimleri görebilmek için bildirim erişimi ister. İzin soluk görünüyorsa: Uygulama bilgisi → ⋮ → Kısıtlı ayarlara izin ver.",
            onOpen = {
                context.startActivity(
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            },
        )

        Text("Gizlenenler", style = MaterialTheme.typography.labelLarge)
        if (rules.isEmpty()) {
            Text(
                "Kural yok.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                "$hidden gizlendi",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        rules.forEachIndexed { index, rule ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (rule.text.isBlank()) "Bütün bildirimleri" else "\"${rule.text}\" geçenler",
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        if (rule.pkg.isBlank()) "Her uygulamadan" else appName(context, rule.pkg),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { save(rules.toMutableList().apply { removeAt(index) }) }) {
                    Text("Sil")
                }
            }
        }
        OutlinedButton(onClick = { draft = NotificationRule("", "") }) { Text("Elle kural ekle") }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Son bildirimler", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = { refresh() }) { Text("Yenile") }
        }
        if (recent.isEmpty()) {
            Text(
                "Bildirim yok.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        recent.forEach { seen -> SeenRow(seen) { draft = NotificationRule(seen.pkg, seen.title) } }
    }

    draft?.let { current ->
        var text by remember(current) { mutableStateOf(current.text) }
        AlertDialog(
            onDismissRequest = { draft = null },
            title = { Text("Bildirimi gizle") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (current.pkg.isBlank()) "Her uygulamadan" else appName(context, current.pkg),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text("İçinde geçen yazı") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = text.isNotBlank() || current.pkg.isNotBlank(),
                    onClick = {
                        save(rules + NotificationRule(current.pkg, text.trim()))
                        draft = null
                        refresh()
                    },
                ) { Text("Gizle") }
            },
            dismissButton = { TextButton(onClick = { draft = null }) { Text("Vazgeç") } },
        )
    }
}

@Composable
private fun SeenRow(seen: SeenNotification, onHide: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(
                seen.app,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (seen.title.isNotBlank()) {
                Text(seen.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (seen.text.isNotBlank()) {
                Text(
                    seen.text,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        TextButton(onClick = onHide) { Text("Gizle") }
    }
}

private fun appName(context: android.content.Context, pkg: String): String = runCatching {
    context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString()
}.getOrDefault(pkg)
