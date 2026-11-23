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
        assertFalse(PhoneNumbers.isDialable("000"))
    }

    @Test
    fun bracketedTrunkZeroIsIgnored() {
        assertTrue(PhoneNumbers.same("+33 (0)6 12 34 56 78", "+33612345678"))
        assertTrue(PhoneNumbers.same("+33 ( 0 ) 6 12 34 56 78", "06 12 34 56 78"))
    }

    @Test
    fun handlesWithoutDigitsMatchOnlyThemselves() {
        assertTrue(PhoneNumbers.same("alice@example.com", "Alice@example.com"))
        assertFalse(PhoneNumbers.same("alice@example.com", "bob@example.com"))
        assertTrue(PhoneNumbers.same("000", "000"))
    }

    @Test
    fun allowingTwiceNeverDuplicatesAndRemovalAlwaysWorks() {
        for (number in listOf("000", "alice@example.com", "06 12 34 56 78")) {
            val allowlist = emptyList<AllowedNumber>()
                .withAllowed(AllowedNumber(number, "a"))
                .withAllowed(AllowedNumber(number, "b"))
            assertEquals(listOf(AllowedNumber(number, "b")), allowlist)
            assertEquals(emptyList<AllowedNumber>(), allowlist.without(number))
        }
    }

    @Test
    fun renamingKeepsThePlaceInTheList() {
        val allowlist = listOf(AllowedNumber("0611111111", "A"), AllowedNumber("0622222222", "B"))
            .withAllowed(AllowedNumber("+33 6 11 11 11 11", "A2"))
        assertEquals(listOf("A2", "B"), allowlist.map { it.label })
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
        val calls = (1..3).fold(emptyList<ScreenedCall>()) { list, i -> list.withNewCall(ScreenedCall("$i", i.toLong()), max = 2) }
        assertEquals(listOf(ScreenedCall("3", 3L), ScreenedCall("2", 2L)), calls)
    }

    @Test
    fun callsSurviveStorage() {
        val calls = listOf(ScreenedCall("+33 6 12 34 56 78", 1_700_000_000_000L), ScreenedCall(null, 1_699_999_000_000L))
        assertEquals(calls, RecordCodec.decodeCalls(RecordCodec.encodeCalls(calls)))
    }

    @Test
    fun allowlistSurvivesStorageWithAwkwardLabels() {
        val stored = RecordCodec.encodeAllowlist(listOf(AllowedNumber("0612345678", "Livreure\tdu\nlundi")))
        assertEquals(listOf(AllowedNumber("0612345678", "Livreure du lundi")), RecordCodec.decodeAllowlist(stored))
    }

    @Test
    fun duplicatedStoredNumbersAreDropped() {
        val stored = "000\ta\n000\tb"
        assertEquals(listOf(AllowedNumber("000", "a")), RecordCodec.decodeAllowlist(stored))
    }

    @Test
    fun corruptOrMissingStorageIsEmpty() {
        assertEquals(emptyList<ScreenedCall>(), RecordCodec.decodeCalls(null))
        assertEquals(emptyList<ScreenedCall>(), RecordCodec.decodeCalls("garbage\nnot-a-time\tx"))
        assertEquals(emptyList<AllowedNumber>(), RecordCodec.decodeAllowlist("\n\tlabel only"))
    }

    @Test
    fun callsThatRangSurviveStorageWithTheirReason() {
        val calls = listOf(
            ScreenedCall("0123456789", 3_000L, CallOutcome.RANG_SECOND_CALL, 2_000L),
            ScreenedCall("0123456789", 2_000L),
            ScreenedCall("+33411223344", 1_000L, CallOutcome.RANG_CALLED_BACK, 500L),
        )
        assertEquals(calls, RecordCodec.decodeCalls(RecordCodec.encodeCalls(calls)))
    }

    @Test
    fun journalOfEarlierVersionsReadsAsRejections() {
        assertEquals(
            listOf(ScreenedCall("0612345678", 2_000L), ScreenedCall(null, 1_000L)),
            RecordCodec.decodeCalls("2000\t0612345678\n1000\t"),
        )
        // An unknown reason, written by a later version, is not taken for a call that rang.
        assertEquals(listOf(ScreenedCall("0612345678", 2_000L)), RecordCodec.decodeCalls("2000\t0612345678\tZ\t5"))
    }

    @Test
    fun calledNumbersAndRejectionsSurviveStorage() {
        val called = listOf(CalledNumber("+44 20 7946 0123", 2_000L, onFrenchNetwork = false), CalledNumber("01 23 45 67 89", 1_000L, true))
        assertEquals(called, RecordCodec.decodeCalledNumbers(RecordCodec.encodeCalledNumbers(called)))
        val rejections = listOf(RecentRejection("123456789", 2_000L, spoofed = true), RecentRejection("612345678", 1_000L, false))
        assertEquals(rejections, RecordCodec.decodeRejections(RecordCodec.encodeRejections(rejections)))
    }

    @Test
    fun damagedMemoryIsDroppedOrReadOnTheSafeSide() {
        assertEquals(emptyList<CalledNumber>(), RecordCodec.decodeCalledNumbers(null))
        assertEquals(emptyList<CalledNumber>(), RecordCodec.decodeCalledNumbers("garbage\nnot-a-time\t0612345678\t1\n5\t\t1"))
        assertEquals(emptyList<RecentRejection>(), RecordCodec.decodeRejections("garbage\n\t612345678\t0"))
        // Without its flag, a rejection counts as spoofed: it opens no second call.
        assertEquals(listOf(RecentRejection("612345678", 5L, spoofed = true)), RecordCodec.decodeRejections("5\t612345678"))
    }

    @Test
    fun frenchNumbersAreRecognisedInEveryForm() {
        listOf("0612345678", "06 12 34 56 78", "06.12.34.56.78", "+33612345678", "+33 6 12 34 56 78", "0033612345678", "+33 (0)6 12 34 56 78")
            .forEach { assertEquals(it, "612345678", FrenchNumbers.nationalDigits(it, nationalFormIsFrench = true)) }
        // Overseas territories share the French plan: +590, +594, +596, +262 and +508.
        assertEquals("690123456", FrenchNumbers.nationalDigits("+590 690 12 34 56", true))
        assertEquals("690123456", FrenchNumbers.nationalDigits("0690 12 34 56", true))
        assertEquals("692123456", FrenchNumbers.nationalDigits("00262692123456", true))
        assertEquals("694123456", FrenchNumbers.nationalDigits("+594694123456", true))
        assertEquals("696123456", FrenchNumbers.nationalDigits("+596696123456", true))
        // Saint-Pierre-et-Miquelon: six digits after +508, 0508 and six digits at home.
        assertEquals("508411234", FrenchNumbers.nationalDigits("+508 41 12 34", true))
        assertEquals("508411234", FrenchNumbers.nationalDigits("0508 41 12 34", true))
    }

    @Test
    fun foreignShortOrMalformedNumbersAreNotFrench() {
        listOf(
            "+447700900123", // foreign mobile
            "0044 20 7946 0123",
            "+687 12 34 56", // New Caledonia has its own plan
            "+689 87 12 34 56", // French Polynesia too
            "+33 6 12 34 56", // too short
            "+330612345678", // trunk zero kept after the country code
            "061234567", // one digit short
            "0012345678", // no such prefix
            "3631", // short number
            "alice@example.com",
            "",
        ).forEach { assertNull(it, FrenchNumbers.nationalDigits(it, nationalFormIsFrench = true)) }
    }

    @Test
    fun nationalFormMetAbroadIsNotFrench() {
        assertNull(FrenchNumbers.nationalDigits("06 12 34 56 78", nationalFormIsFrench = false))
        assertEquals("612345678", FrenchNumbers.nationalDigits("+33 6 12 34 56 78", nationalFormIsFrench = false))
    }

    @Test
    fun ordinaryNumbersAreLandlinesMobilesAndVoipLines() {
        listOf("123456789", "212345678", "312345678", "412345678", "512345678", "612345678", "712345678", "912345678", "690123456", "508411234")
            .forEach { assertTrue(it, FrenchNumbers.isOrdinary(it)) }
    }

    @Test
    fun specialNumbersAndAutomatedRangesAreNotOrdinary() {
        // 08: free, standard and premium rate services.
        listOf("800123456", "811123456", "899123456").forEach { assertFalse(it, FrenchNumbers.isOrdinary(it)) }
        // Ranges automated calling systems must use (Arcep decision 2022-1583).
        listOf("162", "163", "270", "271", "377", "378", "424", "425", "568", "569", "948", "949")
            .forEach { assertFalse(it, FrenchNumbers.isOrdinary(it + "123456")) }
        listOf("9475", "9476", "9477", "9478", "9479").forEach { assertFalse(it, FrenchNumbers.isOrdinary(it + "12345")) }
        // Their neighbours stay ordinary.
        listOf("161234567", "164123456", "947412345", "950123456").forEach { assertTrue(it, FrenchNumbers.isOrdinary(it)) }
        assertFalse(FrenchNumbers.isOrdinary("12345678"))
        assertFalse(FrenchNumbers.isOrdinary(""))
    }

    @Test
    fun aFrenchNumberIsOneCallerWhateverItsForm() {
        val called = NumberIdentity.of("09 69 12 34 56", nationalFormIsFrench = true)
        assertTrue(called.sameCallerAs(NumberIdentity.of("+33969123456", true)))
        assertTrue(called.sameCallerAs(NumberIdentity.of("0033 9 69 12 34 56", false)))
        assertFalse(called.sameCallerAs(NumberIdentity.of("09 69 12 34 57", true)))
    }

    @Test
    fun aNumberEndingLikeAFrenchOneIsAnotherCaller() {
        val called = NumberIdentity.of("09 69 12 34 56", nationalFormIsFrench = true)
        // Same last nine digits, another country: the plain suffix rule would take them for one.
        assertTrue(PhoneNumbers.same("09 69 12 34 56", "+34 969 12 34 56"))
        assertFalse(called.sameCallerAs(NumberIdentity.of("+34 969 12 34 56", true)))
        assertFalse(called.sameCallerAs(NumberIdentity.of("+999969123456", true)))
        // Abroad, the same digits without a country code are a local number.
        assertFalse(called.sameCallerAs(NumberIdentity.of("0969123456", nationalFormIsFrench = false)))
    }

    @Test
    fun foreignNumbersCompareByAllTheirDigits() {
        val called = NumberIdentity.of("+44 20 7946 0123", nationalFormIsFrench = true)
        assertTrue(called.sameCallerAs(NumberIdentity.of("0044 20 7946 0123", true)))
        assertFalse(called.sameCallerAs(NumberIdentity.of("+999442079460123", true)))
        // Its national form, as a network of that country may present it.
        assertTrue(called.sameCallerAs(NumberIdentity.of("020 7946 0123", nationalFormIsFrench = false)))
    }

    @Test
    fun shortNumbersOnlyEqualThemselves() {
        val called = NumberIdentity.of("3631", nationalFormIsFrench = true)
        assertTrue(called.sameCallerAs(NumberIdentity.of("3631", true)))
        assertFalse(called.sameCallerAs(NumberIdentity.of("+9993631", true)))
        assertFalse(called.sameCallerAs(NumberIdentity.of("0123453631", true)))
    }

    @Test
    fun callingAgainRestartsThe30Days() {
        val day = 24 * 60 * 60 * 1000L
        val calls = emptyList<CalledNumber>()
            .withCall(CalledNumber("01 23 45 67 89", 0L, true), now = 0L)
            .withCall(CalledNumber("06 12 34 56 78", day, true), now = day)
            .withCall(CalledNumber("+33 1 23 45 67 89", 10 * day, true), now = 10 * day)
        assertEquals(listOf("+33 1 23 45 67 89", "06 12 34 56 78"), calls.map { it.number })
        val caller = NumberIdentity.of("0123456789", true)
        assertEquals(10 * day, calls.lastCallTo(caller, now = 40 * day)?.timeMillis)
        assertNull(calls.lastCallTo(caller, now = 40 * day + 1))
    }

    @Test
    fun calledNumbersEraseThemselvesAfter30Days() {
        val calls = listOf(CalledNumber("0611111111", 2_000L, true), CalledNumber("0622222222", 1_000L, true))
        assertEquals(calls, calls.unexpired(now = 1_000L + CALLED_BACK_WINDOW_MILLIS))
        assertEquals(calls.take(1), calls.unexpired(now = 1_001L + CALLED_BACK_WINDOW_MILLIS))
        assertEquals(emptyList<CalledNumber>(), calls.unexpired(now = 2_001L + CALLED_BACK_WINDOW_MILLIS))
    }

    @Test
    fun aClockGoneBackErasesNothingAndMatchesNothing() {
        val calls = listOf(CalledNumber("0611111111", 5_000L, true))
        assertEquals(calls, calls.unexpired(now = 1_000L))
        assertNull(calls.lastCallTo(NumberIdentity.of("0611111111", true), now = 4_999L))
        assertFalse(isWithin(since = 5_000L, now = 4_999L, window = 1_000L))
        assertTrue(isWithin(since = 5_000L, now = 6_000L, window = 1_000L))
        assertFalse(isWithin(since = 5_000L, now = 6_001L, window = 1_000L))
    }

    @Test
    fun memoryKeepsTheNewestEntriesOnly() {
        val calls = (1..MAX_CALLED_NUMBERS + 5).fold(emptyList<CalledNumber>()) { list, i ->
            list.withCall(CalledNumber("06%08d".format(i), i.toLong(), true), now = i.toLong())
        }
        assertEquals(MAX_CALLED_NUMBERS, calls.size)
        assertEquals((MAX_CALLED_NUMBERS + 5).toLong(), calls.first().timeMillis)
        assertEquals(6L, calls.last().timeMillis)
        val rejections = (1..MAX_RECENT_REJECTIONS + 5).fold(emptyList<RecentRejection>()) { list, i ->
            list.withRejection(RecentRejection("6%08d".format(i), i.toLong(), false), now = i.toLong())
        }
        assertEquals(MAX_RECENT_REJECTIONS, rejections.size)
        assertEquals(6L, rejections.last().timeMillis)
    }

    @Test
    fun rejectionsEraseThemselvesAfter5Minutes() {
        val rejections = listOf(RecentRejection("612345678", 1_000L, false))
        assertEquals(rejections, rejections.unexpired(now = 1_000L + SECOND_CALL_WINDOW_MILLIS))
        assertEquals(emptyList<RecentRejection>(), rejections.unexpired(now = 1_001L + SECOND_CALL_WINDOW_MILLIS))
        assertEquals(
            listOf(RecentRejection("712345678", 400_000L, false)),
            rejections.withRejection(RecentRejection("712345678", 400_000L, false), now = 400_000L),
        )
    }

    @Test
    fun aSecondCallNeedsACleanRejectionInTheLast5Minutes() {
        val rejections = listOf(RecentRejection("612345678", 120_000L, false), RecentRejection("612345678", 60_000L, false))
        assertEquals(120_000L, rejections.openingSecondCall("612345678", now = 130_000L)?.timeMillis)
        assertNull(rejections.openingSecondCall("712345678", now = 130_000L))
        assertNull(rejections.openingSecondCall("612345678", now = 120_001L + SECOND_CALL_WINDOW_MILLIS))
        val flagged = rejections + RecentRejection("612345678", 90_000L, spoofed = true)
        assertNull(flagged.openingSecondCall("612345678", now = 130_000L))
        // Five minutes after the flagged call, the later clean rejection counts again.
        assertEquals(120_000L, flagged.openingSecondCall("612345678", now = 90_001L + SECOND_CALL_WINDOW_MILLIS)?.timeMillis)
    }

    @Test
    fun callsAreGroupedByLocalDay() {
        val zone = ZoneId.of("Europe/Paris")
        fun at(day: Int, hour: Int) = ZonedDateTime.of(2026, 10, day, hour, 0, 0, 0, zone).toInstant().toEpochMilli()
        val calls = listOf(ScreenedCall("a", at(27, 14)), ScreenedCall("b", at(27, 9)), ScreenedCall("c", at(26, 23)))
        val days = groupByDay(calls, zone)
        assertEquals(listOf(LocalDate.of(2026, 10, 27), LocalDate.of(2026, 10, 26)), days.map { it.first })
        assertEquals(2, days.first().second.size)
    }
}
