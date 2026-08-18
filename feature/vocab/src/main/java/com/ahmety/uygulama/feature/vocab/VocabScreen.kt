package com.ahmety.uygulama.feature.vocab

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahmety.uygulama.core.model.VocabWord
import kotlinx.coroutines.delay
import kotlin.math.abs

/** Kelimeyi cihazın tarayıcısında aratır. */
private fun lookUp(context: android.content.Context, word: String) {
    val intent = android.content.Intent(
        android.content.Intent.ACTION_VIEW,
        android.net.Uri.parse("https://www.google.com/search?q=" + android.net.Uri.encode("$word meaning")),
    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

@Composable
fun VocabRoute(
    modifier: Modifier = Modifier,
    viewModel: VocabViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val threshold by viewModel.swipeThreshold.collectAsStateWithLifecycle()
    val enrichingWord by viewModel.enriching.collectAsStateWithLifecycle()
    val aiMessage by viewModel.aiMessage.collectAsStateWithLifecycle()
    val aiReady = remember { viewModel.aiConfigured }
    val density = LocalDensity.current
    var showSettings by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Kelime",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { showSettings = !showSettings }) { Text("Ayar") }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        ) {
            ModeChip("Tümü", state.mode == VocabMode.ALL) { viewModel.setMode(VocabMode.ALL) }
            ModeChip("Bilmediklerim (${state.learningCount})", state.mode == VocabMode.LEARNING) {
                viewModel.setMode(VocabMode.LEARNING)
            }
            ModeChip("Emin değilim (${state.unsureCount})", state.mode == VocabMode.UNSURE) {
                viewModel.setMode(VocabMode.UNSURE)
            }
            ModeChip("Kitaptan (${state.bookCount})", state.mode == VocabMode.BOOK) {
                viewModel.setMode(VocabMode.BOOK)
            }
        }

        if (showSettings) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Fırlatma eşiği",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    enabled = threshold > 20,
                    onClick = { viewModel.setSwipeThreshold(threshold - 5) },
                ) { Text("−") }
                Text("$threshold dp", style = MaterialTheme.typography.bodyMedium)
                TextButton(
                    enabled = threshold < 160,
                    onClick = { viewModel.setSwipeThreshold(threshold + 5) },
                ) { Text("+") }
            }
            Text(
                text = "Küçük değer: az hareketle fırlar.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text(
            text = "Biliyorum ${state.knownCount} · Bilmiyorum ${state.learningCount} · " +
                "Emin değilim ${state.unsureCount}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            when {
                !state.loaded -> Text("Yükleniyor…")
                state.deck.isEmpty() -> EmptyDeck(state.mode)
                else -> {
                    state.deck.getOrNull(1)?.let { next ->
                        WordCardStatic(next)
                    }
                    val top = state.deck.first()
                    SwipeableCard(
                        key = top.word,
                        word = top,
                        threshold = with(density) { threshold.dp.toPx() },
                        enriching = enrichingWord == top.word,
                        onEnrich = if (aiReady) {
                            { viewModel.enrich(top) }
                        } else {
                            null
                        },
                        onKnown = { viewModel.markKnown(top) },
                        onLearning = { viewModel.markLearning(top) },
                        onUnsure = { viewModel.markUnsure(top) },
                    )
                }
            }
        }

        aiMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.clearAiMessage() }
                    .padding(top = 4.dp),
            )
        }

        Text(
            text = "← biliyorum     ↓ emin değilim     bilmiyorum →",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 6.dp),
        )
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label, fontSize = 12.sp) })
}

@Composable
private fun EmptyDeck(mode: VocabMode) {
    Text(
        text = when (mode) {
            VocabMode.LEARNING -> "Çalışılacak kelime yok. \"Tümü\"nde bilmediklerini " +
                "işaretledikçe burası dolar."
            VocabMode.UNSURE -> "Emin olamadığın kelime yok."
            VocabMode.BOOK -> "Kitaptan gelen kelime yok. Kitapta bir kelimeyi mavi " +
                "işaretlersen burada belirir."
            VocabMode.ALL -> "Tebrikler, destedeki tüm kelimeleri eledin."
        },
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(24.dp),
    )
}

/**
 * Sürüklenebilir kart. Sola/sağa fırlatınca biliyorum/bilmiyorum, aşağı
 * fırlatınca "emin olamadım" olur. Eşik ayardan geliyor.
 */
@Composable
private fun SwipeableCard(
    key: String,
    word: VocabWord,
    threshold: Float,
    enriching: Boolean,
    onEnrich: (() -> Unit)?,
    onKnown: () -> Unit,
    onLearning: () -> Unit,
    onUnsure: () -> Unit,
) {
    var offsetX by remember(key) { mutableFloatStateOf(0f) }
    var offsetY by remember(key) { mutableFloatStateOf(0f) }
    var dismissed by remember(key) { mutableStateOf(false) }
    var flyToX by remember(key) { mutableFloatStateOf(0f) }
    var flyToY by remember(key) { mutableFloatStateOf(0f) }
    var decision by remember(key) { mutableStateOf<(() -> Unit)?>(null) }

    val animatedX by animateFloatAsState(
        targetValue = if (dismissed) flyToX else offsetX,
        animationSpec = tween(durationMillis = if (dismissed) 200 else 0),
        label = "cardOffsetX",
    )
    val animatedY by animateFloatAsState(
        targetValue = if (dismissed) flyToY else offsetY,
        animationSpec = tween(durationMillis = if (dismissed) 200 else 0),
        label = "cardOffsetY",
    )

    // Kararı animasyonun bitiş dinleyicisine bağlamak yanlıştı: aşağı
    // fırlatmada yatay hedef 0 kaldığı için o animasyon hiç başlamıyor ve
    // karar hiç uygulanmıyordu. Yönden bağımsız olarak burada uyguluyoruz.
    LaunchedEffect(dismissed) {
        if (dismissed) {
            delay(220)
            decision?.invoke()
        }
    }

    val horizontalProgress = (offsetX / threshold).coerceIn(-1f, 1f)
    val verticalProgress = (offsetY / threshold).coerceIn(-1f, 1f)
    val tint = when {
        offsetY > 0f && abs(offsetY) > abs(offsetX) && verticalProgress > 0.1f ->
            Color(0xFF616161).copy(alpha = verticalProgress * 0.28f)
        horizontalProgress < -0.1f -> Color(0xFF2E7D32).copy(alpha = abs(horizontalProgress) * 0.28f)
        horizontalProgress > 0.1f -> Color(0xFFE65100).copy(alpha = horizontalProgress * 0.28f)
        else -> Color.Transparent
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = animatedX
                translationY = animatedY
                rotationZ = animatedX / 40f
            }
            .pointerInput(key) {
                detectDragGestures(
                    onDragEnd = {
                        val horizontal = abs(offsetX) > abs(offsetY)
                        when {
                            horizontal && offsetX < -threshold -> {
                                flyToX = -1600f; decision = onKnown; dismissed = true
                            }
                            horizontal && offsetX > threshold -> {
                                flyToX = 1600f; decision = onLearning; dismissed = true
                            }
                            !horizontal && offsetY > threshold -> {
                                flyToY = 1800f; decision = onUnsure; dismissed = true
                            }
                            else -> {
                                offsetX = 0f
                                offsetY = 0f
                            }
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount.x
                        offsetY += dragAmount.y
                    },
                )
            },
    ) {
        WordCard(word = word, tint = tint, enriching = enriching, onEnrich = onEnrich)
    }
}

@Composable
private fun WordCardStatic(word: VocabWord) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = 0.96f
                scaleY = 0.96f
            },
    ) {
        WordCard(word = word, tint = Color.Transparent, interactive = false)
    }
}

/**
 * Kartın yüzünde yalnızca kelime durur; dokununca anlam, tanım, örnekler ve
 * ilgili kelimeler açılır. Başlık/etiket koymuyoruz — kullanıcı sade bir yüz
 * istedi; bloklar biçimleriyle ayrışıyor.
 */
@Composable
private fun WordCard(
    word: VocabWord,
    tint: Color,
    interactive: Boolean = true,
    enriching: Boolean = false,
    onEnrich: (() -> Unit)? = null,
) {
    var revealed by remember(word.word) { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (interactive) {
                            Modifier.clickable { revealed = !revealed }
                        } else {
                            Modifier
                        },
                    )
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Kapalıyken kelime kartın ortasına yakın dursun.
                Spacer(Modifier.height(if (revealed) 0.dp else 96.dp))

                Text(
                    text = word.word,
                    style = MaterialTheme.typography.headlineLarge.copy(fontSize = 40.sp),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )

                if (!revealed) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "dokun",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // Kitaptan gelen kelimede bağlam cümlesi kapalıyken de
                    // görünsün: kelimeyi zaten o cümlede görmüştün.
                    if (word.context.isNotBlank()) {
                        Spacer(Modifier.height(18.dp))
                        Text(
                            text = word.context,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Spacer(Modifier.height(16.dp))

                    if (word.meaning.isNotBlank()) {
                        Text(
                            text = word.meaning,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                        )
                    }

                    if (word.definition.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = word.definition,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (word.context.isNotBlank()) {
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = word.context,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                        )
                    }

                    if (word.examples.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        word.examples.forEach { example ->
                            Text(
                                text = example,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }
                    }

                    if (word.related.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = word.related.joinToString(" · "),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }

                    if (word.phrases.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        word.phrases.forEach { phrase ->
                            Text(
                                text = phrase,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 4.dp),
                            )
                        }
                    }

                    // Kitaptan gelip destede karşılığı olmayan kelime: boş kart
                    // göstermek yerine yapay zekâyla doldurmayı öneriyoruz.
                    if (word.meaning.isBlank() && word.definition.isBlank()) {
                        val context = LocalContext.current
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Bu kelime kitaptan geldi, sözlükte karşılığı yok.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (onEnrich != null) {
                                TextButton(enabled = !enriching, onClick = onEnrich) {
                                    Text(if (enriching) "Getiriliyor…" else "Anlamını getir")
                                }
                            }
                            TextButton(onClick = { lookUp(context, word.word) }) {
                                Text("Sözlükte ara")
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(tint, RoundedCornerShape(24.dp)),
            )
        }
    }
}
