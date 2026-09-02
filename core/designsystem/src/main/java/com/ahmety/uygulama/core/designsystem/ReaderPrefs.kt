package com.ahmety.uygulama.core.designsystem

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Okuma zemini.
 *
 * Gece zemininin metni bilerek arayüzünkinden sönük: siyah üstüne beyaz
 * uzun okumada yoruyor, astigmatı olanlarda daha da beter. On birim
 * civarı bir karşıtlık düz yazı için doğru yer.
 */
enum class ReaderTheme(val label: String, val background: Color, val text: Color) {
    PAPER("Kâğıt", Color(0xFFFAF7F0), Color(0xFF22201C)),
    CREAM("Krem", Color(0xFFF3EADA), Color(0xFF2B2620)),
    NIGHT("Gece", Color(0xFF14161A), Color(0xFFC6C2BB)),
    INK("Mürekkep", Color(0xFF000000), Color(0xFFB9B5AE)),
}

/**
 * Okuma tercihleri. Kitap okuyucusuyla makale okuyucusu aynı ayarları
 * paylaşıyor: iki okuma ekranının farklı davranması için bir sebep yok.
 */
class ReaderPrefs(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var theme: ReaderTheme
        get() = runCatching { ReaderTheme.valueOf(prefs.getString(KEY_THEME, null) ?: "") }
            .getOrDefault(ReaderTheme.PAPER)
        set(value) = prefs.edit().putString(KEY_THEME, value.name).apply()

    var fontSizeSp: Int
        get() = prefs.getInt(KEY_FONT, DEFAULT_FONT).coerceIn(MIN_FONT, MAX_FONT)
        set(value) = prefs.edit().putInt(KEY_FONT, value.coerceIn(MIN_FONT, MAX_FONT)).apply()

    /** Sayfa kenarı. Satırın ne kadar uzayacağını da bu belirliyor. */
    var marginDp: Int
        get() = prefs.getInt(KEY_MARGIN, DEFAULT_MARGIN).coerceIn(MIN_MARGIN, MAX_MARGIN)
        set(value) = prefs.edit().putInt(KEY_MARGIN, value.coerceIn(MIN_MARGIN, MAX_MARGIN)).apply()

    companion object {
        const val MIN_FONT = 14
        const val MAX_FONT = 28
        const val DEFAULT_FONT = 18
        const val MIN_MARGIN = 12
        const val MAX_MARGIN = 48
        const val DEFAULT_MARGIN = 20

        private const val PREFS_NAME = "merkez_okuyucu"
        private const val KEY_THEME = "theme"
        private const val KEY_FONT = "font_sp"
        private const val KEY_MARGIN = "margin_dp"
    }
}

/**
 * Görünüm kutusu: zemin, yazı boyutu, kenar boşluğu.
 *
 * Kaydırıcı yok, kademeli artırma var. Her kademe tasarlanmış bir değer
 * olduğu için ayarı bozmak mümkün değil; artı ve eksi de tek elle
 * ulaşılabiliyor.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReaderDisplayDialog(
    theme: ReaderTheme,
    fontSizeSp: Int,
    marginDp: Int,
    onTheme: (ReaderTheme) -> Unit,
    onFontSize: (Int) -> Unit,
    onMargin: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Görünüm") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ReaderTheme.entries.forEach { option ->
                        FilterChip(
                            selected = option == theme,
                            onClick = { onTheme(option) },
                            label = { Text(option.label) },
                        )
                    }
                }
                Stepper(
                    label = "Yazı boyutu",
                    value = "$fontSizeSp",
                    canDecrease = fontSizeSp > ReaderPrefs.MIN_FONT,
                    canIncrease = fontSizeSp < ReaderPrefs.MAX_FONT,
                    onDecrease = { onFontSize(fontSizeSp - 1) },
                    onIncrease = { onFontSize(fontSizeSp + 1) },
                )
                Stepper(
                    label = "Kenar boşluğu",
                    value = "$marginDp",
                    canDecrease = marginDp > ReaderPrefs.MIN_MARGIN,
                    canIncrease = marginDp < ReaderPrefs.MAX_MARGIN,
                    onDecrease = { onMargin(marginDp - 4) },
                    onIncrease = { onMargin(marginDp + 4) },
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Kapat") } },
    )
}

@Composable
private fun Stepper(
    label: String,
    value: String,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        FilledTonalIconButton(
            enabled = canDecrease,
            onClick = onDecrease,
            modifier = Modifier.size(36.dp),
        ) { Text("−", style = MaterialTheme.typography.titleMedium) }
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 14.dp),
        )
        FilledTonalIconButton(
            enabled = canIncrease,
            onClick = onIncrease,
            modifier = Modifier.size(36.dp),
        ) { Text("+", style = MaterialTheme.typography.titleMedium) }
    }
}
