@file:OptIn(ExperimentalLayoutApi::class)

package app.monitor360.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.monitor360.data.ChartData
import app.monitor360.data.Series
import app.monitor360.ui.fmtAxis
import app.monitor360.ui.fmtCompact
import app.monitor360.ui.fmtDateTime
import app.monitor360.ui.fmtSeconds
import app.monitor360.ui.theme.ChartPalette
import kotlin.math.abs

fun valueFormatter(chart: ChartData): (Double) -> String {
    val names = chart.series.joinToString(" ") { it.name.lowercase() }
    return when {
        names.contains("time_") -> { v -> fmtSeconds(v) }
        chart.title.equals("cpu", true) -> { v -> "%.0f%%".format(v) }
        else -> { v -> fmtCompact(v) }
    }
}

fun metricLabel(m: String): String = when (m.lowercase()) {
    "cpu" -> "CPU"
    "mem" -> "Memory"
    "httpd" -> "Apache (httpd)"
    "nginx" -> "NGINX"
    "wp-toolkit" -> "WP Toolkit"
    "bitninja" -> "BitNinja"
    "df" -> "Disks"
    else -> m.replaceFirstChar { it.uppercase() }
}

fun prettySeriesName(name: String): String =
    name.removePrefix("average.").replace("time_to_first_byte", "TTFB").replace("time_", "").replace('_', ' ')

@Composable
fun MetricChart(chart: ChartData, modifier: Modifier = Modifier, height: Dp = 190.dp) {
    val fmt = remember(chart) { valueFormatter(chart) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LineChart(chart.series, fmt, Modifier.fillMaxWidth(), height)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            chart.series.forEachIndexed { i, s ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(ChartPalette[i % ChartPalette.size]))
                    Text(prettySeriesName(s.name), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun LineChart(series: List<Series>, format: (Double) -> String, modifier: Modifier = Modifier, height: Dp = 190.dp) {
    val all = series.flatMap { it.points }
    if (all.isEmpty()) {
        Box(modifier.height(height), contentAlignment = Alignment.Center) {
            Text("No data for this period", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val minX = all.minOf { it.first }
    val maxX = all.maxOf { it.first }.coerceAtLeast(minX + 1)
    val rawMin = all.minOf { it.second }
    val rawMax = all.maxOf { it.second }
    val yMin = if (rawMin >= 0) 0.0 else rawMin
    val yMax = (if (rawMax <= yMin) yMin + 1 else rawMax).let { it + (it - yMin) * 0.08 }

    val textMeasurer = rememberTextMeasurer()
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val grid = MaterialTheme.colorScheme.outlineVariant
    val tipBg = MaterialTheme.colorScheme.inverseSurface
    val tipFg = MaterialTheme.colorScheme.inverseOnSurface
    val density = LocalDensity.current
    var touchX by remember(series) { mutableStateOf<Float?>(null) }
    val axisStyle = TextStyle(color = onSurfaceVariant, fontSize = 10.sp)
    val tipStyle = TextStyle(color = tipFg, fontSize = 11.sp)

    Canvas(
        modifier
            .height(height)
            .pointerInput(series) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent()
                        val c = e.changes.firstOrNull()
                        touchX = when {
                            c == null -> null
                            e.type == PointerEventType.Release || e.type == PointerEventType.Exit || c.isConsumed || !c.pressed -> null
                            else -> c.position.x
                        }
                    }
                }
            },
    ) {
        val left = with(density) { 40.dp.toPx() }
        val right = with(density) { 6.dp.toPx() }
        val top = with(density) { 6.dp.toPx() }
        val bottom = with(density) { 18.dp.toPx() }
        val w = size.width - left - right
        val h = size.height - top - bottom
        fun px(x: Long) = left + (x - minX).toFloat() / (maxX - minX).toFloat() * w
        fun py(y: Double) = top + h - ((y - yMin) / (yMax - yMin)).toFloat() * h

        for (i in 0..3) {
            val v = yMin + (yMax - yMin) * i / 3.0
            val y = py(v)
            drawLine(grid, Offset(left, y), Offset(size.width - right, y), strokeWidth = 1f)
            val label = textMeasurer.measure(format(v), axisStyle)
            drawText(label, topLeft = Offset(left - label.size.width - 6f, y - label.size.height / 2f))
        }
        val span = maxX - minX
        for (i in 0..2) {
            val t = minX + span * i / 2
            val label = textMeasurer.measure(fmtAxis(t, span), axisStyle)
            val x = (px(t) - label.size.width / 2f).coerceIn(0f, size.width - label.size.width)
            drawText(label, topLeft = Offset(x, size.height - label.size.height))
        }

        series.forEachIndexed { idx, s ->
            if (s.points.isEmpty()) return@forEachIndexed
            val color = ChartPalette[idx % ChartPalette.size]
            val path = Path()
            s.points.forEachIndexed { i, (t, v) -> if (i == 0) path.moveTo(px(t), py(v)) else path.lineTo(px(t), py(v)) }
            if (idx == 0 && s.points.size > 1) {
                val fill = Path().apply {
                    addPath(path)
                    lineTo(px(s.points.last().first), py(yMin))
                    lineTo(px(s.points.first().first), py(yMin))
                    close()
                }
                drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.25f), color.copy(alpha = 0f)), startY = top, endY = top + h))
            }
            drawPath(path, color, style = Stroke(width = with(density) { 2.dp.toPx() }, cap = StrokeCap.Round, join = StrokeJoin.Round))
            if (s.points.size == 1) drawCircle(color, with(density) { 3.dp.toPx() }, Offset(px(s.points[0].first), py(s.points[0].second)))
        }

        touchX?.let { tx ->
            val targetT = minX + ((tx - left) / w).coerceIn(0f, 1f) * (maxX - minX)
            val nearest = series.firstOrNull()?.points?.minByOrNull { abs(it.first - targetT) }?.first ?: return@let
            val x = px(nearest)
            drawLine(onSurfaceVariant, Offset(x, top), Offset(x, top + h), strokeWidth = 1.5f)
            val lines = listOf(fmtDateTime(nearest)) + series.mapIndexedNotNull { i, s ->
                s.points.minByOrNull { abs(it.first - nearest) }?.let { "${prettySeriesName(s.name)}: ${format(it.second)}" }
            }
            val measured = lines.map { textMeasurer.measure(it, tipStyle) }
            val boxW = measured.maxOf { it.size.width } + 20f
            val boxH = measured.sumOf { it.size.height } + 16f
            val bx = if (x + boxW + 10 > size.width) x - boxW - 10 else x + 10
            drawRoundRect(tipBg, Offset(bx, top), Size(boxW, boxH), cornerRadius = CornerRadius(14f, 14f))
            var y = top + 8f
            measured.forEach { m -> drawText(m, topLeft = Offset(bx + 10f, y)); y += m.size.height }
            series.forEachIndexed { i, s ->
                s.points.minByOrNull { abs(it.first - nearest) }?.let {
                    drawCircle(ChartPalette[i % ChartPalette.size], 5f, Offset(px(it.first), py(it.second)))
                }
            }
        }
    }
}
