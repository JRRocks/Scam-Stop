package com.scamstop.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreeningDecisionTest {
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
}
