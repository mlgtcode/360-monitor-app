@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.monitor360.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.monitor360.data.Monitor
import app.monitor360.data.Status
import app.monitor360.data.WindowOwner
import app.monitor360.ui.AppViewModel
import app.monitor360.ui.Range
import app.monitor360.ui.components.ChoiceChips
import app.monitor360.ui.components.ClickableCard
import app.monitor360.ui.components.ConfirmDialog
import app.monitor360.ui.components.EmptyState
import app.monitor360.ui.components.ErrorState
import app.monitor360.ui.components.FavoriteButton
import app.monitor360.ui.components.KeyValue
import app.monitor360.ui.components.Load
import app.monitor360.ui.components.LoadContent
import app.monitor360.ui.components.LoadingBox
import app.monitor360.ui.components.LocalSnackbar
import app.monitor360.ui.components.MetricChart
import app.monitor360.ui.components.OverflowMenu
import app.monitor360.ui.components.ScreenScaffold
import app.monitor360.ui.components.SectionCard
import app.monitor360.ui.components.StatTile
import app.monitor360.ui.components.StatusChip
import app.monitor360.ui.components.StatusDot
import app.monitor360.ui.components.rememberLoad
import app.monitor360.ui.fmtDuration
import app.monitor360.ui.fmtPercent
import app.monitor360.ui.fmtSeconds
import app.monitor360.ui.fmtUptime
import app.monitor360.ui.nowSeconds
import app.monitor360.ui.theme.StatusColors
import app.monitor360.ui.timeAgo
import app.monitor360.ui.userMessage
import kotlinx.coroutines.launch

private enum class MonitorFilter(val label: String) { ALL("All"), DOWN("Down"), UP("Up") }
private enum class MonitorSort(val label: String) { STATUS("Problems first"), NAME("Name"), UPTIME("Uptime"), SPEED("Slowest first") }

@Composable
fun WebsitesScreen(vm: AppViewModel, onOpen: (String) -> Unit, onAdd: () -> Unit) {
    val state by vm.monitors.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(MonitorFilter.ALL) }
    var sort by rememberSaveable { mutableStateOf(MonitorSort.STATUS) }

    ScreenScaffold(
        title = "Websites",
        floatingActionButton = { FloatingActionButton(onAdd) { Icon(Icons.Default.Add, "Add website") } },
    ) { pad ->
        PullToRefreshBox(refreshing, vm::refresh, Modifier.padding(pad)) {
            when (val st = state) {
                is Load.Loading -> LoadingBox()
                is Load.Err -> ErrorState(st.message, vm::refresh)
                is Load.Ok -> {
                    val all = st.data
                    val list = all
                        .filter { when (filter) { MonitorFilter.ALL -> true; MonitorFilter.DOWN -> it.status == Status.DOWN; MonitorFilter.UP -> it.status == Status.UP } }
                        .filter { query.isBlank() || it.title.contains(query, true) || it.url.contains(query, true) }
                        .let { l ->
                            when (sort) {
                                MonitorSort.STATUS -> l.sortedWith(compareBy({ statusRank(it.status) }, { it.title.lowercase() }))
                                MonitorSort.NAME -> l.sortedBy { it.title.lowercase() }
                                MonitorSort.UPTIME -> l.sortedBy { it.uptimePercent ?: 101.0 }
                                MonitorSort.SPEED -> l.sortedByDescending { it.ttfb ?: -1.0 }
                            }
                        }
                        .sortedBy { it.id !in prefs.favorites }
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(if (prefs.compactLists) 6.dp else 12.dp),
                    ) {
                        item {
                            OutlinedTextField(
                                query, { query = it }, Modifier.fillMaxWidth(),
                                placeholder = { Text("Search websites") },
                                leadingIcon = { Icon(Icons.Default.Search, null) },
                                singleLine = true, shape = RoundedCornerShape(28.dp),
                            )
                        }
                        item {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(MonitorFilter.entries) { f ->
                                    val n = when (f) { MonitorFilter.ALL -> all.size; MonitorFilter.DOWN -> all.count { it.status == Status.DOWN }; MonitorFilter.UP -> all.count { it.status == Status.UP } }
                                    FilterChip(filter == f, { filter = f }, { Text("${f.label} ($n)") })
                                }
                                items(MonitorSort.entries) { s ->
                                    FilterChip(sort == s, { sort = s }, { Text(s.label) })
                                }
                            }
                        }
                        if (list.isEmpty()) item { EmptyState(if (all.isEmpty()) "No websites monitored yet. Tap + to add one." else "No websites match your filter.") }
                        items(list, key = { it.id }) { m ->
                            MonitorCard(m, m.id in prefs.favorites, prefs.compactLists, prefs.showIds, prefs.uptimeWarn) { onOpen(m.id) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonitorCard(m: Monitor, favorite: Boolean, compact: Boolean, showId: Boolean, uptimeWarn: Double, onClick: () -> Unit) {
    ClickableCard(onClick) {
        Column(Modifier.padding(if (compact) 12.dp else 16.dp), verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(m.status)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(m.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (favorite) Icon(Icons.Default.Star, "Favorite", tint = StatusColors.warn(), modifier = Modifier.padding(start = 6.dp).size(16.dp))
                    }
                    Text(
                        m.url + if (showId) " · ${m.id}" else "",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                StatusChip(m.status, m.statusMessage?.takeIf { m.status == Status.DOWN && it.length <= 16 })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                val low = (m.uptimePercent ?: 100.0) < uptimeWarn
                Text("Uptime ${fmtUptime(m.uptimePercent)}", style = MaterialTheme.typography.labelMedium, color = if (low) StatusColors.warn() else MaterialTheme.colorScheme.onSurfaceVariant)
                Text("TTFB ${fmtSeconds(m.ttfb)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                m.locationName?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

@Composable
fun WebsiteDetailScreen(vm: AppViewModel, id: String, onBack: () -> Unit, onEdit: (String) -> Unit) {
    val listState by vm.monitors.collectAsStateWithLifecycle()
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val cached = (listState as? Load.Ok)?.data?.firstOrNull { it.id == id }
    val handle = rememberLoad(id) { vm.repo.monitor(id) }
    val monitor = handle.dataOrNull ?: cached
    var tab by rememberSaveable { mutableStateOf(0) }
    var confirmDelete by remember { mutableStateOf(false) }
    val snack = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    ScreenScaffold(
        title = monitor?.title ?: "Website",
        onBack = onBack,
        actions = {
            if (monitor != null) {
                FavoriteButton(id in prefs.favorites) { vm.toggleFavorite(id) }
                IconButton({ onEdit(id) }) { Icon(Icons.Default.Edit, "Edit") }
                OverflowMenu(
                    listOf(
                        "Refresh" to { handle.reload(); vm.refresh() },
                        "Share status" to { shareText(ctx, "${monitor.title} status", monitorSummary(monitor)) },
                        "Delete" to { confirmDelete = true },
                    ),
                )
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (monitor == null) {
                when (val v = handle.value) {
                    is Load.Err -> ErrorState(v.message, handle.reload)
                    else -> LoadingBox()
                }
                return@Column
            }
            ScrollableTabRow(tab, edgePadding = 8.dp, containerColor = MaterialTheme.colorScheme.background) {
                listOf("Overview", "Performance", "Events", "Maintenance").forEachIndexed { i, t ->
                    Tab(tab == i, { tab = i }, text = { Text(t, maxLines = 1) })
                }
            }
            when (tab) {
                0 -> WebsiteOverview(monitor, prefs.uptimeWarn, prefs.showIds)
                1 -> WebsitePerformance(monitor, vm)
                2 -> WebsiteEvents(id, vm)
                else -> WindowsTab(WindowOwner.MONITOR, id, vm)
            }
        }
    }
    if (confirmDelete && monitor != null) {
        ConfirmDialog(
            "Delete website monitor?",
            "\"${monitor.title}\" will stop being monitored and its history is removed. This cannot be undone.",
            onConfirm = {
                confirmDelete = false
                scope.launch {
                    try {
                        vm.repo.deleteMonitor(id)
                        vm.refresh()
                        snack.showSnackbar("Monitor deleted")
                        onBack()
                    } catch (e: Exception) {
                        snack.showSnackbar(e.userMessage())
                    }
                }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun WebsiteOverview(m: Monitor, uptimeWarn: Double, showId: Boolean) {
    val ctx = LocalContext.current
    val openable = m.protocol?.lowercase() in listOf("http", "https", null)
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(m.title, style = MaterialTheme.typography.titleLarge)
                    Text(m.url, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StatusChip(m.status)
            }
            m.statusMessage?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = StatusColors.of(m.status)) }
            if (openable && m.url.isNotBlank()) {
                OutlinedButton({
                    val scheme = m.protocol?.lowercase() ?: "https"
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$scheme://${m.url}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }) { Text("Open website") }
            }
        }

        if (m.uptimes.isNotEmpty()) SectionCard(title = "Uptime") {
            m.uptimes.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { u ->
                        StatTile(
                            u.label.replaceFirstChar { it.uppercase() }, fmtPercent(u.percent, 2),
                            if (u.percent < uptimeWarn) StatusColors.warn() else StatusColors.up(), Modifier.weight(1f),
                            sub = if (u.events > 0) "${u.events} events · ${fmtDuration(u.downtimeSeconds)} down" else "no downtime",
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        } else m.uptimePercent?.let {
            SectionCard(title = "Uptime") { StatTile("Overall", fmtPercent(it, 3), if (it < uptimeWarn) StatusColors.warn() else StatusColors.up()) }
        }

        SectionCard(title = "Last check") {
            val total = m.timeTotal ?: 0.0
            listOf("DNS lookup" to m.timeDns, "Connect" to m.timeConnect, "Time to first byte" to m.ttfb, "Total" to m.timeTotal).forEach { (label, v) ->
                if (v != null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(fmtSeconds(v), style = MaterialTheme.typography.labelLarge)
                    }
                    if (total > 0) LinearProgressIndicator(
                        progress = { (v / total).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                        gapSize = 0.dp, drawStopIndicator = {},
                    )
                }
            }
            KeyValue("HTTP status", m.httpCode)
        }

        SectionCard(title = "Details") {
            KeyValue("Check type", m.protocol?.uppercase())
            KeyValue("Location", m.locationName)
            KeyValue("Resolved IP", m.ip)
            KeyValue("Last check", m.lastUpdate?.let { timeAgo(it) })
            KeyValue("Monitoring since", m.firstUpdate?.let { app.monitor360.ui.fmtFull(it) })
            if (showId) KeyValue("Monitor ID", m.id)
        }
    }
}

@Composable
private fun WebsitePerformance(m: Monitor, vm: AppViewModel) {
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    var range by rememberSaveable { mutableStateOf(Range.forHours(prefs.defaultRangeHours)) }
    val chart = rememberLoad(m.id, range) {
        val end = nowSeconds()
        vm.repo.monitorMetrics(m.id, end - range.seconds, end)
    }
    val uptime = rememberLoad(m.id, range, "uptime") {
        val end = nowSeconds()
        vm.repo.monitorUptime(m.id, end - range.seconds, end)
    }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ChoiceChips(Range.entries, range, { it.label }, { range = it })
        uptime.dataOrNull?.let { u ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Uptime", fmtPercent(u.percent, 3), if (u.percent < prefs.uptimeWarn) StatusColors.warn() else StatusColors.up(), Modifier.weight(1f))
                StatTile("Downtime", fmtDuration(u.downtimeSeconds), if (u.downtimeSeconds > 0) StatusColors.down() else MaterialTheme.colorScheme.onSurface, Modifier.weight(1f), sub = "${u.events} events")
            }
        }
        LoadContent(chart) { res ->
            if (res.charts.isEmpty()) EmptyState("No response time data for this period.")
            res.charts.forEach { c -> SectionCard(title = "Response time") { MetricChart(c) } }
        }
    }
}

@Composable
private fun WebsiteEvents(id: String, vm: AppViewModel) {
    var range by rememberSaveable { mutableStateOf(Range.D30) }
    val handle = rememberLoad(id, range) {
        val end = nowSeconds()
        vm.repo.monitorNotifications(id, end - range.seconds, end)
    }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ChoiceChips(listOf(Range.H24, Range.D7, Range.D30), range, { it.label }, { range = it })
        LoadContent(handle) { AlertList(it, "No downtime events in this period.") }
    }
}
