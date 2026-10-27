package com.scamstop.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class CallRecordsTest {
    @Test
    fun nationalAndInternationalFormsMatch() {
        assertTrue(PhoneNumbers.same("06 12 34 56 78", "+33 6 12 34 56 78"))
        assertTrue(PhoneNumbers.same("0612345678", "0033612345678"))
        assertTrue(PhoneNumbers.same("06.12.34.56.78", "+33612345678"))
    }

    @Test
    fun eightDigitNumbersMatchTheirInternationalForm() {
        // French Polynesia: 8 digits nationally, +689 internationally.
        assertTrue(PhoneNumbers.same("87 12 34 56", "+689 87 12 34 56"))
    }

    @Test
    fun differentNumbersDoNotMatch() {
        assertFalse(PhoneNumbers.same("06 12 34 56 78", "06 12 34 56 79"))
        assertFalse(PhoneNumbers.same("", "+33612345678"))
    }

    @Test
    fun shortNumbersOnlyMatchExactly() {
        assertTrue(PhoneNumbers.same("3631", "3631"))
        assertFalse(PhoneNumbers.same("3631", "13631"))
    }

    @Test
    fun dialableNeedsAFewDigits() {
        assertTrue(PhoneNumbers.isDialable("06 12"))
        assertFalse(PhoneNumbers.isDialable("+3"))
        assertFalse(PhoneNumbers.isDialable("Livreure"))
    }

    @Test
    fun allowlistFindsAnEquivalentForm() {
        val allowlist = listOf(AllowedNumber("+33 6 12 34 56 78", "Livreure"))
        assertEquals("Livreure", allowlist.matching("0612345678")?.label)
        assertNull(allowlist.matching("0612345679"))
        assertNull(allowlist.matching(null))
    }

    @Test
    fun allowingTheSameNumberAgainRenamesIt() {
        val allowlist = listOf(AllowedNumber("0612345678", ""))
            .withAllowed(AllowedNumber("+33612345678", "Livreure"))
        assertEquals(listOf(AllowedNumber("+33612345678", "Livreure")), allowlist)
        assertEquals(emptyList<AllowedNumber>(), allowlist.without("06 12 34 56 78"))
    }

    @Test
    fun journalKeepsTheNewestCalls() {
        val calls = (1..3).fold(emptyList<RejectedCall>()) { list, i -> list.withNewCall(RejectedCall("$i", i.toLong()), max = 2) }
        assertEquals(listOf(RejectedCall("3", 3L), RejectedCall("2", 2L)), calls)
    }

    @Test
    fun callsSurviveStorage() {
        val calls = listOf(RejectedCall("+33 6 12 34 56 78", 1_700_000_000_000L), RejectedCall(null, 1_699_999_000_000L))
        assertEquals(calls, RecordCodec.decodeCalls(RecordCodec.encodeCalls(calls)))
    }

    @Test
    fun allowlistSurvivesStorageWithAwkwardLabels() {
        val stored = RecordCodec.encodeAllowlist(listOf(AllowedNumber("0612345678", "Livreure\tdu\nlundi")))
        assertEquals(listOf(AllowedNumber("0612345678", "Livreure du lundi")), RecordCodec.decodeAllowlist(stored))
    }

    @Test
    fun corruptOrMissingStorageIsEmpty() {
        assertEquals(emptyList<RejectedCall>(), RecordCodec.decodeCalls(null))
        assertEquals(emptyList<RejectedCall>(), RecordCodec.decodeCalls("garbage\nnot-a-time\tx"))
        assertEquals(emptyList<AllowedNumber>(), RecordCodec.decodeAllowlist("\n\tlabel only"))
    }

    @Test
    fun callsAreGroupedByLocalDay() {
        val zone = ZoneId.of("Europe/Paris")
        fun at(day: Int, hour: Int) = ZonedDateTime.of(2026, 10, day, hour, 0, 0, 0, zone).toInstant().toEpochMilli()
        val calls = listOf(RejectedCall("a", at(27, 14)), RejectedCall("b", at(27, 9)), RejectedCall("c", at(26, 23)))
        val days = groupByDay(calls, zone)
        assertEquals(listOf(LocalDate.of(2026, 10, 27), LocalDate.of(2026, 10, 26)), days.map { it.first })
        assertEquals(2, days.first().second.size)
    }
}
