package app.monitor360.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import app.monitor360.data.Monitor
import app.monitor360.data.Prefs
import app.monitor360.data.Server
import app.monitor360.data.Status
import app.monitor360.ui.fmtDuration
import app.monitor360.ui.fmtPercent
import app.monitor360.ui.fmtSeconds
import app.monitor360.ui.fmtUptime

fun Server.issues(p: Prefs): List<String> = buildList {
    if (status == Status.DOWN) add("Server is down")
    cpuPercent?.let { if (it >= p.cpuWarn) add("CPU ${it.toInt()}%") }
    memPercent?.let { if (it >= p.memWarn) add("Memory ${it.toInt()}%") }
    diskPercent?.let { if (it >= p.diskWarn) add("Disk ${it.toInt()}%") }
}

fun Monitor.issues(p: Prefs): List<String> = buildList {
    if (status == Status.DOWN) add(statusMessage ?: "Website is down")
    uptimePercent?.let { if (it < p.uptimeWarn) add("Uptime %.2f%%".format(it)) }
}

fun statusRank(s: Status) = when (s) { Status.DOWN -> 0; Status.UNKNOWN -> 1; Status.UP -> 2 }

fun Modifier.onTap(onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(onClick = onClick))

fun serverSummary(s: Server): String = buildString {
    appendLine("${s.name} is ${s.status.name.lowercase()}")
    appendLine("CPU ${fmtPercent(s.cpuPercent)}, memory ${fmtPercent(s.memPercent)}, disk ${fmtPercent(s.diskPercent)}")
    s.uptimeSeconds?.let { appendLine("Uptime ${fmtDuration(it)}") }
    s.ip?.let { append("IP $it") }
}.trim()

fun monitorSummary(m: Monitor): String = buildString {
    appendLine("${m.title} (${m.url}) is ${m.status.name.lowercase()}")
    m.statusMessage?.let { appendLine(it) }
    append("Uptime ${fmtUptime(m.uptimePercent)}, TTFB ${fmtSeconds(m.ttfb)}")
}.trim()

fun shareText(context: Context, subject: String, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, "Share status").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
