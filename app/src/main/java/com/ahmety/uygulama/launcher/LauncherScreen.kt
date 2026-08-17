@file:OptIn(ExperimentalFoundationApi::class)

package com.ahmety.uygulama.launcher

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Ev tuşuna basıldığında ana sayfaya dönmek için kullanılan sinyal.
 * Activity'den Compose'a, [LauncherRoot] imzasını değiştirmeden.
 */
internal object LauncherHomeSignal {
    val requests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
}

/**
 * Launcher ana ekranı. Üç yatay sayfa:
 *  - 0: Ana ekran — sağ elle rahat erişilen yay biçiminde simgeler.
 *  - 1: Uygulama çekmecesi (sola çekince) — tüm uygulamalar, arama.
 *  - 2: Widget'lar (sonraki aşama).
 *
 * Her simgede tek/çift dokun ve yukarı/aşağı sürükleme ayrı komuta bağlı.
 * Uzun basınca simge düzenleyici açılır.
 */
@Composable
fun LauncherRoot() {
    val context = LocalContext.current
    val store = remember { LauncherStore(context) }
    val scope = rememberCoroutineScope()

    val favorites = remember { mutableStateListOf<Favorite>() }
    var loaded by remember { mutableStateOf(false) }
    var onRight by remember { mutableStateOf(store.onRight) }
    var iconSizeDp by remember { mutableIntStateOf(store.iconSizeDp) }
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }

    var editing by remember { mutableStateOf<Favorite?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    // Eylem düzenleyiciden ya da "ekle" akışından uygulama seçmek için.
    var appPicker by remember { mutableStateOf<((AppInfo) -> Unit)?>(null) }

    // 1) Kayıtlı simgeler hemen gelsin: bu yalnızca SharedPreferences okuması.
    //    Ağır paket sorgusunu beklersek ana ekran her açılışta boş görünüyor.
    LaunchedEffect(Unit) {
        val stored = withContext(Dispatchers.IO) { store.load() }
        favorites.clear()
        favorites.addAll(stored)
        loaded = true
    }

    // 2) Uygulama listesi arkada yüklensin; ilk kurulumda tohumlama da burada.
    LaunchedEffect(Unit) {
        val installed = withContext(Dispatchers.IO) { loadLaunchableApps(context) }
        apps = installed
        if (!store.seeded) {
            val seed = seedFavorites(installed)
            store.seeded = true
            if (seed.isNotEmpty()) {
                favorites.clear()
                favorites.addAll(seed)
                withContext(Dispatchers.IO) { store.save(seed) }
            }
        }
    }

    fun persist() {
        val snapshot = favorites.toList()
        scope.launch { withContext(Dispatchers.IO) { store.save(snapshot) } }
    }

    fun addAppFavorite(app: AppInfo) {
        if (favorites.any { it.packageName == app.packageName && it.type == FavoriteType.APP }) {
            Toast.makeText(context, "${app.label} zaten ana ekranda", Toast.LENGTH_SHORT).show()
            return
        }
        favorites.add(
            Favorite(
                id = newFavoriteId(),
                type = FavoriteType.APP,
                label = app.label,
                packageName = app.packageName,
                tap = LauncherAction.OpenApp(app.packageName),
            ),
        )
        persist()
        Toast.makeText(context, "${app.label} ana ekrana eklendi", Toast.LENGTH_SHORT).show()
    }

    val pagerState = rememberPagerState(initialPage = 0) { 3 }

    // Ev tuşu: hangi sayfada olursak olalım ana sayfaya dön.
    LaunchedEffect(Unit) {
        LauncherHomeSignal.requests.collect { pagerState.animateScrollToPage(0) }
    }
    // Geri tuşu launcher'ı bitirmesin; ana sayfaya dönsün.
    BackHandler(enabled = pagerState.currentPage != 0) {
        scope.launch { pagerState.animateScrollToPage(0) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Duvar kâğıdı açık renkliyse beyaz metinler okunmaz; hafif bir
            // karartma metinleri her duvar kâğıdında okunur kılıyor.
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.55f to Color.Black.copy(alpha = 0.15f),
                    1f to Color.Black.copy(alpha = 0.45f),
                ),
            ),
    ) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> HomePage(
                    favorites = favorites,
                    loaded = loaded,
                    onRight = onRight,
                    iconSizeDp = iconSizeDp,
                    onEdit = { editing = it },
                    onOpenSettings = { showSettings = true },
                    onAddApp = { appPicker = { app -> addAppFavorite(app) } },
                    onAddShortcut = {
                        editing = Favorite(
                            id = newFavoriteId(),
                            type = FavoriteType.SHORTCUT,
                            label = "Kısayol",
                        )
                    },
                )
                1 -> DrawerPage(
                    apps = apps,
                    onOpenApp = { LauncherAction.OpenApp(it.packageName).run(context) },
                    onAddFavorite = { addAppFavorite(it) },
                )
                else -> WidgetsPage()
            }
        }

        PageDots(
            count = 3,
            current = pagerState.currentPage,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 10.dp),
        )
    }

    editing?.let { fav ->
        FavoriteEditorDialog(
            favorite = fav,
            appLabelOf = { pkg -> apps.firstOrNull { it.packageName == pkg }?.label ?: pkg },
            onPickApp = { onPicked -> appPicker = onPicked },
            onDismiss = { editing = null },
            onDelete = {
                favorites.removeAll { it.id == fav.id }
                persist()
                editing = null
            },
            onSave = { updated ->
                val index = favorites.indexOfFirst { it.id == updated.id }
                if (index >= 0) favorites[index] = updated else favorites.add(updated)
                persist()
                editing = null
            },
        )
    }

    if (showSettings) {
        LauncherSettingsDialog(
            onRight = onRight,
            iconSizeDp = iconSizeDp,
            onRightChange = { onRight = it; store.onRight = it },
            onIconSizeChange = { iconSizeDp = it; store.iconSizeDp = it },
            onOpenApp = {
                showSettings = false
                openDashboard(context)
            },
            onDismiss = { showSettings = false },
        )
    }

    appPicker?.let { onPicked ->
        AppPickerDialog(
            apps = apps,
            onPick = { app ->
                onPicked(app)
                appPicker = null
            },
            onDismiss = { appPicker = null },
        )
    }
}

@Composable
private fun HomePage(
    favorites: List<Favorite>,
    loaded: Boolean,
    onRight: Boolean,
    iconSizeDp: Int,
    onEdit: (Favorite) -> Unit,
    onOpenSettings: () -> Unit,
    onAddApp: () -> Unit,
    onAddShortcut: () -> Unit,
) {
    val context = LocalContext.current
    var addMenuOpen by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Simgeler önce çizilsin ki üstteki düğmeler onların üzerinde kalsın.
        if (loaded && favorites.isNotEmpty()) {
            ArcFavorites(
                favorites = favorites,
                onRight = onRight,
                iconSizeDp = iconSizeDp,
                onEdit = onEdit,
                context = context,
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding(),
            )
        } else if (loaded) {
            Text(
                text = "Ana ekran boş. Sağ üstteki + ile uygulama veya kısayol ekle; " +
                    "tüm uygulamalar için sola kaydır.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Clock(modifier = Modifier.weight(1f))
            Box {
                IconButton(onClick = { addMenuOpen = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "Ekle", tint = Color.White)
                }
                DropdownMenu(expanded = addMenuOpen, onDismissRequest = { addMenuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Uygulama ekle") },
                        onClick = {
                            addMenuOpen = false
                            onAddApp()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Kısayol ekle (kişi / navigasyon)") },
                        onClick = {
                            addMenuOpen = false
                            onAddShortcut()
                        },
                    )
                }
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = "Ana ekran ayarları", tint = Color.White)
            }
        }
    }
}

/**
 * Simgeleri, kullandığın elin alt köşesini merkez alan yay üzerine dizer.
 *
 * Açısal adım simge boyutundan türetiliyor: komşu simgeler arası mesafe her
 * zaman en az bir simge kutusu kadar. Yay dolunca içeride ikinci bir halka
 * açılıyor — böylece simge sayısı arttıkça simgeler üst üste binmiyor
 * (binerse yanlış uygulama açılır).
 */
@Composable
private fun ArcFavorites(
    favorites: List<Favorite>,
    onRight: Boolean,
    iconSizeDp: Int,
    onEdit: (Favorite) -> Unit,
    context: Context,
    modifier: Modifier = Modifier,
) {
    Layout(
        modifier = modifier,
        content = {
            favorites.forEach { fav ->
                key(fav.id) {
                    FavoriteIcon(
                        favorite = fav,
                        iconSizeDp = iconSizeDp,
                        onTap = { fav.tap.run(context) },
                        onDoubleTap = { fav.doubleTap.run(context) },
                        onSwipeUp = { fav.swipeUp.run(context) },
                        onSwipeDown = { fav.swipeDown.run(context) },
                        onLongPress = { onEdit(fav) },
                    )
                }
            }
        },
    ) { measurables, constraints ->
        val slotWidth = (iconSizeDp * 1.25f).dp.roundToPx()
        val childConstraints = Constraints(maxWidth = slotWidth)
        val placeables = measurables.map { it.measure(childConstraints) }

        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val marginPx = 14.dp.roundToPx()
        val slotH = placeables.maxOfOrNull { it.height } ?: 0
        // Yay merkezini yarım simge içeri al: uçlar ekran dışına taşmasın.
        val anchorX = if (onRight) (w - marginPx - slotWidth / 2f) else (marginPx + slotWidth / 2f)
        val anchorY = h - marginPx - slotH / 2f
        val dirX = if (onRight) -1f else 1f

        val tStart = 0.06 * PI
        val tEnd = 0.50 * PI
        val span = tEnd - tStart
        val gapPx = slotWidth + 6.dp.roundToPx()
        val ringGap = slotH + 12.dp.roundToPx()
        val outerRadius = min(w, h) * 0.70f

        layout(w, h) {
            var i = 0
            var ring = 0
            while (i < placeables.size) {
                val r = (outerRadius - ring * ringGap).coerceAtLeast(gapPx.toFloat())
                val cap = (1 + (span * r / gapPx).toInt()).coerceIn(1, 12)
                val count = minOf(cap, placeables.size - i)
                val step = if (count <= 1) 0.0 else (gapPx / r).toDouble()
                val startT = tStart + (span - step * (count - 1)) / 2.0
                for (k in 0 until count) {
                    val theta = startT + step * k
                    val p = placeables[i + k]
                    val cx = anchorX + dirX * r * sin(theta).toFloat()
                    val cy = anchorY - r * cos(theta).toFloat()
                    p.place(
                        (cx - p.width / 2f).roundToInt().coerceIn(0, (w - p.width).coerceAtLeast(0)),
                        (cy - p.height / 2f).roundToInt().coerceIn(0, (h - p.height).coerceAtLeast(0)),
                    )
                }
                i += count
                ring++
            }
        }
    }
}

@Composable
private fun FavoriteIcon(
    favorite: Favorite,
    iconSizeDp: Int,
    onTap: () -> Unit,
    onDoubleTap: () -> Unit,
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit,
    onLongPress: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width((iconSizeDp * 1.6f).dp)
            .padding(4.dp)
            .iconGestures(
                key = favorite,
                hasDoubleTap = favorite.doubleTap != LauncherAction.None,
                onTap = onTap,
                onDoubleTap = onDoubleTap,
                onSwipeUp = onSwipeUp,
                onSwipeDown = onSwipeDown,
                onLongPress = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongPress()
                },
            ),
    ) {
        IconGlyph(favorite = favorite, sizeDp = iconSizeDp)
        Spacer(Modifier.height(4.dp))
        Text(
            text = favorite.label,
            style = MaterialTheme.typography.labelSmall.copy(
                shadow = Shadow(color = Color.Black.copy(alpha = 0.7f), blurRadius = 6f),
            ),
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun IconGlyph(favorite: Favorite, sizeDp: Int) {
    val bitmap = rememberAppIcon(favorite.packageName.takeIf { favorite.type == FavoriteType.APP })
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = favorite.label,
            modifier = Modifier.size(sizeDp.dp),
        )
    } else {
        Box(
            modifier = Modifier
                .size(sizeDp.dp)
                .background(Color(favorite.colorArgb), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = favorite.label.trim().take(1).uppercase(),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (sizeDp * 0.42f).sp,
            )
        }
    }
}

@Composable
private fun DrawerPage(
    apps: List<AppInfo>,
    onOpenApp: (AppInfo) -> Unit,
    onAddFavorite: (AppInfo) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(apps, query) {
        if (query.isBlank()) apps
        else apps.filter { it.label.contains(query, ignoreCase = true) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.93f))
            .systemBarsPadding()
            .padding(top = 16.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Uygulama ara") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )
        Text(
            text = "Uygulamaya uzun bas → ana ekrana ekle",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, top = 6.dp),
        )
        Spacer(Modifier.height(4.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(76.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 48.dp),
        ) {
            items(filtered, key = { it.packageName }) { app ->
                DrawerAppCell(
                    app = app,
                    onClick = { onOpenApp(app) },
                    onLongClick = { onAddFavorite(app) },
                )
            }
        }
    }
}

@Composable
private fun DrawerAppCell(
    app: AppInfo,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val bitmap = rememberAppIcon(app.packageName)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(8.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        if (bitmap != null) {
            Image(bitmap = bitmap, contentDescription = app.label, modifier = Modifier.size(52.dp))
        } else {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = app.label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun WidgetsPage() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.93f))
            .systemBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Bu sayfaya widget ekleme yakında gelecek.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(32.dp),
        )
    }
}

@Composable
private fun Clock(modifier: Modifier = Modifier) {
    var timeText by remember { mutableStateOf(nowTime()) }
    var dateText by remember { mutableStateOf(nowDate()) }
    LaunchedEffect(Unit) {
        while (true) {
            timeText = nowTime()
            dateText = nowDate()
            delay(15_000)
        }
    }
    val shadow = Shadow(color = Color.Black.copy(alpha = 0.7f), blurRadius = 8f)
    Column(modifier = modifier) {
        Text(
            text = timeText,
            fontSize = 46.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            style = MaterialTheme.typography.headlineLarge.copy(shadow = shadow),
        )
        Text(
            text = dateText,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium.copy(shadow = shadow),
        )
    }
}

@Composable
private fun PageDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) { i ->
            Box(
                modifier = Modifier
                    .size(if (i == current) 9.dp else 7.dp)
                    .background(
                        if (i == current) Color.White else Color.White.copy(alpha = 0.4f),
                        CircleShape,
                    ),
            )
        }
    }
}

/**
 * Simgeyi önbellekten anında verir; yoksa arka planda çözüp gösterir.
 * Her hücrede senkron `getApplicationIcon` çağrısı çekmeceyi takıyordu.
 */
@Composable
private fun rememberAppIcon(packageName: String?): ImageBitmap? {
    val context = LocalContext.current
    var bitmap by remember(packageName) {
        mutableStateOf(packageName?.let { AppIconCache.cached(it) })
    }
    LaunchedEffect(packageName) {
        if (packageName != null && bitmap == null) {
            bitmap = AppIconCache.load(context, packageName)
        }
    }
    return bitmap
}

private fun nowTime(): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
private fun nowDate(): String =
    SimpleDateFormat("d MMMM EEEE", Locale("tr")).format(Date())

private fun openDashboard(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (intent != null) context.startActivity(intent)
}

// --- Ayarlar ve seçici diyalogları ---

@Composable
private fun LauncherSettingsDialog(
    onRight: Boolean,
    iconSizeDp: Int,
    onRightChange: (Boolean) -> Unit,
    onIconSizeChange: (Int) -> Unit,
    onOpenApp: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Kapat") } },
        title = { Text("Ana ekran ayarları") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Yay hangi elde", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !onRight, onClick = { onRightChange(false) }, label = { Text("Sol") })
                    FilterChip(selected = onRight, onClick = { onRightChange(true) }, label = { Text("Sağ") })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Simge boyutu", modifier = Modifier.weight(1f))
                    TextButton(
                        enabled = iconSizeDp > 44,
                        onClick = { onIconSizeChange((iconSizeDp - 4).coerceAtLeast(44)) },
                    ) { Text("−") }
                    Text("$iconSizeDp dp")
                    TextButton(
                        enabled = iconSizeDp < 88,
                        onClick = { onIconSizeChange((iconSizeDp + 4).coerceAtMost(88)) },
                    ) { Text("+") }
                }
                OutlinedButton(onClick = onOpenApp, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Apps, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Merkez panelini aç")
                }
            }
        },
    )
}

@Composable
private fun AppPickerDialog(
    apps: List<AppInfo>,
    onPick: (AppInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(apps, query) {
        if (query.isBlank()) apps else apps.filter { it.label.contains(query, ignoreCase = true) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("İptal") } },
        title = { Text("Uygulama seç") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Ara") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(76.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                ) {
                    items(filtered, key = { it.packageName }) { app ->
                        DrawerAppCell(app = app, onClick = { onPick(app) }, onLongClick = { onPick(app) })
                    }
                }
            }
        },
    )
}
