@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.monitor360.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.monitor360.BuildConfig
import app.monitor360.data.Accent
import app.monitor360.data.Prefs
import app.monitor360.data.ThemeMode
import app.monitor360.ui.AppViewModel
import app.monitor360.ui.components.ChoiceChips
import app.monitor360.ui.components.Dropdown
import app.monitor360.ui.components.Field
import app.monitor360.ui.components.LocalSnackbar
import app.monitor360.ui.components.ScreenScaffold
import app.monitor360.ui.components.SectionCard
import app.monitor360.ui.theme.StatusColors
import app.monitor360.ui.userMessage
import app.monitor360.widget.WidgetUpdater
import app.monitor360.work.Notifier
import app.monitor360.work.Scheduler
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(vm: AppViewModel, onSignedOut: () -> Unit) {
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val rate by vm.rateLimit.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val snack = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    var editKey by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }

    fun update(t: (Prefs) -> Prefs) {
        vm.settings.update(t)
        Scheduler.apply(ctx, vm.settings.current)
    }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) scope.launch { snack.showSnackbar("Notifications are blocked in system settings") }
    }

    ScreenScaffold("Settings") { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionCard(title = "Account") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("API key", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(maskKey(prefs.apiKey), style = MaterialTheme.typography.labelLarge)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("API usage", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        rate?.let { r -> if (r.remaining != null && r.limit != null) "${r.remaining} / ${r.limit} left this hour" else "–" } ?: "–",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ editKey = true }) { Text("Change key / endpoint") }
                    OutlinedButton({
                        testing = true
                        scope.launch {
                            try {
                                vm.repo.servers()
                                snack.showSnackbar("Connection OK")
                            } catch (e: Exception) {
                                snack.showSnackbar(e.userMessage())
                            }
                            testing = false
                        }
                    }, enabled = !testing) { Text(if (testing) "Testing…" else "Test") }
                }
                TextButton({ confirmSignOut = true }) { Text("Sign out & clear data", color = MaterialTheme.colorScheme.error) }
            }

            SectionCard(title = "Appearance") {
                Text("Theme", style = MaterialTheme.typography.labelLarge)
                ChoiceChips(ThemeMode.entries, prefs.themeMode, { it.label }, { m -> update { it.copy(themeMode = m) } })
                SwitchRow("Pure black (AMOLED)", "Uses true black backgrounds in dark mode", prefs.amoled) { v -> update { it.copy(amoled = v) } }
                if (Build.VERSION.SDK_INT >= 31) {
                    SwitchRow("Dynamic colour", "Use colours from your wallpaper (Material You)", prefs.dynamicColor) { v -> update { it.copy(dynamicColor = v) } }
                }
                if (!(prefs.dynamicColor && Build.VERSION.SDK_INT >= 31)) {
                    Text("Accent colour", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Accent.entries.forEach { a ->
                            val selected = a == prefs.accent
                            Box(
                                Modifier.size(44.dp).clip(CircleShape).background(Color(a.seed))
                                    .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                                    .clickable { update { it.copy(accent = a) } },
                                contentAlignment = Alignment.Center,
                            ) { if (selected) Icon(Icons.Default.Check, a.label, tint = Color.White) }
                        }
                    }
                }
                SwitchRow("Compact lists", "Denser server and website cards", prefs.compactLists) { v -> update { it.copy(compactLists = v) } }
                SwitchRow("Show IDs", "Display server and monitor IDs in lists and details", prefs.showIds) { v -> update { it.copy(showIds = v) } }
            }

            SectionCard(title = "Refresh") {
                Dropdown(
                    "Auto-refresh while the app is open", listOf(0, 30, 60, 120, 300), prefs.autoRefreshSeconds,
                    { if (it == 0) "Off (pull to refresh)" else if (it < 60) "Every $it seconds" else "Every ${it / 60} min" },
                    { v -> update { it.copy(autoRefreshSeconds = v) } },
                )
                Dropdown(
                    "Background refresh (widgets & alerts)", listOf(0, 15, 30, 60, 180), prefs.backgroundRefreshMinutes,
                    { if (it == 0) "Off" else if (it < 60) "Every $it min" else "Every ${it / 60} h" },
                    { v -> update { it.copy(backgroundRefreshMinutes = v) } },
                )
                Text("Android limits background work to at most once every 15 minutes.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Default chart range", style = MaterialTheme.typography.labelLarge)
                ChoiceChips(listOf(1, 6, 24, 168, 720), prefs.defaultRangeHours, { if (it < 24) "$it h" else "${it / 24} d" }, { v -> update { it.copy(defaultRangeHours = v) } })
            }

            SectionCard(title = "Notifications") {
                SwitchRow("Status change notifications", "Notify when a server or website goes down or recovers (checked in the background)", prefs.notificationsEnabled) { v ->
                    update { it.copy(notificationsEnabled = v) }
                    if (v && Build.VERSION.SDK_INT >= 33 && !Notifier.canNotify(ctx)) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                if (prefs.notificationsEnabled) {
                    SwitchRow("Notify when down", null, prefs.notifyOnDown) { v -> update { it.copy(notifyOnDown = v) } }
                    SwitchRow("Notify when recovered", null, prefs.notifyOnRecovery) { v -> update { it.copy(notifyOnRecovery = v) } }
                }
                if (prefs.backgroundRefreshMinutes == 0) Text("Background refresh is off, so no notifications will be sent.", style = MaterialTheme.typography.bodySmall, color = StatusColors.warn())
            }

            SectionCard(title = "Warning thresholds") {
                Text("Used for highlighting and the \"Needs attention\" list.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ThresholdSlider("CPU usage", prefs.cpuWarn, 50f..100f) { v -> update { it.copy(cpuWarn = v) } }
                ThresholdSlider("Memory usage", prefs.memWarn, 50f..100f) { v -> update { it.copy(memWarn = v) } }
                ThresholdSlider("Disk usage", prefs.diskWarn, 50f..100f) { v -> update { it.copy(diskWarn = v) } }
                Text("Website uptime below %.1f%%".format(prefs.uptimeWarn), style = MaterialTheme.typography.labelLarge)
                Slider(prefs.uptimeWarn.toFloat(), { v -> update { it.copy(uptimeWarn = (Math.round(v * 10) / 10.0)) } }, valueRange = 90f..100f)
            }

            SectionCard(title = "Widgets") {
                Text("Long-press your home screen, choose Widgets and look for \"360 Monitor\": Status overview, Websites and Servers.", style = MaterialTheme.typography.bodyMedium)
                OutlinedButton({
                    Scheduler.refreshNow(ctx)
                    scope.launch { WidgetUpdater.updateAll(ctx); snack.showSnackbar("Refreshing widgets…") }
                }) { Text("Refresh widgets now") }
            }

            SectionCard(title = "Android Auto") {
                Text(
                    "In Android Auto the app appears as a media app: browse servers, websites and problems and tap an entry to hear its status. Alerts are also sent as messages. The app is not from the Play Store, so in Android Auto tap Version 10 times, open Developer settings, enable \"Unknown sources\" and tick 360 Monitor under Customize launcher. More → Diagnostics shows whether the car ever connected.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            SectionCard(title = "About") {
                Text("360 Monitor ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleSmall)
                Text("App created by Manuel Gatterer", style = MaterialTheme.typography.bodyMedium)
                Text("Disclaimer", style = MaterialTheme.typography.labelLarge)
                Text(DISCLAIMER, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton({
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://docs.360monitoring.com/reference/introduction-to-the-360-monitoring-api")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }) { Text("API documentation") }
                TextButton({
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/mlgtcode/360-monitor-app")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }) { Text("Source code on GitHub") }
            }

            SectionCard(title = "Privacy") {
                Text(PRIVACY, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (editKey) ConnectionDialog(prefs, onDismiss = { editKey = false }) { key, url ->
        update { it.copy(apiKey = key, baseUrl = url) }
        editKey = false
        vm.refresh()
    }
    if (confirmSignOut) AlertDialog(
        onDismissRequest = { confirmSignOut = false },
        title = { Text("Sign out?") },
        text = { Text("Your API key, settings and cached data will be removed from this device.") },
        confirmButton = {
            TextButton({
                confirmSignOut = false
                vm.resetData()
                vm.settings.signOut()
                Scheduler.apply(ctx, vm.settings.current)
                scope.launch { WidgetUpdater.updateAll(ctx) }
                onSignedOut()
            }) { Text("Sign out", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton({ confirmSignOut = false }) { Text("Cancel") } },
    )
}

private const val DISCLAIMER =
    "This app is an independent, unofficial client for the 360 Monitoring API. It is not affiliated with, " +
        "endorsed by or sponsored by 360 Monitoring, WebPros or Plesk; all trademarks belong to their respective owners.\n\n" +
        "The app is provided \"as is\", without warranty of any kind. Use it at your own risk. The author is not liable " +
        "for any damage, data loss, missed alerts or costs that result from using it. Do not rely on this app as your " +
        "only monitoring or alerting channel.\n\n" +
        "Your API key is stored on this device and is only sent to the API endpoint you configured."

private const val PRIVACY =
    "This app has no account, no analytics, no advertising and no tracking, and it does not talk to any server of its own. " +
        "The only network traffic goes to the API endpoint you configured (by default api.monitoring360.io).\n\n" +
        "Your API key is kept in encrypted storage on this device. Servers, websites and settings are cached locally so that " +
        "widgets, notifications and Android Auto can show them. Signing out deletes the key, the cache and all settings.\n\n" +
        "Exports and archives are written only to the place you choose, and sharing a file or chart link is always your decision. " +
        "Contact data in exports includes e-mail addresses and phone numbers, so store those files carefully. App data is excluded from Android's automatic cloud backup."

private fun maskKey(k: String) = if (k.length <= 6) "••••" else "••••••" + k.takeLast(4)

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onChange)
    }
}

@Composable
private fun ThresholdSlider(label: String, value: Int, range: ClosedFloatingPointRange<Float>, onChange: (Int) -> Unit) {
    Column {
        Text("$label from $value%", style = MaterialTheme.typography.labelLarge)
        Slider(value.toFloat(), { onChange(it.toInt()) }, valueRange = range, steps = ((range.endInclusive - range.start) / 5).toInt() - 1)
    }
}

@Composable
fun ConnectionDialog(prefs: Prefs, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var key by remember { mutableStateOf("") }
    var url by remember { mutableStateOf(prefs.baseUrl) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Connection") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Field(key, { key = it.trim() }, "New API key", password = true, supporting = "Leave empty to keep the current key")
                Field(url, { url = it.trim() }, "API endpoint", supporting = "Default: ${Prefs.DEFAULT_BASE_URL}")
            }
        },
        confirmButton = {
            TextButton({ onSave(key.ifBlank { prefs.apiKey }, url.ifBlank { Prefs.DEFAULT_BASE_URL }) }, enabled = url.startsWith("https://")) { Text("Save") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}
