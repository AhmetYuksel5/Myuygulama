package com.ahmety.uygulama.launcher

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Launcher ana ekranı. Üç yatay sayfa:
 *  - 0: Ana ekran — sağ elle rahat erişilen yay biçiminde simgeler.
 *  - 1: Uygulama çekmecesi (sola çekince) — tüm uygulamalar, arama.
 *  - 2: Widget'lar (Faz 2).
 *
 * Her simgede tek/çift dokun ve yukarı/aşağı sürükleme ayrı komuta bağlı.
 * Uzun basınca simge düzenleyici açılır.
 */
@Composable
fun LauncherRoot() {
    val context = LocalContext.current
    val store = remember { LauncherStore(context) }

    val favorites = remember { mutableStateListOf<Favorite>() }
    var loaded by remember { mutableStateOf(false) }
    var onRight by remember { mutableStateOf(store.onRight) }
    var iconSizeDp by remember { mutableIntStateOf(store.iconSizeDp) }
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }

    var editing by remember { mutableStateOf<Favorite?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    // Eylem düzenleyiciden uygulama seçmek için: seçilince bu geri-çağrı çalışır.
    var appPicker by remember { mutableStateOf<((AppInfo) -> Unit)?>(null) }

    LaunchedEffect(Unit) {
        val initial = withContext(Dispatchers.IO) {
            val seeded = if (!store.seeded) {
                val seed = seedFavorites(context)
                store.save(seed)
                store.seeded = true
                seed
            } else {
                store.load()
            }
            seeded to loadLaunchableApps(context)
        }
        favorites.clear()
        favorites.addAll(initial.first)
        apps = initial.second
        loaded = true
    }

    fun persist() = store.save(favorites.toList())

    fun addAppFavorite(app: AppInfo) {
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
    }

    val pagerState = rememberPagerState(initialPage = 0) { 3 }

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> HomePage(
                    favorites = favorites,
                    onRight = onRight,
                    iconSizeDp = iconSizeDp,
                    onEdit = { editing = it },
                    onOpenSettings = { showSettings = true },
                )
                1 -> DrawerPage(
                    apps = apps,
                    onOpenApp = { LauncherAction.OpenApp(it.packageName).run(context) },
                    onAddFavorite = { addAppFavorite(it) },
                )
                else -> WidgetsPage()
            }
        }

        // Sayfa göstergesi (küçük noktalar).
        PageDots(
            count = 3,
            current = pagerState.currentPage,
            modifier = Modifier
                .align(Alignment.BottomCenter)
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
            onAddShortcut = {
                showSettings = false
                editing = Favorite(
                    id = newFavoriteId(),
                    type = FavoriteType.SHORTCUT,
                    label = "Kısayol",
                )
            },
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
    onRight: Boolean,
    iconSizeDp: Int,
    onEdit: (Favorite) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    Box(modifier = Modifier.fillMaxSize()) {
        // Üst: saat + ayar düğmesi.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Clock(modifier = Modifier.weight(1f))
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = "Launcher ayarları")
            }
        }

        if (favorites.isEmpty()) {
            Text(
                text = "Ana ekran boş. Uygulama çekmecesini açmak için sola kaydır; " +
                    "bir uygulamaya uzun basıp buraya ekleyebilirsin. " +
                    "Kısayol (kişi/navigasyon) eklemek için sağ üstteki ayara dokun.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
            )
        } else {
            ArcFavorites(
                favorites = favorites,
                onRight = onRight,
                iconSizeDp = iconSizeDp,
                onEdit = onEdit,
                context = context,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Simgeleri sağ (ya da sol) alt köşeyi merkez alan çeyrek yay üzerine dizer.
 * Başparmakla erişimi kolay olsun diye yay, kullandığın elin köşesinden
 * yukarı-içeri doğru açılır.
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
        },
    ) { measurables, constraints ->
        val slotWidth = (iconSizeDp * 1.7f).dp.roundToPx()
        val childConstraints = Constraints(maxWidth = slotWidth)
        val placeables = measurables.map { it.measure(childConstraints) }

        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val marginPx = 14.dp.roundToPx()
        val anchorX = if (onRight) (w - marginPx).toFloat() else marginPx.toFloat()
        val anchorY = (h - marginPx).toFloat()
        val radius = min(w, h) * 0.70f
        val dirX = if (onRight) -1f else 1f

        val n = placeables.size
        // Yay açısı: 0 ≈ yukarı, π/2 ≈ yatay. Uçlardan biraz içeride kalsın.
        val tStart = 0.08 * PI
        val tEnd = 0.46 * PI

        layout(w, h) {
            placeables.forEachIndexed { i, placeable ->
                val f = if (n <= 1) 0.5 else i.toDouble() / (n - 1)
                val theta = tStart + (tEnd - tStart) * f
                val centerX = anchorX + dirX * radius * sin(theta).toFloat()
                val centerY = anchorY - radius * cos(theta).toFloat()
                val x = (centerX - placeable.width / 2f).roundToInt()
                    .coerceIn(0, (w - placeable.width).coerceAtLeast(0))
                val y = (centerY - placeable.height / 2f).roundToInt()
                    .coerceIn(0, (h - placeable.height).coerceAtLeast(0))
                placeable.place(x, y)
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
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width((iconSizeDp * 1.6f).dp)
            .padding(4.dp)
            .iconGestures(
                onTap = onTap,
                onDoubleTap = onDoubleTap,
                onSwipeUp = onSwipeUp,
                onSwipeDown = onSwipeDown,
                onLongPress = onLongPress,
            ),
    ) {
        IconGlyph(favorite = favorite, sizeDp = iconSizeDp)
        Spacer(Modifier.height(4.dp))
        Text(
            text = favorite.label,
            style = MaterialTheme.typography.labelSmall,
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

    Column(modifier = Modifier.fillMaxSize().padding(top = 16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Uygulama ara") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(8.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(76.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
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
            Box(modifier = Modifier.size(52.dp).background(Color.Gray, CircleShape))
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
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "Widget'lar yakında (Faz 2).\nBurada ana ekran widget'larını yerleştireceksin.",
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
    Column(modifier = modifier) {
        Text(timeText, fontSize = 46.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(dateText, style = MaterialTheme.typography.titleMedium, color = Color.White)
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

@Composable
private fun rememberAppIcon(packageName: String?): ImageBitmap? {
    val context = LocalContext.current
    return remember(packageName) {
        if (packageName == null) {
            null
        } else {
            runCatching {
                context.packageManager.getApplicationIcon(packageName)
                    .toBitmap(144, 144)
                    .asImageBitmap()
            }.getOrNull()
        }
    }
}

private fun nowTime(): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
private fun nowDate(): String =
    SimpleDateFormat("d MMMM EEEE", Locale("tr")).format(Date())

private fun openDashboard(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (intent != null) context.startActivity(intent)
}

// --- Ayarlar ve düzenleyici diyalogları ---

@Composable
private fun LauncherSettingsDialog(
    onRight: Boolean,
    iconSizeDp: Int,
    onRightChange: (Boolean) -> Unit,
    onIconSizeChange: (Int) -> Unit,
    onAddShortcut: () -> Unit,
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
                OutlinedButton(onClick = onAddShortcut, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Kısayol ekle (kişi / navigasyon)")
                }
                OutlinedButton(onClick = onOpenApp, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Apps, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Uygulamayı aç (panel)")
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
                    modifier = Modifier.fillMaxWidth().height(320.dp),
                ) {
                    items(filtered, key = { it.packageName }) { app ->
                        DrawerAppCell(app = app, onClick = { onPick(app) }, onLongClick = { onPick(app) })
                    }
                }
            }
        },
    )
}
