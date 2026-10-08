package app.monitor360.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class Source(val id: String, val name: String, val owner: WindowOwner) {
    val kind get() = if (owner == WindowOwner.SERVER) "Server" else "Website"
}

class Collected<T>(val rows: List<Pair<Source, T>>, val failed: Int, val skipped: Int)

suspend fun Repository.allSources(servers: List<Server>? = null, monitors: List<Monitor>? = null): List<Source> {
    val s = servers ?: servers()
    val m = monitors ?: monitors()
    return s.map { Source(it.id, it.name, WindowOwner.SERVER) } + m.map { Source(it.id, it.title, WindowOwner.MONITOR) }
}

suspend fun <T> collectFromSources(
    sources: List<Source>,
    limit: Int,
    parallel: Int = 5,
    fetch: suspend (Source) -> List<T>,
): Collected<T> = coroutineScope {
    val used = sources.take(limit)
    val gate = Semaphore(parallel)
    val results = used.map { source ->
        async { gate.withPermit { runCatching { fetch(source) } } to source }
    }.awaitAll()
    val rows = results.flatMap { (result, source) -> result.getOrNull().orEmpty().map { source to it } }
    Collected(rows, results.count { it.first.isFailure }, sources.size - used.size)
}
