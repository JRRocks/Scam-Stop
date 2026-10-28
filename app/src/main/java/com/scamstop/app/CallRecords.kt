package com.scamstop.app

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal data class RejectedCall(val number: String?, val timeMillis: Long)

internal data class AllowedNumber(val number: String, val label: String)

internal const val MAX_REJECTED_CALLS = 100

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

    private val TRUNK_ZERO = Regex("\\(\\s*0\\s*\\)")
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

internal fun List<RejectedCall>.withNewCall(call: RejectedCall, max: Int = MAX_REJECTED_CALLS): List<RejectedCall> =
    (listOf(call) + this).take(max)

/** Calls are stored newest first, so days come out newest first too. */
internal fun groupByDay(calls: List<RejectedCall>, zone: ZoneId): List<Pair<LocalDate, List<RejectedCall>>> =
    calls.groupBy { Instant.ofEpochMilli(it.timeMillis).atZone(zone).toLocalDate() }.toList()

/** Line-based storage format: one record per line, fields separated by a tab. */
internal object RecordCodec {
    fun encodeCalls(calls: List<RejectedCall>): String =
        calls.joinToString("\n") { "${it.timeMillis}\t${clean(it.number.orEmpty())}" }

    fun decodeCalls(stored: String?): List<RejectedCall> = records(stored).mapNotNull { (first, second) ->
        first.toLongOrNull()?.let { RejectedCall(second.ifEmpty { null }, it) }
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
}
