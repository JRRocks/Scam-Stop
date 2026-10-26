package com.scamstop.app

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
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

        val action = screeningAction(direction, BlockingPreference.isEnabled(this))
        if (action == ScreeningAction.IGNORE) return

        // Omitting READ_CONTACTS makes Android withhold contact calls from this service.
        // Granting it would change that platform filtering behavior.
        val response = CallScreeningService.CallResponse.Builder()
            .setDisallowCall(action == ScreeningAction.BLOCK)
            .setRejectCall(false)
            .build()
        respondToCall(callDetails, response)

        if (action == ScreeningAction.BLOCK) {
            showBlockedCallNotification(callDetails.handle?.schemeSpecificPart)
        }
    }

    private fun showBlockedCallNotification(number: String?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        if (!notificationManager.areNotificationsEnabled()) return

        notificationManager.createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = getString(R.string.notification_channel_description)
            },
        )

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val displayedNumber = number ?: getString(R.string.unknown_number)
        val notification = Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.blocked_call_notification_title))
            .setContentText(getString(R.string.blocked_call_notification_text, displayedNumber))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_STATUS)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .build()
        notificationManager.notify(nextNotificationId.getAndIncrement(), notification)
    }

    private companion object {
        const val NOTIFICATION_CHANNEL_ID = "blocked_calls"
        val nextNotificationId = AtomicInteger(SystemClock.elapsedRealtime().toInt())
    }
}
