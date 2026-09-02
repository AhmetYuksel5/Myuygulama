package com.ahmety.uygulama.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.ahmety.uygulama.core.designsystem.MerkezIcons
import com.ahmety.uygulama.feature.ebook.BookReaderRoute
import com.ahmety.uygulama.feature.ebook.BookShelfRoute
import com.ahmety.uygulama.feature.habits.HabitsRoute
import com.ahmety.uygulama.feature.library.PocketRoute
import com.ahmety.uygulama.feature.reader.ArticleRoute
import com.ahmety.uygulama.feature.reader.SaveArticleDialog
import com.ahmety.uygulama.feature.subtitles.SubtitleRoute
import com.ahmety.uygulama.feature.vocab.VocabRoute
import com.ahmety.uygulama.takvim.TakvimBildirimi
import com.ahmety.uygulama.ui.ai.AiSettingsScreen
import com.ahmety.uygulama.ui.gestures.GestureSettingsScreen
import com.ahmety.uygulama.ui.gestures.QuickCursorScreen
import com.ahmety.uygulama.ui.permissions.PermissionsScreen
import com.ahmety.uygulama.ui.sync.SyncScreen
import com.ahmety.uygulama.ui.update.UpdateDialog
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.navArgument
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

/**
 * Alt çubuktaki sekmeler.
 *
 * Uygulama okuma ve kelime çalışma etrafında toplandı; görevler, notlar,
 * Pocket ve kendi ana ekranı çıkarıldı. Kayıtları veritabanında duruyor,
 * yalnızca ekranları ve kodları derlemeye girmiyor.
 */
private enum class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    BOOKS("books", "Kitaplık", MerkezIcons.Book),
    VOCAB("vocab", "Kelimeler", MerkezIcons.Translate),
    POCKET("pocket", "Pocket", MerkezIcons.Pocket),
    MORE("more", "Daha", MerkezIcons.MoreHoriz),
}

@Composable
fun MerkezApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    var showUpdate by remember { mutableStateOf(false) }
    var showSaveArticle by remember { mutableStateOf(false) }

    if (showUpdate) {
        UpdateDialog(onDismiss = { showUpdate = false })
    }

    if (showSaveArticle) {
        SaveArticleDialog(
            onDismiss = { showSaveArticle = false },
            onSaved = { id ->
                showSaveArticle = false
                navController.navigate("$ARTICLE_ROUTE/$id")
            },
        )
    }

    // Okuma ekranında alt sekme çubuğu metni sıkıştırıyor ve sağ alt köşedeki
    // "ileri" dokunma bölgesini kapatıyor; o rotada gizliyoruz.
    val immersive = currentDestination?.route?.startsWith("$BOOK_ROUTE/") == true

    Scaffold(
        // Okuma ekranında alt inset'i Scaffold'a bırakmıyoruz: bıraksaydık
        // sayfanın krem/siyah zemini gezinme çubuğunun hizasına kadar
        // uzanmaz, orada uygulama zemini renginde bir bant kalırdı.
        // Okuyucu kendi alt boşluğunu kendisi veriyor.
        contentWindowInsets = if (immersive) {
            WindowInsets.statusBars
        } else {
            ScaffoldDefaults.contentWindowInsets
        },
        bottomBar = {
            if (immersive) return@Scaffold
            NavigationBar {
                TopLevelDestination.entries.forEach { destination ->
                    val selected = currentDestination?.hierarchy?.any {
                        it.route == destination.route
                    } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                // restoreState kullanmıyoruz: kaydedilen yığın alt
                                // sayfayı da içerdiği için sekmeye basınca o alt
                                // sayfaya dönüyor, sekmenin kendisi açılmıyordu.
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = false
                                }
                                launchSingleTop = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label, maxLines = 1) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.BOOKS.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(HABITS_ROUTE) {
                HabitsRoute()
            }
            composable(TopLevelDestination.MORE.route) {
                MoreScreen(
                    onCheckUpdate = { showUpdate = true },
                    onOpenHabits = { navController.navigate(HABITS_ROUTE) },
                    onOpenPermissions = { navController.navigate(PERMISSIONS_ROUTE) },
                    onOpenSync = { navController.navigate(SYNC_ROUTE) },
                    onOpenAi = { navController.navigate(AI_ROUTE) },
                    onOpenGestures = { navController.navigate(GESTURES_ROUTE) },
                    onOpenCursor = { navController.navigate(CURSOR_ROUTE) },
                    onOpenSubtitles = { navController.navigate(SUBTITLE_ROUTE) },
                )
            }
            composable(TopLevelDestination.VOCAB.route) {
                VocabRoute()
            }
            composable(TopLevelDestination.POCKET.route) {
                PocketRoute(
                    onOpenArticle = { navController.navigate("$ARTICLE_ROUTE/$it") },
                    onAddArticle = { showSaveArticle = true },
                )
            }
            composable(
                route = "$ARTICLE_ROUTE/{articleId}",
                arguments = listOf(navArgument("articleId") { type = NavType.LongType }),
            ) { backStackEntry ->
                ArticleRoute(entryId = backStackEntry.arguments?.getLong("articleId") ?: 0L)
            }
            composable(TopLevelDestination.BOOKS.route) {
                BookShelfRoute(onOpenBook = { navController.navigate("$BOOK_ROUTE/$it") })
            }
            composable(
                route = "$BOOK_ROUTE/{bookId}",
                arguments = listOf(navArgument("bookId") { type = NavType.LongType }),
            ) { backStackEntry ->
                BookReaderRoute(bookId = backStackEntry.arguments?.getLong("bookId") ?: 0L)
            }
            composable(PERMISSIONS_ROUTE) {
                PermissionsScreen(onContinue = { navController.popBackStack() })
            }
            composable(SYNC_ROUTE) {
                SyncScreen()
            }
            composable(AI_ROUTE) {
                AiSettingsScreen()
            }
            composable(GESTURES_ROUTE) {
                GestureSettingsScreen()
            }
            composable(CURSOR_ROUTE) {
                QuickCursorScreen()
            }
            composable(SUBTITLE_ROUTE) {
                SubtitleRoute()
            }
        }
    }
}

private const val HABITS_ROUTE = "aliskanliklar"
private const val BOOK_ROUTE = "book"
private const val ARTICLE_ROUTE = "article"
private const val AI_ROUTE = "ai"
private const val SUBTITLE_ROUTE = "altyazi"
private const val PERMISSIONS_ROUTE = "permissions"
private const val SYNC_ROUTE = "sync"
private const val GESTURES_ROUTE = "gestures"
private const val CURSOR_ROUTE = "cursor"

@Composable
private fun MoreScreen(
    onCheckUpdate: () -> Unit,
    onOpenHabits: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenSync: () -> Unit,
    onOpenAi: () -> Unit,
    onOpenGestures: () -> Unit,
    onOpenCursor: () -> Unit,
    onOpenSubtitles: () -> Unit,
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Başlık satırının sağında güncelleme: bir liste satırı olarak
        // durduğunda öbür ayarların arasında kayboluyordu, oysa en sık
        // dokunulan şey o.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Daha",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onCheckUpdate) {
                Icon(MerkezIcons.Download, contentDescription = "Güncellemeyi kontrol et")
            }
        }

        // Takvim bildirimi: açık kalınca bildirim çubuğunda duruyor.
        var takvimAcik by remember { mutableStateOf(TakvimBildirimi.acikMi(context)) }
        ListItem(
            headlineContent = { Text("Takvim bildirimi") },
            supportingContent = { Text("Bildirim çubuğunda duran ay takvimi") },
            trailingContent = {
                Switch(
                    checked = takvimAcik,
                    onCheckedChange = { acik ->
                        takvimAcik = acik
                        TakvimBildirimi.ayarla(context, acik)
                    },
                )
            },
            modifier = Modifier.clickable {
                takvimAcik = !takvimAcik
                TakvimBildirimi.ayarla(context, takvimAcik)
            },
        )
        ListItem(
            headlineContent = { Text("Alışkanlıklar") },
            supportingContent = { Text("Günlük takip, seriler, haftalık şerit") },
            modifier = Modifier.clickable(onClick = onOpenHabits),
        )
        ListItem(
            headlineContent = { Text("Altyazı arama") },
            supportingContent = { Text("Altyazıyı indir, bilmediğin kelimeleri çıkar") },
            modifier = Modifier.clickable(onClick = onOpenSubtitles),
        )

        // Araçlar: ekranın üstüne binen, uygulamadan bağımsız çalışan şeyler.
        Text(text = "Araçlar", style = MaterialTheme.typography.titleMedium)
        ListItem(
            headlineContent = { Text("Kenar hareketleri") },
            supportingContent = { Text("Son uygulamalar ve bildirim paneli için kenar şeridi") },
            modifier = Modifier.clickable(onClick = onOpenGestures),
        )
        ListItem(
            headlineContent = { Text("Tek elle imleç") },
            supportingContent = { Text("Ulaşılamayan köşelere basmak için sanal imleç") },
            modifier = Modifier.clickable(onClick = onOpenCursor),
        )

        Text(text = "Ayarlar", style = MaterialTheme.typography.titleMedium)
        ListItem(
            headlineContent = { Text("İzinler") },
            supportingContent = { Text("Bildirim, takvim, alarm, dosya erişimi") },
            modifier = Modifier.clickable(onClick = onOpenPermissions),
        )
        ListItem(
            headlineContent = { Text("Senkronizasyon") },
            supportingContent = { Text("İki telefon arasında paylaşılan klasör") },
            modifier = Modifier.clickable(onClick = onOpenSync),
        )
        ListItem(
            headlineContent = { Text("Yapay zekâ") },
            supportingContent = { Text("OpenAI anahtarı — kitaptan gelen kelimeleri doldurur") },
            modifier = Modifier.clickable(onClick = onOpenAi),
        )
    }
}
