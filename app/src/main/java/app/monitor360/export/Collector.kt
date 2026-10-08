package app.monitor360.export

import app.monitor360.data.Repository
import app.monitor360.data.collectFromSources
import app.monitor360.data.WindowOwner
import app.monitor360.data.allSources
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

class ExportOptions(
    val datasets: Set<Dataset>,
    val alertDays: Int = 30,
    val uptimeDays: Int = 30,
    val maxSources: Int = 100,
)

private fun iso(epoch: Long?): String = epoch?.let { Instant.ofEpochSecond(it).toString() } ?: ""

private fun round(v: Double?, decimals: Int = 2): Double? =
    v?.let { BigDecimal(it).setScale(decimals, RoundingMode.HALF_UP).toDouble() }

class ExportCollector(
    private val repo: Repository,
    private val now: () -> Long = { System.currentTimeMillis() / 1000 },
) {
    suspend fun collect(options: ExportOptions): ExportBundle {
        val wanted = Dataset.entries.filter { it in options.datasets }
        val needsServers = wanted.any { it in setOf(Dataset.SERVERS, Dataset.WINDOWS, Dataset.ALERTS) }
        val needsMonitors = wanted.any { it in setOf(Dataset.WEBSITES, Dataset.WINDOWS, Dataset.ALERTS, Dataset.UPTIME) }
        val servers = if (needsServers) repo.servers() else null
        val monitors = if (needsMonitors) repo.monitors() else null
        val notes = mutableListOf<String>()
        val end = now()

        val tables = wanted.map { dataset ->
            when (dataset) {
                Dataset.SERVERS -> serversTable(servers.orEmpty())
                Dataset.WEBSITES -> websitesTable(monitors.orEmpty())
                Dataset.CONTACTS -> contactsTable()
                Dataset.WINDOWS -> windowsTable(repo.allSources(servers, monitors), options, notes)
                Dataset.ALERTS -> alertsTable(repo.allSources(servers, monitors), options, end, notes)
                Dataset.UPTIME -> uptimeTable(monitors.orEmpty(), options, end, notes)
            }
        }
        return ExportBundle(end, tables, notes)
    }

    private fun serversTable(servers: List<app.monitor360.data.Server>) = Table(
        Dataset.SERVERS,
        listOf(
            Column("id"), Column("name"), Column("status"), Column("ip"), Column("os"), Column("agent_version"),
            Column("tags"), Column("cpu_percent", true), Column("memory_percent", true), Column("disk_percent", true),
            Column("cores", true), Column("uptime_seconds", true), Column("last_update"),
        ),
        servers.map {
            listOf(
                it.id, it.name, it.status.name.lowercase(), it.ip, it.os, it.agentVersion, it.tags.joinToString(";"),
                round(it.cpuPercent), round(it.memPercent), round(it.diskPercent), it.cores, it.uptimeSeconds, iso(it.lastUpdate).ifEmpty { null },
            )
        },
        pdfColumns = listOf("name", "status", "ip", "cpu_percent", "memory_percent", "disk_percent"),
    )

    private fun websitesTable(monitors: List<app.monitor360.data.Monitor>) = Table(
        Dataset.WEBSITES,
        listOf(
            Column("id"), Column("name"), Column("url"), Column("type"), Column("status"), Column("status_message"),
            Column("http_code"), Column("uptime_percent", true), Column("ttfb_ms", true), Column("total_ms", true),
            Column("location"), Column("last_update"),
        ),
        monitors.map {
            listOf(
                it.id, it.title, it.url, it.protocol, it.status.name.lowercase(), it.statusMessage, it.httpCode,
                round(it.uptimePercent, 3), it.ttfb?.let { v -> round(v * 1000, 0) }, it.timeTotal?.let { v -> round(v * 1000, 0) },
                it.locationName, iso(it.lastUpdate).ifEmpty { null },
            )
        },
        pdfColumns = listOf("name", "status", "uptime_percent", "ttfb_ms", "location"),
    )

    private suspend fun contactsTable() = Table(
        Dataset.CONTACTS,
        listOf(Column("id"), Column("name"), Column("email"), Column("sms"), Column("slack_channel"), Column("webhook"), Column("pushbullet"), Column("pushover")),
        repo.contacts().map { listOf(it.id, it.name, it.email, it.sms, it.slackChannel, it.webhook, it.pushbullet, it.pushover) },
        pdfColumns = listOf("name", "email", "sms"),
    )

    private suspend fun windowsTable(sources: List<app.monitor360.data.Source>, options: ExportOptions, notes: MutableList<String>): Table {
        val got = collectFromSources(sources, options.maxSources) { repo.windows(it.owner, it.id) }
        noteGaps(got.failed, got.skipped, "maintenance windows", notes)
        return Table(
            Dataset.WINDOWS,
            listOf(
                Column("owner_type"), Column("owner_id"), Column("owner_name"), Column("id"), Column("name"), Column("interval"),
                Column("impact"), Column("start_date"), Column("end_date"), Column("start_time"), Column("end_time"), Column("days"), Column("day_of_month"),
            ),
            got.rows.map { (s, w) ->
                listOf(s.kind.lowercase(), s.id, s.name, w.id, w.name, w.interval, w.impact, w.startDate, w.endDate, w.startTime, w.endTime, w.days, w.dayOfMonth)
            },
            pdfColumns = listOf("owner_name", "name", "interval", "start_time", "end_time"),
        )
    }

    private suspend fun alertsTable(sources: List<app.monitor360.data.Source>, options: ExportOptions, end: Long, notes: MutableList<String>): Table {
        val start = end - options.alertDays * 86400L
        val got = collectFromSources(sources, options.maxSources) {
            if (it.owner == WindowOwner.SERVER) repo.serverNotifications(it.id, start, end) else repo.monitorNotifications(it.id, start, end)
        }
        noteGaps(got.failed, got.skipped, "alerts", notes)
        return Table(
            Dataset.ALERTS,
            listOf(
                Column("owner_type"), Column("owner_id"), Column("owner_name"), Column("start"), Column("end"), Column("duration_seconds", true),
                Column("status"), Column("summary"), Column("metric"), Column("comparison"), Column("recipients"),
            ),
            got.rows.sortedByDescending { it.second.start ?: 0 }.map { (s, a) ->
                listOf(
                    s.kind.lowercase(), s.id, s.name, iso(a.start), iso(a.end), a.start?.let { st -> ((a.end ?: end) - st).coerceAtLeast(0) },
                    if (a.open) "open" else "closed", a.summary, a.metric, a.comparison, a.recipients.joinToString(";") { r -> "${r.name} (${r.type})" },
                )
            },
            pdfColumns = listOf("owner_name", "start", "duration_seconds", "status", "summary"),
        )
    }

    private suspend fun uptimeTable(monitors: List<app.monitor360.data.Monitor>, options: ExportOptions, end: Long, notes: MutableList<String>): Table {
        val start = end - options.uptimeDays * 86400L
        val sources = monitors.map { app.monitor360.data.Source(it.id, it.title, WindowOwner.MONITOR) }
        val urls = monitors.associate { it.id to it.url }
        val got = collectFromSources(sources, options.maxSources) { listOf(repo.monitorUptime(it.id, start, end)) }
        noteGaps(got.failed, got.skipped, "uptime figures", notes)
        return Table(
            Dataset.UPTIME,
            listOf(
                Column("monitor_id"), Column("name"), Column("url"), Column("period_start"), Column("period_end"),
                Column("uptime_percent", true), Column("downtime_seconds", true), Column("events", true),
            ),
            got.rows.sortedBy { it.second.percent }.map { (s, u) ->
                listOf(s.id, s.name, urls[s.id], iso(start), iso(end), round(u.percent, 3), u.downtimeSeconds, u.events)
            },
            pdfColumns = listOf("name", "uptime_percent", "downtime_seconds", "events"),
        )
    }

    private fun noteGaps(failed: Int, skipped: Int, what: String, notes: MutableList<String>) {
        if (failed > 0) notes += "$failed source(s) could not be read for $what."
        if (skipped > 0) notes += "$skipped source(s) were skipped for $what (limit reached)."
    }
}
