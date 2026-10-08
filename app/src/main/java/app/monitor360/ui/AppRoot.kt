@file:OptIn(ExperimentalMaterial3Api::class)

package app.monitor360.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.monitor360.ui.components.AppIcons
import app.monitor360.ui.components.LocalSnackbar
import app.monitor360.ui.screens.AlertsOverviewScreen
import app.monitor360.ui.screens.ContactsScreen
import app.monitor360.ui.screens.DiagnosticsScreen
import app.monitor360.ui.screens.ArchiveScreen
import app.monitor360.ui.screens.ExportScreen
import app.monitor360.ui.screens.FavoritesScreen
import app.monitor360.ui.screens.UptimeReportScreen
import app.monitor360.ui.screens.MaintenanceOverviewScreen
import app.monitor360.ui.screens.TagsScreen
import app.monitor360.ui.screens.DashboardScreen
import app.monitor360.ui.screens.IncidentsScreen
import app.monitor360.ui.screens.LoginScreen
import app.monitor360.ui.screens.MonitorFormScreen
import app.monitor360.ui.screens.MoreScreen
import app.monitor360.ui.screens.NodesScreen
import app.monitor360.ui.screens.ServerDetailScreen
import app.monitor360.ui.screens.ServersScreen
import app.monitor360.ui.screens.SettingsScreen
import app.monitor360.ui.screens.TokensScreen
import app.monitor360.ui.screens.WebsiteDetailScreen
import app.monitor360.ui.screens.WebsitesScreen

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private const val START = "dashboard"

private fun NavHostController.openTab(route: String) {
    if (route == START) {
        popBackStack(START, inclusive = false)
        return
    }
    navigate(route) {
        popUpTo(START) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private val TABS = listOf(
    Tab("dashboard", "Overview", AppIcons.Dashboard),
    Tab("servers", "Servers", AppIcons.Dns),
    Tab("websites", "Websites", AppIcons.Language),
    Tab("more", "More", AppIcons.Apps),
    Tab("settings", "Settings", Icons.Default.Settings),
)

@Composable
fun AppRoot(vm: AppViewModel) {
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    CompositionLocalProvider(LocalSnackbar provides snack) {
        if (!prefs.isConfigured) {
            LoginScreen(vm)
        } else {
            MainNav(vm, snack)
        }
    }
}

@Composable
private fun MainNav(vm: AppViewModel, snack: SnackbarHostState) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBar = TABS.any { it.route == route }
    val shared by vm.sharedUrl.collectAsStateWithLifecycle()
    LaunchedEffect(shared) {
        if (shared != null && route != "website/new") nav.navigate("website/new") { launchSingleTop = true }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            if (showBar) NavigationBar {
                TABS.forEach { t ->
                    NavigationBarItem(
                        selected = route == t.route,
                        onClick = { nav.openTab(t.route) },
                        icon = { Icon(t.icon, t.label) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { pad ->
        NavHost(nav, startDestination = "dashboard", modifier = Modifier.padding(pad)) {
            composable("dashboard") {
                DashboardScreen(
                    vm,
                    onServer = { nav.navigate("server/$it") },
                    onMonitor = { nav.navigate("website/$it") },
                    onOpenServers = { nav.openTab("servers") },
                    onOpenWebsites = { nav.openTab("websites") },
                )
            }
            composable("servers") { ServersScreen(vm) { nav.navigate("server/$it") } }
            composable("websites") { WebsitesScreen(vm, onOpen = { nav.navigate("website/$it") }, onAdd = { nav.navigate("website/new") }) }
            composable("more") { MoreScreen { nav.navigate(it) } }
            composable("settings") { SettingsScreen(vm, onSignedOut = {}) }
            composable("server/{id}", listOf(navArgument("id") { type = NavType.StringType })) {
                ServerDetailScreen(vm, it.arguments?.getString("id").orEmpty()) { nav.popBackStack() }
            }
            composable("website/new") { MonitorFormScreen(vm, null) { nav.popBackStack() } }
            composable("website/{id}", listOf(navArgument("id") { type = NavType.StringType })) {
                val id = it.arguments?.getString("id").orEmpty()
                WebsiteDetailScreen(vm, id, onBack = { nav.popBackStack() }, onEdit = { nav.navigate("website/$id/edit") })
            }
            composable("website/{id}/edit", listOf(navArgument("id") { type = NavType.StringType })) {
                MonitorFormScreen(vm, it.arguments?.getString("id")) { nav.popBackStack() }
            }
            composable("alerts") {
                AlertsOverviewScreen(vm, { nav.popBackStack() }, { nav.navigate("server/$it") }, { nav.navigate("website/$it") })
            }
            composable("maintenance") {
                MaintenanceOverviewScreen(vm, { nav.popBackStack() }, { nav.navigate("server/$it") }, { nav.navigate("website/$it") })
            }
            composable("tags") {
                TagsScreen(vm, { nav.popBackStack() }) { tag ->
                    vm.tagFilter.value = tag
                    nav.openTab("servers")
                }
            }
            composable("favorites") {
                FavoritesScreen(vm, { nav.popBackStack() }, { nav.navigate("server/$it") }, { nav.navigate("website/$it") })
            }
            composable("archive") { ArchiveScreen(vm) { nav.popBackStack() } }
            composable("export") { ExportScreen(vm) { nav.popBackStack() } }
            composable("uptime-report") { UptimeReportScreen(vm, { nav.popBackStack() }) { nav.navigate("website/$it") } }
            composable("diagnostics") { DiagnosticsScreen(vm) { nav.popBackStack() } }
            composable("contacts") { ContactsScreen(vm) { nav.popBackStack() } }
            composable("incidents") { IncidentsScreen(vm) { nav.popBackStack() } }
            composable("tokens") { TokensScreen(vm) { nav.popBackStack() } }
            composable("nodes") { NodesScreen(vm) { nav.popBackStack() } }
        }
    }
}
