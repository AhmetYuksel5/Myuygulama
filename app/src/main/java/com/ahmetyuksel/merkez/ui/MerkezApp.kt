package com.ahmetyuksel.merkez.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ahmetyuksel.merkez.ui.permissions.PermissionsScreen
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

private enum class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    TODAY("today", "Bugün", Icons.Outlined.Today),
    LIBRARY("library", "Arşiv", Icons.Outlined.Description),
    SEARCH("search", "Ara", Icons.Outlined.Search),
    SETTINGS("settings", "Ayarlar", Icons.Outlined.Settings),
}

@Composable
fun MerkezApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

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
                PlaceholderScreen(
                    title = "Bugün",
                    description = "Alışkanlıklar, günün görevleri ve ajanda buraya gelecek.",
                )
            }
            composable(TopLevelDestination.LIBRARY.route) {
                PlaceholderScreen(
                    title = "Arşiv",
                    description = "Notlar, makaleler, dokümanlar ve alıntılar tek listede.",
                )
            }
            composable(TopLevelDestination.SEARCH.route) {
                PlaceholderScreen(
                    title = "Ara",
                    description = "Tüm kayıtlarda tam metin arama.",
                )
            }
            composable(TopLevelDestination.SETTINGS.route) {
                SettingsScreen(
                    onOpenPermissions = { navController.navigate(PERMISSIONS_ROUTE) },
                )
            }
            composable(PERMISSIONS_ROUTE) {
                PermissionsScreen(onContinue = { navController.popBackStack() })
            }
        }
    }
}

private const val PERMISSIONS_ROUTE = "permissions"

@Composable
private fun SettingsScreen(onOpenPermissions: () -> Unit) {
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
    }
}

@Composable
private fun PlaceholderScreen(title: String, description: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = title, style = MaterialTheme.typography.headlineMedium)
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
