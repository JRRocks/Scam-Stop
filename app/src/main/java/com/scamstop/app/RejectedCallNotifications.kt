package com.scamstop.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicInteger

internal object RejectedCallNotifications {
    const val CHANNEL_ID = "blocked_calls"
    private val nextId = AtomicInteger(SystemClock.elapsedRealtime().toInt())

    /** Created at start-up so the category shows in Android settings before the first call. */
    fun ensureChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.notification_channel_description) },
        )
    }

    fun isChannelBlocked(context: Context): Boolean = context.getSystemService(NotificationManager::class.java)
        .getNotificationChannel(CHANNEL_ID)?.importance == NotificationManager.IMPORTANCE_NONE

    fun show(context: Context, number: String?) {
        if (!context.canPostNotifications()) return
        ensureChannel(context)
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.blocked_call_notification_title))
            .setContentText(context.formatPhoneNumber(number))
            .setContentIntent(openRejectedCalls(context))
            .setAutoCancel(true)
            .setShowWhen(true)
            .setCategory(Notification.CATEGORY_STATUS)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(nextId.getAndIncrement(), notification)
    }

    private fun openRejectedCalls(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_SHOW_REJECTED_CALLS)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
