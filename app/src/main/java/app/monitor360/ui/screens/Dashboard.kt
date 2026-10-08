@file:OptIn(ExperimentalMaterial3Api::class)

package app.monitor360.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.monitor360.data.Monitor
import app.monitor360.data.Server
import app.monitor360.data.Status
import app.monitor360.ui.AppViewModel
import app.monitor360.ui.components.ClickableCard
import app.monitor360.ui.components.EmptyState
import app.monitor360.ui.components.ErrorState
import app.monitor360.ui.components.Load
import app.monitor360.ui.components.LoadingBox
import app.monitor360.ui.components.ScreenScaffold
import app.monitor360.ui.components.SectionCard
import app.monitor360.ui.components.StatTile
import app.monitor360.ui.components.StatusDot
import app.monitor360.ui.components.UsageBar
import app.monitor360.ui.fmtPercent
import app.monitor360.ui.fmtSeconds
import app.monitor360.ui.theme.StatusColors
import app.monitor360.ui.timeAgo

@Composable
fun DashboardScreen(
    vm: AppViewModel,
    onServer: (String) -> Unit,
    onMonitor: (String) -> Unit,
    onOpenServers: () -> Unit,
    onOpenWebsites: () -> Unit,
) {
    val servers by vm.servers.collectAsStateWithLifecycle()
    val monitors by vm.monitors.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val last by vm.lastUpdated.collectAsStateWithLifecycle()
    val rate by vm.rateLimit.collectAsStateWithLifecycle()

    ScreenScaffold(
        title = "Overview",
        actions = { IconButton(vm::refresh) { Icon(Icons.Default.Refresh, "Refresh") } },
    ) { pad ->
        PullToRefreshBox(refreshing, vm::refresh, Modifier.padding(pad)) {
            val s = (servers as? Load.Ok)?.data
            val m = (monitors as? Load.Ok)?.data
            when {
                s == null && m == null && servers is Load.Loading -> LoadingBox()
                s == null && m == null -> ErrorState((servers as? Load.Err)?.message ?: "Could not load data", vm::refresh)
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    val serverIssues = s.orEmpty().filter { it.issues(prefs).isNotEmpty() }.sortedBy { statusRank(it.status) }
                    val monitorIssues = m.orEmpty().filter { it.issues(prefs).isNotEmpty() }.sortedBy { statusRank(it.status) }
                    val issueCount = serverIssues.size + monitorIssues.size

                    item { OverallBanner(issueCount, s.orEmpty().count { it.status == Status.DOWN } + m.orEmpty().count { it.status == Status.DOWN }) }

                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SummaryCard("Servers", s?.map { it.status }, Modifier.weight(1f), onOpenServers)
                            SummaryCard("Websites", m?.map { it.status }, Modifier.weight(1f), onOpenWebsites)
                        }
                    }

                    val favServers = s.orEmpty().filter { it.id in prefs.favorites }
                    val favMonitors = m.orEmpty().filter { it.id in prefs.favorites }
                    if (favServers.isNotEmpty() || favMonitors.isNotEmpty()) item {
                        SectionCard(title = "Favorites") {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                favServers.forEach { sv ->
                                    FavoriteRow(sv.status, sv.name, "CPU ${fmtPercent(sv.cpuPercent)} · Memory ${fmtPercent(sv.memPercent)}") { onServer(sv.id) }
                                }
                                favMonitors.forEach { mo ->
                                    FavoriteRow(mo.status, mo.title, "Uptime ${fmtPercent(mo.uptimePercent, 2)} · TTFB ${fmtSeconds(mo.ttfb)}") { onMonitor(mo.id) }
                                }
                            }
                        }
                    }

                    if (issueCount > 0) item {
                        SectionCard(title = "Needs attention") {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                serverIssues.take(8).forEach { sv ->
                                    AttentionRow(sv.status, sv.name, sv.issues(prefs).joinToString(" · "), "Server") { onServer(sv.id) }
                                }
                                monitorIssues.take(8).forEach { mo ->
                                    AttentionRow(mo.status, mo.title, mo.issues(prefs).joinToString(" · "), "Website") { onMonitor(mo.id) }
                                }
                            }
                        }
                    }

                    if (!s.isNullOrEmpty()) item { ResourcesCard(s, prefs.cpuWarn, prefs.memWarn, prefs.diskWarn, onServer) }
                    if (!m.isNullOrEmpty()) item { PerformanceCard(m, onMonitor) }
                    if (s.isNullOrEmpty() && m.isNullOrEmpty()) item {
                        EmptyState("Nothing is monitored yet. Add a website from the Websites tab, or install the monitoring agent on a server.")
                    }

                    item {
                        Text(
                            buildString {
                                append("Updated ").append(if (last > 0) timeAgo(last / 1000) else "–")
                                rate?.remaining?.let { append(" · API requests left this hour: ").append(it) }
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OverallBanner(issueCount: Int, downCount: Int) {
    val ok = issueCount == 0
    val color = if (ok) StatusColors.up() else if (downCount > 0) StatusColors.down() else StatusColors.warn()
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(color.copy(alpha = 0.14f)).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(50)).background(color), contentAlignment = Alignment.Center) {
            Icon(if (ok) Icons.Default.Check else Icons.Default.Warning, null, tint = Color.White)
        }
        Column {
            Text(
                if (ok) "All systems operational" else "$issueCount ${if (issueCount == 1) "issue needs" else "issues need"} attention",
                style = MaterialTheme.typography.titleMedium, color = color,
            )
            Text(
                if (ok) "Everything you monitor looks healthy." else if (downCount > 0) "$downCount down right now" else "Resources or uptime are beyond your thresholds.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SummaryCard(title: String, statuses: List<Status>?, modifier: Modifier, onClick: () -> Unit) {
    ClickableCard(onClick, modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val up = statuses?.count { it == Status.UP } ?: 0
            val down = statuses?.count { it == Status.DOWN } ?: 0
            val unk = statuses?.count { it == Status.UNKNOWN } ?: 0
            Row(verticalAlignment = Alignment.Bottom) {
                Text("$up", style = MaterialTheme.typography.displaySmall, color = StatusColors.up(), fontWeight = FontWeight.Bold)
                Text(" up", Modifier.padding(bottom = 6.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                if (down > 0) "$down down" else if (statuses == null) "unavailable" else "none down",
                color = if (down > 0) StatusColors.down() else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium, fontWeight = if (down > 0) FontWeight.Bold else FontWeight.Normal,
            )
            val total = (up + down + unk).coerceAtLeast(1)
            Row(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                if (up > 0) Box(Modifier.weight(up.toFloat() / total).height(8.dp).background(StatusColors.up()))
                if (down > 0) Box(Modifier.weight(down.toFloat() / total).height(8.dp).background(StatusColors.down()))
                if (unk > 0) Box(Modifier.weight(unk.toFloat() / total).height(8.dp).background(StatusColors.unknown()))
            }
            if (unk > 0) Text("$unk unknown", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FavoriteRow(status: Status, name: String, detail: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).onTap(onClick).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(status)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun AttentionRow(status: Status, name: String, detail: String, kind: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).onTap(onClick).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (status == Status.UP) Box(Modifier.size(12.dp).clip(RoundedCornerShape(50)).background(StatusColors.warn())) else StatusDot(status)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
        Text(kind, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ResourcesCard(servers: List<Server>, cpuWarn: Int, memWarn: Int, diskWarn: Int, onServer: (String) -> Unit) {
    SectionCard(title = "Server resources") {
        fun avg(f: (Server) -> Double?): Double? = servers.mapNotNull(f).takeIf { it.isNotEmpty() }?.average()
        UsageBar("CPU (average)", avg { it.cpuPercent }, cpuWarn)
        UsageBar("Memory (average)", avg { it.memPercent }, memWarn)
        UsageBar("Disk (average)", avg { it.diskPercent }, diskWarn)
        val top = servers.filter { it.cpuPercent != null }.sortedByDescending { it.cpuPercent }.take(5)
        if (top.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text("Top CPU consumers", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            top.forEach { sv ->
                Box(Modifier.onTap { onServer(sv.id) }) { UsageBar(sv.name, sv.cpuPercent, cpuWarn) }
            }
        }
    }
}

@Composable
private fun PerformanceCard(monitors: List<Monitor>, onMonitor: (String) -> Unit) {
    SectionCard(title = "Website performance") {
        val avgUptime = monitors.mapNotNull { it.uptimePercent }.takeIf { it.isNotEmpty() }?.average()
        val ttfbs = monitors.mapNotNull { it.ttfb }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("Avg. uptime", fmtPercent(avgUptime, 2), StatusColors.up(), Modifier.weight(1f))
            StatTile("Avg. TTFB", fmtSeconds(ttfbs.takeIf { it.isNotEmpty() }?.average()), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
        }
        val slow = monitors.filter { it.ttfb != null }.sortedByDescending { it.ttfb }.take(5)
        if (slow.isNotEmpty()) {
            Text("Slowest websites", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            slow.forEach { mo ->
                Row(Modifier.fillMaxWidth().onTap { onMonitor(mo.id) }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(mo.status, 10.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(mo.title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(fmtSeconds(mo.ttfb), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
