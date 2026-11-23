package com.scamstop.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreeningDecisionTest {
    private val minute = 60 * 1000L
    private val day = 24 * 60 * minute
    private val now = 1_800_000_000_000L

    private val artisan = "01 23 45 67 89"
    private val artisanDigits = "123456789"

    @Test
    fun incomingCallIsBlockedWhenEnabled() {
        assertEquals(ScreeningAction.BLOCK, screeningAction(CallDirection.INCOMING, isBlocking = true))
    }

    @Test
    fun incomingCallIsAllowedWhenDisabled() {
        assertEquals(ScreeningAction.ALLOW, screeningAction(CallDirection.INCOMING, isBlocking = false))
    }

    @Test
    fun allowlistedCallRingsWhileBlocking() {
        assertEquals(
            ScreeningAction.ALLOW,
            screeningAction(CallDirection.INCOMING, isBlocking = true, isAllowlisted = true),
        )
    }

    @Test
    fun outgoingCallIsIgnored() {
        assertEquals(ScreeningAction.IGNORE, screeningAction(CallDirection.OUTGOING, isBlocking = true))
    }

    @Test
    fun unknownDirectionIsIgnored() {
        assertEquals(ScreeningAction.IGNORE, screeningAction(CallDirection.UNKNOWN, isBlocking = true))
    }

    @Test
    fun pausedProtectionDoesNotBlock() {
        val now = 1_000_000L
        assertEquals(false, isBlocking(isEnabled = true, pausedUntil = now + 1, now = now))
        assertEquals(true, isBlocking(isEnabled = true, pausedUntil = now, now = now))
        assertEquals(false, isBlocking(isEnabled = false, pausedUntil = 0L, now = now))
    }

    @Test
    fun incomingDecisionCompletesWithinScreeningDeadline() {
        val startedAt = System.nanoTime()
        screeningAction(CallDirection.INCOMING, isBlocking = true)
        val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000

        assertTrue("Decision took ${elapsedMillis}ms", elapsedMillis < 5_000)
    }

    // First call of an unknown number: rejected at once.

    @Test
    fun firstCallOfAnUnknownNumberIsRejected() {
        assertEquals(ScreeningVerdict.Rejected(artisanDigits), incoming(artisan, blocking()))
        assertEquals(ScreeningAction.BLOCK, incoming(artisan, blocking()).action)
    }

    @Test
    fun aWithheldNumberIsRejectedAndOpensNothing() {
        val state = blocking(
            calledNumbers = listOf(CalledNumber("", now, true)),
            recentRejections = listOf(RecentRejection("", now, false)),
        )
        assertEquals(ScreeningVerdict.Rejected(null), incoming(null, state))
        assertEquals(ScreeningVerdict.Rejected(null), incoming("", state))
        assertEquals(ScreeningVerdict.Rejected(null), incoming("  ", state))
    }

    // "Ceux que j'ai appelés": a number the user called rings for 30 days.

    @Test
    fun aNumberTheUserCalledRingsFor30Days() {
        val state = blocking(calledNumbers = listOf(CalledNumber(artisan, now, true)))
        assertEquals(ScreeningVerdict.CalledBack(now), incoming(artisan, state, at = now))
        assertEquals(ScreeningVerdict.CalledBack(now), incoming(artisan, state, at = now + 30 * day))
        assertEquals(ScreeningVerdict.Rejected(artisanDigits), incoming(artisan, state, at = now + 30 * day + 1))
        assertEquals(ScreeningAction.ALLOW, incoming(artisan, state).action)
    }

    @Test
    fun everyFormOfACalledNumberRings() {
        val dialled = blocking(calledNumbers = listOf(CalledNumber(artisan, now, true)))
        listOf("0123456789", "+33123456789", "+33 1 23 45 67 89", "0033 1 23 45 67 89", "+33 (0)1 23 45 67 89", "01.23.45.67.89")
            .forEach { assertEquals(it, ScreeningVerdict.CalledBack(now), incoming(it, dialled)) }
        val tapped = blocking(calledNumbers = listOf(CalledNumber("+33612345678", now, true)))
        assertEquals(ScreeningVerdict.CalledBack(now), incoming("06 12 34 56 78", tapped))
    }

    @Test
    fun aCalledNumberMayBeForeignSpecialOrShort() {
        val state = blocking(
            calledNumbers = listOf(
                CalledNumber("+44 20 7946 0123", now, true),
                CalledNumber("0 800 12 34 56", now - day, true),
                CalledNumber("3631", now - 2 * day, true),
            ),
        )
        assertEquals(ScreeningVerdict.CalledBack(now), incoming("+442079460123", state))
        assertEquals(ScreeningVerdict.CalledBack(now - day), incoming("+33800123456", state))
        assertEquals(ScreeningVerdict.CalledBack(now - 2 * day), incoming("3631", state))
    }

    @Test
    fun aLookalikeNumberDoesNotPassForACalledOne() {
        val state = blocking(calledNumbers = listOf(CalledNumber("09 69 12 34 56", now, true), CalledNumber("+44 20 7946 0123", now, true)))
        // Same last digits behind another country code.
        assertEquals(ScreeningVerdict.Rejected(null), incoming("+34 969 12 34 56", state))
        assertEquals(ScreeningVerdict.Rejected(null), incoming("+999969123456", state))
        assertEquals(ScreeningVerdict.Rejected(null), incoming("+999442079460123", state))
        // One digit away.
        assertEquals(ScreeningVerdict.Rejected("969123457"), incoming("09 69 12 34 57", state))
    }

    @Test
    fun aCalledNumberFlaggedAsSpoofedIsRejected() {
        val state = blocking(calledNumbers = listOf(CalledNumber(artisan, now, true)))
        assertEquals(ScreeningVerdict.Rejected(artisanDigits), incoming(artisan, state, spoofed = true))
    }

    @Test
    fun calledBackCanBeTurnedOff() {
        val state = blocking(calledBackEnabled = false, calledNumbers = listOf(CalledNumber(artisan, now, true)))
        assertEquals(ScreeningVerdict.Rejected(artisanDigits), incoming(artisan, state))
    }

    @Test
    fun aCallDatedAfterNowDoesNotCount() {
        val state = blocking(calledNumbers = listOf(CalledNumber(artisan, now + 1, true)))
        assertEquals(ScreeningVerdict.Rejected(artisanDigits), incoming(artisan, state))
    }

    // "Le 2e appel passe": a rejected number rings if it calls again within 5 minutes.

    @Test
    fun secondCallWithin5MinutesRings() {
        val state = blocking(recentRejections = listOf(RecentRejection(artisanDigits, now, false)))
        assertEquals(ScreeningVerdict.SecondCall(now), incoming(artisan, state, at = now + 40_000))
        assertEquals(ScreeningVerdict.SecondCall(now), incoming(artisan, state, at = now + 5 * minute))
        assertEquals(ScreeningVerdict.Rejected(artisanDigits), incoming(artisan, state, at = now + 5 * minute + 1))
        assertEquals(ScreeningAction.ALLOW, incoming(artisan, state, at = now + 40_000).action)
    }

    @Test
    fun aRejectionDatedAfterNowDoesNotCount() {
        val state = blocking(recentRejections = listOf(RecentRejection(artisanDigits, now + 1, false)))
        assertEquals(ScreeningVerdict.Rejected(artisanDigits), incoming(artisan, state))
    }

    @Test
    fun everyFormOfTheSameNumberIsASecondCall() {
        val state = blocking(recentRejections = listOf(RecentRejection(artisanDigits, now, false)))
        listOf("0123456789", "+33123456789", "0033 1 23 45 67 89", "+33 (0)1 23 45 67 89")
            .forEach { assertEquals(it, ScreeningVerdict.SecondCall(now), incoming(it, state, at = now + minute)) }
        assertEquals(ScreeningVerdict.Rejected("123456780"), incoming("01 23 45 67 80", state, at = now + minute))
    }

    @Test
    fun overseasFrenchNumbersGetASecondCall() {
        val first = incoming("+262 692 12 34 56", blocking())
        assertEquals(ScreeningVerdict.Rejected("692123456"), first)
        val state = blocking(recentRejections = listOf(RecentRejection("692123456", now, false)))
        assertEquals(ScreeningVerdict.SecondCall(now), incoming("0692 12 34 56", state, at = now + minute))
        assertEquals(ScreeningVerdict.Rejected("690123456"), incoming("+590 690 12 34 56", blocking()))
        assertEquals(ScreeningVerdict.Rejected("508411234"), incoming("+508 41 12 34", blocking()))
    }

    @Test
    fun aForeignNumberNeverGetsASecondCall() {
        listOf("+44 7700 900123", "0044 20 7946 0123", "+1 202 555 0123", "+687 12 34 56", "+689 87 12 34 56").forEach { number ->
            assertEquals(number, ScreeningVerdict.Rejected(null), incoming(number, blocking()))
        }
        // Even a memory that should not exist does not let one through.
        val state = blocking(recentRejections = listOf(RecentRejection("447700900123", now, false), RecentRejection("700900123", now, false)))
        assertEquals(ScreeningVerdict.Rejected(null), incoming("+44 7700 900123", state, at = now + minute))
    }

    @Test
    fun aNationalNumberMetAbroadNeverGetsASecondCall() {
        val state = blocking(recentRejections = listOf(RecentRejection("612345678", now, false)))
        assertEquals(ScreeningVerdict.Rejected(null), incoming("06 12 34 56 78", state, at = now + minute, onFrenchNetwork = false))
        assertEquals(ScreeningVerdict.SecondCall(now), incoming("+33 6 12 34 56 78", state, at = now + minute, onFrenchNetwork = false))
    }

    @Test
    fun numbersNoPersonDialsFromNeverGetASecondCall() {
        listOf(
            "01 62 12 34 56", // automated calling systems
            "09 48 12 34 56",
            "+33 5 68 12 34 56",
            "09 47 51 23 45", // the same, overseas
            "08 00 12 34 56", // special numbers
            "08 99 12 34 56",
            "3631", // short numbers
            "118 712",
        ).forEach { number ->
            assertEquals(number, ScreeningVerdict.Rejected(null), incoming(number, blocking()))
        }
        val state = blocking(recentRejections = listOf(RecentRejection("162123456", now, false), RecentRejection("800123456", now, false)))
        assertEquals(ScreeningVerdict.Rejected(null), incoming("01 62 12 34 56", state, at = now + minute))
        assertEquals(ScreeningVerdict.Rejected(null), incoming("0800123456", state, at = now + minute))
    }

    @Test
    fun aSpoofedSecondCallIsRejected() {
        val state = blocking(recentRejections = listOf(RecentRejection(artisanDigits, now, false)))
        assertEquals(ScreeningVerdict.Rejected(artisanDigits), incoming(artisan, state, at = now + minute, spoofed = true))
    }

    @Test
    fun aNumberFlaggedAsSpoofedGetsNoSecondCallFor5Minutes() {
        val flaggedFirst = blocking(recentRejections = listOf(RecentRejection(artisanDigits, now, spoofed = true)))
        assertEquals(ScreeningVerdict.Rejected(artisanDigits), incoming(artisan, flaggedFirst, at = now + minute))
        val flaggedBetween = blocking(
            recentRejections = listOf(RecentRejection(artisanDigits, now + minute, spoofed = true), RecentRejection(artisanDigits, now, false)),
        )
        assertEquals(ScreeningVerdict.Rejected(artisanDigits), incoming(artisan, flaggedBetween, at = now + 2 * minute))
    }

    @Test
    fun secondCallCanBeTurnedOff() {
        val state = blocking(secondCallEnabled = false, recentRejections = listOf(RecentRejection(artisanDigits, now, false)))
        assertEquals(ScreeningVerdict.Rejected(null), incoming(artisan, state, at = now + minute))
    }

    @Test
    fun theUsersOwnCallWinsOverTheSecondCall() {
        val state = blocking(
            calledNumbers = listOf(CalledNumber(artisan, now - day, true)),
            recentRejections = listOf(RecentRejection(artisanDigits, now, false)),
        )
        assertEquals(ScreeningVerdict.CalledBack(now - day), incoming(artisan, state, at = now + minute))
    }

    // Pause, switch off, allowed numbers and outgoing calls: nothing changes.

    @Test
    fun pausedOrDisabledProtectionRingsWhateverIsRemembered() {
        val state = ScreeningState(
            isBlocking = false,
            calledNumbers = listOf(CalledNumber(artisan, now, true)),
            recentRejections = listOf(RecentRejection(artisanDigits, now, false)),
        )
        assertEquals(ScreeningVerdict.Unfiltered, incoming(artisan, state))
        assertEquals(ScreeningVerdict.Unfiltered, incoming("+44 7700 900123", state))
        assertEquals(ScreeningVerdict.Unfiltered, incoming(null, state))
    }

    @Test
    fun anAllowedNumberRingsWhateverIsRemembered() {
        val state = blocking(allowlist = listOf(AllowedNumber("+33 1 23 45 67 89", "Artisan")))
        assertEquals(ScreeningVerdict.Unfiltered, incoming(artisan, state))
        assertEquals(ScreeningVerdict.Unfiltered, incoming(artisan, state, spoofed = true))
    }

    @Test
    fun callsThatAreNotIncomingGetNoVerdict() {
        val state = blocking(calledNumbers = listOf(CalledNumber(artisan, now, true)))
        assertEquals(ScreeningVerdict.Ignored, screenIncoming(CallDirection.OUTGOING, artisan, false, true, state, now))
        assertEquals(ScreeningVerdict.Ignored, screenIncoming(CallDirection.UNKNOWN, artisan, false, true, state, now))
    }

    // The service: answer first, then remember.

    @Test
    fun theAnswerComesBeforeAnythingIsWritten() {
        val phone = FakePhone(blocking(notify = true, smsReply = true))
        phone.call("06 12 34 56 78", at = now)
        assertEquals(listOf("reject", "journal", "remember rejection", "notify", "sms 06 12 34 56 78"), phone.events)

        phone.events.clear()
        phone.call("06 12 34 56 78", at = now + minute)
        assertEquals(listOf("ring", "journal"), phone.events)
    }

    @Test
    fun aRejectedThenInsistentCallerIsHeardThenRejectedAgain() {
        val phone = FakePhone(blocking())
        assertEquals(ScreeningVerdict.Rejected(artisanDigits), phone.call(artisan, at = now))
        // Second and third calls inside the 5 minutes that follow the rejection.
        assertEquals(ScreeningVerdict.SecondCall(now), phone.call("+33123456789", at = now + 40_000))
        assertEquals(ScreeningVerdict.SecondCall(now), phone.call(artisan, at = now + 5 * minute))
        // Later: rejected again, which opens 5 new minutes.
        val later = now + 5 * minute + 1
        assertEquals(ScreeningVerdict.Rejected(artisanDigits), phone.call(artisan, at = later))
        assertEquals(ScreeningVerdict.SecondCall(later), phone.call(artisan, at = later + minute))

        assertEquals(
            listOf(
                ScreenedCall(artisan, later + minute, CallOutcome.RANG_SECOND_CALL, later),
                ScreenedCall(artisan, later),
                ScreenedCall(artisan, now + 5 * minute, CallOutcome.RANG_SECOND_CALL, now),
                ScreenedCall("+33123456789", now + 40_000, CallOutcome.RANG_SECOND_CALL, now),
                ScreenedCall(artisan, now),
            ),
            phone.journal,
        )
    }

    @Test
    fun aNumberTheUserDialsIsRememberedAndRingsBack() {
        val phone = FakePhone(blocking())
        assertEquals(ScreeningVerdict.Ignored, phone.call(artisan, at = now, direction = CallDirection.OUTGOING))
        // Android expects no answer for an outgoing call, and nothing goes to the journal.
        assertEquals(listOf("remember call"), phone.events)
        assertEquals(ScreeningVerdict.CalledBack(now), phone.call("+33 1 23 45 67 89", at = now + 3 * day))
        assertEquals(listOf(ScreenedCall("+33 1 23 45 67 89", now + 3 * day, CallOutcome.RANG_CALLED_BACK, now)), phone.journal)
    }

    @Test
    fun outgoingCallsAreRememberedEvenWhileProtectionIsPaused() {
        val phone = FakePhone(ScreeningState(isBlocking = false))
        phone.call(artisan, at = now, direction = CallDirection.OUTGOING)
        assertEquals(listOf(CalledNumber(artisan, now, true)), phone.state.calledNumbers)
    }

    @Test
    fun nothingIsRememberedOfAnOutgoingCallWhenTurnedOffOrNotDialable() {
        val off = FakePhone(blocking(calledBackEnabled = false))
        off.call(artisan, at = now, direction = CallDirection.OUTGOING)
        assertEquals(emptyList<String>(), off.events)

        val phone = FakePhone(blocking())
        phone.call(null, at = now, direction = CallDirection.OUTGOING)
        phone.call("15", at = now, direction = CallDirection.OUTGOING)
        phone.call(artisan, at = now, direction = CallDirection.UNKNOWN)
        assertEquals(emptyList<String>(), phone.events)
    }

    @Test
    fun aCallThatRingsUnfilteredLeavesNoTrace() {
        val paused = FakePhone(ScreeningState(isBlocking = false, notify = true, smsReply = true))
        assertEquals(ScreeningVerdict.Unfiltered, paused.call(artisan, at = now))
        assertEquals(listOf("ring"), paused.events)

        val allowed = FakePhone(blocking(allowlist = listOf(AllowedNumber(artisan, "")), notify = true, smsReply = true))
        allowed.call(artisan, at = now)
        assertEquals(listOf("ring"), allowed.events)
    }

    @Test
    fun aSpoofedNumberIsNeverTextedAndBlocksItsSecondCall() {
        val phone = FakePhone(blocking(smsReply = true))
        phone.call("06 12 34 56 78", at = now, spoofed = true)
        assertEquals(listOf("reject", "journal", "remember rejection"), phone.events)
        assertEquals(ScreeningVerdict.Rejected("612345678"), phone.call("06 12 34 56 78", at = now + minute))
    }

    @Test
    fun aRejectionOutsideTheSecondCallRuleRemembersNothing() {
        val phone = FakePhone(blocking())
        phone.call("+44 7700 900123", at = now)
        phone.call(null, at = now)
        assertEquals(listOf("reject", "journal", "reject", "journal"), phone.events)
        assertEquals(emptyList<RecentRejection>(), phone.state.recentRejections)
    }

    @Test
    fun memorySurvivesARestartBetweenTwoCalls() {
        val before = FakePhone(blocking())
        before.call(artisan, at = now)
        before.call("06 12 34 56 78", at = now, direction = CallDirection.OUTGOING)

        // What the store writes is all a new process has: wall-clock times, no running timer.
        val after = FakePhone(
            blocking(
                calledNumbers = RecordCodec.decodeCalledNumbers(RecordCodec.encodeCalledNumbers(before.state.calledNumbers)),
                recentRejections = RecordCodec.decodeRejections(RecordCodec.encodeRejections(before.state.recentRejections)),
            ),
        )
        assertEquals(ScreeningVerdict.SecondCall(now), after.call(artisan, at = now + 2 * minute))
        assertEquals(ScreeningVerdict.CalledBack(now), after.call("+33612345678", at = now + 29 * day))
    }

    @Test
    fun aFullMemoryIsSearchedFarInsideTheScreeningDeadline() {
        val state = blocking(
            allowlist = (1..100).map { AllowedNumber("07%08d".format(it), "n$it") },
            calledNumbers = (1..MAX_CALLED_NUMBERS).map { CalledNumber("+33 6 %08d".format(it), now, true) },
            recentRejections = (1..MAX_RECENT_REJECTIONS).map { RecentRejection("5%08d".format(it), now, false) },
        )
        val startedAt = System.nanoTime()
        // No entry matches: each decision reads the three lists to their end.
        repeat(200) { assertEquals(ScreeningVerdict.Rejected(artisanDigits), incoming(artisan, state)) }
        val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000

        // Android waits 5 seconds for one answer; 200 of them must fit.
        assertTrue("200 decisions took ${elapsedMillis}ms", elapsedMillis < 5_000)
    }

    private fun blocking(
        allowlist: List<AllowedNumber> = emptyList(),
        calledBackEnabled: Boolean = true,
        secondCallEnabled: Boolean = true,
        calledNumbers: List<CalledNumber> = emptyList(),
        recentRejections: List<RecentRejection> = emptyList(),
        notify: Boolean = false,
        smsReply: Boolean = false,
    ) = ScreeningState(true, allowlist, calledBackEnabled, secondCallEnabled, calledNumbers, recentRejections, notify, smsReply)

    private fun incoming(
        number: String?,
        state: ScreeningState,
        at: Long = now,
        spoofed: Boolean = false,
        onFrenchNetwork: Boolean = true,
    ) = screenIncoming(CallDirection.INCOMING, number, spoofed, onFrenchNetwork, state, at)

    /** Stands for the store and for Android: keeps what is remembered and the order of events. */
    private class FakePhone(var state: ScreeningState) : ScreeningMemory, ScreeningEffects {
        val events = mutableListOf<String>()
        val journal = mutableListOf<ScreenedCall>()

        fun call(
            number: String?,
            at: Long,
            direction: CallDirection = CallDirection.INCOMING,
            spoofed: Boolean = false,
        ) = CallScreener(this, this).onCall(direction, number, spoofed, onFrenchNetwork = true, now = at)

        override fun screeningState(now: Long) = state

        override fun rememberCalledNumber(call: CalledNumber) {
            events += "remember call"
            state = state.copy(calledNumbers = state.calledNumbers.withCall(call, call.timeMillis))
        }

        override fun rememberRejection(rejection: RecentRejection) {
            events += "remember rejection"
            state = state.copy(recentRejections = state.recentRejections.withRejection(rejection, rejection.timeMillis))
        }

        override fun addToJournal(call: ScreenedCall) {
            events += "journal"
            journal.add(0, call)
        }

        override fun respond(block: Boolean) {
            events += if (block) "reject" else "ring"
        }

        override fun notifyRejected(number: String?) {
            events += "notify"
        }

        override fun replyBySms(number: String) {
            events += "sms $number"
        }
    }
}
