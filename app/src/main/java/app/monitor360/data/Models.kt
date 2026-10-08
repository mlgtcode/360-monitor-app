package app.monitor360.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

enum class Status { UP, DOWN, UNKNOWN }

fun parseStatus(raw: String?): Status = when (raw?.trim()?.lowercase()) {
    "up", "ok", "online", "alive", "active", "healthy", "closed" -> Status.UP
    "down", "offline", "error", "failed", "critical", "open" -> Status.DOWN
    else -> Status.UNKNOWN
}

data class Disk(val mount: String, val freeBytes: Double, val usedBytes: Double) {
    val total get() = freeBytes + usedBytes
    val usedPercent get() = if (total > 0) usedBytes / total * 100 else 0.0
}

data class Server(
    val id: String,
    val name: String,
    val status: Status,
    val statusRaw: String?,
    val os: String?,
    val agentVersion: String?,
    val ip: String?,
    val country: String?,
    val org: String?,
    val tags: List<String>,
    val cpuPercent: Double?,
    val memPercent: Double?,
    val diskPercent: Double?,
    val uptimeSeconds: Long?,
    val cores: Int?,

    val memTotalKb: Double?,
    val memUsedKb: Double?,
    val disks: List<Disk>,
    val lastUpdate: Long?,
    val plugins: List<String>,
    val devices: Map<String, List<DeviceHint>> = emptyMap(),
    val fields: Map<String, List<String>> = emptyMap(),
)

data class DeviceHint(val id: String, val label: String)

fun JsonObject.toServer(): Server {
    val last = obj("last_data")
    val summary = obj("summary")
    val whois = obj("ip_whois")
    val memory = last?.obj("memory")
    val disks = last?.arr("df")?.mapNotNull { it as? JsonObject }?.map {
        Disk(it.str("mount") ?: "/", it.dbl("free_bytes") ?: 0.0, it.dbl("used_bytes") ?: 0.0)
    } ?: emptyList()
    val raw = str("status")
    return Server(
        id = str("id", "_id") ?: "",
        name = str("name", "hostname") ?: "(unnamed)",
        status = parseStatus(raw),
        statusRaw = raw,
        os = str("os"),
        agentVersion = str("agent_version"),
        ip = whois?.str("ip") ?: str("connecting_ip"),
        country = whois?.str("country"),
        org = whois?.str("org"),
        tags = strList("tags"),
        cpuPercent = summary?.dbl("cpu_usage_percent"),
        memPercent = summary?.dbl("mem_usage_percent"),
        diskPercent = summary?.dbl("disk_usage_percent"),
        uptimeSeconds = last?.obj("uptime")?.lng("seconds"),
        cores = last?.int("cores"),
        memTotalKb = memory?.dbl("total"),
        memUsedKb = memory?.dbl("used"),
        disks = disks,
        lastUpdate = lng("last_update", "last_data_update", "updated"),
        plugins = last?.filterValues { it is JsonObject || it is JsonArray }?.keys?.toList() ?: emptyList(),
        devices = last?.let(::deviceHints) ?: emptyMap(),
        fields = last?.let(::fieldHints) ?: emptyMap(),
    )
}

private val DEVICE_LABEL_KEYS = arrayOf("name", "device", "mount", "interface", "iface", "ifname", "disk", "label", "path")

private fun deviceHints(last: JsonObject): Map<String, List<DeviceHint>> {
    val out = linkedMapOf<String, List<DeviceHint>>()
    for ((metric, value) in last) {
        val hints = when (value) {
            is JsonArray -> value.mapIndexedNotNull { i, el ->
                val label = (el as? JsonObject)?.str(*DEVICE_LABEL_KEYS) ?: el.asStr()
                DeviceHint(i.toString(), label?.let { "$i · $it" } ?: i.toString())
            }
            is JsonObject -> value.entries.filter { it.value is JsonObject }.map { DeviceHint(it.key, it.key) }
            else -> emptyList()
        }
        if (hints.size > 1) out[metric] = hints
    }
    return out
}

private fun fieldHints(last: JsonObject): Map<String, List<String>> {
    val out = linkedMapOf<String, List<String>>()
    for ((metric, value) in last) {
        val sample = when (value) {
            is JsonObject -> value.values.firstOrNull { it is JsonObject } as? JsonObject ?: value
            is JsonArray -> value.firstOrNull() as? JsonObject
            else -> null
        } ?: continue
        val keys = sample.filterValues { it is JsonPrimitive }.keys.toList()
        if (keys.isNotEmpty()) out[metric] = keys
    }
    return out
}

data class UptimePeriod(
    val label: String,
    val percent: Double,
    val downtimeSeconds: Long,
    val events: Int,
    val start: Long?,
    val end: Long?,
)

fun JsonObject.toUptimePeriod() = UptimePeriod(
    label = str("date", "label") ?: "",
    percent = dbl("uptime_percentage") ?: 0.0,
    downtimeSeconds = lng("downtime_seconds") ?: 0,
    events = int("events") ?: 0,
    start = lng("start"),
    end = lng("end"),
)

data class Monitor(
    val id: String,
    val name: String,
    val url: String,
    val protocol: String?,
    val status: Status,
    val statusMessage: String?,
    val httpCode: String?,
    val uptimePercent: Double?,
    val ttfb: Double?,
    val timeTotal: Double?,
    val timeDns: Double?,
    val timeConnect: Double?,
    val locationName: String?,
    val locationId: String?,
    val ip: String?,
    val lastUpdate: Long?,
    val firstUpdate: Long?,
    val uptimes: List<UptimePeriod>,
    val raw: JsonObject,
) {
    val title get() = name.ifBlank { url }
}

fun JsonObject.toMonitor(): Monitor {
    val check = obj("last_check")
    val node = obj("monitor")
    return Monitor(
        id = str("id", "_id") ?: "",
        name = str("name") ?: "",
        url = str("url") ?: "",
        protocol = str("type", "protocol"),
        status = parseStatus(str("status")),
        statusMessage = str("status_message"),
        httpCode = str("code") ?: check?.str("http_status_code"),
        uptimePercent = dbl("uptime_percentage"),
        ttfb = check?.dbl("time_to_first_byte", "ttfb"),
        timeTotal = check?.dbl("time_total"),
        timeDns = check?.dbl("time_dns"),
        timeConnect = check?.dbl("time_connect"),
        locationName = node?.str("name"),
        locationId = node?.str("id", "_id"),
        ip = str("ip_address"),
        lastUpdate = lng("last_update"),
        firstUpdate = lng("first_update"),
        uptimes = arr("uptimes")?.mapNotNull { (it as? JsonObject)?.toUptimePeriod() } ?: emptyList(),
        raw = this,
    )
}

data class Node(val id: String, val name: String, val ip: String?)

fun JsonObject.toNode() = Node(
    id = str("id", "_id") ?: "",
    name = str("pretty_name", "name") ?: "Unknown",
    ip = str("ip"),
)

data class Contact(
    val id: String,
    val name: String,
    val email: String?,
    val sms: String?,
    val pushbullet: String?,
    val pushover: String?,
    val slackChannel: String?,
    val slackWebhook: String?,
    val webhook: String?,
) {
    val channels: List<String>
        get() = buildList {
            if (!email.isNullOrBlank()) add("email")
            if (!sms.isNullOrBlank()) add("sms")
            if (!pushbullet.isNullOrBlank()) add("pushbullet")
            if (!pushover.isNullOrBlank()) add("pushover")
            if (!slackChannel.isNullOrBlank() || !slackWebhook.isNullOrBlank()) add("slack")
            if (!webhook.isNullOrBlank()) add("webhook")
        }
}

fun JsonObject.toContact(): Contact {
    val ch = obj("channels") ?: this
    return Contact(
        id = str("id", "_id") ?: "",
        name = str("name") ?: "(unnamed)",
        email = ch.str("email"),
        sms = ch.str("sms", "phonenumber"),
        pushbullet = ch.str("pushbullet"),
        pushover = ch.str("pushover"),
        slackChannel = ch.str("slack_channel"),
        slackWebhook = ch.str("slack_webhook"),
        webhook = ch.str("webhook"),
    )
}

data class Recipient(val name: String, val type: String)

data class AlertEvent(
    val id: String,
    val start: Long?,
    val end: Long?,
    val open: Boolean,
    val summary: String,
    val metric: String?,
    val comparison: String?,
    val recipients: List<Recipient>,
)

fun JsonObject.toAlert(): AlertEvent {
    val endEl = this["end"]
    val endTs = (endEl as? JsonPrimitive)?.takeIf { !it.isString && it.content != "false" && it !is JsonNull }
        ?.content?.toDoubleOrNull()?.toLong()
    val status = str("status")
    return AlertEvent(
        id = str("id", "_id") ?: "",
        start = lng("start", "opened"),
        end = endTs,
        open = status?.equals("open", true) ?: (endTs == null),
        summary = str("summary") ?: "(no summary)",
        metric = str("metric"),
        comparison = str("comparison"),
        recipients = arr("recipients")?.mapNotNull { it as? JsonObject }
            ?.map { Recipient(it.str("name") ?: "", it.str("type") ?: "") } ?: emptyList(),
    )
}

data class MaintenanceWindow(
    val id: String,
    val name: String,
    val interval: String,
    val impact: String?,
    val startDate: String?,
    val endDate: String?,
    val startTime: String?,
    val endTime: String?,
    val days: String?,
    val dayOfMonth: String?,
)

fun JsonObject.toWindow() = MaintenanceWindow(
    id = str("id", "_id") ?: "",
    name = str("name") ?: "",
    interval = str("interval") ?: "once",
    impact = str("impact"),
    startDate = str("startdate", "start-date", "start_date"),
    endDate = str("enddate", "end-date", "end_date"),
    startTime = str("starttime", "start-time", "start_time"),
    endTime = str("endtime", "end-time", "end_time"),
    days = this["days"].let { el ->
        if (el is JsonArray) el.mapNotNull { it.asStr() }.joinToString(",") else el.asStr()
    },
    dayOfMonth = str("day-of-month", "day_of_month", "dayofmonth"),
)

data class IncidentUpdate(val id: String, val name: String?, val body: String?, val status: Int?, val timestamp: Long?)

data class Incident(
    val id: String,
    val name: String,
    val body: String?,
    val status: Int?,
    val timestamp: Long?,
    val updates: List<IncidentUpdate>,
)

fun JsonObject.toIncident() = Incident(
    id = str("id", "_id") ?: "",
    name = str("name") ?: "(unnamed)",
    body = str("body"),
    status = int("status"),
    timestamp = lng("timestamp", "created"),
    updates = (arr("updates") ?: arr("incident_updates"))?.mapNotNull { it as? JsonObject }?.map {
        IncidentUpdate(it.str("id", "_id") ?: "", it.str("name"), it.str("body"), it.int("status"), it.lng("timestamp"))
    } ?: emptyList(),
)

data class UserToken(val token: String, val name: String?, val tags: List<String>)

fun JsonObject.toUserToken() = UserToken(str("token", "id") ?: "", str("name"), strList("tags"))

data class MonitorInput(
    val name: String,
    val url: String,
    val protocol: String = "https",
    val port: Int? = null,
    val interval: Int? = null,
    val timeout: Int? = null,
    val connectTimeout: Int? = null,
    val redirects: Int? = null,
    val method: String? = null,
    val keyword: String? = null,
    val keywordAlertWhenFound: Boolean? = null,
    val userAgent: String? = null,
    val headers: String? = null,
    val postData: String? = null,
    val resolve: String? = null,
    val ipVersion: String? = null,
    val downFromMin: Int? = null,
    val triggerAfterChecks: Int? = null,
    val auth: Int? = null,
    val httpUsername: String? = null,
    val httpPassword: String? = null,
    val nodeId: String? = null,
    val dependencyId: String? = null,

    val contactList: List<String> = emptyList(),
) {
    fun toJson(): JsonObject = jsonObjectOf(
        "name" to name.toJson(),

        "url" to url.trim().removePrefix("https://").removePrefix("http://").toJson(),
        "protocol" to protocol.toJson(),
        "port" to port.toJson(),
        "interval" to interval.toJson(),
        "timeout" to timeout.toJson(),
        "ctimeout" to connectTimeout.toJson(),
        "redirects" to redirects.toJson(),
        "method" to method.toJson(),
        "keyword" to keyword.toJson(),
        "match_type" to (if (keyword.isNullOrBlank()) null else if (keywordAlertWhenFound == false) "no" else "yes").toJson(),
        "useragent" to userAgent.toJson(),
        "headers" to headers.toJson(),
        "postdata" to postData.toJson(),
        "resolve" to resolve.toJson(),
        "ipv4_ipv6" to ipVersion.toJson(),
        "down_from_min" to downFromMin.toJson(),
        "trigger_after_checks" to triggerAfterChecks.toJson(),
        "auth" to auth.toJson(),
        "http_username" to httpUsername.toJson(),
        "http_password" to httpPassword.toJson(),
        "monitor" to nodeId.toJson(),
        "dep" to dependencyId.toJson(),
        "contact_list[]" to contactList.takeIf { it.isNotEmpty() }?.toJsonArray(),
    )
}

data class ContactInput(
    val name: String,
    val email: String = "",
    val sms: String = "",
    val pushbullet: String = "",
    val pushover: String = "",
    val slackChannel: String = "",
    val slackWebhook: String = "",
    val webhook: String = "",
) {
    fun toJson(): JsonObject = jsonObjectOf(
        "name" to name.toJson(),
        "channels" to jsonObjectOf(
            "email" to email.toJson(),
            "sms" to sms.toJson(),
            "pushbullet" to pushbullet.toJson(),
            "pushover" to pushover.toJson(),
            "slack_channel" to slackChannel.toJson(),
            "slack_webhook" to slackWebhook.toJson(),
            "webhook" to webhook.toJson(),
        ),
    )
}

data class WindowInput(
    val name: String,
    val interval: String = "once",
    val impact: String = "minor",
    val startDate: String = "",
    val endDate: String = "",
    val startTime: String = "",
    val endTime: String = "",
    val days: String = "",
    val dayOfMonth: String = "",
) {
    fun toJson(): JsonObject = jsonObjectOf(
        "name" to name.toJson(),
        "interval" to interval.toJson(),
        "impact" to impact.toJson(),
        "start-time" to startTime.toJson(),
        "end-time" to endTime.toJson(),
        "start-date" to (if (interval == "once") startDate else "").toJson(),
        "end-date" to (if (interval == "once") endDate else "").toJson(),
        "days" to (if (interval == "daily") days else "").toJson(),
        "day-of-month" to (if (interval == "monthly") dayOfMonth else "").toJson(),
    )
}

data class IncidentInput(val name: String, val body: String, val status: Int)

fun IncidentInput.toJson(): JsonObject = jsonObjectOf(
    "name" to name.toJson(),
    "body" to body.toJson(),
    "status" to status.toJson(),
)

data class Series(val name: String, val points: List<Pair<Long, Double>>)
data class ChartData(val title: String, val series: List<Series>)

private val TS_KEY = Regex("^\\d{9,11}$")

private fun JsonObject.isTimestampMap() = isNotEmpty() && keys.all { TS_KEY.matches(it) }

private fun JsonObject.toPoints(): List<Pair<Long, Double>> =
    entries.mapNotNull { (k, v) ->
        val d = (v as? JsonPrimitive)?.content?.toDoubleOrNull() ?: return@mapNotNull null
        k.toLong() to d
    }.sortedBy { it.first }

private fun pointOf(el: JsonElement): Pair<Long, Double>? {
    val arr = el as? JsonArray ?: return null
    if (arr.size != 2) return null
    val t = (arr[0] as? JsonPrimitive)?.content?.toDoubleOrNull() ?: return null
    val v = (arr[1] as? JsonPrimitive)?.content?.toDoubleOrNull() ?: return null
    return t.toLong() to v
}

private fun collectSeries(path: String, el: JsonElement, out: MutableList<Series>, includeCores: Boolean) {
    when (el) {
        is JsonObject -> {
            if (el.isTimestampMap()) {
                val first = el.values.first()
                if (first is JsonObject) {
                    val names = el.values.flatMap { (it as? JsonObject)?.keys ?: emptySet() }.distinct()
                    for (n in names) {
                        val pts = el.entries.mapNotNull { (k, v) ->
                            val d = (v as? JsonObject)?.get(n)?.let { (it as? JsonPrimitive)?.content?.toDoubleOrNull() }
                            d?.let { k.toLong() to it }
                        }.sortedBy { it.first }
                        if (pts.isNotEmpty()) out += Series(if (path.isEmpty()) n else "$path.$n", pts)
                    }
                } else {
                    val pts = el.toPoints()
                    if (pts.isNotEmpty()) out += Series(path.ifEmpty { "value" }, pts)
                }
            } else {
                for ((k, v) in el) {
                    if (!includeCores && k == "cores") continue
                    collectSeries(if (path.isEmpty()) k else "$path.$k", v, out, includeCores)
                }
            }
        }
        is JsonArray -> {
            val pairs = el.mapNotNull { pointOf(it) }
            if (pairs.isNotEmpty() && pairs.size == el.size) {
                out += Series(path.ifEmpty { "value" }, pairs.sortedBy { it.first })
            } else {
                el.forEachIndexed { i, v -> collectSeries("$path[$i]", v, out, includeCores) }
            }
        }
        else -> Unit
    }
}

fun parseMetrics(root: JsonElement, includeCores: Boolean = false): List<ChartData> {
    val data = (root as? JsonObject)?.get("data") ?: root
    val obj = data as? JsonObject ?: return emptyList()
    if (obj.isTimestampMap()) {
        val out = mutableListOf<Series>()
        collectSeries("", obj, out, includeCores)
        return if (out.isEmpty()) emptyList() else listOf(ChartData("metrics", out))
    }
    return obj.entries.mapNotNull { (k, v) ->
        val out = mutableListOf<Series>()
        collectSeries("", v, out, includeCores)
        if (out.isEmpty()) null else ChartData(k, out.take(14))
    }
}
