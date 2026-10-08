package app.monitor360.car

import app.monitor360.data.Monitor
import app.monitor360.data.Prefs
import app.monitor360.data.Repository
import app.monitor360.data.Server
import app.monitor360.ui.userMessage

interface CarDataSource {
    suspend fun servers(): List<Server>
    suspend fun monitors(): List<Monitor>
}

class RepositoryCarSource(private val repo: Repository) : CarDataSource {
    override suspend fun servers() = repo.servers()
    override suspend fun monitors() = repo.monitors()
}

class CarState(
    private val source: CarDataSource,
    val prefs: () -> Prefs,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    var servers: List<Server>? = null
        private set
    var monitors: List<Monitor>? = null
        private set
    var error: String? = null
        private set
    var loading = true
        private set
    private var lastRefresh = 0L

    val hasData get() = servers != null || monitors != null

    suspend fun refresh(minAgeMs: Long = 0) {
        if (hasData && clock() - lastRefresh < minAgeMs) return
        loading = true
        val s = runCatching { source.servers() }
        val m = runCatching { source.monitors() }
        s.onSuccess { servers = it }
        m.onSuccess { monitors = it }
        error = if (s.isFailure && m.isFailure) (s.exceptionOrNull() ?: m.exceptionOrNull())?.userMessage() else null
        if (s.isSuccess || m.isSuccess) lastRefresh = clock()
        loading = false
    }
}
