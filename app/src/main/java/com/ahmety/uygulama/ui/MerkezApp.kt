package com.ahmety.uygulama.ui

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ahmety.uygulama.launcher.LauncherActivity
import com.ahmety.uygulama.feature.ebook.BookReaderRoute
import com.ahmety.uygulama.feature.ebook.BookShelfRoute
import com.ahmety.uygulama.feature.library.NoteEditorRoute
import com.ahmety.uygulama.feature.reader.ArticleRoute
import com.ahmety.uygulama.feature.reader.SaveArticleDialog
import com.ahmety.uygulama.feature.library.NotesRoute
import com.ahmety.uygulama.feature.library.PocketRoute
import com.ahmety.uygulama.feature.library.SearchRoute
import com.ahmety.uygulama.feature.tasks.TasksRoute
import com.ahmety.uygulama.feature.vocab.VocabRoute
import com.ahmety.uygulama.ui.permissions.PermissionsScreen
import com.ahmety.uygulama.ui.ai.AiSettingsScreen
import com.ahmety.uygulama.ui.gestures.GestureSettingsScreen
import com.ahmety.uygulama.ui.gestures.QuickCursorScreen
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

private enum class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    BOOKS("books", "Kitaplık", Icons.Outlined.MenuBook),
    VOCAB("vocab", "Kelimeler", Icons.Outlined.Translate),
    HOME_SCREEN("home_screen", "Ana ekran", Icons.Outlined.GridView),
    POCKET("pocket", "Pocket", Icons.Outlined.Bookmark),
    MORE("more", "Daha", Icons.Outlined.MoreHoriz),
}

@Composable
fun MerkezApp() {
    val context = LocalContext.current
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    // Widget'tan gelen "görevlere git / görev ekle" istekleri.
    val navRequest by NavRequestBus.target.collectAsState()
    var pendingAddTask by remember { mutableStateOf(false) }
    var showSaveArticle by remember { mutableStateOf(false) }
    var showUpdate by remember { mutableStateOf(false) }
    LaunchedEffect(navRequest) {
        when (navRequest) {
            NavRequestBus.TARGET_TASKS, NavRequestBus.TARGET_ADD_TASK -> {
                if (navRequest == NavRequestBus.TARGET_ADD_TASK) pendingAddTask = true
                navController.navigate(TASKS_ROUTE) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
        if (navRequest != null) NavRequestBus.consume()
    }

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
                            if (destination == TopLevelDestination.HOME_SCREEN) {
                                // Bu sekme bir ekran değil, kendi başlatıcımızı açar.
                                runCatching {
                                    context.startActivity(
                                        Intent(context, LauncherActivity::class.java)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                    )
                                }
                                return@NavigationBarItem
                            }
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
            composable(TODAY_ROUTE) {
                TodayScreen()
            }
            composable(TASKS_ROUTE) {
                TasksRoute(
                    openAddDialog = pendingAddTask,
                    onAddDialogConsumed = { pendingAddTask = false },
                )
            }
            composable(NOTES_ROUTE) {
                NotesRoute(
                    onOpenNote = { navController.navigate("$NOTE_ROUTE/$it") },
                )
            }
            composable(TopLevelDestination.POCKET.route) {
                PocketRoute(
                    onOpenArticle = { navController.navigate("$ARTICLE_ROUTE/$it") },
                    onAddArticle = { showSaveArticle = true },
                )
            }
            composable(TopLevelDestination.MORE.route) {
                MoreScreen(
                    onOpenToday = { navController.navigate(TODAY_ROUTE) },
                    onOpenTasks = { navController.navigate(TASKS_ROUTE) },
                    onOpenNotes = { navController.navigate(NOTES_ROUTE) },
                    onOpenSearch = { navController.navigate(SEARCH_ROUTE) },
                    onCheckUpdate = { showUpdate = true },
                    onOpenPermissions = { navController.navigate(PERMISSIONS_ROUTE) },
                    onOpenSync = { navController.navigate(SYNC_ROUTE) },
                    onOpenGestures = { navController.navigate(GESTURES_ROUTE) },
                    onOpenCursor = { navController.navigate(CURSOR_ROUTE) },
                    onOpenAi = { navController.navigate(AI_ROUTE) },
                )
            }
            composable(TopLevelDestination.VOCAB.route) {
                VocabRoute()
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
            composable(SEARCH_ROUTE) {
                SearchRoute(
                    onOpenNote = { navController.navigate("$NOTE_ROUTE/$it") },
                    onOpenArticle = { navController.navigate("$ARTICLE_ROUTE/$it") },
                )
            }
            composable(PERMISSIONS_ROUTE) {
                PermissionsScreen(onContinue = { navController.popBackStack() })
            }
            composable(SYNC_ROUTE) {
                SyncScreen()
            }
            composable(GESTURES_ROUTE) {
                GestureSettingsScreen()
            }
            composable(CURSOR_ROUTE) {
                QuickCursorScreen()
            }
            composable(AI_ROUTE) {
                AiSettingsScreen()
            }
            composable(
                route = "$NOTE_ROUTE/{noteId}",
                arguments = listOf(navArgument("noteId") { type = NavType.LongType }),
            ) { backStackEntry ->
                NoteEditorRoute(
                    noteId = backStackEntry.arguments?.getLong("noteId") ?: 0L,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = "$ARTICLE_ROUTE/{articleId}",
                arguments = listOf(navArgument("articleId") { type = NavType.LongType }),
            ) { backStackEntry ->
                ArticleRoute(
                    entryId = backStackEntry.arguments?.getLong("articleId") ?: 0L,
                )
            }
        }
    }
}

private const val NOTES_ROUTE = "notes"
private const val TODAY_ROUTE = "today"
private const val TASKS_ROUTE = "tasks"
private const val BOOK_ROUTE = "book"
private const val SEARCH_ROUTE = "search"
private const val AI_ROUTE = "ai"
private const val PERMISSIONS_ROUTE = "permissions"
private const val SYNC_ROUTE = "sync"
private const val GESTURES_ROUTE = "gestures"
private const val CURSOR_ROUTE = "cursor"
private const val NOTE_ROUTE = "note"
private const val ARTICLE_ROUTE = "article"

@Composable
private fun MoreScreen(
    onOpenToday: () -> Unit,
    onOpenTasks: () -> Unit,
    onOpenNotes: () -> Unit,
    onOpenSearch: () -> Unit,
    onCheckUpdate: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenSync: () -> Unit,
    onOpenGestures: () -> Unit,
    onOpenCursor: () -> Unit,
    onOpenAi: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = "Daha", style = MaterialTheme.typography.headlineMedium)

        // Güncelleme en üstte: tıklayınca pencere açılıp hemen kontrol ediyor.
        ListItem(
            headlineContent = { Text("Güncelleme") },
            supportingContent = { Text("Yeni sürümü kontrol et ve kur") },
            modifier = Modifier.clickable(onClick = onCheckUpdate),
        )

        ListItem(
            headlineContent = { Text("Bugün") },
            supportingContent = { Text("Alışkanlıklar, ajanda, günün özeti") },
            modifier = Modifier.clickable(onClick = onOpenToday),
        )
        ListItem(
            headlineContent = { Text("Görevler") },
            supportingContent = { Text("Listeler, alt görevler, tekrar") },
            modifier = Modifier.clickable(onClick = onOpenTasks),
        )
        ListItem(
            headlineContent = { Text("Notlar") },
            supportingContent = { Text("Renkli kartlar, listeler, fotoğraflar") },
            modifier = Modifier.clickable(onClick = onOpenNotes),
        )
        ListItem(
            headlineContent = { Text("Ara") },
            supportingContent = { Text("Notlar, makaleler, görevler — tek indeks") },
            modifier = Modifier.clickable(onClick = onOpenSearch),
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
