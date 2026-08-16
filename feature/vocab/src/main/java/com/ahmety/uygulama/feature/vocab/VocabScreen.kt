package com.ahmety.uygulama.feature.vocab

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Kelime çalışması",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.mode == VocabMode.ALL,
                onClick = { viewModel.setMode(VocabMode.ALL) },
                label = { Text("Tümü") },
            )
            FilterChip(
                selected = state.mode == VocabMode.LEARNING,
                onClick = { viewModel.setMode(VocabMode.LEARNING) },
                label = { Text("Bilmediklerim (${state.learningCount})") },
            )
        }

        Text(
            text = "Biliyorum ${state.knownCount} · Bilmiyorum ${state.learningCount}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
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
                    // Altta bir sonraki kartın ucu görünsün (deste hissi).
                    state.deck.getOrNull(1)?.let { next ->
                        WordCardStatic(next, offsetScale = 0.96f)
                    }
                    val top = state.deck.first()
                    SwipeableCard(
                        key = top.word,
                        word = top,
                        onKnown = { viewModel.markKnown(top) },
                        onLearning = { viewModel.markLearning(top) },
                    )
                }
            }
        }

        Text(
            text = "← biliyorum        bilmiyorum →",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun EmptyDeck(mode: VocabMode) {
    Text(
        text = if (mode == VocabMode.LEARNING) {
            "Çalışılacak kelime yok. \"Tümü\"nde bilmediklerini işaretledikçe burası dolar."
        } else {
            "Tebrikler, destedeki tüm kelimeleri elediniz. \"Bilmediklerim\" ile " +
                "çalışmaya devam edebilirsin."
        },
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(24.dp),
    )
}

/**
 * Sürüklenebilir kart. Parmakla yatayda taşınır; eşik geçilince yönüne göre
 * uçar ve karar (biliyorum/bilmiyorum) verilir. Renk, sürükleme yönüne göre
 * yeşil (biliyorum) veya turuncuya (bilmiyorum) kayar.
 */
@Composable
private fun SwipeableCard(
    key: String,
    word: VocabWord,
    onKnown: () -> Unit,
    onLearning: () -> Unit,
) {
    var offsetX by remember(key) { mutableFloatStateOf(0f) }
    var dismissed by remember(key) { mutableStateOf(false) }
    var flyTo by remember(key) { mutableFloatStateOf(0f) }

    val animatedOffset by animateFloatAsState(
        targetValue = if (dismissed) flyTo else offsetX,
        animationSpec = tween(durationMillis = if (dismissed) 200 else 0),
        label = "cardOffset",
        finishedListener = {
            if (dismissed) {
                if (flyTo < 0) onKnown() else onLearning()
            }
        },
    )

    val threshold = 320f
    val progress = (offsetX / threshold).coerceIn(-1f, 1f)
    val tint = when {
        progress < -0.1f -> Color(0xFF2E7D32).copy(alpha = abs(progress) * 0.28f) // yeşil
        progress > 0.1f -> Color(0xFFE65100).copy(alpha = progress * 0.28f)         // turuncu
        else -> Color.Transparent
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(340.dp)
            .graphicsLayer {
                translationX = animatedOffset
                rotationZ = animatedOffset / 40f
            }
            .pointerInput(key) {
                detectDragGestures(
                    onDragEnd = {
                        when {
                            offsetX < -threshold -> {
                                flyTo = -1600f
                                dismissed = true
                            }
                            offsetX > threshold -> {
                                flyTo = 1600f
                                dismissed = true
                            }
                            else -> offsetX = 0f // eşik geçilmedi, geri yerine
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount.x
                    },
                )
            },
    ) {
        WordCard(word = word, tint = tint)
    }
}

@Composable
private fun WordCardStatic(word: VocabWord, offsetScale: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(340.dp)
            .graphicsLayer {
                scaleX = offsetScale
                scaleY = offsetScale
            },
    ) {
        WordCard(word = word, tint = Color.Transparent)
    }
}

@Composable
private fun WordCard(word: VocabWord, tint: Color) {
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
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = word.word,
                    style = MaterialTheme.typography.headlineLarge.copy(fontSize = 40.sp),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = word.meaning,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = word.example,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Sürükleme yönü ipucu rengi kartın üstüne biner.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(tint, RoundedCornerShape(24.dp)),
            )
        }
    }
}
