package app.monitor360.car

import app.monitor360.data.Monitor
import app.monitor360.data.Prefs
import app.monitor360.data.Server
import app.monitor360.data.Status
import app.monitor360.ui.fmtPercent
import app.monitor360.ui.fmtSeconds
import app.monitor360.ui.fmtUptime
import app.monitor360.ui.screens.issues
import app.monitor360.ui.screens.statusRank
import kotlin.math.roundToInt

data class TreeItem(
    val id: String,
    val title: String,
    val subtitle: String?,
    val browsable: Boolean,
)

class StatusData(val servers: List<Server>, val monitors: List<Monitor>, val prefs: Prefs)

object StatusTree {
    const val ROOT = "root"
    const val OVERVIEW = "overview"
    const val SERVERS = "servers"
    const val WEBSITES = "websites"
    const val PROBLEMS = "problems"
    const val SERVER_PREFIX = "server:"
    const val MONITOR_PREFIX = "monitor:"
    const val NOT_CONFIGURED = "not-configured"
    const val MAX_ITEMS = 100

    private fun counts(statuses: List<Status>): String {
        val up = statuses.count { it == Status.UP }
        val down = statuses.count { it == Status.DOWN }
        return if (down > 0) "$up up · $down down" else "$up up"
    }

    private fun problemCount(d: StatusData) =
        d.servers.count { it.issues(d.prefs).isNotEmpty() } + d.monitors.count { it.issues(d.prefs).isNotEmpty() }

    private fun serverItem(s: Server, prefix: String = "") = TreeItem(
        SERVER_PREFIX + s.id,
        prefix + s.name,
        if (s.status == Status.DOWN) "DOWN" else "${s.status.name} · CPU ${fmtPercent(s.cpuPercent)} · Memory ${fmtPercent(s.memPercent)} · Disk ${fmtPercent(s.diskPercent)}",
        false,
    )

    private fun monitorItem(m: Monitor, prefix: String = "") = TreeItem(
        MONITOR_PREFIX + m.id,
        prefix + m.title,
        if (m.status == Status.DOWN) "DOWN · ${m.statusMessage ?: "not reachable"}" else "${m.status.name} · Uptime ${fmtUptime(m.uptimePercent)} · TTFB ${fmtSeconds(m.ttfb)}",
        false,
    )

    fun children(parent: String, d: StatusData?): List<TreeItem> {
        if (d == null) return listOf(TreeItem(NOT_CONFIGURED, "Open 360 Monitor on your phone and sign in", null, false))
        val favorites = d.prefs.favorites
        return when (parent) {
            ROOT -> listOf(
                TreeItem(OVERVIEW, "Status summary", "Tap to hear it", false),
                TreeItem(SERVERS, "Servers", counts(d.servers.map { it.status }), true),
                TreeItem(WEBSITES, "Websites", counts(d.monitors.map { it.status }), true),
                TreeItem(PROBLEMS, "Needs attention", problemCount(d).let { if (it == 0) "All good" else "$it item${if (it == 1) "" else "s"}" }, true),
            )
            SERVERS -> d.servers
                .sortedWith(compareBy({ it.id !in favorites }, { statusRank(it.status) }, { it.name.lowercase() }))
                .take(MAX_ITEMS).map { serverItem(it) }
            WEBSITES -> d.monitors
                .sortedWith(compareBy({ it.id !in favorites }, { statusRank(it.status) }, { it.title.lowercase() }))
                .take(MAX_ITEMS).map { monitorItem(it) }
            PROBLEMS -> (
                d.servers.filter { it.issues(d.prefs).isNotEmpty() }.map { statusRank(it.status) to serverItem(it, "! ") } +
                    d.monitors.filter { it.issues(d.prefs).isNotEmpty() }.map { statusRank(it.status) to monitorItem(it, "! ") }
                ).sortedBy { it.first }.map { it.second }.take(MAX_ITEMS)
            else -> emptyList()
        }
    }

    private fun percent(v: Double?) = v?.let { "${it.roundToInt()} percent" }

    fun speech(id: String, d: StatusData?): String {
        if (d == null) return "Open 360 Monitor on your phone and sign in first."
        return when {
            id == OVERVIEW -> overviewSpeech(d)
            id.startsWith(SERVER_PREFIX) -> d.servers.firstOrNull { it.id == id.removePrefix(SERVER_PREFIX) }?.let { serverSpeech(it) }
            id.startsWith(MONITOR_PREFIX) -> d.monitors.firstOrNull { it.id == id.removePrefix(MONITOR_PREFIX) }?.let { monitorSpeech(it) }
            else -> null
        } ?: "That item is no longer available."
    }

    private fun serverSpeech(s: Server): String = buildString {
        append("${s.name} is ${s.status.name.lowercase()}.")
        listOfNotNull(percent(s.cpuPercent)?.let { "CPU $it" }, percent(s.memPercent)?.let { "memory $it" }, percent(s.diskPercent)?.let { "disk $it" })
            .takeIf { it.isNotEmpty() }?.let { append(" ").append(it.joinToString(", ")).append(".") }
    }

    private fun monitorSpeech(m: Monitor): String = buildString {
        append("${m.title} is ${m.status.name.lowercase()}.")
        if (m.status == Status.DOWN) m.statusMessage?.let { append(" $it.") }
        m.uptimePercent?.let { append(" Uptime ${"%.2f".format(it)} percent.") }
        m.ttfb?.let { append(" Response time ${(it * 1000).roundToInt()} milliseconds.") }
    }

    private fun overviewSpeech(d: StatusData): String = buildString {
        fun group(name: String, statuses: List<Status>) {
            val up = statuses.count { it == Status.UP }
            val down = statuses.count { it == Status.DOWN }
            if (statuses.isEmpty()) append("No $name. ")
            else append("$up of ${statuses.size} $name up${if (down > 0) ", $down down" else ""}. ")
        }
        group("servers", d.servers.map { it.status })
        group("websites", d.monitors.map { it.status })
        val problems = children(PROBLEMS, d)
        if (problems.isEmpty()) {
            append("Nothing needs attention.")
        } else {
            append("${problems.size} item${if (problems.size == 1) "" else "s"} need attention: ")
            append(problems.take(5).joinToString(". ") { it.title.removePrefix("! ") + ", " + it.subtitle.orEmpty().substringAfter(" · ", it.subtitle.orEmpty()) })
            append(".")
        }
    }
}
