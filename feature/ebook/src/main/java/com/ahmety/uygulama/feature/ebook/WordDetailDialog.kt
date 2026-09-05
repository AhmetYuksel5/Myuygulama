package com.ahmety.uygulama.feature.ebook

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ahmety.uygulama.core.ai.WordInfo
import com.ahmety.uygulama.core.designsystem.WordCard
import com.ahmety.uygulama.core.model.VocabWord

/** Okurken açılan kelime kartının durumu. */
data class WordDetail(
    val word: String,
    /** Kelimenin geçtiği cümle; örnek çoğaltırken aynı bağlam gidiyor. */
    val context: String = "",
    val info: WordInfo? = null,
    val busy: Boolean = false,
    val error: String = "",
)

/**
 * Kelime kartı, okurken.
 *
 * Kartın kendisi destedekiyle **aynı** bileşen: ortak katmandaki
 * [WordCard]. Buraya ayrı bir kart yazmak, birini değiştirince ötekinin
 * geride kalması demekti — üstelik zaten beğenilen bir kart varken.
 *
 * Deste ekranına gitmek yerine burada açılıyor: gitmek okumayı bölüyor ve
 * kelime henüz işaretlenmemişse destede kart da yok.
 */
@Composable
fun WordDetailDialog(
    detail: WordDetail,
    onMoreExamples: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 24.dp),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                val info = detail.info
                if (info == null) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        if (detail.error.isBlank()) {
                            CircularProgressIndicator()
                            Text("Kelime kartı hazırlanıyor…")
                        } else {
                            Text(
                                text = detail.error,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                } else {
                    WordCard(
                        word = info.toVocabWord(detail.context),
                        tint = Color.Transparent,
                        interactive = false,
                        revealed = true,
                        enriching = detail.busy,
                        onMoreExamples = onMoreExamples,
                        // Aynı pencere her kelime için yeniden kullanılıyor;
                        // konum kelimeye bağlanmazsa yeni kart öncekinin
                        // bıraktığı yerden açılıyor.
                        scrollState = rememberSaveable(
                            detail.word,
                            saver = ScrollState.Saver,
                        ) { ScrollState(0) },
                    )
                }
            }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End),
            ) { Text("Kapat") }
        }
    }
}

/**
 * Kart, destedeki kelime biçimini bekliyor.
 *
 * Okurken kelime henüz destede olmayabiliyor; kartı göstermek için
 * kaydetmeyi şart koşmak yanlış olurdu, o yüzden gelen bilgi geçici bir
 * kelimeye çevriliyor.
 */
private fun WordInfo.toVocabWord(context: String) = VocabWord(
    word = word,
    meaning = meaning,
    definition = definition,
    reading = reading,
    examples = examples,
    related = related,
    synonyms = synonyms,
    antonyms = antonyms,
    root = root,
    family = family,
    confusions = confusions,
    collocations = collocations,
    answers = answers,
    context = context,
)
