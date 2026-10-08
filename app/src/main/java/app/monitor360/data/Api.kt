package app.monitor360.data

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

data class ApiLogEntry(
    val time: Long,
    val method: String,
    val path: String,
    val code: Int,
    val millis: Long,
    val error: String?,
)

class ApiException(val code: Int, override val message: String) : IOException(message)

data class RateLimit(val limit: Int?, val remaining: Int?, val resetEpochSeconds: Long?)

class ApiClient(
    private val prefs: () -> Prefs,
    private val http: OkHttpClient = defaultClient(),
) {
    private val _rateLimit = MutableStateFlow<RateLimit?>(null)
    val rateLimit: StateFlow<RateLimit?> = _rateLimit.asStateFlow()

    private val _log = MutableStateFlow<List<ApiLogEntry>>(emptyList())
    val log: StateFlow<List<ApiLogEntry>> = _log.asStateFlow()

    private fun record(method: String, path: String, code: Int, startedNanos: Long, error: String?) {
        val ms = (System.nanoTime() - startedNanos) / 1_000_000
        val entry = ApiLogEntry(System.currentTimeMillis(), method, path, code, ms, error)
        _log.value = (listOf(entry) + _log.value).take(LOG_SIZE)
    }

    suspend fun get(path: String, query: Map<String, String?> = emptyMap()): JsonElement =
        call("GET", path, query, null)

    suspend fun post(path: String, body: JsonObject? = null, query: Map<String, String?> = emptyMap()): JsonElement =
        call("POST", path, query, body ?: JsonObject(emptyMap()))

    suspend fun put(path: String, body: JsonObject, query: Map<String, String?> = emptyMap()): JsonElement =
        call("PUT", path, query, body)

    suspend fun delete(path: String, query: Map<String, String?> = emptyMap()): JsonElement =
        call("DELETE", path, query, null)

    private suspend fun call(
        method: String,
        path: String,
        query: Map<String, String?>,
        body: JsonObject?,
    ): JsonElement = withContext(Dispatchers.IO) {
        val p = prefs()
        if (p.apiKey.isBlank()) throw ApiException(401, "No API key configured")
        val base = p.baseUrl.trim().trimEnd('/')
        val url = try {
            "$base/${path.trimStart('/')}".toHttpUrl().newBuilder().apply {
                addQueryParameter("token", p.apiKey)
                query.forEach { (k, v) -> if (v != null) addQueryParameter(k, v) }
            }.build()
        } catch (e: IllegalArgumentException) {
            throw ApiException(0, "Invalid API endpoint: ${p.baseUrl}")
        }
        val requestBody = body?.let { AppJson.encodeToString(JsonObject.serializer(), it).toRequestBody(JSON) }
        val request = Request.Builder()
            .url(url)
            .method(method, requestBody)
            .header("Authorization", "Bearer ${p.apiKey}")
            .header("Accept", "application/json")
            .header("User-Agent", "360-Monitor-Android/1.0")
            .build()
        val started = System.nanoTime()
        try {
            http.newCall(request).execute().use { resp ->
                _rateLimit.value = RateLimit(
                    resp.header("RateLimit-Limit")?.toIntOrNull(),
                    resp.header("RateLimit-Remaining")?.toIntOrNull(),
                    resp.header("RateLimit-Reset")?.toLongOrNull(),
                )
                val text = resp.body?.string().orEmpty()
                val failed = !resp.isSuccessful
                record(method, path, resp.code, started, if (failed) errorMessage(resp.code, text) else null)
                if (failed) throw ApiException(resp.code, errorMessage(resp.code, text))
                if (text.isBlank()) JsonNull else runCatching { AppJson.parseToJsonElement(text) }.getOrDefault(JsonNull)
            }
        } catch (e: ApiException) {
            throw e
        } catch (e: IOException) {
            val message = "Network error: ${e.message ?: "could not reach the server"}"
            record(method, path, 0, started, message)
            throw ApiException(0, message)
        }
    }

    companion object {
        private const val LOG_SIZE = 60
        private val JSON = "application/json; charset=utf-8".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        fun errorMessage(code: Int, body: String): String {
            val apiError = runCatching { AppJson.parseToJsonElement(body).asObj()?.str("error", "message") }.getOrNull()
            return when (code) {
                401, 403 -> "Authorization failed (HTTP $code). Check that the API key is correct and that your plan includes API access."
                404 -> apiError ?: "Not found"
                429 -> "Rate limit exceeded (1500 requests per hour). Try again later."
                else -> apiError ?: "Request failed (HTTP $code)"
            }
        }
    }
}
