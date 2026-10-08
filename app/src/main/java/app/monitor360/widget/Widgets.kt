package app.monitor360.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.monitor360.MainActivity
import app.monitor360.app
import app.monitor360.data.SnapMonitor
import app.monitor360.data.SnapServer
import app.monitor360.data.Snapshot
import app.monitor360.data.Status
import app.monitor360.work.Scheduler
import java.text.DateFormat
import java.util.Date

private val BannerGreen = Color(0xFF1B9E5A)
private val BannerRed = Color(0xFFD7263D)
private val BannerGrey = Color(0xFF52667A)
private val OnBanner = ColorProvider(Color.White)
private val OnBannerMuted = ColorProvider(Color(0xCCFFFFFF))

private fun WidgetPalette.of(status: Status) = when (status) {
    Status.UP -> up
    Status.DOWN -> down
    Status.UNKNOWN -> unknown
}

private fun openApp(context: Context): Action = actionStartActivity(Intent(context, MainActivity::class.java))

private fun openSettings(context: Context, widgetId: Int): Action = actionStartActivity(
    Intent(context, WidgetConfigActivity::class.java)
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        .setData(Uri.parse("monitor360://widget/$widgetId")),
)

private fun rank(s: Status) = when (s) {
    Status.DOWN -> 0
    Status.UNKNOWN -> 1
    Status.UP -> 2
}

private val Snapshot.problems get() = serversDown + monitorsDown

private fun updatedText(s: Snapshot): String {
    if (!s.hasData) return "Tap ⟳ to load"
    val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(s.updatedAt))
    return if (s.error != null) "Offline · $time" else "Updated $time"
}

object WidgetUpdater {
    suspend fun updateAll(context: Context) {
        listOf(
            StatusWidget(), StatusDarkWidget(), StatusCompactWidget(), StatusBannerWidget(),
            StatusDotsWidget(), StatusCounterWidget(), WebsitesWidget(), ServersWidget(),
        ).forEach { it.updateAll(context) }
    }
}

class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        Scheduler.refreshNow(context)
    }
}

@Composable
private fun Controls(context: Context, widgetId: Int, color: ColorProvider, style: WidgetStyle) {
    Text(
        "⟳",
        style = TextStyle(color = color, fontWeight = FontWeight.Bold, fontSize = style.sp(18)),
        modifier = GlanceModifier.clickable(actionRunCallback<RefreshAction>()),
    )
    Spacer(GlanceModifier.width(10.dp))
    Text(
        "⚙",
        style = TextStyle(color = color, fontSize = style.sp(16)),
        modifier = GlanceModifier.clickable(openSettings(context, widgetId)),
    )
}

@Composable
private fun Header(context: Context, title: String, snap: Snapshot, p: WidgetPalette, style: WidgetStyle, widgetId: Int) {
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = GlanceModifier.size(width = 4.dp, height = 16.dp).background(p.accent).cornerRadius(2.dp)) {}
        Spacer(GlanceModifier.width(8.dp))
        Text(title, maxLines = 1, style = TextStyle(color = p.text, fontWeight = FontWeight.Bold, fontSize = style.sp(14)), modifier = GlanceModifier.defaultWeight())
        if (style.textSize != TextSize.LARGE) {
            Text(updatedText(snap), maxLines = 1, style = TextStyle(color = p.muted, fontSize = style.sp(10)))
        }
        if (style.showControls) {
            Spacer(GlanceModifier.width(8.dp))
            Controls(context, widgetId, p.accent, style)
        }
    }
}

@Composable
private fun Dot(color: ColorProvider, size: Int = 10) {
    Box(modifier = GlanceModifier.size(size.dp).background(color).cornerRadius((size / 2).dp)) {}
}

@Composable
private fun Counts(up: Int, down: Int, p: WidgetPalette, style: WidgetStyle) {
    Row(modifier = GlanceModifier.fillMaxWidth().padding(top = 4.dp, start = 12.dp)) {
        Text("$up up", style = TextStyle(color = p.up, fontWeight = FontWeight.Bold, fontSize = style.sp(12)))
        Text("  ·  ", style = TextStyle(color = p.muted, fontSize = style.sp(12)))
        Text(
            if (down > 0) "$down down" else "none down",
            style = TextStyle(
                color = if (down > 0) p.down else p.muted,
                fontWeight = if (down > 0) FontWeight.Bold else FontWeight.Normal,
                fontSize = style.sp(12),
            ),
        )
    }
}

@Composable
private fun StatCard(label: String, up: Int, down: Int, p: WidgetPalette, style: WidgetStyle, modifier: GlanceModifier) {
    Column(modifier = modifier.background(p.surfaceAlt).cornerRadius(14.dp).padding(8.dp)) {
        Text(label, style = TextStyle(color = p.muted, fontSize = style.sp(11)))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("$up", style = TextStyle(color = p.up, fontWeight = FontWeight.Bold, fontSize = style.sp(24)))
            Text(" up", style = TextStyle(color = p.muted, fontSize = style.sp(11)))
        }
        Text(
            if (down > 0) "$down down" else "all good",
            style = TextStyle(
                color = if (down > 0) p.down else p.muted,
                fontWeight = if (down > 0) FontWeight.Bold else FontWeight.Normal,
                fontSize = style.sp(12),
            ),
        )
    }
}

private fun GlanceModifier.card(p: WidgetPalette, style: WidgetStyle, padding: Int = 12) =
    fillMaxSize().background(p.surface).cornerRadius(style.corners.dp.dp).padding(padding.dp)

@Composable
fun StatusCardsContent(context: Context, snap: Snapshot, style: WidgetStyle = WidgetStyle(), widgetId: Int = 0) {
    val p = style.palette(context.app.settings.current.accent)
    Column(modifier = GlanceModifier.card(p, style).clickable(openApp(context))) {
        Header(context, "360 Monitor", snap, p, style, widgetId)
        Spacer(GlanceModifier.height(8.dp))
        Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
            StatCard("Servers", snap.serversUp, snap.serversDown, p, style, GlanceModifier.defaultWeight().fillMaxHeight())
            Spacer(GlanceModifier.width(8.dp))
            StatCard("Websites", snap.monitorsUp, snap.monitorsDown, p, style, GlanceModifier.defaultWeight().fillMaxHeight())
        }
    }
}

@Composable
private fun CompactBlock(label: String, up: Int, total: Int, down: Int, p: WidgetPalette, style: WidgetStyle) {
    Column {
        Text(label, style = TextStyle(color = p.muted, fontSize = style.sp(10)))
        Text(
            "$up/$total",
            style = TextStyle(color = if (down > 0) p.down else p.up, fontWeight = FontWeight.Bold, fontSize = style.sp(18)),
        )
    }
}

@Composable
fun StatusCompactContent(context: Context, snap: Snapshot, style: WidgetStyle = WidgetStyle(), widgetId: Int = 0) {
    val p = style.palette(context.app.settings.current.accent)
    val overall = when {
        !snap.hasData -> p.unknown
        snap.problems > 0 -> p.down
        else -> p.up
    }
    Row(
        modifier = GlanceModifier.fillMaxSize().background(p.surface).cornerRadius(style.corners.dp.dp)
            .padding(horizontal = 14.dp, vertical = 8.dp).clickable(openApp(context)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Dot(overall, 12)
        Spacer(GlanceModifier.width(12.dp))
        CompactBlock("Servers", snap.serversUp, snap.servers.size, snap.serversDown, p, style)
        Spacer(GlanceModifier.width(18.dp))
        CompactBlock("Websites", snap.monitorsUp, snap.monitors.size, snap.monitorsDown, p, style)
        Spacer(GlanceModifier.defaultWeight())
        if (style.showControls) Controls(context, widgetId, p.accent, style)
    }
}

@Composable
fun StatusBannerContent(context: Context, snap: Snapshot, style: WidgetStyle = WidgetStyle(), widgetId: Int = 0) {
    val (color, symbol, title) = when {
        !snap.hasData -> Triple(BannerGrey, "…", "No data yet")
        snap.problems > 0 -> Triple(BannerRed, "!", "${snap.problems} down right now")
        else -> Triple(BannerGreen, "✓", "All systems operational")
    }
    val alpha = style.opacity.coerceIn(10, 100) / 100f
    Row(
        modifier = GlanceModifier.fillMaxSize().background(ColorProvider(color.copy(alpha = alpha)))
            .cornerRadius(style.corners.dp.dp).padding(horizontal = 16.dp, vertical = 8.dp).clickable(openApp(context)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(symbol, style = TextStyle(color = OnBanner, fontWeight = FontWeight.Bold, fontSize = style.sp(30)))
        Spacer(GlanceModifier.width(14.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(title, maxLines = 1, style = TextStyle(color = OnBanner, fontWeight = FontWeight.Bold, fontSize = style.sp(16)))
            Text(
                "${snap.serversUp}/${snap.servers.size} servers · ${snap.monitorsUp}/${snap.monitors.size} websites",
                maxLines = 1,
                style = TextStyle(color = OnBannerMuted, fontSize = style.sp(11)),
            )
        }
        if (style.showControls) Controls(context, widgetId, OnBanner, style)
    }
}

@Composable
private fun DotsBlock(label: String, statuses: List<Status>, p: WidgetPalette, style: WidgetStyle) {
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Text(
            "$label · ${statuses.count { it == Status.UP }}/${statuses.size} up",
            style = TextStyle(color = p.muted, fontSize = style.sp(11)),
        )
        Spacer(GlanceModifier.height(4.dp))
        if (statuses.isEmpty()) {
            Text("none", style = TextStyle(color = p.muted, fontSize = style.sp(11)))
        }
        statuses.sortedBy { rank(it) }.take(DOTS_MAX).chunked(DOTS_PER_ROW).forEach { row ->
            Row(modifier = GlanceModifier.padding(bottom = 4.dp)) {
                row.forEach { status ->
                    Dot(p.of(status), 12)
                    Spacer(GlanceModifier.width(5.dp))
                }
            }
        }
    }
}

private const val DOTS_PER_ROW = 12
private const val DOTS_MAX = 48

@Composable
fun StatusDotsContent(context: Context, snap: Snapshot, style: WidgetStyle = WidgetStyle(), widgetId: Int = 0) {
    val p = style.palette(context.app.settings.current.accent)
    Column(modifier = GlanceModifier.card(p, style).clickable(openApp(context))) {
        Header(context, "Status", snap, p, style, widgetId)
        Spacer(GlanceModifier.height(10.dp))
        DotsBlock("Servers", snap.servers.map { it.status }, p, style)
        Spacer(GlanceModifier.height(6.dp))
        DotsBlock("Websites", snap.monitors.map { it.status }, p, style)
    }
}

@Composable
fun StatusCounterContent(context: Context, snap: Snapshot, style: WidgetStyle = WidgetStyle(), widgetId: Int = 0) {
    val p = style.palette(context.app.settings.current.accent)
    val problems = snap.problems
    val color = when {
        !snap.hasData -> p.unknown
        problems > 0 -> p.down
        else -> p.up
    }
    Column(
        modifier = GlanceModifier.card(p, style, 10).clickable(openApp(context)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            when {
                !snap.hasData -> "–"
                problems > 0 -> "$problems"
                else -> "✓"
            },
            style = TextStyle(color = color, fontWeight = FontWeight.Bold, fontSize = style.sp(44), textAlign = TextAlign.Center),
        )
        Text(
            when {
                !snap.hasData -> "no data"
                problems > 0 -> "down"
                else -> "all up"
            },
            style = TextStyle(color = p.text, fontWeight = FontWeight.Medium, fontSize = style.sp(14)),
        )
        Spacer(GlanceModifier.height(4.dp))
        Text(
            "${snap.serversUp}/${snap.servers.size} srv · ${snap.monitorsUp}/${snap.monitors.size} web",
            style = TextStyle(color = p.muted, fontSize = style.sp(10), textAlign = TextAlign.Center),
        )
        if (style.showControls) {
            Spacer(GlanceModifier.height(4.dp))
            Row { Controls(context, widgetId, p.accent, style) }
        }
    }
}

@Composable
private fun EmptyList(context: Context, text: String, p: WidgetPalette, style: WidgetStyle) {
    Box(GlanceModifier.fillMaxSize().clickable(openApp(context)), contentAlignment = Alignment.Center) {
        Text(text, style = TextStyle(color = p.muted, fontSize = style.sp(12)))
    }
}

@Composable
fun WebsitesContent(context: Context, snap: Snapshot, style: WidgetStyle = WidgetStyle(), widgetId: Int = 0) {
    val p = style.palette(context.app.settings.current.accent)
    val items = snap.monitors
        .filter {
            when (style.filter) {
                ListFilter.ALL -> true
                ListFilter.PROBLEMS -> it.status != Status.UP
                ListFilter.FAVORITES -> it.favorite
            }
        }
        .sortedWith(compareBy<SnapMonitor> { !it.favorite }.thenBy { rank(it.status) }.thenBy { it.name.lowercase() })
    Column(modifier = GlanceModifier.card(p, style)) {
        Header(context, "Websites", snap, p, style, widgetId)
        Counts(snap.monitorsUp, snap.monitorsDown, p, style)
        Spacer(GlanceModifier.height(6.dp))
        if (items.isEmpty()) {
            EmptyList(context, emptyText("websites", snap.monitors.isEmpty(), style.filter), p, style)
        } else {
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(items.size) { i ->
                    val m = items[i]
                    Row(
                        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 5.dp).clickable(openApp(context)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Dot(p.of(m.status))
                        Spacer(GlanceModifier.width(8.dp))
                        Text(
                            (if (m.favorite) "★ " else "") + m.name,
                            maxLines = 1,
                            style = TextStyle(color = p.text, fontSize = style.sp(13)),
                            modifier = GlanceModifier.defaultWeight(),
                        )
                        val detail = listOfNotNull(
                            m.uptime?.let { "%.2f%%".format(it) },
                            m.ttfb?.let { "${(it * 1000).toInt()} ms" },
                        ).joinToString(" · ")
                        if (detail.isNotEmpty()) Text(detail, maxLines = 1, style = TextStyle(color = p.muted, fontSize = style.sp(11)))
                    }
                }
            }
        }
    }
}

@Composable
fun ServersContent(context: Context, snap: Snapshot, style: WidgetStyle = WidgetStyle(), widgetId: Int = 0) {
    val p = style.palette(context.app.settings.current.accent)
    val items = snap.servers
        .filter {
            when (style.filter) {
                ListFilter.ALL -> true
                ListFilter.PROBLEMS -> it.status != Status.UP
                ListFilter.FAVORITES -> it.favorite
            }
        }
        .sortedWith(compareBy<SnapServer> { !it.favorite }.thenBy { rank(it.status) }.thenBy { it.name.lowercase() })
    Column(modifier = GlanceModifier.card(p, style)) {
        Header(context, "Servers", snap, p, style, widgetId)
        Counts(snap.serversUp, snap.serversDown, p, style)
        Spacer(GlanceModifier.height(6.dp))
        if (items.isEmpty()) {
            EmptyList(context, emptyText("servers", snap.servers.isEmpty(), style.filter), p, style)
        } else {
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(items.size) { i ->
                    val s = items[i]
                    Row(
                        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 5.dp).clickable(openApp(context)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Dot(p.of(s.status))
                        Spacer(GlanceModifier.width(8.dp))
                        Text(
                            (if (s.favorite) "★ " else "") + s.name,
                            maxLines = 1,
                            style = TextStyle(color = p.text, fontSize = style.sp(13)),
                            modifier = GlanceModifier.defaultWeight(),
                        )
                        val detail = listOfNotNull(
                            s.cpu?.let { "CPU ${it.toInt()}%" },
                            s.mem?.let { "MEM ${it.toInt()}%" },
                        ).joinToString(" · ")
                        if (detail.isNotEmpty()) Text(detail, maxLines = 1, style = TextStyle(color = p.muted, fontSize = style.sp(11)))
                    }
                }
            }
        }
    }
}

private fun emptyText(kind: String, nothingAtAll: Boolean, filter: ListFilter) = when {
    nothingAtAll -> "No $kind yet – open the app"
    filter == ListFilter.PROBLEMS -> "No problems 🎉"
    filter == ListFilter.FAVORITES -> "No favorite $kind"
    else -> "Nothing to show"
}

abstract class SnapshotWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single

    open val defaultStyle = WidgetStyle()

    @Composable
    abstract fun Content(context: Context, snap: Snapshot, style: WidgetStyle, widgetId: Int)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snap = context.app.snapshots.load()
        val widgetId = runCatching { GlanceAppWidgetManager(context).getAppWidgetId(id) }.getOrDefault(0)
        val style = context.app.widgetStyles.get(widgetId, defaultStyle)
        provideContent { Content(context, snap, style, widgetId) }
    }
}

class StatusWidget : SnapshotWidget() {
    @Composable
    override fun Content(context: Context, snap: Snapshot, style: WidgetStyle, widgetId: Int) =
        StatusCardsContent(context, snap, style, widgetId)
}

class StatusDarkWidget : SnapshotWidget() {
    override val defaultStyle = WidgetStyle(theme = WidgetTheme.DARK)

    @Composable
    override fun Content(context: Context, snap: Snapshot, style: WidgetStyle, widgetId: Int) =
        StatusCardsContent(context, snap, style, widgetId)
}

class StatusCompactWidget : SnapshotWidget() {
    @Composable
    override fun Content(context: Context, snap: Snapshot, style: WidgetStyle, widgetId: Int) =
        StatusCompactContent(context, snap, style, widgetId)
}

class StatusBannerWidget : SnapshotWidget() {
    @Composable
    override fun Content(context: Context, snap: Snapshot, style: WidgetStyle, widgetId: Int) =
        StatusBannerContent(context, snap, style, widgetId)
}

class StatusDotsWidget : SnapshotWidget() {
    @Composable
    override fun Content(context: Context, snap: Snapshot, style: WidgetStyle, widgetId: Int) =
        StatusDotsContent(context, snap, style, widgetId)
}

class StatusCounterWidget : SnapshotWidget() {
    @Composable
    override fun Content(context: Context, snap: Snapshot, style: WidgetStyle, widgetId: Int) =
        StatusCounterContent(context, snap, style, widgetId)
}

class WebsitesWidget : SnapshotWidget() {
    @Composable
    override fun Content(context: Context, snap: Snapshot, style: WidgetStyle, widgetId: Int) =
        WebsitesContent(context, snap, style, widgetId)
}

class ServersWidget : SnapshotWidget() {
    @Composable
    override fun Content(context: Context, snap: Snapshot, style: WidgetStyle, widgetId: Int) =
        ServersContent(context, snap, style, widgetId)
}

abstract class StyledReceiver : GlanceAppWidgetReceiver() {
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        context.app.widgetStyles.remove(appWidgetIds)
    }
}

class StatusWidgetReceiver : StyledReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StatusWidget()
}

class StatusDarkWidgetReceiver : StyledReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StatusDarkWidget()
}

class StatusCompactWidgetReceiver : StyledReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StatusCompactWidget()
}

class StatusBannerWidgetReceiver : StyledReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StatusBannerWidget()
}

class StatusDotsWidgetReceiver : StyledReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StatusDotsWidget()
}

class StatusCounterWidgetReceiver : StyledReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StatusCounterWidget()
}

class WebsitesWidgetReceiver : StyledReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WebsitesWidget()
}

class ServersWidgetReceiver : StyledReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ServersWidget()
}
