@file:OptIn(ExperimentalFoundationApi::class)

package com.ahmety.uygulama.feature.vocab

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahmety.uygulama.core.model.Collocation
import com.ahmety.uygulama.core.model.VocabSource
import com.ahmety.uygulama.core.model.VocabStatus
import com.ahmety.uygulama.core.model.VocabWord
import kotlinx.coroutines.delay
import kotlin.math.abs

@Composable
fun VocabRoute(
    modifier: Modifier = Modifier,
    viewModel: VocabViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val threshold by viewModel.swipeThreshold.collectAsStateWithLifecycle()
    val enrichingWord by viewModel.enriching.collectAsStateWithLifecycle()
    val aiMessage by viewModel.aiMessage.collectAsStateWithLifecycle()
    // remember ile sarmıyoruz: anahtar Ayarlar'dan yeni girildiyse bu ekrana
    // dönüldüğünde düğmenin hemen görünmesi gerekiyor.
    val aiReady = viewModel.aiConfigured
    val density = LocalDensity.current
    var showSettings by remember { mutableStateOf(false) }
    // Kart destesi mi, tam liste mi. Liste "hepsini bir arada gör" için;
    // kart çalışmak için.
    var listView by rememberSaveable { mutableStateOf(false) }
    var menuWord by remember { mutableStateOf<VocabWord?>(null) }
    // Listeden açılan kart. Destedekinden farkı: karar verilmiyor, sadece
    // bakılıyor.
    var cardWord by remember { mutableStateOf<VocabWord?>(null) }
    var editWord by remember { mutableStateOf<VocabWord?>(null) }

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
            // Başlık nerede olduğunu söylüyor; sayılar zaten çiplerde.
            Text(
                text = state.filter.sourceName.ifBlank {
                    state.filter.source?.label ?: "Tüm kelimeler"
                },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            // Kalem süzgeci: kırmızı işaretlediklerin cümle, mavi
            // işaretlediklerin kelime. Tek düğme, üç durum.
            PenToggle(
                pen = state.filter.pen,
                count = when (state.filter.pen) {
                    VocabPen.RED -> state.redCount
                    VocabPen.BLUE -> state.blueCount
                    VocabPen.BOTH -> state.redCount + state.blueCount
                },
                onClick = viewModel::cyclePen,
            )
            TextButton(onClick = { listView = !listView }) {
                Text(if (listView) "Kart" else "Liste")
            }
            TextButton(onClick = { showSettings = !showSettings }) { Text("Ayar") }
        }

        // Kaynak asıl kapsam, bu yüzden üstte: bir kitap seçince alttaki
        // bölmeler ve sayılar o kitabın içinde çalışıyor.
        if (state.bookCount > 0 || state.subtitleCount > 0 || state.selectionCount > 0) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            ) {
                val filter = state.filter
                ModeChip("Her kaynak", filter.source == null && filter.sourceName.isBlank()) {
                    viewModel.setFilter(VocabFilter())
                }
                if (state.bookCount > 0) {
                    ModeChip(
                        label = "Kitaptan (${state.bookCount})",
                        selected = filter.source == VocabSource.BOOK &&
                            filter.sourceName.isBlank(),
                    ) { viewModel.setFilter(VocabFilter(source = VocabSource.BOOK)) }
                }
                if (state.subtitleCount > 0) {
                    ModeChip(
                        label = "Filmden (${state.subtitleCount})",
                        selected = filter.source == VocabSource.SUBTITLE &&
                            filter.sourceName.isBlank(),
                    ) { viewModel.setFilter(VocabFilter(source = VocabSource.SUBTITLE)) }
                }
                if (state.selectionCount > 0) {
                    ModeChip(
                        label = "Seçtiklerim (${state.selectionCount})",
                        selected = filter.source == VocabSource.SELECTION &&
                            filter.sourceName.isBlank(),
                    ) { viewModel.setFilter(VocabFilter(source = VocabSource.SELECTION)) }
                }
                // Tek tek başlıklar: "şu kitaptan" ya da "şu filmden" çalışmak.
                state.sources.forEach { (source, name) ->
                    ModeChip(label = name, selected = filter.sourceName == name) {
                        viewModel.setFilter(VocabFilter(source = source, sourceName = name))
                    }
                }
            }
        }

        // Bölmeler seçili kaynağın içinde; sayılar da ona göre.
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .horizontalScroll(rememberScrollState()),
        ) {
            ModeChip("Tümü (${state.totalCount})", state.mode == VocabMode.ALL) {
                viewModel.setMode(VocabMode.ALL)
            }
            ModeChip("Yeni (${state.newCount})", state.mode == VocabMode.NEW) {
                viewModel.setMode(VocabMode.NEW)
            }
            ModeChip("Tekrar (${state.dueToday})", state.mode == VocabMode.TODAY) {
                viewModel.setMode(VocabMode.TODAY)
            }
            ModeChip(
                label = "Öğrendiklerim (${state.knownCount})",
                selected = state.mode == VocabMode.KNOWN,
            ) { viewModel.setMode(VocabMode.KNOWN) }
            ModeChip(
                label = "Önemsiz (${state.unsureCount})",
                selected = state.mode == VocabMode.IGNORED,
            ) { viewModel.setMode(VocabMode.IGNORED) }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            when {
                !state.loaded -> Text("Yükleniyor…")
                listView -> WordList(
                    rows = state.list,
                    emptyMessage = penEmptyMessage(state) ?: "Bu süzgeçte kelime yok.",
                    onSelect = { cardWord = it },
                    onLongPress = { menuWord = it },
                )
                state.deck.isEmpty() -> EmptyDeck(state)
                else -> {
                    state.deck.getOrNull(1)?.let { next ->
                        WordCardStatic(next)
                    }
                    val top = state.deck.first()
                    SwipeableCard(
                        // Karar sayacı anahtarda: aynı kelime yeniden üste
                        // gelse bile kart sıfırdan kuruluyor.
                        key = "${top.word}#${state.turn}",
                        word = top,
                        threshold = with(density) { threshold.dp.toPx() },
                        enriching = enrichingWord == top.word,
                        onEnrich = if (aiReady) {
                            { viewModel.enrich(top) }
                        } else {
                            null
                        },
                        onKnown = { viewModel.markKnown(top, it) },
                        onLearning = { viewModel.markLearning(top, it) },
                        onIgnore = { viewModel.markIgnored(top, it) },
                        onSkip = { viewModel.skip(top, it) },
                        onLongPress = { menuWord = top },
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
    }

    if (showSettings) {
        VocabSettingsDialog(
            threshold = threshold,
            onThresholdChange = viewModel::setSwipeThreshold,
            onDismiss = { showSettings = false },
        )
    }

    cardWord?.let { word ->
        WordCardDialog(word = word, onDismiss = { cardWord = null })
    }

    menuWord?.let { word ->
        WordMenuDialog(
            word = word,
            aiReady = aiReady,
            onEdit = {
                menuWord = null
                editWord = word
            },
            onRefresh = {
                menuWord = null
                viewModel.refresh(word)
            },
            onMoreExamples = {
                menuWord = null
                viewModel.addMoreExamples(word)
            },
            onDelete = {
                menuWord = null
                viewModel.delete(word)
            },
            onDismiss = { menuWord = null },
        )
    }

    editWord?.let { word ->
        WordEditDialog(
            word = word,
            onSave = {
                editWord = null
                viewModel.saveEdit(it)
            },
            onDismiss = { editWord = null },
        )
    }
}

/**
 * Listeden açılan kart.
 *
 * Destedeki kartın aynısı, ama açık hâlde ve karar vermeden: listeye
 * "şu kelime neydi" diye bakmaya geliyorsun, sağa sola atmaya değil.
 */
@Composable
private fun WordCardDialog(word: VocabWord, onDismiss: () -> Unit) {
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
                WordCard(
                    word = word,
                    tint = Color.Transparent,
                    interactive = false,
                    revealed = true,
                    scrollState = rememberScrollState(),
                )
            }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End),
            ) { Text("Kapat") }
        }
    }
}

/** Kelime kartına uzun basınca ya da listede satıra dokununca çıkan menü. */
@Composable
private fun WordMenuDialog(
    word: VocabWord,
    aiReady: Boolean,
    onEdit: () -> Unit,
    onRefresh: () -> Unit,
    onMoreExamples: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(word.word) },
        text = {
            Column {
                if (confirmDelete) {
                    Text(
                        "Bu kelime silinecek; kitapta ya da filmde bıraktığın " +
                            "işaret de kalkacak. Yeniden işaretlersen geri gelir.",
                    )
                } else {
                    // Listeden gelindiğinde kelimenin ne olduğunu görmek
                    // gerekiyor: menü tek başına "bu neydi?" bırakıyordu.
                    val summary = word.meaning.ifBlank { word.definition }
                    if (summary.isNotBlank()) {
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    MenuRow("Kelimeyi düzenle", onEdit)
                    if (aiReady) {
                        MenuRow("Bilgiyi yenile", onRefresh)
                        MenuRow("Örnek çoğalt", onMoreExamples)
                    }
                    MenuRow("Sil") { confirmDelete = true }
                }
            }
        },
        confirmButton = {
            if (confirmDelete) {
                TextButton(onClick = onDelete) { Text("Sil") }
            } else {
                TextButton(onClick = onDismiss) { Text("Kapat") }
            }
        },
        dismissButton = {
            if (confirmDelete) {
                TextButton(onClick = { confirmDelete = false }) { Text("Vazgeç") }
            }
        },
    )
}

@Composable
private fun MenuRow(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    )
}

/**
 * Kelime bilgisini elle düzenleme.
 *
 * Örnekler ve öbekler satır satır yazılıyor; boş satırlar atılıyor. Yapay
 * zekânın getirdiğini düzeltmek ya da kendi cümleni eklemek için.
 */
@Composable
private fun WordEditDialog(
    word: VocabWord,
    onSave: (VocabWord) -> Unit,
    onDismiss: () -> Unit,
) {
    var meaning by remember(word.word) { mutableStateOf(word.meaning) }
    var definition by remember(word.word) { mutableStateOf(word.definition) }
    var examples by remember(word.word) { mutableStateOf(word.examples.joinToString("\n")) }
    var synonyms by remember(word.word) { mutableStateOf(word.synonyms.joinToString(", ")) }
    var antonyms by remember(word.word) { mutableStateOf(word.antonyms.joinToString(", ")) }
    var root by remember(word.word) { mutableStateOf(word.root) }
    var family by remember(word.word) { mutableStateOf(word.family.joinToString(", ")) }
    var confusions by remember(word.word) { mutableStateOf(word.confusions.joinToString("\n")) }
    var collocations by remember(word.word) {
        mutableStateOf(
            word.collocations.joinToString("\n") { "${it.pattern}: ${it.words.joinToString(", ")}" },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(word.word) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = meaning,
                    onValueChange = { meaning = it },
                    label = { Text("Türkçe karşılık") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = definition,
                    onValueChange = { definition = it },
                    label = { Text("İngilizce tanım") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = examples,
                    onValueChange = { examples = it },
                    label = { Text("Örnekler (her satır bir örnek)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = synonyms,
                    onValueChange = { synonyms = it },
                    label = { Text("Eş anlamlılar (virgülle)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = antonyms,
                    onValueChange = { antonyms = it },
                    label = { Text("Zıt anlamlılar (virgülle)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = root,
                    onValueChange = { root = it },
                    label = { Text("Kök") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = family,
                    onValueChange = { family = it },
                    label = { Text("Kökendaş kelimeler (virgülle)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = confusions,
                    onValueChange = { confusions = it },
                    label = { Text("Karıştırma (her satır \"kelime — fark\")") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = collocations,
                    onValueChange = { collocations = it },
                    label = { Text("Eşdizim (her satır \"kalıp: kelime, kelime\")") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        word.copy(
                            meaning = meaning.trim(),
                            definition = definition.trim(),
                            examples = examples.lines().map { it.trim() }.filter { it.isNotBlank() },
                            synonyms = synonyms.split(',').map { it.trim() }
                                .filter { it.isNotBlank() },
                            antonyms = antonyms.split(',').map { it.trim() }
                                .filter { it.isNotBlank() },
                            root = root.trim(),
                            family = family.split(',').map { it.trim() }.filter { it.isNotBlank() },
                            confusions = confusions.lines().map { it.trim() }
                                .filter { it.isNotBlank() },
                            collocations = parseCollocations(collocations),
                        ),
                    )
                },
            ) { Text("Kaydet") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}

/** Kelime ekranının ayarları. Yeni ayar eklenirse buraya giriyor. */
@Composable
private fun VocabSettingsDialog(
    threshold: Int,
    onThresholdChange: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kelime ayarları") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Fırlatma eşiği",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        enabled = threshold > 20,
                        onClick = { onThresholdChange(threshold - 5) },
                    ) { Text("-") }
                    Text("$threshold dp", style = MaterialTheme.typography.bodyMedium)
                    TextButton(
                        enabled = threshold < 160,
                        onClick = { onThresholdChange(threshold + 5) },
                    ) { Text("+") }
                }
                Text(
                    text = "Küçük değer: az hareketle fırlar.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Kapat") } },
    )
}

/**
 * Kalem süzgeci düğmesi.
 *
 * Üç durumu tek simgeyle anlatıyor: ikiye bölünmüş kare "her ikisi", dolu
 * kırmızı kare yalnız cümleler, dolu mavi kare yalnız kelimeler. Yanındaki
 * sayı o kalemde kaç kelime olduğunu söylüyor — düğmenin ne yaptığı ilk
 * basışta anlaşılsın diye.
 */
@Composable
private fun PenToggle(
    pen: VocabPen,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val divider = MaterialTheme.colorScheme.surface
    // Süzgeç açıkken düğmenin kendisi de dolu görünüyor: "bir şey saklıyorum"
    // sinyalini renk tek başına vermiyordu.
    val background = if (pen == VocabPen.BOTH) {
        Color.Transparent
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    Row(
        modifier = modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(background)
            .clickable(onClick = onClick)
            .semantics { contentDescription = pen.label }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Canvas(modifier = Modifier.size(20.dp)) {
            val radius = CornerRadius(5.dp.toPx(), 5.dp.toPx())
            when (pen) {
                VocabPen.RED -> drawRoundRect(color = CHIP_RED, cornerRadius = radius)
                VocabPen.BLUE -> drawRoundRect(color = CHIP_BLUE, cornerRadius = radius)
                VocabPen.BOTH -> {
                    drawRoundRect(color = CHIP_RED, cornerRadius = radius)
                    clipRect(left = size.width / 2f) {
                        drawRoundRect(color = CHIP_BLUE, cornerRadius = radius)
                    }
                    // İnce ayraç: iki yarım tek renk gibi görünmesin.
                    drawLine(
                        color = divider,
                        start = Offset(size.width / 2f, 0f),
                        end = Offset(size.width / 2f, size.height),
                        strokeWidth = 1.5.dp.toPx(),
                    )
                }
            }
        }
        Text(
            text = "$count",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Kapsamdaki bütün kelimeler, alfabetik.
 *
 * Kart destesi tek tek çalışmak için; bu liste ne olduğunu bir bakışta
 * görmek için. Her satırda kelime solda, geldiği kaynak sağda; soldaki
 * renk şeridi kitapta hangi kalemle işaretlediğini söylüyor.
 */
@Composable
private fun WordList(
    rows: List<VocabListItem>,
    emptyMessage: String,
    onSelect: (VocabWord) -> Unit,
    onLongPress: (VocabWord) -> Unit,
) {
    if (rows.isEmpty()) {
        Text(
            text = emptyMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(vertical = 6.dp),
    ) {
        // Kelime benzersiz: depo aynı kelimenin ikinci kaydını eliyor.
        items(rows, key = { it.word.word }) { item ->
            WordRow(
                item = item,
                onClick = { onSelect(item.word) },
                onLongClick = { onLongPress(item.word) },
            )
        }
    }
}

@Composable
private fun WordRow(
    item: VocabListItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val word = item.word
    val penColor = if (word.isPassage) CHIP_RED else CHIP_BLUE
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            // Kartla aynı sözleşme: dokun bak, uzun bas menü.
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(
            modifier = Modifier
                .height(IntrinsicSize.Min)
                .padding(start = 10.dp, top = 10.dp, end = 12.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Kalem şeridi: kırmızı cümle, mavi kelime. Satır iki satırlık
            // bir cümle olduğunda da boyunca uzuyor, ortada asılı kalmıyor.
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(penColor),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = word.word,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = if (word.isPassage) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val second = word.meaning.ifBlank { word.definition }
                if (second.isNotBlank()) {
                    Text(
                        text = second,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            // Kaynak sabit genişlikte: satırlar alt alta gelince sağ kenar
            // hizalı duruyor, "aynı satırda" gerçekten bir sütun oluyor.
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.width(116.dp),
            ) {
                // Önce nereden geldiği (Filmden / Kitaptan), sonra o filmin
                // ya da kitabın adı, en altta durumu.
                Text(
                    text = word.source.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    textAlign = TextAlign.End,
                )
                if (word.sourceName.isNotBlank()) {
                    Text(
                        text = word.sourceName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End,
                    )
                }
                val status = statusLabel(item.status)
                if (status != null) {
                    Text(
                        text = status,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

private fun statusLabel(status: VocabStatus?): String? = when (status) {
    VocabStatus.KNOWN -> "öğrendim"
    VocabStatus.LEARNING -> "çalışıyorum"
    VocabStatus.IGNORED -> "önemsiz"
    else -> null
}

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label, fontSize = 12.sp) })
}

/**
 * Boşluğun sebebi kalem süzgeciyse onu söyleyen ileti; değilse null.
 */
private fun penEmptyMessage(state: VocabUiState): String? = when (state.filter.pen) {
    VocabPen.RED -> if (state.redCount == 0 && state.blueCount > 0) {
        "Bu kapsamda kırmızı kalemli yok; ${state.blueCount} mavi var. " +
            "Kare simgeye bas."
    } else {
        null
    }

    VocabPen.BLUE -> if (state.blueCount == 0 && state.redCount > 0) {
        "Bu kapsamda mavi kalemli yok; ${state.redCount} kırmızı var. " +
            "Kare simgeye bas."
    } else {
        null
    }

    VocabPen.BOTH -> null
}

@Composable
private fun EmptyDeck(state: VocabUiState) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(24.dp),
    ) {
        Text(
            // Kalem süzgeci açıkken "hiç kelime yok" demek yanıltıcı: kelime
            // var, süzgeç saklıyor. Önce onu söylüyoruz.
            text = penEmptyMessage(state) ?: when (state.mode) {
                VocabMode.TODAY -> "Bugün tekrar edilecek kelime yok."
                VocabMode.NEW -> "Karar verilmemiş kelime kalmadı."
                VocabMode.ALL -> "Burada kelime yok. Kitapta ya da altyazıda " +
                    "işaretledikçe dolar."
                VocabMode.IGNORED -> "Önemsize atılmış kelime yok."
                VocabMode.KNOWN -> "Henüz öğrendiğin kelime yok."
            },
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        if (state.mode == VocabMode.TODAY) {
            Text(
                text = when {
                    state.nextDueInDays != null ->
                        "Sıradaki tekrar ${state.nextDueInDays} gün sonra."
                    state.newCount > 0 ->
                        "Tekrar, çalıştığın kelimelerden oluşuyor. " +
                            "\"Yeni\" bölmesinde ${state.newCount} kelime bekliyor."
                    else -> "Yeni kelime eklemek için kitapta ya da altyazıda işaretle."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/**
 * Sürüklenebilir kart. Yukarı = öğrendim, sol = çalıştım, sağ = şimdilik geç,
 * aşağı = önemsiz. Eşik ayardan geliyor. Uzun basınca kelime menüsü açılıyor.
 */
@Composable
private fun SwipeableCard(
    key: String,
    word: VocabWord,
    threshold: Float,
    enriching: Boolean,
    onEnrich: (() -> Unit)?,
    onKnown: (Boolean) -> Unit,
    onLearning: (Boolean) -> Unit,
    onIgnore: (Boolean) -> Unit,
    onSkip: (Boolean) -> Unit,
    onLongPress: () -> Unit,
) {
    // Anlamı açtıysan hatırlayamadın demektir; karar bunu da taşıyor,
    // program kademeyi ilerletmiyor.
    var revealed by remember(key) { mutableStateOf(false) }
    var offsetX by remember(key) { mutableFloatStateOf(0f) }
    var offsetY by remember(key) { mutableFloatStateOf(0f) }
    var dismissed by remember(key) { mutableStateOf(false) }
    var flyToX by remember(key) { mutableFloatStateOf(0f) }
    var flyToY by remember(key) { mutableFloatStateOf(0f) }
    var decision by remember(key) { mutableStateOf<(() -> Unit)?>(null) }
    val scrollState = rememberScrollState()

    /** Dikey fırlatma kararını uygular; hem jestten hem kaydırma artığından çağrılıyor. */
    fun decideVertical() {
        when {
            offsetY < -threshold -> {
                flyToY = -1800f
                decision = { onKnown(revealed) }
                dismissed = true
            }

            offsetY > threshold -> {
                flyToY = 1800f
                decision = { onIgnore(revealed) }
                dismissed = true
            }

            else -> offsetY = 0f
        }
    }

    /**
     * Kart açıkken içerik kaydırılabiliyor; ama "öğrendim" ve "önemsiz"
     * jestleri de dikey. Kaydırmanın **tüketemediği** dikey hareketi buradan
     * alıyoruz: metin sonuna gelindiğinde ya da zaten sığıyorsa kart hareket
     * ediyor. Böylece iki davranış da kayboluyor değil, sırayla çalışıyor.
     */
    val verticalGestures = remember(key, threshold) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (dismissed || available.y == 0f) return Offset.Zero
                offsetY += available.y
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!dismissed && offsetY != 0f) decideVertical()
                return Velocity.Zero
            }
        }
    }

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
        offsetY < 0f && abs(offsetY) > abs(offsetX) && verticalProgress < -0.1f ->
            Color(0xFF1565C0).copy(alpha = abs(verticalProgress) * 0.20f)
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
            .nestedScroll(verticalGestures)
            .pointerInput(key) {
                detectDragGestures(
                    onDragEnd = {
                        when {
                            offsetX < -threshold -> {
                                flyToX = -1600f
                                decision = { onLearning(revealed) }
                                dismissed = true
                            }

                            offsetX > threshold -> {
                                flyToX = 1600f
                                decision = { onSkip(revealed) }
                                dismissed = true
                            }

                            else -> offsetX = 0f
                        }
                    },
                    onDrag = { change, dragAmount ->
                        // Dikeyi tüketmiyoruz: kart açıkken metnin kaydırılması
                        // ve "öğrendim/önemsiz" jestleri o katmandan geçiyor.
                        if (abs(dragAmount.x) > abs(dragAmount.y) || offsetX != 0f) {
                            change.consume()
                            offsetX += dragAmount.x
                        }
                    },
                )
            },
    ) {
        WordCard(
            word = word,
            tint = tint,
            enriching = enriching,
            onEnrich = onEnrich,
            revealed = revealed,
            onToggleReveal = { revealed = !revealed },
            onLongPress = onLongPress,
            scrollState = if (revealed) scrollState else null,
        )
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
    revealed: Boolean = false,
    onToggleReveal: () -> Unit = {},
    onLongPress: () -> Unit = {},
    scrollState: ScrollState? = null,
) {

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
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Kapalıyken kelime kartın tam ortasında dursun.
                if (!revealed) Spacer(Modifier.weight(1f))

                Text(
                    text = word.word,
                    // Kitaptan seçilen öbekler uzun olabiliyor; tek kelimelik
                    // kartın puntosuyla ekrana sığmıyorlar.
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontSize = when {
                            word.word.length > 44 -> if (revealed) 18.sp else 22.sp
                            word.word.length > 22 -> if (revealed) 24.sp else 30.sp
                            else -> if (revealed) 30.sp else 40.sp
                        },
                    ),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )

                if (!revealed) {
                    // Kitaptan gelen kelimede bağlam cümlesi kapalıyken de
                    // görünsün: kelimeyi zaten o cümlede görmüştün.
                    if (word.context.isNotBlank()) {
                        Spacer(Modifier.height(18.dp))
                        BookQuote(
                            text = word.context,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Spacer(Modifier.height(10.dp))

                    if (word.meaning.isNotBlank()) {
                        Text(
                            text = word.meaning,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                        )
                    }

                    if (word.definition.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = word.definition,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (word.context.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        BookQuote(
                            text = word.context,
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
                    }

                    if (word.root.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        LabeledBlock("Kök", word.root)
                    }

                    if (word.family.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        // Her kökendaş kendi satırında: yanlarında Türkçeleri
                        // var, yan yana dizilince okunmuyorlar. Aralarına yön
                        // oku koymuyoruz — biri ötekinden türemiyor, hepsi
                        // aynı kökten geliyor.
                        LabeledBlock("Aile", word.family.first())
                        word.family.drop(1).forEach { line ->
                            LabeledBlock("", line)
                        }
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
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Start,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 4.dp),
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(tint, RoundedCornerShape(24.dp)),
            )

            // Anlamı henüz getirilmemiş kelime: düğme kartın dibinde duruyor,
            // metnin arasında değil. Açıklama yazısı yok — düğme zaten ne
            // yapacağını söylüyor.
            if (word.meaning.isBlank() && word.definition.isBlank() && onEnrich != null) {
                TextButton(
                    enabled = !enriching,
                    onClick = onEnrich,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp),
                ) {
                    Text(if (enriching) "Getiriliyor…" else "Anlamını getir")
                }
            }
        }
    }
}

/** Kitaptan alınan cümle: alıntı olduğu belli olsun diye tırnak içinde ve italik. */
@Composable
private fun BookQuote(text: String, color: Color) {
    Text(
        text = "\u201C${text.trim().trim('\u201C', '\u201D', '"')}\u201D",
        style = MaterialTheme.typography.bodyMedium,
        fontStyle = FontStyle.Italic,
        textAlign = TextAlign.Center,
        color = color,
    )
}

/** Sola yaslı, "1. 2. 3." diye numaralanmış örnek cümle. */
@Composable
private fun NumberedLine(number: Int, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
    ) {
        Text(
            text = "$number.",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(22.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Start,
        )
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
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = content,
        )
    }
}

/** Eski kayıtlarda zıt anlamlılar "scarce (zıt)" diye işaretliydi; renk artık söylüyor. */
private fun cleanOpposite(word: String): String =
    word.replace("(zıt)", "", ignoreCase = true).trim()

/** Açık ve koyu temada da beyaz yazıyı taşıyan bir mavi. */
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
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Start,
        )
    }
}

/** "kalıp: kelime, kelime" satırlarını çözer; bozuk satırları atlar. */
internal fun parseCollocations(text: String): List<Collocation> = text.lines()
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
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Start,
            )
        }
    }
}
