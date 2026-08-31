package com.ahmety.uygulama.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahmety.uygulama.core.model.HighlightColor
import kotlinx.coroutines.delay

/**
 * Okuma metninde kelime seçme düzeneği.
 *
 * Kitap okuyucusu için yazılmıştı; Pocket'taki makalelerde de aynısı
 * gerekiyor — orada bir kelimeye dokununca paragrafın tamamı alıntılanıyordu
 * ve iki ekran birbirine hiç benzemiyordu. Kural tek yerde dursun diye
 * ortak modüle taşındı.
 */

/** Renklerin ekrandaki karşılığı. */
@Composable
fun highlightPaint(color: HighlightColor): Color = when (color) {
    HighlightColor.YELLOW -> Color(0xFFFFE082)
    HighlightColor.BLUE -> Color(0xFF90CAF9)
    HighlightColor.GREEN -> Color(0xFFA5D6A7)
    HighlightColor.RED -> Color(0xFFEF9A9A)
}

/** Renk kutusunun beklettiği seçim: seçilen metin ve geçtiği cümle. */
data class PendingHighlight(val word: String, val sentence: String)

/**
 * Çift dokunuştan sonra üçüncü dokunuşu bekleme süresi.
 *
 * Compose ikinci dokunuşu hemen bildiriyor ama üçüncüyü, kendi çift dokunuş
 * penceresi dolmadan haber vermiyor; o yüzden bu süre onun üstünde olmak
 * zorunda. Kısaltırsak üç dokunuş hiç çalışmaz.
 */
private const val TRIPLE_TAP_WINDOW_MS = 420L

/**
 * Paragrafı çizer ve dokunulan kelimeyi bulur.
 *
 * Metin tek bir [Text] olarak çiziliyor; dokunma noktası, yerleşim sonucundan
 * karakter konumuna çevrilip kelime sınırları bulunuyor. Böylece kelimeleri
 * ayrı ayrı bileşenlere bölmeden, akıcı bir okuma metni korunuyor.
 */
@Composable
fun HighlightableParagraph(
    raw: String,
    colors: Map<String, HighlightColor>,
    textColor: Color,
    fontSizeSp: Int,
    onZoneTap: (Float) -> Unit = {},
    onSelection: (text: String, context: String) -> Unit,
    onPreview: (String?) -> Unit,
) {
    // Altyazıdan gelen metinde `<i>` etiketleri duruyor: eğik yazı, sesin
    // sahnede olmadığını söylüyor. Etiketleri burada metinden çıkarıp yerine
    // gerçek eğik yazı koyuyoruz. Bundan sonrası tek bir düz metin üzerinde
    // çalışıyor — seçim ve işaretleme konumları etiket görmüyor.
    val italic = remember(raw) { extractItalics(raw) }
    val paragraph = italic.text

    var layout by remember(paragraph) { mutableStateOf<TextLayoutResult?>(null) }
    // Seçim: uzun basınca kelimede başlar, parmak sürüklendikçe genişler.
    var selection by remember(paragraph) { mutableStateOf<IntRange?>(null) }
    var anchor by remember(paragraph) { mutableStateOf<Pair<Int, Int>?>(null) }
    // Çift dokunuşta seçilen kelime hemen açılmıyor: üçüncü bir dokunuş
    // gelirse cümlenin tamamı seçilecek. Bekleme olmadan üçüncü dokunuşa
    // sıra gelmiyordu, çünkü ikinci dokunuşta renk kutusu açılıyordu.
    var pendingWord by remember(paragraph) { mutableStateOf<Pair<Int, Int>?>(null) }

    LaunchedEffect(pendingWord) {
        val bounds = pendingWord ?: return@LaunchedEffect
        delay(TRIPLE_TAP_WINDOW_MS)
        pendingWord = null
        onSelection(
            paragraph.substring(bounds.first, bounds.second),
            contextAround(paragraph, bounds.first, bounds.second),
        )
    }
    val haptic = LocalHapticFeedback.current
    val selectionTint = MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)

    val painted: AnnotatedString = remember(paragraph, colors, selection, selectionTint) {
        buildAnnotatedString {
            append(paragraph)

            italic.spans.forEach { span ->
                addStyle(SpanStyle(fontStyle = FontStyle.Italic), span.first, span.last + 1)
            }

            // Önce çok kelimeli işaretlemeler: metinde geçtiği her yeri boya.
            colors.forEach { (text, color) ->
                if (!text.contains(' ')) return@forEach
                var index = paragraph.indexOf(text, ignoreCase = true)
                while (index >= 0) {
                    addStyle(SpanStyle(background = paintOf(color)), index, index + text.length)
                    index = paragraph.indexOf(text, index + text.length, ignoreCase = true)
                }
            }

            // Sonra tek kelimeler.
            forEachWord(paragraph) { rawStart, rawEnd ->
                val bounds = trimBounds(paragraph, rawStart, rawEnd) ?: return@forEachWord
                val word = paragraph.substring(bounds.first, bounds.second).lowercase()
                colors[word]?.let { color ->
                    addStyle(SpanStyle(background = paintOf(color)), bounds.first, bounds.second)
                }
            }

            // En üstte, sürüklenirken görünen seçim.
            selection?.let { range ->
                addStyle(SpanStyle(background = selectionTint), range.first, range.last + 1)
            }
        }
    }

    // Seçilen metni ekranın üstündeki şeride bildiriyoruz. Paragrafın içine
    // koymak metni aşağı itiyor ve okuduğun yer sürüklerken oynuyordu.
    LaunchedEffect(selection) {
        // Yalnız dolu seçimi bildiriyoruz: kaydırırken görünüme giren her
        // paragraf boş bildirseydi başkasının seçimini silerdi. Temizleme
        // parmağın kalktığı yerde yapılıyor.
        selection?.let { onPreview(paragraph.substring(it.first, it.last + 1)) }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = painted,
            color = textColor,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = fontSizeSp.sp,
                lineHeight = (fontSizeSp * 1.65f).sp,
            ),
            onTextLayout = { layout = it },
            modifier = Modifier
                .fillMaxWidth()
                // Dokunma bölgesi: solda geri, sağda ileri, ortada arayüz.
                // onLongPress boş bırakılıyor ki uzun basıp seçim yaparken
                // parmak kalkınca ayrıca "dokunuş" sayılmasın.
                .pointerInput(paragraph, colors) {
                    detectTapGestures(
                        onLongPress = {},
                        // Çift dokunuş kelimeyi, üçüncü dokunuş cümleyi
                        // seçiyor. Uzun basıp sürüklemek hâlâ serbest seçim
                        // için duruyor ama tek kelime ya da tam cümle için
                        // uğraştırıyordu.
                        onDoubleTap = { position ->
                            val result = layout ?: return@detectTapGestures
                            val offset = result.getOffsetForPosition(position)
                            pendingWord = wordBoundsAt(paragraph, offset)
                        },
                        onTap = { position ->
                            val offset = layout?.getOffsetForPosition(position)
                            // Çift dokunuşun ardından gelen dokunuş üçüncüdür:
                            // bekleyen kelime seçimi iptal edilip cümlenin
                            // tamamı seçiliyor.
                            if (pendingWord != null && offset != null) {
                                pendingWord = null
                                val bounds = sentenceBoundsAt(paragraph, offset)
                                if (bounds != null) {
                                    onSelection(
                                        paragraph.substring(bounds.first, bounds.second).trim(),
                                        paragraph,
                                    )
                                    return@detectTapGestures
                                }
                            }
                            // İşaretli bir kelimeye dokunmak renk kutusunu
                            // açsın: işareti kaldırmanın tek yolu oydu ama
                            // dokunuş arayüzü açıp kapatmakla harcanıyordu.
                            val marked = layout?.let {
                                markedAt(paragraph, colors, it.getOffsetForPosition(position))
                            }
                            if (marked != null) {
                                onSelection(
                                    marked.text,
                                    contextAround(paragraph, marked.start, marked.end),
                                )
                            } else {
                                onZoneTap(position.x / size.width.toFloat())
                            }
                        },
                    )
                }
                // Uzun bas + sürükle: birden çok kelime seçilebiliyor. Sarı ile
                // altı çizilecek yerler genelde tek kelime değil.
                .pointerInput(paragraph) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { position ->
                            val result = layout ?: return@detectDragGesturesAfterLongPress
                            val offset = result.getOffsetForPosition(position)
                            val bounds = wordBoundsAt(paragraph, offset)
                                ?: return@detectDragGesturesAfterLongPress
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            anchor = bounds
                            selection = bounds.first until bounds.second
                        },
                        onDrag = { change, _ ->
                            val result = layout ?: return@detectDragGesturesAfterLongPress
                            val start = anchor ?: return@detectDragGesturesAfterLongPress
                            val offset = result.getOffsetForPosition(change.position)
                            val bounds = wordBoundsAt(paragraph, offset)
                                ?: return@detectDragGesturesAfterLongPress
                            selection = minOf(start.first, bounds.first) until
                                maxOf(start.second, bounds.second)
                        },
                        onDragEnd = {
                            onPreview(null)
                            val range = selection
                            if (range != null && !range.isEmpty()) {
                                val text = paragraph.substring(range.first, range.last + 1).trim()
                                if (text.isNotEmpty()) {
                                    onSelection(
                                        text,
                                        contextAround(paragraph, range.first, range.last + 1),
                                    )
                                }
                            }
                            selection = null
                            anchor = null
                        },
                        onDragCancel = {
                            onPreview(null)
                            selection = null
                            anchor = null
                        },
                    )
                },
        )
    }
}

/**
 * Verilen konumdaki cümlenin sınırları.
 *
 * Cümle sonu ".", "!" ya da "?" — ama "Mr." gibi kısaltmalarda yanılmamak
 * için noktadan sonra boşluk arıyoruz.
 */
private fun sentenceBoundsAt(text: String, offset: Int): Pair<Int, Int>? {
    if (text.isEmpty()) return null
    val index = offset.coerceIn(0, text.length - 1)
    var start = index
    while (start > 0) {
        val char = text[start - 1]
        if (char in ".!?" && (start >= text.length || text[start].isWhitespace())) break
        start--
    }
    var end = index
    while (end < text.length) {
        val char = text[end]
        end++
        if (char in ".!?" && (end >= text.length || text[end].isWhitespace())) break
    }
    while (start < end && text[start].isWhitespace()) start++
    return if (end - start < 2) null else start to end
}

/** Paragraftaki bir işaretin metni ve yeri. */
private data class MarkedSpan(val text: String, val start: Int, val end: Int)

/**
 * Dokunulan yerde bir işaret var mı.
 *
 * Önce çok kelimeli işaretlere bakıyoruz — onlar tek kelimeyi de kapsıyor
 * olabilir — sonra tek kelimeye.
 */
private fun markedAt(
    paragraph: String,
    colors: Map<String, HighlightColor>,
    offset: Int,
): MarkedSpan? {
    colors.keys.filter { it.contains(' ') }.forEach { phrase ->
        var index = paragraph.indexOf(phrase, ignoreCase = true)
        while (index >= 0) {
            val end = index + phrase.length
            if (offset in index until end) {
                return MarkedSpan(paragraph.substring(index, end), index, end)
            }
            index = paragraph.indexOf(phrase, end, ignoreCase = true)
        }
    }
    val bounds = wordBoundsAt(paragraph, offset) ?: return null
    val word = paragraph.substring(bounds.first, bounds.second)
    if (word.lowercase() !in colors) return null
    return MarkedSpan(word, bounds.first, bounds.second)
}

/**
 * Sürüklerken seçilen metnin önizlemesi.
 *
 * Metnin içine değil üstüne çiziliyor: akışa girseydi paragrafı aşağı iter
 * ve okuduğun satır parmağının altından kayardı. Koyu zemin, sayfanın
 * temasından bağımsız olarak okunsun diye.
 */
@Composable
fun SelectionPreview(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shadowElevation = 6.dp,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

@Composable
fun ColorPickerDialog(
    request: PendingHighlight,
    current: HighlightColor?,
    onDismiss: () -> Unit,
    onPick: (HighlightColor, Boolean) -> Unit,
    onRemove: () -> Unit,
) {
    var keepContext by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = request.word,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                style = if (request.word.length > 40) {
                    MaterialTheme.typography.titleSmall
                } else {
                    MaterialTheme.typography.titleLarge
                },
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Renkler kendini anlatıyor; ad yazmaya gerek yok.
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    HighlightColor.entries.forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .background(highlightPaint(color), CircleShape)
                                .then(
                                    if (color == current) {
                                        Modifier.border(
                                            3.dp,
                                            MaterialTheme.colorScheme.onSurface,
                                            CircleShape,
                                        )
                                    } else {
                                        Modifier
                                    },
                                )
                                // Seçili renge tekrar basmak işareti kaldırıyor:
                                // "Kaldır" düğmesini aramak gerekmiyor.
                                .clickable {
                                    if (color == current) onRemove() else onPick(color, keepContext)
                                },
                        )
                    }
                }

                if (request.sentence.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = keepContext, onCheckedChange = { keepContext = it })
                        Text(
                            text = request.sentence,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Kapat") } },
        dismissButton = {
            if (current != null) {
                TextButton(onClick = onRemove) {
                    Text("Kaldır", color = MaterialTheme.colorScheme.error)
                }
            }
        },
    )
}

// --- Metin yardımcıları (saf fonksiyonlar) ---

private fun isWordChar(c: Char): Boolean = c.isLetter() || c == '\'' || c == '-' || c == '’'

/** [offset] konumundaki kelimenin [start, end) sınırları; kelime değilse null. */
private fun wordBoundsAt(text: String, offset: Int): Pair<Int, Int>? {
    if (text.isEmpty()) return null
    var index = offset.coerceIn(0, text.length - 1)
    if (!isWordChar(text[index])) {
        // Kelimenin hemen sonrasına dokunulmuş olabilir.
        if (index > 0 && isWordChar(text[index - 1])) index -= 1 else return null
    }
    var start = index
    while (start > 0 && isWordChar(text[start - 1])) start--
    var end = index + 1
    while (end < text.length && isWordChar(text[end])) end++
    return trimBounds(text, start, end)
}

/**
 * Kelimeye yapışan tırnak/tireyi sınırların dışında bırakır. Yalnızca uzunluk
 * kontrolünde kırpmak yetmiyordu: `'quiet'` içindeki kelime `quiet'` olarak
 * kaydedilip boyama eşleşmesini de bozuyordu.
 */
private fun trimBounds(text: String, startIn: Int, endIn: Int): Pair<Int, Int>? {
    var start = startIn
    var end = endIn
    while (start < end && !text[start].isLetter()) start++
    while (end > start && !text[end - 1].isLetter()) end--
    // Tek harfli kelimeler de kelimedir: "a" ve "I" hem kendi başlarına
    // işaretlenebilmeli hem de bir seçimin başında ya da sonunda kalınca
    // seçime girmeli. Eskiden iki harften kısası yok sayılıyordu ve
    // "I owe you a favor" seçimi baştaki "I"yı dışarıda bırakıyordu.
    return if (end <= start) null else start to end
}

/** Paragraf içinde her kelimeyi dolaşır. */
private inline fun forEachWord(text: String, action: (start: Int, end: Int) -> Unit) {
    var i = 0
    while (i < text.length) {
        if (isWordChar(text[i])) {
            val start = i
            while (i < text.length && isWordChar(text[i])) i++
            action(start, i)
        } else {
            i++
        }
    }
}

/**
 * Kelimenin bağlamı.
 *
 * Cümle kısaysa (en fazla [MAX_SENTENCE_WORDS] kelime) cümlenin tamamı;
 * uzunsa kelimenin çevresinden [WINDOW_WORDS] kelimelik bir pencere alınır.
 * Uzun cümlelerin tamamını saklamak kartı okunmaz hâle getiriyordu.
 */
private fun contextAround(text: String, wordStart: Int, wordEnd: Int): String {
    val enders = charArrayOf('.', '!', '?')
    var start = wordStart
    while (start > 0 && text[start - 1] !in enders) start--
    var end = wordEnd
    while (end < text.length && text[end] !in enders) end++
    if (end < text.length) end++
    val sentence = text.substring(start.coerceAtMost(end), end).trim()
    if (sentence.split(' ').count { it.isNotBlank() } <= MAX_SENTENCE_WORDS) return sentence

    // Uzun cümle: kelimenin önünden ve arkasından birkaç kelime.
    val before = text.substring(start, wordStart).split(' ').filter { it.isNotBlank() }
    val after = text.substring(wordEnd, end).split(' ').filter { it.isNotBlank() }
    val word = text.substring(wordStart, wordEnd)
    val left = before.takeLast(WINDOW_WORDS)
    val right = after.take(WINDOW_WORDS)
    return buildString {
        if (before.size > WINDOW_WORDS) append("… ")
        if (left.isNotEmpty()) append(left.joinToString(" ")).append(' ')
        append(word)
        if (right.isNotEmpty()) append(' ').append(right.joinToString(" "))
        if (after.size > WINDOW_WORDS) append(" …")
    }.trim()
}

private const val MAX_SENTENCE_WORDS = 10
private const val WINDOW_WORDS = 5

/** Composable olmayan yerlerden kullanılabilen renk eşlemesi. */
private fun paintOf(color: HighlightColor): Color = when (color) {
    HighlightColor.YELLOW -> Color(0xFFFFE082)
    HighlightColor.BLUE -> Color(0xFF90CAF9)
    HighlightColor.GREEN -> Color(0xFFA5D6A7)
    HighlightColor.RED -> Color(0xFFEF9A9A)
}

/** Düz metin ve içindeki eğik yazı aralıkları. */
private data class ItalicText(val text: String, val spans: List<IntRange>)

/**
 * `<i>…</i>` etiketlerini metinden çıkarır, yerlerini aralık olarak verir.
 *
 * Altyazıda eğik yazı "bu ses sahnede değil" demek: dış ses, telefondaki
 * karşı taraf, radyo. Okurken bunu görmek gerekiyor ama etiketin kendisini
 * görmek gerekmiyor. Kapanışı olmayan `<i>` satırın sonuna kadar sayılıyor;
 * altyazılarda kapanış unutulması sık.
 *
 * Kitaplarda etiket geçmiyor: ilk `<` yoksa metin olduğu gibi dönüyor.
 */
private fun extractItalics(raw: String): ItalicText {
    if (!raw.contains('<')) return ItalicText(raw, emptyList())
    val out = StringBuilder(raw.length)
    val spans = mutableListOf<IntRange>()
    var open = -1
    var index = 0
    while (index < raw.length) {
        when {
            raw.startsWith("<i>", index, ignoreCase = true) -> {
                if (open < 0) open = out.length
                index += 3
            }

            raw.startsWith("</i>", index, ignoreCase = true) -> {
                if (open in 0 until out.length) spans += open until out.length
                open = -1
                index += 4
            }

            else -> {
                out.append(raw[index])
                index++
            }
        }
    }
    if (open in 0 until out.length) spans += open until out.length
    return ItalicText(out.toString(), spans)
}
