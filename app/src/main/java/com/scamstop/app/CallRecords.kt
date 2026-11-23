package com.scamstop.app

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** What Scam Stop did with an incoming call from a number outside the contacts. */
internal enum class CallOutcome(val code: String) {
    REJECTED("R"),

    /** Rang: the user had called that number in the last 30 days. */
    RANG_CALLED_BACK("C"),

    /** Rang: the same number had been rejected in the last 5 minutes. */
    RANG_SECOND_CALL("S"),
}

/**
 * A journal line. For a call that rang, [sinceMillis] is when the user last called the number
 * (called back) or when it had been rejected (second call).
 */
internal data class ScreenedCall(
    val number: String?,
    val timeMillis: Long,
    val outcome: CallOutcome = CallOutcome.REJECTED,
    val sinceMillis: Long = 0L,
)

internal data class AllowedNumber(val number: String, val label: String)

/** A number the user dialled. [onFrenchNetwork] tells how to read a national form (0Z…). */
internal data class CalledNumber(val number: String, val timeMillis: Long, val onFrenchNetwork: Boolean) {
    val identity: NumberIdentity get() = NumberIdentity.of(number, onFrenchNetwork)
}

/** A rejection after which the same French number may ring; [digits] come from [FrenchNumbers]. */
internal data class RecentRejection(val digits: String, val timeMillis: Long, val spoofed: Boolean)

internal const val MAX_JOURNAL_CALLS = 100
internal const val MAX_CALLED_NUMBERS = 200
internal const val MAX_RECENT_REJECTIONS = 50

/** A number the user called may ring back for 30 days. */
internal const val CALLED_BACK_WINDOW_MILLIS = 30L * 24 * 60 * 60 * 1000

/** A rejected number may ring if it calls again within 5 minutes. */
internal const val SECOND_CALL_WINDOW_MILLIS = 5L * 60 * 1000

/** True from [since] to [window] later, both included. False when the clock went back before [since]. */
internal fun isWithin(since: Long, now: Long, window: Long): Boolean = now - since in 0..window

internal object PhoneNumbers {
    /** Shortest number that may match a longer one by its ending (a national vs an international form). */
    const val MIN_SUFFIX_MATCH = 8

    /**
     * Digits that identify a number regardless of how it was written: separators dropped,
     * the "00" international prefix folded into "+", and trunk zeros removed, so
     * "06 12 34 56 78", "+33 6 12 34 56 78" and "0033612345678" share their last 9 digits.
     */
    fun significantDigits(raw: String): String {
        // "+33 (0)6 12 …": the bracketed trunk zero is never dialled from abroad.
        val digits = raw.replace(TRUNK_ZERO, "").filter { it in '0'..'9' }
        val international = raw.trimStart().startsWith("+")
        val withoutPrefix = if (!international && digits.startsWith("00")) digits.drop(2) else digits
        return withoutPrefix.trimStart('0')
    }

    fun same(a: String, b: String): Boolean {
        // Identical text always matches, even a handle without digits (a SIP address).
        if (a.trim().equals(b.trim(), ignoreCase = true) && a.isNotBlank()) return true
        val x = significantDigits(a)
        val y = significantDigits(b)
        if (x.isEmpty() || y.isEmpty()) return false
        if (x == y) return true
        val (shorter, longer) = if (x.length <= y.length) x to y else y to x
        return shorter.length >= MIN_SUFFIX_MATCH && longer.endsWith(shorter)
    }

    fun isDialable(raw: String): Boolean = significantDigits(raw).length >= 3

    /** The digits alone, a leading "+" kept: "+33 (0)6 12 34 56 78" gives "+33612345678". */
    fun compact(raw: String): String {
        val trimmed = raw.replace(TRUNK_ZERO, "").trim()
        val digits = trimmed.filter { it in '0'..'9' }
        return if (trimmed.startsWith("+")) "+$digits" else digits
    }

    private val TRUNK_ZERO = Regex("\\(\\s*0\\s*\\)")
}

/**
 * The French numbering plan kept by Arcep (decision 2018-0881 as amended): mainland (+33) and
 * the overseas territories sharing it (+590, +594, +596, +262, +508). Any other code is foreign.
 */
internal object FrenchNumbers {
    /** Countries whose mobile networks present national numbers in the French form. */
    val NETWORK_COUNTRIES = setOf("fr", "gp", "mq", "gf", "re", "yt", "pm", "bl", "mf")

    /**
     * The nine national digits (ZABPQMCDU) of a French number whatever its form, or null.
     * A national form (0Z…) only counts when [nationalFormIsFrench]: abroad it is a local number.
     */
    fun nationalDigits(raw: String, nationalFormIsFrench: Boolean): String? {
        val compact = PhoneNumbers.compact(raw)
        INTERNATIONAL.matchEntire(compact)?.let { match ->
            val (code, rest) = match.destructured
            return when {
                rest.length == 9 && rest[0] != '0' -> rest
                // From abroad, Saint-Pierre-et-Miquelon is +508 and the last six digits of 0508 PQ MC DU.
                code == "508" && rest.length == 6 -> code + rest
                else -> null
            }
        }
        return if (nationalFormIsFrench) NATIONAL.matchEntire(compact)?.groupValues?.get(1) else null
    }

    /**
     * Whether a person may be dialling from it: a landline (01-05), a mobile (06, 07) or a
     * VoIP line (09). Left out: special numbers (08) and the ranges that automated calling
     * systems must use since 2023 (Arcep decision 2022-1583, "numéros polyvalents vérifiés").
     */
    fun isOrdinary(digits: String): Boolean =
        digits.length == 9 && digits[0] in ORDINARY_FIRST_DIGITS && AUTOMATED_RANGES.none(digits::startsWith)

    /** Z of 0Z AB PQ MC DU: 1 to 5 landlines, 6 and 7 mobiles, 9 VoIP lines; 8 is for special numbers. */
    private const val ORDINARY_FIRST_DIGITS = "12345679"

    private val INTERNATIONAL = Regex("(?:\\+|00)(33|590|594|596|262|508)(\\d+)")
    private val NATIONAL = Regex("0([1-9]\\d{8})")

    // 01 62, 01 63, 02 70, 02 71, 03 77, 03 78, 04 24, 04 25, 05 68, 05 69, 09 48, 09 49,
    // then 09 475 to 09 479 overseas.
    private val AUTOMATED_RANGES = listOf(
        "162", "163", "270", "271", "377", "378", "424", "425", "568", "569", "948", "949",
        "9475", "9476", "9477", "9478", "9479",
    )
}

/** A phone number read in its context: what tells whether two handles are the same caller. */
internal sealed interface NumberIdentity {
    /** A number of the French plan, by its nine national digits. */
    data class French(val digits: String) : NumberIdentity

    /** A foreign number written with its country code. */
    data class Foreign(val digits: String) : NumberIdentity

    /** No country to go by: a short number, or a national form met abroad. */
    data class Local(val raw: String) : NumberIdentity

    /**
     * French numbers compare by their national digits and foreign ones by all of theirs, so
     * "+34 969…" never passes for the "09 69…" the user called. Anything else falls back to
     * [PhoneNumbers.same].
     */
    fun sameCallerAs(other: NumberIdentity): Boolean = when {
        this is French && other is French -> digits == other.digits
        this is Foreign && other is Foreign -> digits == other.digits
        this is French || other is French -> false
        else -> PhoneNumbers.same(text, other.text)
    }

    private val text: String
        get() = when (this) {
            is French -> digits
            is Foreign -> "+$digits"
            is Local -> raw
        }

    companion object {
        private const val MIN_INTERNATIONAL_DIGITS = 7

        fun of(raw: String, nationalFormIsFrench: Boolean): NumberIdentity {
            FrenchNumbers.nationalDigits(raw, nationalFormIsFrench)?.let { return French(it) }
            val compact = PhoneNumbers.compact(raw)
            val digits = when {
                compact.startsWith("+") -> compact.drop(1)
                compact.startsWith("00") -> compact.drop(2)
                else -> return Local(raw)
            }
            return if (digits.length >= MIN_INTERNATIONAL_DIGITS) Foreign(digits) else Local(raw)
        }
    }
}

internal fun List<AllowedNumber>.matching(number: String?): AllowedNumber? =
    number?.let { n -> firstOrNull { PhoneNumbers.same(it.number, n) } }

/** Adds the number, or replaces every equivalent form already allowed, keeping its place. */
internal fun List<AllowedNumber>.withAllowed(entry: AllowedNumber): List<AllowedNumber> {
    val index = indexOfFirst { PhoneNumbers.same(it.number, entry.number) }
    if (index < 0) return this + entry
    val others = filterNot { PhoneNumbers.same(it.number, entry.number) }
    return others.toMutableList().apply { add(index.coerceAtMost(size), entry) }
}

internal fun List<AllowedNumber>.without(number: String): List<AllowedNumber> =
    filterNot { PhoneNumbers.same(it.number, number) }

internal fun List<ScreenedCall>.withNewCall(call: ScreenedCall, max: Int = MAX_JOURNAL_CALLS): List<ScreenedCall> =
    (listOf(call) + this).take(max)

/** Calls are stored newest first, so days come out newest first too. */
internal fun groupByDay(calls: List<ScreenedCall>, zone: ZoneId): List<Pair<LocalDate, List<ScreenedCall>>> =
    calls.groupBy { Instant.ofEpochMilli(it.timeMillis).atZone(zone).toLocalDate() }.toList()

/**
 * Entries older than [window] are dropped. One dated in the future is kept: the clock may be
 * wrong for a moment, and it matches nothing meanwhile.
 */
private fun <T> List<T>.unexpired(now: Long, window: Long, max: Int, time: (T) -> Long): List<T> =
    filter { now - time(it) <= window }.sortedByDescending(time).take(max)

@JvmName("unexpiredCalledNumbers")
internal fun List<CalledNumber>.unexpired(now: Long): List<CalledNumber> =
    unexpired(now, CALLED_BACK_WINDOW_MILLIS, MAX_CALLED_NUMBERS) { it.timeMillis }

/** Remembers the call, in place of any earlier call to the same number. */
internal fun List<CalledNumber>.withCall(call: CalledNumber, now: Long): List<CalledNumber> {
    val identity = call.identity
    return (listOf(call) + filterNot { it.identity.sameCallerAs(identity) }).unexpired(now)
}

/** The call that lets [caller] ring back at [now], if the user made one in the last 30 days. */
internal fun List<CalledNumber>.lastCallTo(caller: NumberIdentity, now: Long): CalledNumber? =
    firstOrNull { isWithin(it.timeMillis, now, CALLED_BACK_WINDOW_MILLIS) && it.identity.sameCallerAs(caller) }

@JvmName("unexpiredRejections")
internal fun List<RecentRejection>.unexpired(now: Long): List<RecentRejection> =
    unexpired(now, SECOND_CALL_WINDOW_MILLIS, MAX_RECENT_REJECTIONS) { it.timeMillis }

internal fun List<RecentRejection>.withRejection(rejection: RecentRejection, now: Long): List<RecentRejection> =
    (listOf(rejection) + this).unexpired(now)

/**
 * The rejection that lets [digits] ring at [now]: one in the last 5 minutes, provided the
 * network flagged none of that number's calls as spoofed in the meantime.
 */
internal fun List<RecentRejection>.openingSecondCall(digits: String, now: Long): RecentRejection? {
    val recent = filter { it.digits == digits && isWithin(it.timeMillis, now, SECOND_CALL_WINDOW_MILLIS) }
    return if (recent.any { it.spoofed }) null else recent.maxByOrNull { it.timeMillis }
}

/** Line-based storage format: one record per line, fields separated by a tab. */
internal object RecordCodec {
    /** A rejection keeps the two fields of the first versions; a call that rang adds why and since when. */
    fun encodeCalls(calls: List<ScreenedCall>): String = calls.joinToString("\n") {
        val line = "${it.timeMillis}\t${clean(it.number.orEmpty())}"
        if (it.outcome == CallOutcome.REJECTED) line else "$line\t${it.outcome.code}\t${it.sinceMillis}"
    }

    fun decodeCalls(stored: String?): List<ScreenedCall> = records(stored).mapNotNull { (first, second) ->
        val time = first.toLongOrNull() ?: return@mapNotNull null
        val fields = second.split('\t')
        val outcome = CallOutcome.entries.firstOrNull { it.code == fields.getOrNull(1) } ?: CallOutcome.REJECTED
        val since = if (outcome == CallOutcome.REJECTED) 0L else fields.getOrNull(2)?.toLongOrNull() ?: 0L
        ScreenedCall(fields[0].ifEmpty { null }, time, outcome, since)
    }

    fun encodeCalledNumbers(calls: List<CalledNumber>): String =
        calls.joinToString("\n") { "${it.timeMillis}\t${clean(it.number)}\t${flag(it.onFrenchNetwork)}" }

    fun decodeCalledNumbers(stored: String?): List<CalledNumber> = records(stored).mapNotNull { (first, second) ->
        val time = first.toLongOrNull() ?: return@mapNotNull null
        val fields = second.split('\t')
        if (fields[0].isBlank()) null else CalledNumber(fields[0], time, fields.getOrNull(1) != FLAG_OFF)
    }

    fun encodeRejections(rejections: List<RecentRejection>): String =
        rejections.joinToString("\n") { "${it.timeMillis}\t${clean(it.digits)}\t${flag(it.spoofed)}" }

    fun decodeRejections(stored: String?): List<RecentRejection> = records(stored).mapNotNull { (first, second) ->
        val time = first.toLongOrNull() ?: return@mapNotNull null
        val fields = second.split('\t')
        // A line whose flag was lost counts as spoofed: it then opens no second call.
        if (fields[0].isBlank()) null else RecentRejection(fields[0], time, fields.getOrNull(1) != FLAG_OFF)
    }

    fun encodeAllowlist(entries: List<AllowedNumber>): String =
        entries.joinToString("\n") { "${clean(it.number)}\t${clean(it.label)}" }

    fun decodeAllowlist(stored: String?): List<AllowedNumber> = records(stored).mapNotNull { (first, second) ->
        if (first.isBlank()) null else AllowedNumber(first, second)
    }.distinctBy { it.number }

    fun encodeSmsReplies(replies: Map<String, Long>): String =
        replies.entries.joinToString("\n") { "${it.value}\t${clean(it.key)}" }

    fun decodeSmsReplies(stored: String?): Map<String, Long> = records(stored).mapNotNull { (first, second) ->
        first.toLongOrNull()?.let { second to it }
    }.toMap()

    private fun records(stored: String?): List<Pair<String, String>> =
        stored.orEmpty().lineSequence().mapNotNull { line ->
            val tab = line.indexOf('\t')
            if (tab < 0) null else line.substring(0, tab) to line.substring(tab + 1)
        }.toList()

    private fun clean(value: String) = value.replace(Regex("[\\t\\r\\n]+"), " ").trim()

    private fun flag(value: Boolean) = if (value) FLAG_ON else FLAG_OFF

    private const val FLAG_ON = "1"
    private const val FLAG_OFF = "0"
}
