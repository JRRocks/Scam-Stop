package com.scamstop.app

internal enum class CallDirection {
    INCOMING,
    OUTGOING,
    UNKNOWN,
}

internal enum class ScreeningAction {
    BLOCK,
    ALLOW,
    IGNORE,
}

/**
 * Android only hands this service calls from numbers outside the contacts, so every
 * incoming call reaching it is unknown: rejected while blocking is on, unless the user
 * allowed that number in the app.
 */
internal fun screeningAction(
    direction: CallDirection,
    isBlocking: Boolean,
    isAllowlisted: Boolean = false,
): ScreeningAction = when {
    direction != CallDirection.INCOMING -> ScreeningAction.IGNORE
    isBlocking && !isAllowlisted -> ScreeningAction.BLOCK
    else -> ScreeningAction.ALLOW
}

/** What the service knows when a call comes in: the switches and what it remembers. */
internal data class ScreeningState(
    val isBlocking: Boolean,
    val allowlist: List<AllowedNumber> = emptyList(),
    val calledBackEnabled: Boolean = true,
    val secondCallEnabled: Boolean = true,
    val calledNumbers: List<CalledNumber> = emptyList(),
    val recentRejections: List<RecentRejection> = emptyList(),
    val notify: Boolean = false,
    val smsReply: Boolean = false,
)

internal sealed interface ScreeningVerdict {
    val action: ScreeningAction

    /** Not an incoming call: nothing to answer. */
    data object Ignored : ScreeningVerdict {
        override val action = ScreeningAction.IGNORE
    }

    /** Protection paused or off, or a number the user allowed: it rings and leaves no trace. */
    data object Unfiltered : ScreeningVerdict {
        override val action = ScreeningAction.ALLOW
    }

    /** Rings: the user called this number at [calledAt], less than 30 days ago. */
    data class CalledBack(val calledAt: Long) : ScreeningVerdict {
        override val action = ScreeningAction.ALLOW
    }

    /** Rings: this number was rejected at [rejectedAt], less than 5 minutes ago. */
    data class SecondCall(val rejectedAt: Long) : ScreeningVerdict {
        override val action = ScreeningAction.ALLOW
    }

    /** [secondCallDigits] is set when the same number may ring if it calls back within 5 minutes. */
    data class Rejected(val secondCallDigits: String?) : ScreeningVerdict {
        override val action = ScreeningAction.BLOCK
    }
}

/**
 * The first call of an unknown number is rejected. Two exceptions let it ring: the user
 * called that number in the last 30 days, or it is an ordinary French number calling again
 * within 5 minutes of being rejected. A number the network flags as spoofed gets neither.
 */
internal fun screenIncoming(
    direction: CallDirection,
    number: String?,
    spoofed: Boolean,
    onFrenchNetwork: Boolean,
    state: ScreeningState,
    now: Long,
): ScreeningVerdict {
    when (screeningAction(direction, state.isBlocking, state.allowlist.matching(number) != null)) {
        ScreeningAction.IGNORE -> return ScreeningVerdict.Ignored
        ScreeningAction.ALLOW -> return ScreeningVerdict.Unfiltered
        ScreeningAction.BLOCK -> Unit
    }
    // A withheld number: nobody to recognise.
    if (number.isNullOrBlank()) return ScreeningVerdict.Rejected(secondCallDigits = null)

    if (state.calledBackEnabled && !spoofed) {
        val caller = NumberIdentity.of(number, onFrenchNetwork)
        state.calledNumbers.lastCallTo(caller, now)?.let { return ScreeningVerdict.CalledBack(it.timeMillis) }
    }
    if (!state.secondCallEnabled) return ScreeningVerdict.Rejected(secondCallDigits = null)

    val digits = FrenchNumbers.nationalDigits(number, onFrenchNetwork)?.takeIf(FrenchNumbers::isOrdinary)
        ?: return ScreeningVerdict.Rejected(secondCallDigits = null)
    if (!spoofed) {
        state.recentRejections.openingSecondCall(digits, now)?.let { return ScreeningVerdict.SecondCall(it.timeMillis) }
    }
    return ScreeningVerdict.Rejected(secondCallDigits = digits)
}

/** What the service remembers between two calls: the store in the app, a fake in tests. */
internal interface ScreeningMemory {
    fun screeningState(now: Long): ScreeningState

    fun rememberCalledNumber(call: CalledNumber)

    fun rememberRejection(rejection: RecentRejection)

    fun addToJournal(call: ScreenedCall)
}

/** What the service does to the outside world. */
internal interface ScreeningEffects {
    /** Android waits 5 seconds for this answer, with the phone silent meanwhile. */
    fun respond(block: Boolean)

    fun notifyRejected(number: String?)

    fun replyBySms(number: String)
}

/**
 * Handles one call handed over by Android. The answer is given before anything is written,
 * so storage never stands between a caller and the 5 seconds Android allows.
 */
internal class CallScreener(private val memory: ScreeningMemory, private val effects: ScreeningEffects) {
    fun onCall(
        direction: CallDirection,
        number: String?,
        spoofed: Boolean,
        onFrenchNetwork: Boolean,
        now: Long,
    ): ScreeningVerdict {
        val state = memory.screeningState(now)
        val verdict = screenIncoming(direction, number, spoofed, onFrenchNetwork, state, now)
        if (verdict.action != ScreeningAction.IGNORE) effects.respond(block = verdict.action == ScreeningAction.BLOCK)

        when (verdict) {
            ScreeningVerdict.Ignored ->
                // Android also announces the calls the user places to numbers outside the contacts.
                if (direction == CallDirection.OUTGOING && state.calledBackEnabled &&
                    number != null && PhoneNumbers.isDialable(number)
                ) {
                    memory.rememberCalledNumber(CalledNumber(number, now, onFrenchNetwork))
                }
            ScreeningVerdict.Unfiltered -> Unit
            is ScreeningVerdict.CalledBack ->
                memory.addToJournal(ScreenedCall(number, now, CallOutcome.RANG_CALLED_BACK, verdict.calledAt))
            is ScreeningVerdict.SecondCall ->
                memory.addToJournal(ScreenedCall(number, now, CallOutcome.RANG_SECOND_CALL, verdict.rejectedAt))
            is ScreeningVerdict.Rejected -> {
                memory.addToJournal(ScreenedCall(number, now))
                verdict.secondCallDigits?.let { memory.rememberRejection(RecentRejection(it, now, spoofed)) }
                if (state.notify) effects.notifyRejected(number)
                // A number the network flags as spoofed belongs to someone else: never text it.
                if (state.smsReply && !spoofed && number != null) effects.replyBySms(number)
            }
        }
        return verdict
    }
}
