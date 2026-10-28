package com.scamstop.app

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.Connection

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
            if (settings.notify) RejectedCallNotifications.show(this, number)
            // A number the network flags as spoofed belongs to someone else: never text it.
            val spoofed = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                callDetails.callerNumberVerificationStatus == Connection.VERIFICATION_STATUS_FAILED
            if (settings.smsReply && !spoofed) SmsReply.sendIfAllowed(this, number)
        }
    }
}
