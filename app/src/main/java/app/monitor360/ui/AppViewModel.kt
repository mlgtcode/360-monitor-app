package app.monitor360.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.monitor360.MonitorApp
import app.monitor360.data.Contact
import app.monitor360.data.Monitor
import app.monitor360.data.Node
import app.monitor360.data.Server
import app.monitor360.data.Snapshot
import app.monitor360.data.toSnap
import app.monitor360.data.toSnapMonitors
import app.monitor360.ui.components.Load
import app.monitor360.widget.WidgetUpdater
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as MonitorApp
    val settings = app.settings
    val repo = app.repo
    val prefs = settings.prefs
    val rateLimit = app.api.rateLimit
    val requestLog = app.api.log
    val tagFilter = MutableStateFlow<String?>(null)
    val sharedUrl = MutableStateFlow<String?>(null)

    private val _servers = MutableStateFlow<Load<List<Server>>>(Load.Loading)
    val servers: StateFlow<Load<List<Server>>> = _servers.asStateFlow()
    private val _monitors = MutableStateFlow<Load<List<Monitor>>>(Load.Loading)
    val monitors: StateFlow<Load<List<Monitor>>> = _monitors.asStateFlow()
    private val _contacts = MutableStateFlow<Load<List<Contact>>>(Load.Loading)
    val contacts: StateFlow<Load<List<Contact>>> = _contacts.asStateFlow()
    private val _nodes = MutableStateFlow<Load<List<Node>>>(Load.Loading)
    val nodes: StateFlow<Load<List<Node>>> = _nodes.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()
    private val _lastUpdated = MutableStateFlow(0L)
    val lastUpdated: StateFlow<Long> = _lastUpdated.asStateFlow()

    @Volatile
    private var active = true

    init {
        viewModelScope.launch {
            while (true) {
                val p = settings.current
                val quotaLow = (rateLimit.value?.remaining ?: Int.MAX_VALUE) < LOW_QUOTA
                val due = p.autoRefreshSeconds > 0 || _servers.value is Load.Loading
                if (active && p.isConfigured && due && !quotaLow) refresh()
                delay(if (p.autoRefreshSeconds > 0) p.autoRefreshSeconds * 1000L else 5_000L)
            }
        }
    }

    fun setActive(value: Boolean) {
        active = value
        if (value && settings.current.isConfigured && System.currentTimeMillis() - _lastUpdated.value > STALE_MS) refresh()
    }

    fun toggleFavorite(id: String) {
        settings.update { p -> p.copy(favorites = if (id in p.favorites) p.favorites - id else p.favorites + id) }
    }

    fun refresh() {
        if (!settings.current.isConfigured || _refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            val (s, m) = coroutineScope {
                val a = async { runCatching { repo.servers() } }
                val b = async { runCatching { repo.monitors() } }
                a.await() to b.await()
            }
            s.onSuccess { _servers.value = Load.Ok(it) }.onFailure { if (_servers.value !is Load.Ok) _servers.value = Load.Err(it.userMessage()) }
            m.onSuccess { _monitors.value = Load.Ok(it) }.onFailure { if (_monitors.value !is Load.Ok) _monitors.value = Load.Err(it.userMessage()) }
            if (s.isSuccess || m.isSuccess) {
                _lastUpdated.value = System.currentTimeMillis()
                val old = app.snapshots.load()
                app.snapshots.save(
                    Snapshot(
                        servers = s.getOrNull()?.toSnap(settings.current.favorites) ?: old.servers,
                        monitors = m.getOrNull()?.toSnapMonitors(settings.current.favorites) ?: old.monitors,
                        updatedAt = System.currentTimeMillis(),
                        error = null,
                    ),
                )
                WidgetUpdater.updateAll(app)
            }
            _refreshing.value = false
        }
    }

    fun loadContacts() {
        viewModelScope.launch {
            runCatching { repo.contacts() }
                .onSuccess { _contacts.value = Load.Ok(it) }
                .onFailure { _contacts.value = Load.Err(it.userMessage()) }
        }
    }

    fun loadNodes() {
        if (_nodes.value is Load.Ok) return
        viewModelScope.launch {
            runCatching { repo.nodes() }
                .onSuccess { _nodes.value = Load.Ok(it) }
                .onFailure { _nodes.value = Load.Err(it.userMessage()) }
        }
    }

    private companion object {
        const val LOW_QUOTA = 100
        const val STALE_MS = 30_000L
    }

    fun resetData() {
        _servers.value = Load.Loading
        _monitors.value = Load.Loading
        _contacts.value = Load.Loading
        _nodes.value = Load.Loading
        app.snapshots.clear()
    }
}
