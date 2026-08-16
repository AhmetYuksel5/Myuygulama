package com.ahmety.uygulama.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import com.ahmety.uygulama.feature.library.NoteEditorRoute
import com.ahmety.uygulama.feature.reader.ArticleRoute
import com.ahmety.uygulama.feature.reader.SaveArticleDialog
import com.ahmety.uygulama.feature.library.NotesRoute
import com.ahmety.uygulama.feature.library.SearchRoute
import com.ahmety.uygulama.feature.tasks.TasksRoute
import com.ahmety.uygulama.ui.permissions.PermissionsScreen
import com.ahmety.uygulama.ui.gestures.GestureSettingsScreen
import com.ahmety.uygulama.ui.sync.SyncScreen
import com.ahmety.uygulama.ui.update.UpdateScreen
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
    TODAY("today", "Bugün", Icons.Outlined.Today),
    TASKS("tasks", "Görevler", Icons.Outlined.CheckCircle),
    LIBRARY("library", "Arşiv", Icons.Outlined.Description),
    SEARCH("search", "Ara", Icons.Outlined.Search),
    SETTINGS("settings", "Ayarlar", Icons.Outlined.Settings),
}

@Composable
fun MerkezApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    // Widget'tan gelen "görevlere git / görev ekle" istekleri.
    val navRequest by NavRequestBus.target.collectAsState()
    var pendingAddTask by remember { mutableStateOf(false) }
    var showSaveArticle by remember { mutableStateOf(false) }
    LaunchedEffect(navRequest) {
        when (navRequest) {
            NavRequestBus.TARGET_TASKS, NavRequestBus.TARGET_ADD_TASK -> {
                if (navRequest == NavRequestBus.TARGET_ADD_TASK) pendingAddTask = true
                navController.navigate(TopLevelDestination.TASKS.route) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
        if (navRequest != null) NavRequestBus.consume()
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

    Scaffold(
        bottomBar = {
            NavigationBar {
                TopLevelDestination.entries.forEach { destination ->
                    val selected = currentDestination?.hierarchy?.any {
                        it.route == destination.route
                    } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.TODAY.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(TopLevelDestination.TODAY.route) {
                TodayScreen()
            }
            composable(TopLevelDestination.TASKS.route) {
                TasksRoute(
                    openAddDialog = pendingAddTask,
                    onAddDialogConsumed = { pendingAddTask = false },
                )
            }
            composable(TopLevelDestination.LIBRARY.route) {
                NotesRoute(
                    onOpenNote = { navController.navigate("$NOTE_ROUTE/$it") },
                    onOpenArticle = { navController.navigate("$ARTICLE_ROUTE/$it") },
                    onAddArticle = { showSaveArticle = true },
                )
            }
            composable(TopLevelDestination.SEARCH.route) {
                SearchRoute(
                    onOpenNote = { navController.navigate("$NOTE_ROUTE/$it") },
                    onOpenArticle = { navController.navigate("$ARTICLE_ROUTE/$it") },
                )
            }
            composable(TopLevelDestination.SETTINGS.route) {
                SettingsScreen(
                    onOpenPermissions = { navController.navigate(PERMISSIONS_ROUTE) },
                    onOpenSync = { navController.navigate(SYNC_ROUTE) },
                    onOpenGestures = { navController.navigate(GESTURES_ROUTE) },
                    onOpenUpdates = { navController.navigate(UPDATE_ROUTE) },
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
            composable(UPDATE_ROUTE) {
                UpdateScreen()
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

private const val PERMISSIONS_ROUTE = "permissions"
private const val SYNC_ROUTE = "sync"
private const val GESTURES_ROUTE = "gestures"
private const val NOTE_ROUTE = "note"
private const val UPDATE_ROUTE = "update"
private const val ARTICLE_ROUTE = "article"

@Composable
private fun SettingsScreen(
    onOpenPermissions: () -> Unit,
    onOpenSync: () -> Unit,
    onOpenGestures: () -> Unit,
    onOpenUpdates: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = "Ayarlar", style = MaterialTheme.typography.headlineMedium)
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
            headlineContent = { Text("Kenar hareketleri") },
            supportingContent = { Text("Son uygulamalar ve bildirim paneli için kenar şeridi") },
            modifier = Modifier.clickable(onClick = onOpenGestures),
        )
        ListItem(
            headlineContent = { Text("Güncellemeler") },
            supportingContent = { Text("Yeni sürümü uygulama içinden indir ve kur") },
            modifier = Modifier.clickable(onClick = onOpenUpdates),
        )
    }
}
