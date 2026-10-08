package app.monitor360.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat

class CarActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(Notifier.EXTRA_NOTIFICATION_ID, 0)
        if (id != 0) NotificationManagerCompat.from(context).cancel(id)
    }
}
