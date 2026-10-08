package app.monitor360.archive

import android.content.Context
import android.net.Uri
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.monitor360.app
import app.monitor360.work.Notifier
import java.util.concurrent.TimeUnit

object ArchiveScheduler {
    private const val PERIODIC = "archive_periodic"
    private const val ONCE = "archive_now"

    private val constraints get() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun apply(context: Context, config: ArchiveConfig) {
        val wm = WorkManager.getInstance(context)
        if (!config.enabled || !config.targetReady) {
            wm.cancelUniqueWork(PERIODIC)
            return
        }
        val req = PeriodicWorkRequestBuilder<ArchiveWorker>(config.frequency.hours, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()
        wm.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    fun runNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            ONCE, ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ArchiveWorker>().setConstraints(constraints).build(),
        )
    }
}

fun Context.archiveSink(config: ArchiveConfig): ArchiveSink = FolderSink(this, Uri.parse(config.folderUri))

class ArchiveWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext.app
        if (!app.settings.current.isConfigured) return Result.success()
        val config = app.archive.load()
        if (!config.targetReady) return Result.success()
        val result = ArchiveRunner(app.repo).run(config, applicationContext.archiveSink(config))
        app.archive.saveResult(result)
        if (!result.ok) {
            if (runAttemptCount < 2) return Result.retry()
            Notifier.notify(applicationContext, "archive-failed", "Archive failed", result.message)
        }
        return Result.success()
    }
}
