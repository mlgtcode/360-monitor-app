package app.monitor360.work

import android.content.Context
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
import app.monitor360.data.Prefs
import app.monitor360.data.Snapshot
import app.monitor360.data.Status
import app.monitor360.data.toSnap
import app.monitor360.data.toSnapMonitors
import app.monitor360.widget.WidgetUpdater
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

object Scheduler {
    private const val PERIODIC = "periodic_refresh"
    private const val ONCE = "refresh_now"

    fun apply(context: Context, prefs: Prefs) {
        val wm = WorkManager.getInstance(context)
        if (!prefs.isConfigured || prefs.backgroundRefreshMinutes <= 0) {
            wm.cancelUniqueWork(PERIODIC)
            return
        }
        val req = PeriodicWorkRequestBuilder<RefreshWorker>(prefs.backgroundRefreshMinutes.toLong().coerceAtLeast(15), TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        wm.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    fun refreshNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            ONCE,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<RefreshWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build(),
        )
    }
}

class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val app = ctx.app
        val prefs = app.settings.current
        if (!prefs.isConfigured) return Result.success()

        val old = app.snapshots.load()
        val (servers, monitors) = coroutineScope {
            val s = async { runCatching { app.repo.servers() } }
            val m = async { runCatching { app.repo.monitors() } }
            s.await() to m.await()
        }
        val error = (servers.exceptionOrNull() ?: monitors.exceptionOrNull())?.message
        val new = Snapshot(
            servers = servers.getOrNull()?.toSnap(prefs.favorites) ?: old.servers,
            monitors = monitors.getOrNull()?.toSnapMonitors(prefs.favorites) ?: old.monitors,
            updatedAt = if (error == null) System.currentTimeMillis() else old.updatedAt,
            error = error,
        )
        app.snapshots.save(new)
        if (old.hasData && error == null && prefs.notificationsEnabled) notifyChanges(old, new, prefs)
        WidgetUpdater.updateAll(ctx)
        return if (error != null && runAttemptCount < 2) Result.retry() else Result.success()
    }

    private fun notifyChanges(old: Snapshot, new: Snapshot, prefs: Prefs) {
        val ctx = applicationContext
        val oldServers = old.servers.associateBy { it.id }
        val oldMonitors = old.monitors.associateBy { it.id }
        new.servers.forEach { s ->
            val before = oldServers[s.id]?.status ?: return@forEach
            if (before != Status.DOWN && s.status == Status.DOWN && prefs.notifyOnDown)
                Notifier.notify(ctx, "s${s.id}", "Server down", "${s.name} is down")
            if (before == Status.DOWN && s.status == Status.UP && prefs.notifyOnRecovery)
                Notifier.notify(ctx, "s${s.id}", "Server recovered", "${s.name} is up again")
        }
        new.monitors.forEach { m ->
            val before = oldMonitors[m.id]?.status ?: return@forEach
            if (before != Status.DOWN && m.status == Status.DOWN && prefs.notifyOnDown)
                Notifier.notify(ctx, "m${m.id}", "Website down", "${m.name} is down")
            if (before == Status.DOWN && m.status == Status.UP && prefs.notifyOnRecovery)
                Notifier.notify(ctx, "m${m.id}", "Website recovered", "${m.name} is up again")
        }
    }
}
