package com.scamstop.app

import android.os.Build
import android.os.SystemClock
import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.Connection
import android.telephony.TelephonyManager
import java.util.Locale

class NonContactCallScreeningService : CallScreeningService() {
    override fun onScreenCall(callDetails: Call.Details) {
        val startedAt = SystemClock.elapsedRealtime()
        var answeredAt = startedAt
        val direction = when (callDetails.callDirection) {
            Call.Details.DIRECTION_INCOMING -> CallDirection.INCOMING
            Call.Details.DIRECTION_OUTGOING -> CallDirection.OUTGOING
            else -> CallDirection.UNKNOWN
        }
        val number = callDetails.handle?.schemeSpecificPart
        val verification =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) callDetails.callerNumberVerificationStatus else null
        val spoofed =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && verification == Connection.VERIFICATION_STATUS_FAILED
        val onFrenchNetwork = isOnFrenchNetwork()
        val now = System.currentTimeMillis()

        // Omitting READ_CONTACTS makes Android withhold contact calls from this service.
        // Granting it would change that platform filtering behavior.
        val effects = object : ScreeningEffects {
            // Rejecting, not only disallowing, makes the network hand the caller straight to
            // voicemail, where the recorded Scam Stop greeting plays.
            override fun respond(block: Boolean) {
                respondToCall(callDetails, CallResponse.Builder().setDisallowCall(block).setRejectCall(block).build())
                answeredAt = SystemClock.elapsedRealtime()
            }

            override fun notifyRejected(number: String?) =
                RejectedCallNotifications.show(this@NonContactCallScreeningService, number)

            override fun replyBySms(number: String) = SmsReply.sendIfAllowed(this@NonContactCallScreeningService, number)
        }
        val verdict = CallScreener(ScamStopStore.get(this), effects).onCall(
            direction = direction,
            number = number,
            spoofed = spoofed,
            onFrenchNetwork = onFrenchNetwork,
            now = now,
        )
        ScreeningTrace.record(this, direction, number, verification, onFrenchNetwork, verdict, now, answeredAt - startedAt)
    }

    /** Abroad, a number without a country code is a local one, not a French one. */
    private fun isOnFrenchNetwork(): Boolean {
        val telephony = getSystemService(TelephonyManager::class.java) ?: return true
        val country = telephony.networkCountryIso.orEmpty().ifBlank { telephony.simCountryIso.orEmpty() }
        return country.isBlank() || country.lowercase(Locale.ROOT) in FrenchNumbers.NETWORK_COUNTRIES
    }
}
