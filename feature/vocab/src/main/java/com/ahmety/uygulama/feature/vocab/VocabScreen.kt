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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahmety.uygulama.core.model.VocabWord
import kotlin.math.abs

@Composable
fun VocabRoute(
    modifier: Modifier = Modifier,
    viewModel: VocabViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val threshold by viewModel.swipeThreshold.collectAsStateWithLifecycle()
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
                    enabled = threshold > 40,
                    onClick = { viewModel.setSwipeThreshold(threshold - 20) },
                ) { Text("−") }
                Text("$threshold", style = MaterialTheme.typography.bodyMedium)
                TextButton(
                    enabled = threshold < 400,
                    onClick = { viewModel.setSwipeThreshold(threshold + 20) },
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
                        threshold = threshold.toFloat(),
                        onKnown = { viewModel.markKnown(top) },
                        onLearning = { viewModel.markLearning(top) },
                        onUnsure = { viewModel.markUnsure(top) },
                    )
                }
            }
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
            VocabMode.ALL -> "Tebrikler, destedeki tüm kelimeleri elediniz."
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
        finishedListener = { if (dismissed) decision?.invoke() },
    )
    val animatedY by animateFloatAsState(
        targetValue = if (dismissed) flyToY else offsetY,
        animationSpec = tween(durationMillis = if (dismissed) 200 else 0),
        label = "cardOffsetY",
    )

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
        WordCard(word = word, tint = tint)
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
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
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
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(bottom = 10.dp),
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
