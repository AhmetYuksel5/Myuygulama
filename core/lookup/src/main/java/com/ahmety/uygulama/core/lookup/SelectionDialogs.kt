package com.ahmety.uygulama.core.lookup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.ahmety.uygulama.core.designsystem.ColorPickerDialog
import com.ahmety.uygulama.core.designsystem.PendingHighlight
import com.ahmety.uygulama.core.model.HighlightColor

/**
 * Seçim kutusu ve arkasından açılan kelime kartı, tek parça.
 *
 * Ekran yalnız seçimi ([request]) ve o seçimin şimdiki rengini veriyor;
 * karşılık, soru ve kart [lookup] üzerinden kendiliğinden geliyor. Renk
 * seçilince ya da kutu kapanınca [lookup] temizleniyor — ekranın bunu
 * hatırlaması gerekmiyor.
 *
 * [onPick] ikinci parametre: bağlam cümlesi de kaydedilsin mi.
 */
@Composable
fun SelectionDialogs(
    request: PendingHighlight?,
    current: HighlightColor?,
    lookup: SelectionLookup,
    onPick: (HighlightColor, Boolean) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val gloss by lookup.gloss.collectAsState()
    val answer by lookup.answer.collectAsState()
    val detail by lookup.detail.collectAsState()

    request?.let { pending ->
        // Kutu açılır açılmaz karşılık soruluyor: kelimeyi işaretleyip
        // işaretlememe kararı buna bakılarak veriliyor.
        LaunchedEffect(pending) { lookup.lookUp(pending.word, pending.sentence) }

        ColorPickerDialog(
            request = pending,
            current = current,
            gloss = gloss,
            onDetail = { lookup.openDetail(pending.word, pending.sentence) },
            onAsk = { question -> lookup.ask(pending.word, pending.sentence, question) },
            answer = answer,
            onDismiss = {
                lookup.clear()
                onDismiss()
            },
            onPick = { color, keepContext ->
                lookup.clear()
                onPick(color, keepContext)
            },
            onRemove = {
                lookup.clear()
                onRemove()
            },
        )
    }

    detail?.let { card ->
        WordDetailDialog(
            detail = card,
            onMoreExamples = lookup::moreExamples,
            onDismiss = lookup::closeDetail,
        )
    }
}
