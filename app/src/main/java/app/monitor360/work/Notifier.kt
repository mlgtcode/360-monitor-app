package app.monitor360.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import app.monitor360.MainActivity
import app.monitor360.R

object Notifier {
    const val CHANNEL = "alerts"
    const val ACTION_MARK_READ = "app.monitor360.action.MARK_READ"
    const val ACTION_ACKNOWLEDGE = "app.monitor360.action.ACKNOWLEDGE"
    const val EXTRA_NOTIFICATION_ID = "notification_id"
    const val REMOTE_INPUT_KEY = "acknowledge"

    fun createChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.channel_alerts), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.channel_alerts_desc)
            },
        )
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun notify(context: Context, key: String, title: String, text: String): Boolean {
        if (!canNotify(context)) return false
        val id = key.hashCode()
        runCatching { NotificationManagerCompat.from(context).notify(id, build(context, id, title, text)) }
            .onFailure { return false }
        return true
    }

    fun build(context: Context, id: Int, title: String, text: String) = NotificationCompat.Builder(context, CHANNEL)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(title)
        .setContentText(text)
        .setCategory(NotificationCompat.CATEGORY_MESSAGE)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setStyle(messagingStyle(title, text))
        .setContentIntent(openApp(context))
        .addAction(markAsRead(context, id))
        .addAction(acknowledge(context, id))
        .setAutoCancel(true)
        .build()

    private fun messagingStyle(title: String, text: String): NotificationCompat.MessagingStyle {
        val me = Person.Builder().setName("Me").build()
        val sender = Person.Builder().setName("360 Monitor").setKey("app.monitor360").build()
        return NotificationCompat.MessagingStyle(me)
            .setConversationTitle(title)
            .addMessage(text, System.currentTimeMillis(), sender)
    }

    private fun openApp(context: Context) = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun broadcast(context: Context, action: String, id: Int, mutable: Boolean): PendingIntent {
        val intent = Intent(context, CarActionReceiver::class.java).setAction(action).putExtra(EXTRA_NOTIFICATION_ID, id)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (mutable) mutableFlag() else PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, id, intent, flags)
    }

    private fun mutableFlag() = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0

    private fun markAsRead(context: Context, id: Int) = NotificationCompat.Action.Builder(
        R.drawable.ic_notification, "Mark as read", broadcast(context, ACTION_MARK_READ, id, mutable = false),
    )
        .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
        .setShowsUserInterface(false)
        .build()

    private fun acknowledge(context: Context, id: Int) = NotificationCompat.Action.Builder(
        R.drawable.ic_notification, "Acknowledge", broadcast(context, ACTION_ACKNOWLEDGE, id, mutable = true),
    )
        .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
        .setShowsUserInterface(false)
        .addRemoteInput(RemoteInput.Builder(REMOTE_INPUT_KEY).setLabel("Acknowledge").build())
        .build()
}
