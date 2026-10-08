package app.monitor360.data

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

enum class WindowOwner(val path: String) { SERVER("server"), MONITOR("monitor") }

class MetricsResult(val charts: List<ChartData>, val granularitySeconds: Int?)

class Repository(private val api: ApiClient) {
    val rateLimit get() = api.rateLimit
    val requestLog get() = api.log

    private suspend fun paged(path: String, key: String, extra: Map<String, String?> = emptyMap()): List<JsonObject> {
        val out = mutableListOf<JsonObject>()
        var page = 1
        while (page <= MAX_PAGES) {
            val res = api.get(path, extra + mapOf("page" to page.toString(), "perpage" to PAGE_SIZE.toString()))
            val items = res.listUnder(key)
            out += items
            val total = (res as? JsonObject)?.obj("meta")?.int("total")
            if (items.size < PAGE_SIZE || (total != null && out.size >= total)) break
            page++
        }
        return out
    }

    suspend fun ping() {
        api.get("servers", mapOf("page" to "1", "perpage" to "1"))
    }

    suspend fun servers(tags: String? = null): List<Server> =
        paged("servers", "servers", mapOf("tags" to tags?.takeIf { it.isNotBlank() })).map { it.toServer() }

    suspend fun server(id: String): Server {
        val res = api.get("server/$id")
        val obj = res.asObj() ?: throw ApiException(404, "Server not found")
        return (obj.obj("server") ?: obj).toServer()
    }

    suspend fun updateServer(id: String, name: String?, tags: List<String>?) {
        api.put("server/$id", jsonObjectOf("name" to name.toJson(), "tags" to tags?.toJsonArray()))
    }

    suspend fun serverMetrics(
        id: String,
        metric: String,
        start: Long,
        end: Long,
        includeCores: Boolean = false,
        keys: String? = null,
        devices: String? = null,
    ): MetricsResult {
        val query = mapOf(
            "metric" to metric,
            "start" to "$start",
            "end" to "$end",
            "key" to keys?.takeIf { it.isNotBlank() },
            "device" to devices?.takeIf { it.isNotBlank() },
        )
        return metricsResult(api.get("server/$id/metrics", query), includeCores)
    }

    suspend fun serverChartImage(
        id: String,
        metric: String,
        start: Long,
        end: Long,
        keys: String? = null,
        devices: String? = null,
        width: Int = 900,
        height: Int = 400,
    ): String {
        val query = mapOf(
            "metric" to metric,
            "start" to "$start",
            "end" to "$end",
            "output" to "png",
            "width" to "$width",
            "height" to "$height",
            "key" to keys?.takeIf { it.isNotBlank() },
            "device" to devices?.takeIf { it.isNotBlank() },
        )
        val obj = api.get("server/$id/metrics", query).asObj()
        return obj?.str("uri", "url", "link") ?: throw ApiException(404, "The API returned no image for this metric")
    }

    suspend fun serverNotifications(id: String, start: Long, end: Long): List<AlertEvent> =
        api.get("server/$id/notifications", mapOf("start" to "$start", "end" to "$end"))
            .listUnder("data").map { it.toAlert() }.sortedByDescending { it.start ?: 0 }

    suspend fun monitors(): List<Monitor> = paged("monitors", "monitors").map { it.toMonitor() }

    suspend fun monitor(id: String): Monitor {
        val obj = api.get("monitor/$id").asObj() ?: throw ApiException(404, "Monitor not found")
        return (obj.obj("monitor")?.takeIf { it.containsKey("url") } ?: obj).toMonitor()
    }

    suspend fun createMonitor(input: MonitorInput) {
        api.post("monitors", input.toJson())
    }

    suspend fun updateMonitor(id: String, input: MonitorInput) {
        api.put("monitor/$id", input.toJson())
    }

    suspend fun deleteMonitor(id: String) {
        api.delete("monitor/$id")
    }

    suspend fun monitorMetrics(id: String, start: Long, end: Long): MetricsResult =
        metricsResult(api.get("monitor/$id/metrics", mapOf("start" to "$start", "end" to "$end")), false)

    suspend fun monitorUptime(id: String, start: Long, end: Long): UptimePeriod {
        val obj = api.get("monitor/$id/uptime", mapOf("start" to "$start", "end" to "$end")).asObj()
            ?: throw ApiException(404, "No uptime data")
        return obj.toUptimePeriod()
    }

    suspend fun monitorNotifications(id: String, start: Long, end: Long): List<AlertEvent> =
        api.get("monitor/$id/notifications", mapOf("start" to "$start", "end" to "$end"))
            .listUnder("data").map { it.toAlert() }.sortedByDescending { it.start ?: 0 }

    suspend fun windows(owner: WindowOwner, id: String): List<MaintenanceWindow> =
        api.get("${owner.path}/$id/window").listUnder("windows").map { it.toWindow() }

    suspend fun createWindow(owner: WindowOwner, id: String, input: WindowInput) {
        api.post("${owner.path}/$id/window", input.toJson())
    }

    suspend fun updateWindow(owner: WindowOwner, id: String, windowId: String, input: WindowInput) {
        api.put("${owner.path}/$id/window/$windowId", input.toJson())
    }

    suspend fun deleteWindow(owner: WindowOwner, id: String, windowId: String) {
        api.delete("${owner.path}/$id/window/$windowId")
    }

    suspend fun nodes(): List<Node> = paged("nodes", "nodes").map { it.toNode() }

    suspend fun contacts(): List<Contact> = paged("contacts", "contacts").map { it.toContact() }

    suspend fun contact(id: String): Contact {
        val obj = api.get("contact/$id").asObj() ?: throw ApiException(404, "Contact not found")
        return (obj.obj("contact") ?: obj).toContact()
    }

    suspend fun createContact(input: ContactInput) {
        api.post("contacts", input.toJson())
    }

    suspend fun updateContact(id: String, input: ContactInput) {
        api.put("contact/$id", input.toJson())
    }

    suspend fun deleteContact(id: String) {
        api.delete("contact/$id")
    }

    suspend fun userTokens(): List<UserToken> = paged("usertoken", "tokens").map { it.toUserToken() }

    suspend fun createUserToken(name: String, tags: List<String>) {
        api.post("usertoken", jsonObjectOf("name" to name.toJson(), "tags" to tags.toJsonArray()))
    }

    suspend fun incidents(pageId: String): List<Incident> =
        paged("page/$pageId/incidents", "incidents").map { it.toIncident() }

    suspend fun createIncident(pageId: String, input: IncidentInput) {
        api.post("page/$pageId/incidents", input.toJson())
    }

    suspend fun createIncidentUpdate(pageId: String, incidentId: String, input: IncidentInput) {
        api.post("page/$pageId/incidents/$incidentId", input.toJson())
    }

    suspend fun editIncidentUpdate(pageId: String, incidentId: String, updateId: String, input: IncidentInput) {
        api.put("page/$pageId/incident/$incidentId/$updateId", input.toJson())
    }

    suspend fun deleteIncidentUpdate(pageId: String, incidentId: String, updateId: String) {
        api.delete("page/$pageId/incident/$incidentId/$updateId")
    }

    private fun metricsResult(res: JsonElement, includeCores: Boolean): MetricsResult {
        val gran = (res as? JsonObject)?.obj("meta")?.int("granularity")
        return MetricsResult(parseMetrics(res, includeCores), gran)
    }

    companion object {
        const val PAGE_SIZE = 100
        const val MAX_PAGES = 30
    }
}
