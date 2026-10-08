@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.monitor360.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.monitor360.data.AlertEvent
import app.monitor360.data.Server
import app.monitor360.data.Status
import app.monitor360.data.WindowOwner
import app.monitor360.ui.AppViewModel
import app.monitor360.ui.Range
import app.monitor360.ui.components.ChipsRow
import app.monitor360.ui.components.ChoiceChips
import app.monitor360.ui.components.ClickableCard
import app.monitor360.ui.components.EmptyState
import app.monitor360.ui.components.ErrorState
import app.monitor360.ui.components.FavoriteButton
import app.monitor360.ui.components.Field
import app.monitor360.ui.components.KeyValue
import app.monitor360.ui.components.Load
import app.monitor360.ui.components.LoadContent
import app.monitor360.ui.components.LoadingBox
import app.monitor360.ui.components.LocalSnackbar
import app.monitor360.ui.components.MetricChart
import app.monitor360.ui.components.OverflowMenu
import app.monitor360.ui.components.ScreenScaffold
import app.monitor360.ui.components.SectionCard
import app.monitor360.ui.components.StatusChip
import app.monitor360.ui.components.StatusDot
import app.monitor360.ui.components.UsageBar
import app.monitor360.ui.components.metricLabel
import app.monitor360.ui.components.rememberLoad
import app.monitor360.ui.fmtBytes
import app.monitor360.ui.fmtDateTime
import app.monitor360.ui.fmtDuration
import app.monitor360.ui.fmtKb
import app.monitor360.ui.nowSeconds
import app.monitor360.ui.theme.StatusColors
import app.monitor360.ui.timeAgo
import app.monitor360.ui.userMessage
import kotlinx.coroutines.launch

private enum class ServerSort(val label: String) {
    NAME("Name"), STATUS("Status (problems first)"), CPU("CPU usage"), MEM("Memory usage"), DISK("Disk usage")
}

@Composable
fun ServersScreen(vm: AppViewModel, onOpen: (String) -> Unit) {
    val state by vm.servers.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val tag by vm.tagFilter.collectAsStateWithLifecycle()
    var sort by rememberSaveable { mutableStateOf(ServerSort.STATUS) }
    var sortMenu by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }

    ScreenScaffold(
        title = "Servers",
        actions = {
            Column {
                TextButton({ sortMenu = true }) { Text("Sort: ${sort.label.substringBefore(" (")}") }
                DropdownMenu(sortMenu, { sortMenu = false }) {
                    ServerSort.entries.forEach { s -> DropdownMenuItem({ Text(s.label) }, { sort = s; sortMenu = false }) }
                }
            }
        },
        floatingActionButton = { FloatingActionButton({ showAdd = true }) { Icon(Icons.Default.Add, "Add server") } },
    ) { pad ->
        PullToRefreshBox(refreshing, vm::refresh, Modifier.padding(pad)) {
            when (val st = state) {
                is Load.Loading -> LoadingBox()
                is Load.Err -> ErrorState(st.message, vm::refresh)
                is Load.Ok -> {
                    val all = st.data
                    val tags = all.flatMap { it.tags }.distinct().sorted()
                    val list = all
                        .filter { s -> tag == null || tag in s.tags }
                        .filter { s -> query.isBlank() || s.name.contains(query, true) || (s.ip ?: "").contains(query, true) || s.tags.any { it.contains(query, true) } }
                        .let { l ->
                            when (sort) {
                                ServerSort.NAME -> l.sortedBy { it.name.lowercase() }
                                ServerSort.STATUS -> l.sortedWith(compareBy({ statusRank(it.status) }, { it.name.lowercase() }))
                                ServerSort.CPU -> l.sortedByDescending { it.cpuPercent ?: -1.0 }
                                ServerSort.MEM -> l.sortedByDescending { it.memPercent ?: -1.0 }
                                ServerSort.DISK -> l.sortedByDescending { it.diskPercent ?: -1.0 }
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
                                placeholder = { Text("Search servers, IPs, tags") },
                                leadingIcon = { Icon(Icons.Default.Search, null) },
                                singleLine = true, shape = RoundedCornerShape(28.dp),
                            )
                        }
                        if (tags.isNotEmpty()) item {
                            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    item { FilterChip(tag == null, { vm.tagFilter.value = null }, { Text("All (${all.size})") }) }
                                    items(tags) { t -> FilterChip(tag == t, { vm.tagFilter.value = if (tag == t) null else t }, { Text(t) }) }
                                }
                            }
                        }
                        if (list.isEmpty()) item { EmptyState(if (all.isEmpty()) "No servers yet. Tap + to learn how to add one." else "No servers match your filter.") }
                        items(list, key = { it.id }) { s ->
                            ServerCard(s, s.id in prefs.favorites, prefs.cpuWarn, prefs.memWarn, prefs.diskWarn, prefs.compactLists, prefs.showIds) { onOpen(s.id) }
                        }
                    }
                }
            }
        }
    }
    if (showAdd) AddServerDialog { showAdd = false }
}

@Composable
private fun ServerCard(s: Server, favorite: Boolean, cpuWarn: Int, memWarn: Int, diskWarn: Int, compact: Boolean, showId: Boolean, onClick: () -> Unit) {
    ClickableCard(onClick) {
        Column(Modifier.padding(if (compact) 12.dp else 16.dp), verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(s.status)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(s.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (favorite) Icon(Icons.Default.Star, "Favorite", tint = StatusColors.warn(), modifier = Modifier.padding(start = 6.dp).size(16.dp))
                    }
                    Text(
                        listOfNotNull(s.ip, s.os).joinToString(" · ").ifBlank { "–" } + if (showId) " · ${s.id}" else "",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                StatusChip(s.status)
            }
            if (!compact) {
                UsageBar("CPU", s.cpuPercent, cpuWarn)
                UsageBar("Memory", s.memPercent, memWarn)
                UsageBar("Disk", s.diskPercent, diskWarn)
            } else {
                Text(
                    "CPU ${s.cpuPercent?.toInt() ?: "–"}% · MEM ${s.memPercent?.toInt() ?: "–"}% · DISK ${s.diskPercent?.toInt() ?: "–"}%",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ChipsRow(s.tags)
        }
    }
}

@Composable
private fun AddServerDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a server") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Servers are added by installing the 360 Monitoring agent on the machine; the API has no endpoint to create servers.")
                Text("1. Open the 360 Monitoring web app and click \"Add Server\".\n2. Run the shown install command as root on a Linux server (or run the installer on Windows).\n3. The server appears here within a few minutes.")
                Text("Once it is listed you can rename it, manage its tags and maintenance windows from this app.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Button({
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://app.360monitoring.com")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                onDismiss()
            }) { Text("Open web app") }
        },
        dismissButton = {
            TextButton({
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://docs.360monitoring.com/docs/360-monitoring-installation")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }) { Text("Install guide") }
        },
    )
}

@Composable
fun ServerDetailScreen(vm: AppViewModel, id: String, onBack: () -> Unit) {
    val serversState by vm.servers.collectAsStateWithLifecycle()
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val cached = (serversState as? Load.Ok)?.data?.firstOrNull { it.id == id }
    val handle = rememberLoad(id) { vm.repo.server(id) }
    val server = handle.dataOrNull ?: cached
    var tab by rememberSaveable { mutableStateOf(0) }
    var edit by remember { mutableStateOf(false) }
    val snack = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    ScreenScaffold(
        title = server?.name ?: "Server",
        onBack = onBack,
        actions = {
            if (server != null) {
                FavoriteButton(id in prefs.favorites) { vm.toggleFavorite(id) }
                IconButton({ edit = true }) { Icon(Icons.Default.Edit, "Edit") }
                OverflowMenu(
                    listOf(
                        "Refresh" to { handle.reload(); vm.refresh() },
                        "Share status" to { shareText(ctx, "${server.name} status", serverSummary(server)) },
                    ),
                )
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (server == null) {
                when (val v = handle.value) {
                    is Load.Err -> ErrorState(v.message, handle.reload)
                    else -> LoadingBox()
                }
                return@Column
            }
            ScrollableTabRow(tab, edgePadding = 8.dp, containerColor = MaterialTheme.colorScheme.background) {
                listOf("Overview", "Metrics", "Alerts", "Maintenance").forEachIndexed { i, t ->
                    Tab(tab == i, { tab = i }, text = { Text(t, maxLines = 1) })
                }
            }
            when (tab) {
                0 -> ServerOverview(server, prefs.cpuWarn, prefs.memWarn, prefs.diskWarn, prefs.showIds)
                1 -> MetricsTab(server, vm)
                2 -> AlertsTab(id, vm)
                else -> WindowsTab(WindowOwner.SERVER, id, vm)
            }
        }
    }
    if (edit && server != null) {
        EditServerDialog(server, onDismiss = { edit = false }) { name, tags ->
            scope.launch {
                try {
                    vm.repo.updateServer(server.id, name, tags)
                    snack.showSnackbar("Server updated")
                    edit = false
                    handle.reload(); vm.refresh()
                } catch (e: Exception) {
                    snack.showSnackbar(e.userMessage())
                }
            }
        }
    }
}

@Composable
private fun ServerOverview(s: Server, cpuWarn: Int, memWarn: Int, diskWarn: Int, showId: Boolean) {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(s.name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        s.statusRaw?.let { "Status: $it" } ?: "Status unknown",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusChip(s.status)
            }
            ChipsRow(s.tags)
        }
        SectionCard(title = "Resource usage") {
            UsageBar("CPU", s.cpuPercent, cpuWarn, detail = s.cores?.let { "$it cores" })
            UsageBar(
                "Memory", s.memPercent, memWarn,
                detail = if (s.memTotalKb != null && s.memUsedKb != null) "${fmtKb(s.memUsedKb)} / ${fmtKb(s.memTotalKb)}" else null,
            )
            UsageBar("Disk (overall)", s.diskPercent, diskWarn)
            s.disks.forEach { d ->
                UsageBar(d.mount, d.usedPercent, diskWarn, detail = "${fmtBytes(d.usedBytes)} / ${fmtBytes(d.total)}")
            }
        }
        SectionCard(title = "Details") {
            KeyValue("Uptime", s.uptimeSeconds?.let { fmtDuration(it) })
            KeyValue("Operating system", s.os)
            KeyValue("Agent version", s.agentVersion)
            KeyValue("IP address", s.ip)
            KeyValue("Country", s.country)
            KeyValue("Provider", s.org)
            KeyValue("Last update", s.lastUpdate?.let { timeAgo(it) })
            if (showId) KeyValue("Server ID", s.id)
        }
        if (s.plugins.isNotEmpty()) SectionCard(title = "Reporting plugins") { ChipsRow(s.plugins) }
    }
}

private val BASE_METRICS = listOf("cpu", "mem", "swap", "network", "process", "uptime", "ping")
private val METRIC_ALIASES = mapOf("memory" to "mem", "net" to "network")

private fun availableMetrics(server: Server): List<String> =
    (BASE_METRICS + server.plugins.map { METRIC_ALIASES[it] ?: it }).distinct()

private data class MetricQuery(val metric: String, val keys: String = "", val devices: String = "")

@Composable
private fun MetricsTab(server: Server, vm: AppViewModel) {
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    var query by rememberSaveable(stateSaver = metricQuerySaver) { mutableStateOf(MetricQuery("cpu")) }
    var range by rememberSaveable { mutableStateOf(Range.forHours(prefs.defaultRangeHours)) }
    var cores by rememberSaveable { mutableStateOf(false) }
    var customDialog by remember { mutableStateOf(false) }
    val metrics = remember(server.id, server.plugins) { availableMetrics(server) }
    val ctx = LocalContext.current
    val snack = LocalSnackbar.current
    val scope = rememberCoroutineScope()

    val handle = rememberLoad(server.id, query, range, cores) {
        val end = nowSeconds()
        vm.repo.serverMetrics(server.id, query.metric, end - range.seconds, end, cores, query.keys, query.devices)
    }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ChoiceChips(Range.entries, range, { it.label }, { range = it })

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(metrics) { m ->
                FilterChip(
                    selected = query.metric == m && query.keys.isEmpty() && query.devices.isEmpty(),
                    onClick = { query = MetricQuery(m) },
                    label = { Text(metricLabel(m)) },
                )
            }
            item {
                val custom = query.metric !in metrics || query.keys.isNotEmpty() || query.devices.isNotEmpty()
                FilterChip(
                    selected = custom,
                    onClick = { customDialog = true },
                    label = { Text(if (custom) metricLabel(query.metric) + " ✎" else "Custom…") },
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (query.metric == "cpu") FilterChip(cores, { cores = !cores }, { Text("Per-core breakdown") })
            OutlinedButton({
                scope.launch {
                    try {
                        val end = nowSeconds()
                        val url = vm.repo.serverChartImage(server.id, query.metric, end - range.seconds, end, query.keys, query.devices)
                        shareText(ctx, "${server.name} ${metricLabel(query.metric)}", url)
                    } catch (e: Exception) {
                        snack.showSnackbar(e.userMessage())
                    }
                }
            }) { Text("Share chart image") }
        }

        LoadContent(handle) { res ->
            if (res.charts.isEmpty()) {
                EmptyState(
                    "No data for \"${query.metric}\" in this period. The agent may not report this metric on this server" +
                        " (or it needs specific keys or devices, see Custom…).",
                )
            }
            res.charts.forEach { c ->
                SectionCard(title = metricLabel(c.title)) { MetricChart(c) }
            }
            res.granularitySeconds?.let {
                val step = if (it >= 3600) "${it / 3600} h" else if (it >= 60) "${it / 60} min" else "$it s"
                Text(
                    "Resolution: $step per point",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (customDialog) {
        CustomMetricDialog(server, query, onDismiss = { customDialog = false }) {
            query = it
            customDialog = false
        }
    }
}

private val metricQuerySaver = androidx.compose.runtime.saveable.listSaver<MetricQuery, String>(
    save = { listOf(it.metric, it.keys, it.devices) },
    restore = { MetricQuery(it[0], it[1], it[2]) },
)

@Composable
private fun CustomMetricDialog(server: Server, current: MetricQuery, onDismiss: () -> Unit, onApply: (MetricQuery) -> Unit) {
    var metric by remember { mutableStateOf(current.metric) }
    var keys by remember { mutableStateOf(current.keys) }
    var devices by remember { mutableStateOf(current.devices) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom metric") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Field(metric, { metric = it.trim() }, "Metric", placeholder = "e.g. mysql, nginx, df")
                val known = remember(metric) { server.entryFor(metric) }
                if (known.isNotEmpty()) {
                    Text("Found in the agent data", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        known.forEach { m -> FilterChip(m == metric, { metric = m }, { Text(metricLabel(m)) }) }
                    }
                }
                Field(keys, { keys = it.trim() }, "Keys (optional)", placeholder = "requests_per_second:RPS,waiting", supporting = "Comma separated; a name after the colon is used as label")
                server.lookup(server.fields, metric)?.let { names ->
                    ChipPicker(names.map { it to it }, keys) { keys = it }
                }
                Field(devices, { devices = it.trim() }, "Devices (optional)", placeholder = "0,1,3", supporting = "Comma separated sub devices such as disks or interfaces")
                server.lookup(server.devices, metric)?.let { found ->
                    ChipPicker(found.map { it.id to it.label }, devices) { devices = it }
                }
            }
        },
        confirmButton = { TextButton({ onApply(MetricQuery(metric, keys, devices)) }, enabled = metric.isNotBlank()) { Text("Show") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

private fun Server.entryFor(metric: String): List<String> =
    (devices.keys + fields.keys).distinct().filter { it != metric }

private fun <T> Server.lookup(source: Map<String, T>, metric: String): T? {
    val name = metric.lowercase()
    val alias = METRIC_ALIASES.entries.firstOrNull { it.key == name || it.value == name }
    return source[metric] ?: source[name] ?: alias?.let { source[it.key] ?: source[it.value] }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipPicker(options: List<Pair<String, String>>, selected: String, onChange: (String) -> Unit) {
    val chosen = selected.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.take(40).forEach { (id, label) ->
            val on = chosen.any { it.substringBefore(':') == id }
            FilterChip(on, {
                val next = if (on) chosen.filterNot { it.substringBefore(':') == id } else chosen + id
                onChange(next.joinToString(","))
            }, { Text(label) })
        }
    }
}

@Composable
fun AlertList(events: List<AlertEvent>, emptyText: String) {
    if (events.isEmpty()) {
        EmptyState(emptyText)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        events.forEach { e ->
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusChip(if (e.open) Status.DOWN else Status.UP, if (e.open) "Open" else "Resolved")
                    Spacer(Modifier.width(10.dp))
                    Text(fmtDateTime(e.start), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(e.summary, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                val dur = if (e.start != null) fmtDuration(((e.end ?: nowSeconds()) - e.start).coerceAtLeast(0)) else null
                KeyValue("Duration", dur?.let { if (e.open) "$it (ongoing)" else it })
                KeyValue("Metric", e.metric?.let { it + (e.comparison?.let { c -> " ($c)" } ?: "") })
                KeyValue("Notified", e.recipients.map { it.name.ifBlank { it.type } + " (${it.type})" }.distinct().joinToString().ifBlank { null })
            }
        }
    }
}

@Composable
private fun AlertsTab(id: String, vm: AppViewModel) {
    var range by rememberSaveable { mutableStateOf(Range.D30) }
    val handle = rememberLoad(id, range) {
        val end = nowSeconds()
        vm.repo.serverNotifications(id, end - range.seconds, end)
    }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ChoiceChips(listOf(Range.H24, Range.D7, Range.D30), range, { it.label }, { range = it })
        LoadContent(handle) { AlertList(it, "No alerts in this period. 🎉") }
    }
}

@Composable
private fun EditServerDialog(server: Server, onDismiss: () -> Unit, onSave: (String, List<String>) -> Unit) {
    var name by remember { mutableStateOf(server.name) }
    var tags by remember { mutableStateOf(server.tags.joinToString(", ")) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit server") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Field(name, { name = it }, "Name")
                Field(tags, { tags = it }, "Tags", supporting = "Comma separated, e.g. web, mysql")
            }
        },
        confirmButton = {
            TextButton(
                { onSave(name.trim(), tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }) },
                enabled = name.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}
