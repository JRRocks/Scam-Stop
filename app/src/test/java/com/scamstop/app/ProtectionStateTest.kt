package com.scamstop.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ProtectionStateTest {
    private val now = 1_700_000_000_000L

    @Test
    fun statusFollowsRoleSwitchAndPause() {
        assertEquals(ProtectionKind.UNAVAILABLE, protectionStatus(RoleState.UNAVAILABLE, true, 0L, now).kind)
        assertEquals(ProtectionKind.NEEDS_ROLE, protectionStatus(RoleState.NOT_ACTIVE, true, 0L, now).kind)
        assertEquals(ProtectionKind.OFF, protectionStatus(RoleState.ACTIVE, false, now + 60_000, now).kind)
        assertEquals(ProtectionKind.ACTIVE, protectionStatus(RoleState.ACTIVE, true, 0L, now).kind)
    }

    @Test
    fun pauseEndsByItself() {
        val until = now + 60_000
        assertEquals(ProtectionStatus(ProtectionKind.PAUSED, until), protectionStatus(RoleState.ACTIVE, true, until, now))
        assertEquals(ProtectionKind.ACTIVE, protectionStatus(RoleState.ACTIVE, true, until, until).kind)
    }

    @Test
    fun fixedPausesAddTheirDuration() {
        val start = ZonedDateTime.of(2026, 10, 27, 14, 32, 0, 0, PARIS)
        assertEquals(start.plusMinutes(15), pauseEnd(PauseOption.MINUTES_15, start))
        assertEquals(start.plusHours(1), pauseEnd(PauseOption.HOUR_1, start))
        assertEquals(start.plusHours(3), pauseEnd(PauseOption.HOURS_3, start))
    }

    @Test
    fun morningPauseEndsAtEightTomorrowDuringTheDay() {
        val start = ZonedDateTime.of(2026, 10, 27, 14, 32, 0, 0, PARIS)
        assertEquals(ZonedDateTime.of(2026, 10, 28, 8, 0, 0, 0, PARIS), pauseEnd(PauseOption.UNTIL_MORNING, start))
    }

    @Test
    fun morningPauseEndsAtEightTodayBeforeDawn() {
        val start = ZonedDateTime.of(2026, 10, 27, 2, 10, 0, 0, PARIS)
        assertEquals(ZonedDateTime.of(2026, 10, 27, 8, 0, 0, 0, PARIS), nextMorning(start))
    }

    @Test
    fun morningPauseAtEightSharpWaitsForTheNextDay() {
        val start = ZonedDateTime.of(2026, 10, 27, 8, 0, 0, 0, PARIS)
        assertEquals(ZonedDateTime.of(2026, 10, 28, 8, 0, 0, 0, PARIS), nextMorning(start))
    }

    @Test
    fun morningPauseHandlesDaylightSavingChange() {
        // Clocks go back on 25 October 2026 in Paris: the night is 25 hours long.
        val start = ZonedDateTime.of(2026, 10, 24, 22, 0, 0, 0, PARIS)
        val end = nextMorning(start)
        assertEquals(8, end.hour)
        assertEquals(25, end.dayOfMonth)
    }

    @Test
    fun setupCountsDoneSteps() {
        val progress = SetupProgress(roleDone = true, notificationsDone = false, greetingDone = true)
        assertEquals(2, progress.doneCount)
        assertFalse(progress.isComplete)
        assertTrue(SetupProgress(roleDone = true, notificationsDone = true, greetingDone = true).isComplete)
    }

    private companion object {
        val PARIS: ZoneId = ZoneId.of("Europe/Paris")
    }
}
