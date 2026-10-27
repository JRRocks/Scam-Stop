package com.scamstop.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.telecom.Call
import android.telecom.CallScreeningService
import java.util.concurrent.atomic.AtomicInteger

class NonContactCallScreeningService : CallScreeningService() {
    override fun onScreenCall(callDetails: Call.Details) {
        val direction = when (callDetails.callDirection) {
            Call.Details.DIRECTION_INCOMING -> CallDirection.INCOMING
            Call.Details.DIRECTION_OUTGOING -> CallDirection.OUTGOING
            else -> CallDirection.UNKNOWN
        }

        val store = ScamStopStore.get(this)
        val settings = store.settings.value
        val number = callDetails.handle?.schemeSpecificPart
        val action = screeningAction(
            direction,
            isBlocking = isBlocking(settings.isEnabled, settings.pausedUntil, System.currentTimeMillis()),
            isAllowlisted = store.allowlist.value.matching(number) != null,
        )
        if (action == ScreeningAction.IGNORE) return

        // Omitting READ_CONTACTS makes Android withhold contact calls from this service.
        // Granting it would change that platform filtering behavior.
        // Rejecting, not only disallowing, makes the network hand the caller straight to
        // voicemail, where the recorded Scam Stop greeting plays.
        val block = action == ScreeningAction.BLOCK
        val response = CallScreeningService.CallResponse.Builder()
            .setDisallowCall(block)
            .setRejectCall(block)
            .build()
        respondToCall(callDetails, response)

        if (block) {
            store.addRejectedCall(RejectedCall(number, System.currentTimeMillis()))
            if (settings.notify) showBlockedCallNotification(number)
        }
    }

    private fun showBlockedCallNotification(number: String?) {
        if (!canPostNotifications()) return

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = getString(R.string.notification_channel_description)
            },
        )

        val notification = Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.blocked_call_notification_title))
            .setContentText(formatPhoneNumber(number))
            .setContentIntent(openRejectedCalls(this))
            .setAutoCancel(true)
            .setShowWhen(true)
            .setCategory(Notification.CATEGORY_STATUS)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .build()
        notificationManager.notify(nextNotificationId.getAndIncrement(), notification)
    }

    private companion object {
        const val NOTIFICATION_CHANNEL_ID = "blocked_calls"
        val nextNotificationId = AtomicInteger(SystemClock.elapsedRealtime().toInt())

        fun openRejectedCalls(context: Context): PendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .setAction(MainActivity.ACTION_SHOW_REJECTED_CALLS)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
