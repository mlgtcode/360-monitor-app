package app.monitor360.archive

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import app.monitor360.data.Repository
import app.monitor360.export.Dataset
import app.monitor360.export.ExportCollector
import app.monitor360.export.ExportFile
import app.monitor360.export.ExportFormat
import app.monitor360.export.ExportIo
import app.monitor360.export.ExportOptions
import app.monitor360.export.Exporter

enum class ArchiveFrequency(val label: String, val hours: Long) {
    DAILY("Daily", 24),
    WEEKLY("Weekly", 24 * 7),
    MONTHLY("Monthly", 24 * 30),
}

data class ArchiveConfig(
    val enabled: Boolean = false,
    val frequency: ArchiveFrequency = ArchiveFrequency.WEEKLY,
    val folderUri: String = "",
    val datasets: Set<Dataset> = setOf(Dataset.SERVERS, Dataset.WEBSITES, Dataset.ALERTS, Dataset.UPTIME),
    val formats: Set<ExportFormat> = setOf(ExportFormat.CSV, ExportFormat.PDF),
    val alertDays: Int = 30,
    val keep: Int = 12,
) {
    val targetReady: Boolean get() = folderUri.isNotBlank()
}

data class ArchiveResult(val at: Long, val ok: Boolean, val message: String)

class ArchiveStore(context: Context) {
    private val prefs = context.getSharedPreferences("archive", Context.MODE_PRIVATE)

    fun load(): ArchiveConfig {
        val d = ArchiveConfig()
        return ArchiveConfig(
            enabled = prefs.getBoolean("enabled", d.enabled),
            frequency = enumOf("frequency", d.frequency),
            folderUri = prefs.getString("folder_uri", "") ?: "",
            datasets = prefs.getStringSet("datasets", null)?.mapNotNull { n -> Dataset.entries.firstOrNull { it.name == n } }?.toSet() ?: d.datasets,
            formats = prefs.getStringSet("formats", null)?.mapNotNull { n -> ExportFormat.entries.firstOrNull { it.name == n } }?.toSet() ?: d.formats,
            alertDays = prefs.getInt("alert_days", d.alertDays),
            keep = prefs.getInt("keep", d.keep),
        )
    }

    fun save(config: ArchiveConfig) {
        prefs.edit()
            .putBoolean("enabled", config.enabled)
            .putString("frequency", config.frequency.name)
            .putString("folder_uri", config.folderUri)
            .putStringSet("datasets", config.datasets.map { it.name }.toSet())
            .putStringSet("formats", config.formats.map { it.name }.toSet())
            .putInt("alert_days", config.alertDays)
            .putInt("keep", config.keep)
            .apply()
    }

    fun lastResult(): ArchiveResult? {
        val at = prefs.getLong("last_at", 0L)
        if (at == 0L) return null
        return ArchiveResult(at, prefs.getBoolean("last_ok", false), prefs.getString("last_message", "") ?: "")
    }

    fun saveResult(result: ArchiveResult) {
        prefs.edit().putLong("last_at", result.at).putBoolean("last_ok", result.ok).putString("last_message", result.message).apply()
    }

    private inline fun <reified E : Enum<E>> enumOf(key: String, default: E): E =
        prefs.getString(key, null)?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default
}

interface ArchiveSink {
    suspend fun put(file: ExportFile)
    suspend fun prune(keep: Int): Int
}

const val ARCHIVE_PREFIX = "360monitor-archive-"

private fun isArchive(name: String) = name.startsWith(ARCHIVE_PREFIX) && name.endsWith(".zip")

class FolderSink(private val context: Context, private val tree: Uri) : ArchiveSink {
    private fun dir(): DocumentFile {
        val d = DocumentFile.fromTreeUri(context, tree) ?: error("The archive folder is not available")
        check(d.exists() && d.canWrite()) { "No write access to the archive folder. Choose it again." }
        return d
    }

    override suspend fun put(file: ExportFile) {
        val dir = dir()
        dir.findFile(file.name)?.delete()
        val target = dir.createFile(file.mime, file.name) ?: error("Could not create ${file.name} in the archive folder")
        val out = context.contentResolver.openOutputStream(target.uri, "wt") ?: error("Could not write ${file.name}")
        out.use { it.write(file.bytes) }
    }

    override suspend fun prune(keep: Int): Int {
        if (keep <= 0) return 0
        val old = dir().listFiles().filter { f -> f.name?.let(::isArchive) == true }.sortedByDescending { it.name }.drop(keep)
        return old.count { it.delete() }
    }
}

class ArchiveRunner(
    private val repo: Repository,
    private val now: () -> Long = { System.currentTimeMillis() / 1000 },
) {
    private fun result(ok: Boolean, message: String) = ArchiveResult(now() * 1000, ok, message)

    suspend fun run(config: ArchiveConfig, sink: ArchiveSink): ArchiveResult {
        if (config.datasets.isEmpty()) return result(false, "Nothing selected to archive")
        if (config.formats.isEmpty()) return result(false, "No file format selected")
        return try {
            val options = ExportOptions(config.datasets, alertDays = config.alertDays)
            val bundle = ExportCollector(repo, now).collect(options)
            val file = Exporter.archive(bundle, config.formats)
            sink.put(file)
            val removed = sink.prune(config.keep)
            val size = ExportIo.sizeLabel(file.bytes.size)
            val extra = if (removed > 0) ", removed $removed old archive${if (removed == 1) "" else "s"}" else ""
            result(true, "Saved ${file.name} ($size)$extra")
        } catch (e: Exception) {
            result(false, e.message ?: e.javaClass.simpleName)
        }
    }
}
