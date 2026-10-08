package app.monitor360.data

import android.content.Context
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

data class SnapServer(val id: String, val name: String, val status: Status, val cpu: Double?, val mem: Double?, val disk: Double?, val favorite: Boolean = false)
data class SnapMonitor(val id: String, val name: String, val url: String, val status: Status, val uptime: Double?, val ttfb: Double?, val favorite: Boolean = false)

data class Snapshot(
    val servers: List<SnapServer> = emptyList(),
    val monitors: List<SnapMonitor> = emptyList(),
    val updatedAt: Long = 0,
    val error: String? = null,
) {
    val serversUp get() = servers.count { it.status == Status.UP }
    val serversDown get() = servers.count { it.status == Status.DOWN }
    val monitorsUp get() = monitors.count { it.status == Status.UP }
    val monitorsDown get() = monitors.count { it.status == Status.DOWN }
    val hasData get() = updatedAt > 0
}

fun List<Server>.toSnap(favorites: Set<String> = emptySet()) =
    map { SnapServer(it.id, it.name, it.status, it.cpuPercent, it.memPercent, it.diskPercent, it.id in favorites) }

fun List<Monitor>.toSnapMonitors(favorites: Set<String> = emptySet()) =
    map { SnapMonitor(it.id, it.title, it.url, it.status, it.uptimePercent, it.ttfb, it.id in favorites) }

class SnapshotStore(context: Context) {
    private val sp = context.getSharedPreferences("snapshot", Context.MODE_PRIVATE)

    fun load(): Snapshot = runCatching {
        val o = AppJson.parseToJsonElement(sp.getString("data", null) ?: return Snapshot()) as JsonObject
        Snapshot(
            servers = o.arr("servers")?.mapNotNull { it as? JsonObject }?.map {
                SnapServer(it.str("id") ?: "", it.str("name") ?: "", parseStatus(it.str("status")), it.dbl("cpu"), it.dbl("mem"), it.dbl("disk"), it.bool("fav") ?: false)
            } ?: emptyList(),
            monitors = o.arr("monitors")?.mapNotNull { it as? JsonObject }?.map {
                SnapMonitor(it.str("id") ?: "", it.str("name") ?: "", it.str("url") ?: "", parseStatus(it.str("status")), it.dbl("uptime"), it.dbl("ttfb"), it.bool("fav") ?: false)
            } ?: emptyList(),
            updatedAt = o.lng("updatedAt") ?: 0,
            error = o.str("error"),
        )
    }.getOrDefault(Snapshot())

    fun save(s: Snapshot) {
        val o = jsonObjectOf(
            "updatedAt" to JsonPrimitive(s.updatedAt),
            "error" to s.error.toJson(),
            "servers" to JsonArray(s.servers.map {
                jsonObjectOf(
                    "id" to JsonPrimitive(it.id), "name" to JsonPrimitive(it.name),
                    "status" to JsonPrimitive(it.status.name.lowercase()),
                    "cpu" to it.cpu.toJson(), "mem" to it.mem.toJson(), "disk" to it.disk.toJson(),
                    "fav" to JsonPrimitive(it.favorite),
                )
            }),
            "monitors" to JsonArray(s.monitors.map {
                jsonObjectOf(
                    "id" to JsonPrimitive(it.id), "name" to JsonPrimitive(it.name), "url" to JsonPrimitive(it.url),
                    "status" to JsonPrimitive(it.status.name.lowercase()),
                    "uptime" to it.uptime.toJson(), "ttfb" to it.ttfb.toJson(),
                    "fav" to JsonPrimitive(it.favorite),
                )
            }),
        )
        sp.edit().putString("data", o.toString()).apply()
    }

    fun clear() = sp.edit().clear().apply()
}
