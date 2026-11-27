package com.ahmety.uygulama.core.designsystem

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Ekranda sürekli açıklama metni göstermemek için uzun basma bilgi desteği.
 *
 * Normal görünümde hiçbir metin yer kaplamaz; kullanıcı yalnızca anlamını
 * merak ettiği öğenin üzerine biraz basılı tuttuğunda kısa bilgi penceresi
 * açılır. [onClick] verilmezse normal dokunuşta dalga (ripple) üretmez.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.longPressHint(
    title: String,
    hint: String?,
    onClick: (() -> Unit)? = null,
): Modifier {
    if (hint.isNullOrBlank()) {
        return if (onClick != null) {
            this.combinedClickable(onClick = onClick)
        } else {
            this
        }
    }
    var open by remember(title, hint) { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = { Text(hint) },
            confirmButton = {
                TextButton(onClick = { open = false }) { Text("Tamam") }
            },
        )
    }
    return if (onClick != null) {
        this.combinedClickable(
            onClick = onClick,
            onLongClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                open = true
            },
        )
    } else {
        this.combinedClickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = {},
            onLongClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                open = true
            },
        )
    }
}
