package app.monitor360.car

import android.content.Intent
import android.text.SpannableStringBuilder
import androidx.car.app.CarAppService
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
import androidx.car.app.Session
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarText
import androidx.car.app.model.ForegroundCarColorSpan
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.validation.HostValidator
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import app.monitor360.app
import app.monitor360.data.Monitor
import app.monitor360.data.Server
import app.monitor360.data.Status
import app.monitor360.ui.fmtDuration
import app.monitor360.ui.fmtPercent
import app.monitor360.ui.fmtSeconds
import app.monitor360.ui.fmtUptime
import app.monitor360.ui.screens.issues
import app.monitor360.ui.screens.statusRank
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MonitorCarAppService : CarAppService() {
    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(): Session = object : Session() {
        override fun onCreateScreen(intent: Intent): Screen {
            val app = applicationContext.app
            CarConnection.record(applicationContext, carContext.hostInfo?.packageName, carContext.carAppApiLevel)
            val state = CarState(RepositoryCarSource(app.repo), { app.settings.current })
            return OverviewScreen(carContext, state)
        }
    }
}

private const val REFRESH_MS = 60_000L
private const val DEFAULT_LIST_LIMIT = 6

internal fun colorOf(s: Status): CarColor = when (s) {
    Status.UP -> CarColor.GREEN
    Status.DOWN -> CarColor.RED
    Status.UNKNOWN -> CarColor.YELLOW
}

internal fun colored(text: String, color: CarColor): CharSequence =
    SpannableStringBuilder(text).apply { setSpan(ForegroundCarColorSpan.create(color), 0, length, 0) }

internal fun statusTitle(status: Status, name: String): CarText = CarText.create(
    when (status) {
        Status.UP -> "🟢 "
        Status.DOWN -> "🔴 "
        Status.UNKNOWN -> "🟡 "
    } + name,
)

abstract class LiveScreen(ctx: CarContext, protected val state: CarState) : Screen(ctx) {
    private var job: Job? = null

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                job = lifecycleScope.launch {
                    while (true) {
                        state.refresh(minAgeMs = 10_000)
                        invalidate()
                        delay(REFRESH_MS)
                    }
                }
            }

            override fun onStop(owner: LifecycleOwner) {
                job?.cancel()
            }
        })
    }

    protected fun forceRefresh() {
        lifecycleScope.launch {
            state.refresh()
            invalidate()
        }
    }

    protected fun listLimit(): Int = runCatching {
        carContext.getCarService(ConstraintManager::class.java).getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
    }.getOrDefault(DEFAULT_LIST_LIMIT).coerceAtLeast(1)

    protected fun refreshStrip(): ActionStrip = ActionStrip.Builder()
        .addAction(Action.Builder().setTitle("Refresh").setOnClickListener { forceRefresh() }.build())
        .build()

    protected fun placeholder(title: String, root: Boolean = false): Template? {
        if (state.hasData) return null

        val header = if (root) Action.APP_ICON else Action.BACK
        state.error?.let { err ->
            return MessageTemplate.Builder(err)
                .setTitle(title).setHeaderAction(header)
                .addAction(Action.Builder().setTitle("Retry").setOnClickListener { forceRefresh() }.build())
                .build()
        }
        return ListTemplate.Builder().setTitle(title).setHeaderAction(header).setLoading(true).build()
    }

    protected fun push(screen: Screen) = carContext.getCarService(ScreenManager::class.java).push(screen)
}

private fun summary(statuses: List<Status>?): CharSequence {
    if (statuses == null) return "Unavailable"
    val up = statuses.count { it == Status.UP }
    val down = statuses.count { it == Status.DOWN }
    val b = SpannableStringBuilder()
    b.append(colored("$up up", CarColor.GREEN))
    b.append("  ·  ")
    b.append(if (down > 0) colored("$down down", CarColor.RED) else "none down")
    return b
}

class OverviewScreen(ctx: CarContext, state: CarState) : LiveScreen(ctx, state) {
    override fun onGetTemplate(): Template {
        placeholder("360 Monitor", root = true)?.let { return it }
        val p = state.prefs()
        val servers = state.servers
        val monitors = state.monitors
        val problems = servers.orEmpty().count { it.issues(p).isNotEmpty() } + monitors.orEmpty().count { it.issues(p).isNotEmpty() }

        val list = ItemList.Builder()
            .addItem(
                Row.Builder().setTitle("Servers").addText(summary(servers?.map { it.status })).setBrowsable(true)
                    .setOnClickListener { push(ServerListScreen(carContext, state)) }.build(),
            )
            .addItem(
                Row.Builder().setTitle("Websites").addText(summary(monitors?.map { it.status })).setBrowsable(true)
                    .setOnClickListener { push(WebsiteListScreen(carContext, state)) }.build(),
            )
            .addItem(
                Row.Builder().setTitle("Needs attention")
                    .addText(if (problems == 0) colored("All good", CarColor.GREEN) else colored("$problems item${if (problems == 1) "" else "s"}", CarColor.RED))
                    .setBrowsable(problems > 0)
                    .setOnClickListener { if (problems > 0) push(ProblemsScreen(carContext, state)) }.build(),
            )
            .build()
        return ListTemplate.Builder().setSingleList(list).setTitle("360 Monitor")
            .setHeaderAction(Action.APP_ICON).setActionStrip(refreshStrip()).build()
    }
}

class ServerListScreen(ctx: CarContext, state: CarState) : LiveScreen(ctx, state) {
    override fun onGetTemplate(): Template {
        placeholder("Servers")?.let { return it }
        val favorites = state.prefs().favorites
        val all = state.servers.orEmpty().sortedWith(compareBy({ it.id !in favorites }, { statusRank(it.status) }, { it.name.lowercase() }))
        val limit = listLimit()
        val list = ItemList.Builder()
        if (all.isEmpty()) list.setNoItemsMessage("No servers")
        all.take(limit).forEach { s -> list.addItem(serverRow(s) { push(ServerDetailScreen(carContext, state, s.id)) }) }
        return ListTemplate.Builder().setSingleList(list.build()).setTitle("Servers (${all.size})")
            .setHeaderAction(Action.BACK).setActionStrip(refreshStrip()).build()
    }
}

class WebsiteListScreen(ctx: CarContext, state: CarState) : LiveScreen(ctx, state) {
    override fun onGetTemplate(): Template {
        placeholder("Websites")?.let { return it }
        val favorites = state.prefs().favorites
        val all = state.monitors.orEmpty().sortedWith(compareBy({ it.id !in favorites }, { statusRank(it.status) }, { it.title.lowercase() }))
        val limit = listLimit()
        val list = ItemList.Builder()
        if (all.isEmpty()) list.setNoItemsMessage("No websites")
        all.take(limit).forEach { m -> list.addItem(monitorRow(m) { push(WebsiteDetailScreen(carContext, state, m.id)) }) }
        return ListTemplate.Builder().setSingleList(list.build()).setTitle("Websites (${all.size})")
            .setHeaderAction(Action.BACK).setActionStrip(refreshStrip()).build()
    }
}

class ProblemsScreen(ctx: CarContext, state: CarState) : LiveScreen(ctx, state) {
    override fun onGetTemplate(): Template {
        placeholder("Needs attention")?.let { return it }
        val p = state.prefs()
        val rows = buildList {
            state.servers.orEmpty().filter { it.issues(p).isNotEmpty() }.forEach { s ->
                add(statusRank(s.status) to Row.Builder().setTitle(statusTitle(if (s.status == Status.UP) Status.UNKNOWN else s.status, s.name))
                    .addText("Server · " + s.issues(p).joinToString(" · ")).setBrowsable(true)
                    .setOnClickListener { push(ServerDetailScreen(carContext, state, s.id)) }.build())
            }
            state.monitors.orEmpty().filter { it.issues(p).isNotEmpty() }.forEach { m ->
                add(statusRank(m.status) to Row.Builder().setTitle(statusTitle(if (m.status == Status.UP) Status.UNKNOWN else m.status, m.title))
                    .addText("Website · " + m.issues(p).joinToString(" · ")).setBrowsable(true)
                    .setOnClickListener { push(WebsiteDetailScreen(carContext, state, m.id)) }.build())
            }
        }.sortedBy { it.first }.map { it.second }
        val list = ItemList.Builder().setNoItemsMessage("Nothing needs attention")
        rows.take(listLimit()).forEach { list.addItem(it) }
        return ListTemplate.Builder().setSingleList(list.build()).setTitle("Needs attention (${rows.size})")
            .setHeaderAction(Action.BACK).setActionStrip(refreshStrip()).build()
    }
}

private fun serverRow(s: Server, onClick: () -> Unit) = Row.Builder()
    .setTitle(statusTitle(s.status, s.name))
    .addText("CPU ${fmtPercent(s.cpuPercent)} · Memory ${fmtPercent(s.memPercent)} · Disk ${fmtPercent(s.diskPercent)}")
    .setBrowsable(true).setOnClickListener(onClick).build()

private fun monitorRow(m: Monitor, onClick: () -> Unit) = Row.Builder()
    .setTitle(statusTitle(m.status, m.title))
    .addText(listOfNotNull(m.statusMessage?.takeIf { m.status == Status.DOWN }, "Uptime ${fmtUptime(m.uptimePercent)}", m.ttfb?.let { "TTFB ${fmtSeconds(it)}" }).joinToString(" · "))
    .setBrowsable(true).setOnClickListener(onClick).build()

class ServerDetailScreen(ctx: CarContext, state: CarState, private val id: String) : LiveScreen(ctx, state) {
    override fun onGetTemplate(): Template {
        val s = state.servers?.firstOrNull { it.id == id }
            ?: return MessageTemplate.Builder("This server is no longer available.").setTitle("Server").setHeaderAction(Action.BACK).build()
        val p = state.prefs()
        val pane = Pane.Builder()
            .addRow(Row.Builder().setTitle("Status").addText(colored(s.statusRaw?.replaceFirstChar { it.uppercase() } ?: s.status.name.lowercase().replaceFirstChar { it.uppercase() }, colorOf(s.status))).build())
            .addRow(Row.Builder().setTitle("CPU").addText(usage(s.cpuPercent, p.cpuWarn, s.cores?.let { "  ·  $it cores" })).build())
            .addRow(Row.Builder().setTitle("Memory").addText(usage(s.memPercent, p.memWarn)).build())
            .addRow(Row.Builder().setTitle("Disk").addText(usage(s.diskPercent, p.diskWarn, s.uptimeSeconds?.let { "  ·  up ${fmtDuration(it)}" })).build())
            .addAction(Action.Builder().setTitle("Refresh").setOnClickListener { forceRefresh() }.build())
            .build()
        return PaneTemplate.Builder(pane).setTitle(s.name).setHeaderAction(Action.BACK).build()
    }

    private fun usage(v: Double?, warn: Int, suffix: String? = null): CharSequence {
        val b = SpannableStringBuilder()
        if (v == null) b.append("–") else b.append(colored(fmtPercent(v), if (v >= warn) CarColor.RED else CarColor.DEFAULT))
        if (suffix != null) b.append(suffix)
        return b
    }
}

class WebsiteDetailScreen(ctx: CarContext, state: CarState, private val id: String) : LiveScreen(ctx, state) {
    override fun onGetTemplate(): Template {
        val m = state.monitors?.firstOrNull { it.id == id }
            ?: return MessageTemplate.Builder("This website is no longer available.").setTitle("Website").setHeaderAction(Action.BACK).build()
        val p = state.prefs()
        val pane = Pane.Builder()
            .addRow(Row.Builder().setTitle("Status").addText(colored(m.statusMessage ?: m.status.name.lowercase().replaceFirstChar { it.uppercase() }, colorOf(m.status))).build())
            .addRow(Row.Builder().setTitle("Uptime").addText(colored(fmtUptime(m.uptimePercent), if ((m.uptimePercent ?: 100.0) < p.uptimeWarn) CarColor.RED else CarColor.GREEN)).build())
            .addRow(Row.Builder().setTitle("Response").addText("TTFB ${fmtSeconds(m.ttfb)}  ·  total ${fmtSeconds(m.timeTotal)}").build())
            .addRow(Row.Builder().setTitle(m.url).addText(m.locationName ?: "").build())
            .addAction(Action.Builder().setTitle("Refresh").setOnClickListener { forceRefresh() }.build())
            .build()
        return PaneTemplate.Builder(pane).setTitle(m.title).setHeaderAction(Action.BACK).build()
    }
}
