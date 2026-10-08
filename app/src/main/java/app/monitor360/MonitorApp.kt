package app.monitor360

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.work.Configuration
import app.monitor360.archive.ArchiveScheduler
import app.monitor360.archive.ArchiveStore
import app.monitor360.data.ApiClient
import app.monitor360.data.Repository
import app.monitor360.data.SettingsStore
import app.monitor360.data.SnapshotStore
import app.monitor360.widget.WidgetStyleStore
import app.monitor360.work.Notifier
import app.monitor360.work.Scheduler

class MonitorApp : Application(), Configuration.Provider {
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setMinimumLoggingLevel(Log.WARN).build()

    lateinit var settings: SettingsStore
        private set
    lateinit var api: ApiClient
        private set
    lateinit var repo: Repository
        private set
    lateinit var snapshots: SnapshotStore
        private set
    lateinit var widgetStyles: WidgetStyleStore
        private set
    lateinit var archive: ArchiveStore
        private set

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        api = ApiClient({ settings.current })
        repo = Repository(api)
        snapshots = SnapshotStore(this)
        widgetStyles = WidgetStyleStore(this)
        Notifier.createChannel(this)
        archive = ArchiveStore(this)
        Scheduler.apply(this, settings.current)
        ArchiveScheduler.apply(this, archive.load())
    }
}

val Context.app: MonitorApp get() = applicationContext as MonitorApp
