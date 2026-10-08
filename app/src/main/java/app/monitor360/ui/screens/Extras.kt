package app.monitor360.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.monitor360.BuildConfig
import app.monitor360.car.CarConnection
import app.monitor360.data.AlertEvent
import app.monitor360.data.Collected
import app.monitor360.data.Source
import app.monitor360.data.allSources
import app.monitor360.data.collectFromSources
import app.monitor360.data.MaintenanceWindow
import app.monitor360.data.Status
import app.monitor360.data.WindowOwner
import app.monitor360.ui.AppViewModel
import app.monitor360.ui.Range
import app.monitor360.ui.components.ChoiceChips
import app.monitor360.ui.components.ClickableCard
import app.monitor360.ui.components.EmptyState
import app.monitor360.ui.components.FavoriteButton
import app.monitor360.ui.components.KeyValue
import app.monitor360.ui.components.Load
import app.monitor360.ui.components.LoadContent
import app.monitor360.ui.components.LocalSnackbar
import app.monitor360.ui.components.ScreenScaffold
import app.monitor360.ui.components.SectionCard
import app.monitor360.ui.components.StatusChip
import app.monitor360.ui.components.StatusDot
import app.monitor360.ui.components.rememberLoad
import app.monitor360.ui.fmtDateTime
import app.monitor360.ui.fmtDuration
import app.monitor360.ui.fmtPercent
import app.monitor360.ui.fmtSeconds
import app.monitor360.ui.nowSeconds
import app.monitor360.ui.theme.StatusColors
import app.monitor360.ui.userMessage
import app.monitor360.work.Notifier
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

private const val MAX_SOURCES = 40

private suspend fun <T> collect(vm: AppViewModel, fetch: suspend (Source) -> List<T>): Collected<T> {
    val sources = vm.repo.allSources((vm.servers.value as? Load.Ok)?.data, (vm.monitors.value as? Load.Ok)?.data)
    return collectFromSources(sources, MAX_SOURCES, fetch = fetch)
}

@Composable
private fun CollectionNotes(failed: Int, skipped: Int) {
    if (failed > 0) {
        Text("$failed source${if (failed == 1) "" else "s"} could not be loaded.", style = MaterialTheme.typography.bodySmall, color = StatusColors.warn())
    }
    if (skipped > 0) {
        Text("Only the first $MAX_SOURCES sources are checked to save API requests ($skipped skipped).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun open(source: Source, onServer: (String) -> Unit, onMonitor: (String) -> Unit) {
    if (source.owner == WindowOwner.SERVER) onServer(source.id) else onMonitor(source.id)
}

@Composable
fun AlertsOverviewScreen(vm: AppViewModel, onBack: () -> Unit, onServer: (String) -> Unit, onMonitor: (String) -> Unit) {
    var range by rememberSaveable { mutableStateOf(Range.D7) }
    var openOnly by rememberSaveable { mutableStateOf(false) }
    val handle = rememberLoad(range) {
        val end = nowSeconds()
        collect(vm) { source ->
            if (source.owner == WindowOwner.SERVER) vm.repo.serverNotifications(source.id, end - range.seconds, end)
            else vm.repo.monitorNotifications(source.id, end - range.seconds, end)
        }
    }

    ScreenScaffold(
        "Alert history", onBack,
        actions = { IconButton(handle.reload) { Icon(Icons.Default.Refresh, "Refresh") } },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ChoiceChips(listOf(Range.H24, Range.D7, Range.D30), range, { it.label }, { range = it })
            ChoiceChips(listOf(false, true), openOnly, { if (it) "Open only" else "All" }, { openOnly = it })
            LoadContent(handle) { result ->
                CollectionNotes(result.failed, result.skipped)
                val rows = result.rows
                    .filter { !openOnly || it.second.open }
                    .sortedWith(compareByDescending<Pair<Source, AlertEvent>> { it.second.open }.thenByDescending { it.second.start ?: 0 })
                if (rows.isEmpty()) EmptyState(if (openOnly) "No open alerts." else "No alerts in this period.")
                rows.forEach { (source, event) ->
                    ClickableCard({ open(source, onServer, onMonitor) }) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StatusChip(if (event.open) Status.DOWN else Status.UP, if (event.open) "Open" else "Resolved")
                                Text(
                                    "  " + fmtDateTime(event.start),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(event.summary, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            val kind = if (source.owner == WindowOwner.SERVER) "Server" else "Website"
                            val duration = event.start?.let { fmtDuration(((event.end ?: nowSeconds()) - it).coerceAtLeast(0)) }
                            Text(
                                listOfNotNull("$kind ${source.name}", duration).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MaintenanceOverviewScreen(vm: AppViewModel, onBack: () -> Unit, onServer: (String) -> Unit, onMonitor: (String) -> Unit) {
    val handle = rememberLoad { collect(vm) { source -> vm.repo.windows(source.owner, source.id) } }

    ScreenScaffold(
        "Maintenance windows", onBack,
        actions = { IconButton(handle.reload) { Icon(Icons.Default.Refresh, "Refresh") } },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Open a server or website to add, change or delete its windows.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LoadContent(handle) { result ->
                CollectionNotes(result.failed, result.skipped)
                if (result.rows.isEmpty()) EmptyState("No maintenance windows are scheduled.")
                result.rows.sortedBy { it.first.name.lowercase() }.forEach { (source, window) ->
                    ClickableCard({ open(source, onServer, onMonitor) }) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(window.name.ifBlank { "Maintenance" }, style = MaterialTheme.typography.titleMedium)
                            Text(
                                source.kind + " " + source.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(describe(window), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

private fun describe(w: MaintenanceWindow): String {
    val time = listOfNotNull(w.startTime, w.endTime).joinToString(" – ")
    val schedule = when (w.interval) {
        "daily" -> "Daily" + (w.days?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: "")
        "monthly" -> "Monthly" + (w.dayOfMonth?.let { ", day $it" } ?: "")
        else -> listOfNotNull(w.startDate, w.endDate).distinct().joinToString(" → ").ifBlank { "Once" }
    }
    return listOf(schedule, time, w.impact?.let { "$it impact" }).filter { !it.isNullOrBlank() }.joinToString(" · ")
}

@Composable
fun TagsScreen(vm: AppViewModel, onBack: () -> Unit, onPick: (String) -> Unit) {
    val state by vm.servers.collectAsStateWithLifecycle()
    ScreenScaffold("Tags", onBack) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val servers = (state as? Load.Ok)?.data.orEmpty()
            val byTag = servers.flatMap { s -> s.tags.map { it to s } }.groupBy({ it.first }, { it.second }).toSortedMap(String.CASE_INSENSITIVE_ORDER)
            if (byTag.isEmpty()) {
                EmptyState("None of your servers has a tag yet. Tags can be added on a server's edit dialog.")
            }
            byTag.forEach { (tag, list) ->
                ClickableCard({ onPick(tag) }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(tag, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${list.size} server${if (list.size == 1) "" else "s"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        val down = list.count { it.status == Status.DOWN }
                        Text("${list.count { it.status == Status.UP }} up", color = StatusColors.up(), style = MaterialTheme.typography.labelLarge)
                        if (down > 0) Text("  $down down", color = StatusColors.down(), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
fun FavoritesScreen(vm: AppViewModel, onBack: () -> Unit, onServer: (String) -> Unit, onMonitor: (String) -> Unit) {
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val servers by vm.servers.collectAsStateWithLifecycle()
    val monitors by vm.monitors.collectAsStateWithLifecycle()
    val favServers = (servers as? Load.Ok)?.data.orEmpty().filter { it.id in prefs.favorites }
    val favMonitors = (monitors as? Load.Ok)?.data.orEmpty().filter { it.id in prefs.favorites }

    ScreenScaffold("Favorites", onBack) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (favServers.isEmpty() && favMonitors.isEmpty()) {
                EmptyState("Star a server or website on its detail page and it shows up here, at the top of the lists and in the widgets.")
            }
            favServers.forEach { s ->
                ClickableCard({ onServer(s.id) }) {
                    Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(s.status)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(s.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "CPU ${fmtPercent(s.cpuPercent)} · Memory ${fmtPercent(s.memPercent)} · Disk ${fmtPercent(s.diskPercent)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        FavoriteButton(true) { vm.toggleFavorite(s.id) }
                    }
                }
            }
            favMonitors.forEach { m ->
                ClickableCard({ onMonitor(m.id) }) {
                    Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(m.status)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(m.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Uptime ${fmtPercent(m.uptimePercent, 2)} · TTFB ${fmtSeconds(m.ttfb)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        FavoriteButton(true) { vm.toggleFavorite(m.id) }
                    }
                }
            }
        }
    }
}

private const val ANDROID_AUTO_PACKAGE = "com.google.android.projection.gearhead"

private fun androidAutoVersion(context: Context): String? = try {
    context.packageManager.getPackageInfo(ANDROID_AUTO_PACKAGE, 0).versionName ?: "installed"
} catch (e: PackageManager.NameNotFoundException) {
    null
}

private val timeFormat = DateFormat.getTimeInstance(DateFormat.MEDIUM)

@Composable
fun DiagnosticsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val snack = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val rate by vm.rateLimit.collectAsStateWithLifecycle()
    val log by vm.requestLog.collectAsStateWithLifecycle()
    var testing by remember { mutableStateOf(false) }
    val autoVersion = remember { androidAutoVersion(ctx) }
    val carRecord = remember { CarConnection.last(ctx) }
    val mediaRecord = remember { CarConnection.lastMedia(ctx) }

    fun report(): String = buildString {
        appendLine("360 Monitor ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Endpoint ${prefs.baseUrl}")
        appendLine("API quota ${rate?.remaining ?: "?"} / ${rate?.limit ?: "?"}")
        appendLine("Android Auto ${autoVersion ?: "not installed"}, car app category POI")
        appendLine("Media browser connection ${mediaRecord?.let { "${it.hostPackage} at ${Date(it.time)}" } ?: "never"}")
        appendLine("Car app connection ${carRecord?.let { "${it.hostPackage} API ${it.apiLevel} at ${Date(it.time)}" } ?: "never"}")
        appendLine()
        appendLine("Recent requests")
        log.take(30).forEach { appendLine("${timeFormat.format(Date(it.time))} ${it.method} ${it.path} -> ${it.code} (${it.millis} ms)${it.error?.let { e -> " $e" } ?: ""}") }
    }

    ScreenScaffold("Diagnostics", onBack) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionCard(title = "Connection") {
                KeyValue("Endpoint", prefs.baseUrl)
                KeyValue("API quota", rate?.let { r -> if (r.remaining != null && r.limit != null) "${r.remaining} / ${r.limit} left this hour" else null })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button({
                        testing = true
                        scope.launch {
                            try {
                                vm.repo.ping()
                                snack.showSnackbar("Connection OK")
                            } catch (e: Exception) {
                                snack.showSnackbar(e.userMessage())
                            }
                            testing = false
                        }
                    }, enabled = !testing) { Text(if (testing) "Testing…" else "Test connection") }
                    OutlinedButton({
                        (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                            .setPrimaryClip(ClipData.newPlainText("360 Monitor report", report()))
                        scope.launch { snack.showSnackbar("Report copied") }
                    }) { Text("Copy report") }
                }
            }

            SectionCard(title = "Android Auto") {
                KeyValue("Android Auto app", autoVersion ?: "not installed")
                KeyValue("Listed as", "media app (Servers, Websites, Needs attention) and car app (POI)")
                KeyValue("Notifications", if (Notifier.canNotify(ctx)) "allowed" else "blocked")
                KeyValue(
                    "Media browser connection",
                    mediaRecord?.let { "${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it.time))} (${it.hostPackage})" }
                        ?: "never connected",
                )
                KeyValue(
                    "Car app connection",
                    carRecord?.let { "${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it.time))} (${it.hostPackage}, API ${it.apiLevel})" }
                        ?: "never connected",
                )
                OutlinedButton({
                    val sent = Notifier.notify(ctx, "test-alert", "Test alert", "This is a test alert from 360 Monitor. If you can read this in Android Auto, alerts will reach you in the car.")
                    scope.launch { snack.showSnackbar(if (sent) "Test alert sent" else "Notifications are blocked for this app") }
                }) { Text("Send test alert") }
                Text(
                    "In Android Auto: Settings → tap Version 10 times → Developer settings → enable Unknown sources. " +
                        "Then open Settings → Customize launcher and make sure 360 Monitor is ticked. Reconnect to the car afterwards.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (autoVersion == null) {
                    Text("The Android Auto app was not found on this phone.", style = MaterialTheme.typography.bodySmall, color = StatusColors.warn())
                }
            }

            SectionCard(title = "Recent requests") {
                if (log.isEmpty()) {
                    Text("No requests yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                log.take(30).forEach { e ->
                    val ok = e.code in 200..299
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            "${timeFormat.format(Date(e.time))}  ${e.method} ${e.path}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "${e.code}  ${e.millis} ms",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = if (ok) StatusColors.up() else StatusColors.down(),
                        )
                    }
                }
            }

            SectionCard(title = "App") {
                KeyValue("Version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                KeyValue("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                KeyValue("Device", "${Build.MANUFACTURER} ${Build.MODEL}")
            }
        }
    }
}
