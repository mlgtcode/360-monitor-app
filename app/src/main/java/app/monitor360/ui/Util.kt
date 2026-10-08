package app.monitor360.ui

import app.monitor360.data.ApiException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val zone get() = ZoneId.systemDefault()
private val fmtDateTime = DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.ENGLISH)
private val fmtTime = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
private val fmtDay = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
private val fmtFull = DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm", Locale.ENGLISH)

fun fmtDateTime(epoch: Long?): String = epoch?.let { fmtDateTime.format(Instant.ofEpochSecond(it).atZone(zone)) } ?: "–"
fun fmtFull(epoch: Long?): String = epoch?.let { fmtFull.format(Instant.ofEpochSecond(it).atZone(zone)) } ?: "–"
fun fmtAxis(epoch: Long, spanSeconds: Long): String {
    val t = Instant.ofEpochSecond(epoch).atZone(zone)
    return if (spanSeconds <= 2 * 86400) fmtTime.format(t) else fmtDay.format(t)
}

fun fmtPercent(v: Double?, decimals: Int = 0): String =
    v?.let { String.format(Locale.US, "%.${decimals}f%%", it) } ?: "–"

fun fmtUptime(v: Double?): String = v?.let { String.format(Locale.US, "%.3f%%", it) } ?: "–"

fun fmtSeconds(sec: Double?): String = when {
    sec == null -> "–"
    sec < 1.0 -> "${(sec * 1000).roundToInt()} ms"
    else -> String.format(Locale.US, "%.2f s", sec)
}

fun fmtDuration(totalSeconds: Long?): String {
    if (totalSeconds == null) return "–"
    var s = totalSeconds
    val d = s / 86400; s %= 86400
    val h = s / 3600; s %= 3600
    val m = s / 60
    return when {
        d > 0 -> "${d}d ${h}h"
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m ${s % 60}s"
        else -> "${s}s"
    }
}

fun fmtBytes(bytes: Double?): String {
    if (bytes == null) return "–"
    val units = listOf("B", "KB", "MB", "GB", "TB", "PB")
    var v = bytes
    var i = 0
    while (abs(v) >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
    return if (i == 0) "${v.roundToInt()} B" else String.format(Locale.US, "%.1f %s", v, units[i])
}

fun fmtKb(kb: Double?): String = fmtBytes(kb?.times(1024))

fun fmtCompact(v: Double): String {
    val a = abs(v)
    return when {
        a >= 1e9 -> String.format(Locale.US, "%.1fG", v / 1e9)
        a >= 1e6 -> String.format(Locale.US, "%.1fM", v / 1e6)
        a >= 1e4 -> String.format(Locale.US, "%.1fk", v / 1e3)
        a >= 100 -> String.format(Locale.US, "%.0f", v)
        a >= 1 -> String.format(Locale.US, "%.1f", v)
        a == 0.0 -> "0"
        else -> String.format(Locale.US, "%.3f", v)
    }
}

fun timeAgo(epochSeconds: Long?, now: Long = System.currentTimeMillis() / 1000): String {
    if (epochSeconds == null || epochSeconds <= 0) return "never"
    val d = now - epochSeconds
    return when {
        d < 60 -> "just now"
        d < 3600 -> "${d / 60} min ago"
        d < 86400 -> "${d / 3600} h ago"
        else -> "${d / 86400} d ago"
    }
}

fun Throwable.userMessage(): String = when (this) {
    is ApiException -> message
    else -> message ?: "Something went wrong"
}

fun nowSeconds() = System.currentTimeMillis() / 1000

enum class Range(val label: String, val seconds: Long) {
    H1("1 h", 3600), H6("6 h", 6 * 3600), H24("24 h", 86400), D7("7 d", 7 * 86400), D30("30 d", 30 * 86400);

    companion object {
        fun forHours(h: Int) = entries.minByOrNull { abs(it.seconds - h * 3600L) } ?: H24
    }
}
