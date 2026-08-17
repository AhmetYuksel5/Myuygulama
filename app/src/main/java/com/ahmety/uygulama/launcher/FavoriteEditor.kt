package com.ahmety.uygulama.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/** Düzenleyicideki eylem türleri; her biri bir [LauncherAction]'a çevrilir. */
private enum class ActionKind(val label: String) {
    NONE("Yok"),
    APP("Uygulama aç"),
    WHATSAPP("WhatsApp"),
    CALL("Ara"),
    DIAL("Çevirici"),
    SMS("SMS"),
    NAV("Navigasyon"),
    WEB("Web"),
}

private fun kindOf(action: LauncherAction): ActionKind = when (action) {
    is LauncherAction.OpenApp -> ActionKind.APP
    is LauncherAction.WhatsApp -> ActionKind.WHATSAPP
    is LauncherAction.Call -> ActionKind.CALL
    is LauncherAction.Dial -> ActionKind.DIAL
    is LauncherAction.Sms -> ActionKind.SMS
    is LauncherAction.Navigate -> ActionKind.NAV
    is LauncherAction.Web -> ActionKind.WEB
    LauncherAction.None -> ActionKind.NONE
}

private fun argText(action: LauncherAction): String = when (action) {
    is LauncherAction.WhatsApp -> action.phone
    is LauncherAction.Call -> action.phone
    is LauncherAction.Dial -> action.phone
    is LauncherAction.Sms -> action.phone
    is LauncherAction.Navigate -> action.address
    is LauncherAction.Web -> action.url
    else -> ""
}

private fun argPkg(action: LauncherAction): String =
    (action as? LauncherAction.OpenApp)?.packageName ?: ""

private fun buildAction(kind: ActionKind, text: String, pkg: String): LauncherAction {
    // Boş argümanla komut kurulmasın: boş numarayla wa.me açılıyor,
    // boş adrese navigasyon başlıyordu.
    if (kind == ActionKind.APP) {
        return if (pkg.isBlank()) LauncherAction.None else LauncherAction.OpenApp(pkg)
    }
    val value = text.trim()
    if (kind != ActionKind.NONE && value.isBlank()) return LauncherAction.None
    return when (kind) {
        ActionKind.NONE, ActionKind.APP -> LauncherAction.None
        ActionKind.WHATSAPP -> LauncherAction.WhatsApp(value)
        ActionKind.CALL -> LauncherAction.Call(value)
        ActionKind.DIAL -> LauncherAction.Dial(value)
        ActionKind.SMS -> LauncherAction.Sms(value)
        ActionKind.NAV -> LauncherAction.Navigate(value)
        ActionKind.WEB -> LauncherAction.Web(value)
    }
}

private val shortcutColors = listOf(
    0xFF3F51B5.toInt(), 0xFF00897B.toInt(), 0xFFE65100.toInt(),
    0xFFC2185B.toInt(), 0xFF6A1B9A.toInt(), 0xFF2E7D32.toInt(),
)

/**
 * Bir simgenin dört jestini (tek/çift dokun, yukarı/aşağı sürükle) ayrı ayrı
 * komuta bağlamak için düzenleyici. Kişiler için tipik kullanım:
 * tek dokun = WhatsApp, çift dokun = Ara, yukarı = iş navigasyonu, aşağı = ev.
 */
@Composable
fun FavoriteEditorDialog(
    favorite: Favorite,
    appLabelOf: (String) -> String,
    onPickApp: ((AppInfo) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onSave: (Favorite) -> Unit,
) {
    var label by remember { mutableStateOf(favorite.label) }
    var color by remember { mutableIntStateOf(favorite.colorArgb) }
    var tap by remember { mutableStateOf(favorite.tap) }
    var doubleTap by remember { mutableStateOf(favorite.doubleTap) }
    var swipeUp by remember { mutableStateOf(favorite.swipeUp) }
    var swipeDown by remember { mutableStateOf(favorite.swipeDown) }
    // Kişi kısayolu kurarken numarayı dört ayrı alana yazmak zorunda kalma.
    var contactPhone by remember { mutableStateOf(argText(favorite.tap)) }
    // Eylem alanlarını ön ayar uygulanınca sıfırdan kurmak için sayaç.
    var presetStamp by remember { mutableIntStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    favorite.copy(
                        label = label.ifBlank { "Kısayol" },
                        colorArgb = color,
                        tap = tap,
                        doubleTap = doubleTap,
                        swipeUp = swipeUp,
                        swipeDown = swipeDown,
                    ),
                )
            }) { Text("Kaydet") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İptal") } },
        title = { Text(if (favorite.type == FavoriteType.APP) "Simgeyi düzenle" else "Kısayolu düzenle") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Etiket") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (favorite.type == FavoriteType.SHORTCUT) {
                    Text("Kişi hızlı kurulum", style = MaterialTheme.typography.labelLarge)
                    OutlinedTextField(
                        value = contactPhone,
                        onValueChange = { contactPhone = it },
                        label = { Text("Telefon (ülke koduyla, örn. +9055...)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedButton(
                        onClick = {
                            val phone = contactPhone.trim()
                            if (phone.isNotBlank()) {
                                tap = LauncherAction.WhatsApp(phone)
                                doubleTap = LauncherAction.Call(phone)
                                presetStamp++
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Uygula: tek dokun WhatsApp, çift dokun ara")
                    }

                    Text("Renk", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        shortcutColors.forEach { c ->
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .background(Color(c), CircleShape)
                                    .then(
                                        if (c == color) {
                                            Modifier.border(3.dp, Color.White, CircleShape)
                                        } else {
                                            Modifier
                                        },
                                    )
                                    .clickable { color = c },
                            )
                        }
                    }
                }

                ActionEditor("Tek dokun", tap, presetStamp, appLabelOf, onPickApp) { tap = it }
                ActionEditor("Çift dokun", doubleTap, presetStamp, appLabelOf, onPickApp) { doubleTap = it }
                ActionEditor("Yukarı sürükle", swipeUp, presetStamp, appLabelOf, onPickApp) { swipeUp = it }
                ActionEditor("Aşağı sürükle", swipeDown, presetStamp, appLabelOf, onPickApp) { swipeDown = it }

                // Sil, Kaydet'in yanında değil en altta: yanlış dokunuşla
                // tek tek girilmiş bir kısayolu kaybetmek çok kolaydı.
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Bu simgeyi sil", color = MaterialTheme.colorScheme.error)
                }
            }
        },
    )
}

@Composable
private fun ActionEditor(
    title: String,
    initial: LauncherAction,
    /** Değişince alanlar [initial]'dan yeniden kurulur (kişi ön ayarı için). */
    resetKey: Int,
    appLabelOf: (String) -> String,
    onPickApp: ((AppInfo) -> Unit) -> Unit,
    onChanged: (LauncherAction) -> Unit,
) {
    var kind by remember(resetKey) { mutableStateOf(kindOf(initial)) }
    var text by remember(resetKey) { mutableStateOf(argText(initial)) }
    var pkg by remember(resetKey) { mutableStateOf(argPkg(initial)) }
    var menuOpen by remember { mutableStateOf(false) }

    fun emit() = onChanged(buildAction(kind, text, pkg))

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Box {
                OutlinedButton(onClick = { menuOpen = true }) { Text(kind.label) }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    ActionKind.entries.forEach { k ->
                        DropdownMenuItem(
                            text = { Text(k.label) },
                            onClick = {
                                kind = k
                                menuOpen = false
                                emit()
                            },
                        )
                    }
                }
            }
        }

        when (kind) {
            ActionKind.NONE -> Unit
            ActionKind.APP -> Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (pkg.isBlank()) "Uygulama seçili değil" else appLabelOf(pkg),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { onPickApp { app -> pkg = app.packageName; emit() } }) {
                    Text("Seç")
                }
            }
            else -> OutlinedTextField(
                value = text,
                onValueChange = { text = it; emit() },
                label = { Text(hintFor(kind)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (kind == ActionKind.NAV || kind == ActionKind.WEB) {
                        KeyboardType.Text
                    } else {
                        KeyboardType.Phone
                    },
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun hintFor(kind: ActionKind): String = when (kind) {
    ActionKind.WHATSAPP -> "Telefon (ülke koduyla, örn. +9055...)"
    ActionKind.CALL, ActionKind.DIAL -> "Telefon numarası"
    ActionKind.SMS -> "Telefon numarası"
    ActionKind.NAV -> "Adres (örn. iş / ev adresi)"
    ActionKind.WEB -> "Bağlantı (https://...)"
    else -> ""
}
