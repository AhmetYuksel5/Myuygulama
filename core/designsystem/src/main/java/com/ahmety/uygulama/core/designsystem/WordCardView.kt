package com.ahmety.uygulama.core.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahmety.uygulama.core.model.Collocation
import com.ahmety.uygulama.core.model.VocabWord

/**
 * Kartın yüzünde yalnızca kelime durur; dokununca anlam, tanım, örnekler ve
 * ilgili kelimeler açılır. Başlık/etiket koymuyoruz — kullanıcı sade bir yüz
 * istedi; bloklar biçimleriyle ayrışıyor.
 */
@Composable
fun WordCard(
    word: VocabWord,
    tint: Color,
    /** Kaydırılırken verilecek kararın adı ve rengi; duruyorken null. */
    decision: Pair<String, Color>? = null,
    /** Kararın ne kadar yaklaştığı (0..1); yazının koyuluğunu veriyor. */
    decisionStrength: Float = 0f,
    fontScale: Int = 100,
    image: ImageBitmap? = null,
    imaging: Boolean = false,
    onGenerateImage: (() -> Unit)? = null,
    interactive: Boolean = true,
    enriching: Boolean = false,
    onEnrich: (() -> Unit)? = null,
    onAsk: (() -> Unit)? = null,
    /** Örnekleri çoğaltır; her basışta üç örnek daha geliyor. */
    onMoreExamples: (() -> Unit)? = null,
    revealed: Boolean = false,
    onToggleReveal: () -> Unit = {},
    onLongPress: () -> Unit = {},
    scrollState: ScrollState? = null,
) {

    // Cümle işaretlerinde bağlam çoğu zaman cümlenin kendisi oluyor;
    // altyazıdan gelen replikte ikisi harfi harfine aynı. Aynıysa
    // göstermiyoruz: kart aynı satırı iki kez yazmasın.
    val context = remember(word.word, word.context) {
        fun sade(value: String) = value.trim()
            .trim('"', '\u201C', '\u201D', '\u00AB', '\u00BB')
            .trim()
            .lowercase()
        if (sade(word.context) == sade(word.word)) "" else word.context
    }

    // Punto ölçeği yalnız yazıya uygulanıyor: yoğunluğun kendisine değil
    // yazı ölçeğine dokunuyoruz, böylece kenar boşlukları ve kartın kendisi
    // yerinde kalıyor, yalnız harfler büyüyor.
    val density = LocalDensity.current
    val scaled = remember(density, fontScale) {
        Density(density.density, density.fontScale * fontScale / 100f)
    }

    CompositionLocalProvider(LocalDensity provides scaled) {
    Card(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        // Gölgesiz kart, zeminden bir ton koyu bir dikdörtgen olarak
        // duruyordu. Kaldırılabilir bir şey gibi görünmesi gerekiyor.
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    // Kart artık örnekler, aile, ilgili kelimeler, karıştırma
                    // ve eşdizim taşıyor; sığmayan kısım sessizce kırpılıyordu.
                    .then(
                        if (scrollState != null) {
                            Modifier.verticalScroll(scrollState)
                        } else {
                            Modifier
                        },
                    )
                    .then(
                        if (interactive) {
                            // Uzun basış da burada: dış katmandaki bir
                            // algılayıcı dokunuşu göremiyor, çünkü bu
                            // clickable onu önce tüketiyor.
                            Modifier.combinedClickable(
                                onClick = onToggleReveal,
                                onLongClick = onLongPress,
                            )
                        } else {
                            Modifier
                        },
                    )
                    // Dipteki düğme metnin üstünde duruyor; son satırlar
                    // altında kalmasın diye ona yer ayrılıyor.
                    .padding(
                        start = 20.dp,
                        end = 20.dp,
                        top = 18.dp,
                        bottom = if (revealed) 64.dp else 18.dp,
                    ),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Kapalıyken kelime kartın tam ortasında dursun.
                if (!revealed) Spacer(Modifier.weight(1f))

                // Kitaptan seçilen öbekler uzun olabiliyor; tek kelimelik
                // kartın puntosuyla ekrana sığmıyorlar. Tek kelime hiçbir
                // zaman ortadan bölünmüyor: sığana kadar küçülüyor.
                CardTitle(
                    text = word.word,
                    startSize = when {
                        word.word.length > 44 -> if (revealed) 18 else 22
                        word.word.length > 22 -> if (revealed) 24 else 30
                        else -> if (revealed) 32 else 44
                    },
                    passage = word.isPassage,
                )

                if (!revealed) {
                    // Kitaptan gelen kelimede bağlam cümlesi kapalıyken de
                    // görünsün: kelimeyi zaten o cümlede görmüştün.
                    if (context.isNotBlank()) {
                        Spacer(Modifier.height(18.dp))
                        BookQuote(
                            text = context,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Arapçada harekesiz yazım okunuşu vermiyor; okunuş künyesi
                // kelimenin hemen altında, kapalıyken de görünüyor.
                if (word.reading.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = word.reading,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                    )
                }

                // Hatırlatıcı görsel. Görsel bellek sözlük tanımından daha
                // iyi tutuyor; kelimeyi bir sahneye bağlıyor.
                if (revealed && onGenerateImage != null) {
                    Spacer(Modifier.height(12.dp))
                    image?.let { bitmap ->
                        Image(
                            bitmap = bitmap,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth(0.72f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(16.dp)),
                        )
                    }
                    if (imaging) {
                        Spacer(Modifier.height(8.dp))
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    } else {
                        TextButton(onClick = onGenerateImage) {
                            Text(if (image == null) "Görsel üret" else "Görseli yenile")
                        }
                    }
                }

                if (revealed && word.isPassage) {
                    // Kırmızı işaret bir cümle: sıralama kelimeninkinden
                    // başka. Önce cümlenin geçtiği yer, sonra aynı şeyin
                    // kolay İngilizcesi, çizginin altında Türkçesi, en sonda
                    // neyin zorlaştırdığı ve içindeki kalıplar.
                    if (context.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = context,
                            style = MaterialTheme.typography.bodySmall,
                            fontStyle = FontStyle.Italic,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }

                    if (word.definition.isNotBlank()) {
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = word.definition,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                        )
                    }

                    if (word.meaning.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = word.meaning,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                        )
                    }

                    if (word.examples.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        word.examples.forEachIndexed { index, note ->
                            NumberedLine(number = index + 1, text = note)
                        }
                    }

                    // Deyim ve öbek fiiller: cümleyi anlamanı asıl bunlar
                    // engelliyor, o yüzden küçük gri yazı değil.
                    if (word.related.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        word.related.forEach { line ->
                            RightToLeftIfArabic(line) {
                                Text(
                                    text = line,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp),
                                )
                            }
                        }
                    }

                    SavedAnswers(word.answers)
                } else if (revealed) {
                    Spacer(Modifier.height(10.dp))

                    if (word.meaning.isNotBlank()) {
                        Text(
                            text = word.meaning,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                        )
                    }

                    if (word.definition.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = word.definition,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (context.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        BookQuote(
                            text = context,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    if (word.examples.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        word.examples.forEachIndexed { index, example ->
                            NumberedLine(
                                number = index + 1,
                                text = example,
                            )
                        }
                        // Örnekler listenin dibinde çoğaltılıyor: üç örnek
                        // bir kelimeyi anlamaya çoğu zaman yetiyor ama
                        // yetmediğinde menüyü açmak gerekiyordu.
                        if (onMoreExamples != null) {
                            TextButton(
                                enabled = !enriching,
                                onClick = onMoreExamples,
                            ) {
                                Text(
                                    text = if (enriching) "Getiriliyor…" else "+ örnek",
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        }
                    }

                    if (word.root.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        LabeledBlock("Kök", word.root)
                    }

                    if (word.family.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        // Kökendaşlar arasında yön oku yanlış olurdu: biri
                        // ötekinden türemiyor, hepsi aynı kökten geliyor.
                        LabeledBlock("Aile", word.family.joinToString(" · "))
                    }

                    if (word.synonyms.isNotEmpty() ||
                        word.antonyms.isNotEmpty() ||
                        word.related.isNotEmpty()
                    ) {
                        Spacer(Modifier.height(8.dp))
                        WordChips(
                            synonyms = word.synonyms,
                            antonyms = word.antonyms,
                            related = word.related,
                        )
                    }

                    if (word.collocations.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        word.collocations.forEach { group ->
                            CollocationRow(group)
                        }
                    }

                    SavedAnswers(word.answers)

                    // Karıştırma en altta ve çizginin altında: kelimenin
                    // kendisiyle ilgili değil, ona benzeyen başka
                    // kelimelerle ilgili. Karışmasın diye ayırıyoruz.
                    if (word.confusions.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Karıştırma",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(4.dp))
                        word.confusions.forEach { line ->
                            RightToLeftIfArabic(line) {
                                Text(
                                    text = line,
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Start,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 6.dp),
                                )
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(tint, RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center,
            ) {
                // Parmak kalkmadan ne olacağını söylüyor. Eşiğe yaklaştıkça
                // koyulaşıyor; yarıdan sonra tam görünür oluyor.
                decision?.let { (label, color) ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = color.copy(
                            alpha = (decisionStrength * 1.8f).coerceIn(0f, 1f),
                        ),
                        letterSpacing = 2.sp,
                    )
                }
            }

            // Kartın dibindeki düğme: bilgi yoksa getiriyor, varsa soru
            // sormaya açıyor. Metnin arasında değil dipte, çünkü kartın
            // içeriği her kelimede farklı uzunlukta bitiyor.
            val empty = word.meaning.isBlank() && word.definition.isBlank()
            val bottomAction: (() -> Unit)? = when {
                empty -> onEnrich
                else -> onAsk
            }
            if (bottomAction != null && revealed) {
                // Dolu düğme: kartın en çok basılan yeri renkli bir yazı
                // olarak duruyordu, basılabilir olduğu belli değildi.
                FilledTonalButton(
                    enabled = !enriching,
                    onClick = bottomAction,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp),
                ) {
                    Text(
                        when {
                            enriching -> "Getiriliyor…"
                            empty -> "Anlamını getir"
                            else -> "Soru sor"
                        },
                    )
                }
            }
        }
    }
    }
}

/**
 * Sorup kaydettiğin yanıtlar.
 *
 * Senin notların; modelin ürettiği bilgiden ayrı dursun diye çizginin
 * altında. Hem kelime hem cümle kartında aynı yerde.
 */
@Composable
private fun SavedAnswers(answers: List<String>) {
    if (answers.isEmpty()) return
    Spacer(Modifier.height(14.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Spacer(Modifier.height(8.dp))
    answers.forEach { note ->
        Text(
            text = note,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Start,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
        )
    }
}

/**
 * Kartın başlığındaki kelime ya da cümle.
 *
 * Tek kelime asla ortadan bölünmemeli: Arapça "الإناث" gibi bir kelime
 * satır sonuna denk gelince ikiye ayrılıyor ve okunmaz hâle geliyordu.
 * Sığmadığı sürece punto küçülüyor; alt sınıra kadar. Cümlelerde bölme
 * doğal, orada satırlara izin veriliyor.
 */
@Composable
private fun CardTitle(text: String, startSize: Int, passage: Boolean) {
    var size by remember(text, startSize) { mutableStateOf(startSize) }

    Text(
        text = text,
        style = MaterialTheme.typography.headlineLarge.copy(fontSize = size.sp),
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        softWrap = passage,
        maxLines = if (passage) 4 else 1,
        overflow = TextOverflow.Visible,
        onTextLayout = { layout ->
            val tooWide = layout.didOverflowWidth || layout.lineCount > (if (passage) 4 else 1)
            if (tooWide && size > MIN_TITLE_SIZE) size -= 2
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Başlık bundan küçülmüyor; okunaklılığın sınırı. */
private const val MIN_TITLE_SIZE = 16

/** Kitaptan alınan cümle: alıntı olduğu belli olsun diye tırnak içinde ve italik. */
@Composable
private fun BookQuote(text: String, color: Color) {
    Text(
        text = "\u201C${text.trim().trim('\u201C', '\u201D', '"')}\u201D",
        style = MaterialTheme.typography.bodyLarge,
        fontStyle = FontStyle.Italic,
        textAlign = TextAlign.Center,
        color = color,
    )
}

/** Sola yaslı, "1. 2. 3." diye numaralanmış örnek cümle. */
@Composable
private fun NumberedLine(number: Int, text: String) {
    // Arapça örnekte numara da sağda olmalı: satırın başı sağ taraf.
    // Yazının yönünü Compose kendi çözüyor ama satırın düzenini biz
    // veriyoruz, o yüzden yönü burada çeviriyoruz.
    RightToLeftIfArabic(text) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
        ) {
            Text(
                text = "$number.",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(24.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Start,
            )
        }
    }
}

/** Metin Arapçaysa içeriği sağdan sola diziyor. */
@Composable
private fun RightToLeftIfArabic(text: String, content: @Composable () -> Unit) {
    if (text.any { it in '\u0600'..'\u06FF' }) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            content()
        }
    } else {
        content()
    }
}

/** Eş/yakın anlamlı kelimeler mavi rozet içinde. */
/**
 * Eş anlamlılar mavi, zıt anlamlılar kırmızı, aynı anlam alanından
 * kelimeler nötr rozette. Renk ayrımı şart: aynı kutuda aynı renkte
 * durunca zıt anlamlı, eş anlamlı gibi ezberleniyor.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordChips(
    synonyms: List<String>,
    antonyms: List<String>,
    related: List<String>,
) {
    val neutral = MaterialTheme.colorScheme.surfaceVariant
    val onNeutral = MaterialTheme.colorScheme.onSurfaceVariant
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        synonyms.forEach { Chip(it, CHIP_BLUE, Color.White) }
        antonyms.forEach { Chip(cleanOpposite(it), CHIP_RED, Color.White) }
        related.forEach { Chip(it, neutral, onNeutral) }
    }
}

@Composable
private fun Chip(text: String, background: Color, content: Color) {
    Box(
        modifier = Modifier
            .background(background, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = content,
        )
    }
}

/** Eski kayıtlarda zıt anlamlılar "scarce (zıt)" diye işaretliydi; renk artık söylüyor. */
private fun cleanOpposite(word: String): String =
    word.replace("(zıt)", "", ignoreCase = true).trim()

/** Açık ve koyu temada da beyaz yazıyı taşıyan bir mavi. */
/**
 * Dört kaydırma kararının rengi. Kartın üstündeki yazının ve zeminin
 * rengi aynı yerden geliyor ki ikisi birbiriyle çelişmesin.
 */
private val DECISION_LEARNED = Color(0xFF1565C0)
private val DECISION_STUDIED = Color(0xFF2E7D32)
private val DECISION_LATER = Color(0xFFE65100)
private val DECISION_SKIP = Color(0xFF616161)

private val CHIP_BLUE = Color(0xFF1565C0)

/** Zıt anlamlılar için; beyaz yazıyı her iki temada da taşıyor. */
private val CHIP_RED = Color(0xFFB3261E)

/**
 * Bir kullanım kalıbı: solda kalıbın adı, sağında o kalıptaki kelimeler.
 * Oxford eşdizim sözlüğündeki gibi — "make · take · reach a decision".
 */
@Composable
private fun CollocationRow(group: Collocation) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
    ) {
        Text(
            text = group.pattern,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(58.dp),
        )
        Text(
            text = group.words.joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Start,
        )
    }
}

/** "kalıp: kelime, kelime" satırlarını çözer; bozuk satırları atlar. */
fun parseCollocations(text: String): List<Collocation> = text.lines()
    .mapNotNull { line ->
        val pattern = line.substringBefore(':', "").trim()
        val words = line.substringAfter(':', "")
            .split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (pattern.isBlank() || words.isEmpty()) null else Collocation(pattern, words)
    }

/**
 * Etiketli tek satır: solda ne olduğu, sağında içeriği. Eşdizim satırlarıyla
 * aynı hizada duruyor ki kart tek bir düzen gibi okunsun.
 */
@Composable
private fun LabeledBlock(label: String, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(74.dp),
        )
        if (text.isNotBlank()) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Start,
            )
        }
    }
}

