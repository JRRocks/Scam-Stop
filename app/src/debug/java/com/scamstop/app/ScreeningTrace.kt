package com.scamstop.app

import android.content.Context
import android.telecom.Connection
import androidx.core.content.edit

/**
 * Debug builds only: the last calls Android handed to the screening service, kept on the
 * phone to check what a given device really transmits (outgoing calls above all).
 * Numbers are masked before they are stored: the trace shows their form, not who called.
 */
internal object ScreeningTrace {
    const val MAX_EVENTS = 40

    enum class Result { OUTGOING_REMEMBERED, OUTGOING_NOT_REMEMBERED, NOT_A_CALL, UNFILTERED, CALLED_BACK, SECOND_CALL, REJECTED, REJECTED_MAY_CALL_AGAIN }

    data class Event(
        val timeMillis: Long,
        val direction: CallDirection,
        /** Already masked, see [mask]. */
        val number: String,
        val verification: Int?,
        val onFrenchNetwork: Boolean,
        val result: Result,
        val answerMillis: Long,
    )

    fun record(
        context: Context,
        direction: CallDirection,
        number: String?,
        verification: Int?,
        onFrenchNetwork: Boolean,
        verdict: ScreeningVerdict,
        now: Long,
        answerMillis: Long,
    ) {
        val result = when (verdict) {
            ScreeningVerdict.Ignored -> when {
                direction != CallDirection.OUTGOING -> Result.NOT_A_CALL
                ScamStopStore.get(context).calledNumbers.value.any { it.timeMillis == now && it.number == number } ->
                    Result.OUTGOING_REMEMBERED
                else -> Result.OUTGOING_NOT_REMEMBERED
            }
            ScreeningVerdict.Unfiltered -> Result.UNFILTERED
            is ScreeningVerdict.CalledBack -> Result.CALLED_BACK
            is ScreeningVerdict.SecondCall -> Result.SECOND_CALL
            is ScreeningVerdict.Rejected ->
                if (verdict.secondCallDigits != null) Result.REJECTED_MAY_CALL_AGAIN else Result.REJECTED
        }
        val event = Event(now, direction, mask(number), verification, onFrenchNetwork, result, answerMillis)
        val prefs = prefs(context)
        synchronized(this) {
            val events = (listOf(event) + decode(prefs.getString(EVENTS_KEY, null))).take(MAX_EVENTS)
            prefs.edit { putString(EVENTS_KEY, encode(events)) }
        }
    }

    fun events(context: Context): List<Event> = decode(prefs(context).getString(EVENTS_KEY, null))

    fun clear(context: Context) = prefs(context).edit { remove(EVENTS_KEY) }

    fun encode(events: List<Event>): String = events.joinToString("\n") {
        listOf(
            it.timeMillis, it.direction.name, it.number.replace(Regex("[\\t\\r\\n]+"), " "),
            it.verification ?: "", if (it.onFrenchNetwork) 1 else 0, it.result.name, it.answerMillis,
        ).joinToString("\t")
    }

    fun decode(stored: String?): List<Event> = stored.orEmpty().lineSequence().mapNotNull { line ->
        val fields = line.split('\t')
        if (fields.size < 7) return@mapNotNull null
        Event(
            timeMillis = fields[0].toLongOrNull() ?: return@mapNotNull null,
            direction = CallDirection.entries.firstOrNull { it.name == fields[1] } ?: return@mapNotNull null,
            number = fields[2],
            verification = fields[3].toIntOrNull(),
            onFrenchNetwork = fields[4] == "1",
            result = Result.entries.firstOrNull { it.name == fields[5] } ?: return@mapNotNull null,
            answerMillis = fields[6].toLongOrNull() ?: 0L,
        )
    }.toList()

    /** Keeps what tells the form of a number (its start) and hides who it is. */
    fun mask(number: String?): String {
        val compact = number?.filterNot { it.isWhitespace() }.orEmpty()
        if (compact.isEmpty()) return "(aucun numéro)"
        if (compact.length <= 6) return "•".repeat(compact.length)
        return compact.take(4) + "•".repeat(compact.length - 6) + compact.takeLast(2)
    }

    fun verificationName(status: Int?): String = when (status) {
        null -> "inconnu"
        Connection.VERIFICATION_STATUS_NOT_VERIFIED -> "non vérifié"
        Connection.VERIFICATION_STATUS_PASSED -> "validé"
        Connection.VERIFICATION_STATUS_FAILED -> "usurpé"
        else -> status.toString()
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private const val PREFERENCES_NAME = "screening_trace"
    private const val EVENTS_KEY = "events"
}
