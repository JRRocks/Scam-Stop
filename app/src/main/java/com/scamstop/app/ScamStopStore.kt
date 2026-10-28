package com.scamstop.app

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class ProtectionSettings(
    val isEnabled: Boolean,
    val pausedUntil: Long,
    val notify: Boolean,
    val notificationsDecided: Boolean,
    val notificationPermissionAsked: Boolean,
    val greetingInstalled: Boolean,
    val setupHidden: Boolean,
    val tileAdded: Boolean,
    val smsReply: Boolean,
)

/**
 * The blocking switch and the pause stay in the device-protected file of version 1.0.
 * The call journal, the allowed numbers and the screen flags are personal data: they live
 * in credential-protected storage, which the screening service can read because Android
 * only binds it once the phone has been unlocked.
 * One instance per process: the app, the screening service and the tile share its flows.
 */
internal class ScamStopStore private constructor(private val appContext: Context) {
    private val protection: SharedPreferences = appContext.createDeviceProtectedStorageContext()
        .getSharedPreferences(PROTECTION_PREFERENCES, Context.MODE_PRIVATE)
    private val prefs: SharedPreferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<ProtectionSettings> = _settings.asStateFlow()

    private val _calls = MutableStateFlow(RecordCodec.decodeCalls(prefs.getString(CALLS_KEY, null)))
    val calls: StateFlow<List<RejectedCall>> = _calls.asStateFlow()

    private val _allowlist = MutableStateFlow(RecordCodec.decodeAllowlist(prefs.getString(ALLOWLIST_KEY, null)))
    val allowlist: StateFlow<List<AllowedNumber>> = _allowlist.asStateFlow()

    fun setEnabled(enabled: Boolean) = editProtection {
        putBoolean(ENABLED_KEY, enabled)
        putLong(PAUSED_UNTIL_KEY, 0L)
    }

    fun pauseUntil(timeMillis: Long) = editProtection {
        putBoolean(ENABLED_KEY, true)
        putLong(PAUSED_UNTIL_KEY, timeMillis)
    }

    fun resume() = editProtection { putLong(PAUSED_UNTIL_KEY, 0L) }

    fun setNotify(notify: Boolean) = editSettings {
        putBoolean(NOTIFY_KEY, notify)
        putBoolean(NOTIFICATIONS_DECIDED_KEY, true)
    }

    fun markNotificationPermissionAsked() = editSettings { putBoolean(NOTIFICATION_ASKED_KEY, true) }

    fun setGreetingInstalled(installed: Boolean) = editSettings { putBoolean(GREETING_INSTALLED_KEY, installed) }

    fun hideSetup() = editSettings { putBoolean(SETUP_HIDDEN_KEY, true) }

    fun setTileAdded(added: Boolean) = editSettings { putBoolean(TILE_ADDED_KEY, added) }

    fun setSmsReply(enabled: Boolean) = editSettings { putBoolean(SMS_REPLY_KEY, enabled) }

    /**
     * Marks the number as texted when a reply is allowed, before it is sent: two calls in
     * a row from the same number cannot both get one.
     */
    fun claimSmsReply(number: String, roamingAbroad: Boolean, now: Long): Boolean = synchronized(this) {
        val key = frenchMobileKey(number) ?: return false
        val sent = RecordCodec.decodeSmsReplies(prefs.getString(SMS_REPLIES_KEY, null))
        if (!canReplyBySms(key, roamingAbroad, sent, now)) return false
        prefs.edit { putString(SMS_REPLIES_KEY, RecordCodec.encodeSmsReplies(sent + (key to now))) }
        true
    }

    fun addRejectedCall(call: RejectedCall) = writeCalls(_calls.value.withNewCall(call))

    /** Returns what was cleared, so the screen can offer to undo. */
    fun clearCalls(): List<RejectedCall> = synchronized(this) {
        _calls.value.also { writeCalls(emptyList()) }
    }

    fun restoreCalls(calls: List<RejectedCall>) = synchronized(this) {
        writeCalls((_calls.value + calls).sortedByDescending { it.timeMillis }.take(MAX_REJECTED_CALLS))
    }

    fun allow(number: String, label: String) = writeAllowlist(_allowlist.value.withAllowed(AllowedNumber(number, label)))

    fun disallow(number: String) = writeAllowlist(_allowlist.value.without(number))

    private fun readSettings() = ProtectionSettings(
        isEnabled = protection.getBoolean(ENABLED_KEY, true),
        pausedUntil = protection.getLong(PAUSED_UNTIL_KEY, 0L),
        notify = prefs.getBoolean(NOTIFY_KEY, true),
        notificationsDecided = prefs.getBoolean(NOTIFICATIONS_DECIDED_KEY, false),
        notificationPermissionAsked = prefs.getBoolean(NOTIFICATION_ASKED_KEY, false),
        greetingInstalled = prefs.getBoolean(GREETING_INSTALLED_KEY, false),
        setupHidden = prefs.getBoolean(SETUP_HIDDEN_KEY, false),
        tileAdded = prefs.getBoolean(TILE_ADDED_KEY, false),
        smsReply = prefs.getBoolean(SMS_REPLY_KEY, false),
    )

    private fun editSettings(block: SharedPreferences.Editor.() -> Unit) = synchronized(this) {
        prefs.edit(action = block)
        _settings.value = readSettings()
    }

    private fun editProtection(block: SharedPreferences.Editor.() -> Unit) {
        synchronized(this) {
            protection.edit(action = block)
            _settings.value = readSettings()
        }
        ProtectionTileService.requestUpdate(appContext)
    }

    private fun writeCalls(calls: List<RejectedCall>) = synchronized(this) {
        prefs.edit { putString(CALLS_KEY, RecordCodec.encodeCalls(calls)) }
        _calls.value = calls
    }

    private fun writeAllowlist(entries: List<AllowedNumber>) = synchronized(this) {
        prefs.edit { putString(ALLOWLIST_KEY, RecordCodec.encodeAllowlist(entries)) }
        _allowlist.value = entries
    }

    companion object {
        // Same file and key as the original BlockingPreference, so an update keeps the switch.
        private const val PROTECTION_PREFERENCES = "blocking_policy"
        private const val PREFERENCES_NAME = "scam_stop"
        private const val ENABLED_KEY = "enabled"
        private const val PAUSED_UNTIL_KEY = "paused_until"
        private const val NOTIFY_KEY = "notify"
        private const val NOTIFICATIONS_DECIDED_KEY = "notifications_decided"
        private const val NOTIFICATION_ASKED_KEY = "notification_permission_asked"
        private const val GREETING_INSTALLED_KEY = "greeting_installed"
        private const val SETUP_HIDDEN_KEY = "setup_hidden"
        private const val TILE_ADDED_KEY = "tile_added"
        private const val SMS_REPLY_KEY = "sms_reply"
        private const val SMS_REPLIES_KEY = "sms_replies"
        private const val CALLS_KEY = "rejected_calls"
        private const val ALLOWLIST_KEY = "allowlist"

        @Volatile
        private var instance: ScamStopStore? = null

        fun get(context: Context): ScamStopStore = instance ?: synchronized(this) {
            instance ?: ScamStopStore(context.applicationContext).also { instance = it }
        }
    }
}
