package com.scamstop.app

import android.content.Context
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager

/** Replies sent in any 24 hours: a wave of different spoofed numbers cannot go beyond it. */
internal const val SMS_REPLY_DAILY_LIMIT = 10

private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

private val TRUNK_ZERO = Regex("\\(\\s*0\\s*\\)")
private val FRENCH_MOBILE = Regex("(?:\\+33|0033|0)([67]\\d{8})")

/**
 * The 9 digits that identify a mainland French mobile (06, 07) whatever its form, or null
 * for any other number: foreign, landline, special (08) or short numbers can cost money.
 */
internal fun frenchMobileKey(raw: String?): String? {
    val compact = raw?.replace(TRUNK_ZERO, "")?.filterNot { it in " .-()" } ?: return null
    return FRENCH_MOBILE.matchEntire(compact)?.groupValues?.get(1)
}

/** [sent] maps each number already texted (its [frenchMobileKey]) to when it was. */
internal fun canReplyBySms(key: String?, roamingAbroad: Boolean, sent: Map<String, Long>, now: Long): Boolean =
    key != null && !roamingAbroad && key !in sent &&
        sent.values.count { now - it < DAY_MILLIS } < SMS_REPLY_DAILY_LIMIT

/**
 * Texts the voicemail greeting to a rejected caller. The checks above keep it free on a
 * French plan with unlimited texts; a reply already sent can never be called back.
 */
internal object SmsReply {
    fun sendIfAllowed(context: Context, number: String?) {
        if (number == null || !context.hasSmsPermission()) return
        val subId = SmsManager.getDefaultSmsSubscriptionId()
        // "Ask every time" on a dual SIM phone: no SIM whose roaming could be checked.
        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) return
        val telephony = context.getSystemService(TelephonyManager::class.java)?.createForSubscriptionId(subId) ?: return
        // A partner network in France (national roaming) costs nothing extra.
        val roamingAbroad = telephony.isNetworkRoaming && telephony.networkCountryIso != telephony.simCountryIso
        if (!ScamStopStore.get(context).claimSmsReply(number, roamingAbroad, System.currentTimeMillis())) return

        val text = GreetingPrefs(context).text?.takeIf { it.isNotBlank() } ?: context.getString(R.string.default_greeting)
        try {
            val sms = smsManager(context, subId)
            sms.sendMultipartTextMessage(number, null, sms.divideMessage(text), null, null)
        } catch (e: SecurityException) {
            // Permission revoked in between: the number stays marked, so no retry flood.
        } catch (e: IllegalArgumentException) {
            // Number or text refused by the platform.
        }
    }

    private fun smsManager(context: Context, subId: Int): SmsManager =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java).createForSubscriptionId(subId)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getSmsManagerForSubscriptionId(subId)
        }
}
