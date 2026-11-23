package com.scamstop.app

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
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
    /** "Ceux que j'ai appelés": a number the user called may ring back for 30 days. */
    val calledBack: Boolean,
    /** "Le 2e appel passe": a rejected number may ring if it calls again within 5 minutes. */
    val secondCall: Boolean,
)

/**
 * The blocking switch and the pause stay in the device-protected file of version 1.0.
 * The call journal, the allowed numbers, the numbers the user called, the recent rejections
 * and the screen flags are personal data: they live in credential-protected storage, which
 * the screening service can read because Android only binds it once the phone has been
 * unlocked. Nothing here is backed up or transferred (res/xml/data_extraction_rules.xml).
 * One instance per process: the app, the screening service and the tile share its flows.
 */
internal class ScamStopStore private constructor(private val appContext: Context) : ScreeningMemory {
    private val protection: SharedPreferences = appContext.createDeviceProtectedStorageContext()
        .getSharedPreferences(PROTECTION_PREFERENCES, Context.MODE_PRIVATE)
    private val prefs: SharedPreferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<ProtectionSettings> = _settings.asStateFlow()

    private val _calls = MutableStateFlow(RecordCodec.decodeCalls(prefs.getString(CALLS_KEY, null)))

    /** The journal, newest first: the rejected calls and those an exception let ring. */
    val calls: StateFlow<List<ScreenedCall>> = _calls.asStateFlow()

    private val _allowlist = MutableStateFlow(RecordCodec.decodeAllowlist(prefs.getString(ALLOWLIST_KEY, null)))
    val allowlist: StateFlow<List<AllowedNumber>> = _allowlist.asStateFlow()

    private val _calledNumbers =
        MutableStateFlow(RecordCodec.decodeCalledNumbers(prefs.getString(CALLED_NUMBERS_KEY, null)))

    /** Newest first. An entry stops counting after 30 days, a little before it is erased. */
    val calledNumbers: StateFlow<List<CalledNumber>> = _calledNumbers.asStateFlow()

    private var recentRejections = RecordCodec.decodeRejections(prefs.getString(RECENT_REJECTIONS_KEY, null))

    init {
        forgetExpired(System.currentTimeMillis())
    }

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

    /** Turning it off also forgets the numbers remembered so far. */
    fun setCalledBack(enabled: Boolean) = synchronized(this) {
        if (!enabled) writeCalledNumbers(emptyList())
        editSettings { putBoolean(CALLED_BACK_KEY, enabled) }
    }

    fun setSecondCall(enabled: Boolean) = synchronized(this) {
        if (!enabled) writeRejections(emptyList())
        editSettings { putBoolean(SECOND_CALL_KEY, enabled) }
    }

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

    override fun screeningState(now: Long): ScreeningState = synchronized(this) {
        val settings = _settings.value
        ScreeningState(
            isBlocking = isBlocking(settings.isEnabled, settings.pausedUntil, now),
            allowlist = _allowlist.value,
            calledBackEnabled = settings.calledBack,
            secondCallEnabled = settings.secondCall,
            calledNumbers = _calledNumbers.value,
            recentRejections = recentRejections,
            notify = settings.notify,
            smsReply = settings.smsReply,
        )
    }

    override fun addToJournal(call: ScreenedCall) = synchronized(this) {
        writeCalls(_calls.value.withNewCall(call))
        forgetExpired(call.timeMillis)
    }

    override fun rememberCalledNumber(call: CalledNumber) = synchronized(this) {
        writeCalledNumbers(_calledNumbers.value.withCall(call, call.timeMillis))
        forgetExpired(call.timeMillis)
    }

    override fun rememberRejection(rejection: RecentRejection) = synchronized(this) {
        writeRejections(recentRejections.withRejection(rejection, rejection.timeMillis))
    }

    /**
     * Erases what no longer counts: numbers called more than 30 days ago and rejections older
     * than 5 minutes. Runs when the process starts, then with each call written down.
     */
    fun forgetExpired(now: Long) = synchronized(this) {
        val calls = _calledNumbers.value.unexpired(now)
        if (calls != _calledNumbers.value) writeCalledNumbers(calls)
        val rejections = recentRejections.unexpired(now)
        if (rejections != recentRejections) writeRejections(rejections)
    }

    /** Returns what was cleared, so the screen can offer to undo. */
    fun clearCalls(): List<ScreenedCall> = synchronized(this) {
        _calls.value.also { writeCalls(emptyList()) }
    }

    fun restoreCalls(calls: List<ScreenedCall>) = synchronized(this) {
        writeCalls((_calls.value + calls).sortedByDescending { it.timeMillis }.take(MAX_JOURNAL_CALLS))
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
        calledBack = prefs.getBoolean(CALLED_BACK_KEY, true),
        secondCall = prefs.getBoolean(SECOND_CALL_KEY, true),
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

    private fun writeCalls(calls: List<ScreenedCall>) = synchronized(this) {
        prefs.edit { putString(CALLS_KEY, RecordCodec.encodeCalls(calls)) }
        _calls.value = calls
    }

    private fun writeCalledNumbers(calls: List<CalledNumber>) = synchronized(this) {
        prefs.edit { putString(CALLED_NUMBERS_KEY, RecordCodec.encodeCalledNumbers(calls)) }
        _calledNumbers.value = calls
    }

    private fun writeRejections(rejections: List<RecentRejection>) = synchronized(this) {
        prefs.edit { putString(RECENT_REJECTIONS_KEY, RecordCodec.encodeRejections(rejections)) }
        recentRejections = rejections
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
        private const val CALLED_BACK_KEY = "called_back"
        private const val SECOND_CALL_KEY = "second_call"
        private const val CALLED_NUMBERS_KEY = "called_numbers"
        private const val RECENT_REJECTIONS_KEY = "recent_rejections"
        private const val CALLS_KEY = "rejected_calls"
        private const val ALLOWLIST_KEY = "allowlist"

        @Volatile
        private var instance: ScamStopStore? = null

        fun get(context: Context): ScamStopStore = instance ?: synchronized(this) {
            instance ?: ScamStopStore(context.applicationContext).also { instance = it }
        }

        /** Tests only: the next [get] reads the stored data again, as a new process would. */
        @VisibleForTesting
        fun forgetInstance() = synchronized(this) { instance = null }
    }
}
