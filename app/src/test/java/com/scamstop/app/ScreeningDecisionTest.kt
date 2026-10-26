package com.scamstop.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreeningDecisionTest {
    @Test
    fun incomingCallIsBlockedWhenEnabled() {
        assertEquals(
            ScreeningAction.BLOCK,
            screeningAction(CallDirection.INCOMING, isBlockingEnabled = true),
        )
    }

    @Test
    fun incomingCallIsAllowedWhenDisabled() {
        assertEquals(
            ScreeningAction.ALLOW,
            screeningAction(CallDirection.INCOMING, isBlockingEnabled = false),
        )
    }

    @Test
    fun outgoingCallIsIgnored() {
        assertEquals(
            ScreeningAction.IGNORE,
            screeningAction(CallDirection.OUTGOING, isBlockingEnabled = true),
        )
    }

    @Test
    fun incomingDecisionCompletesWithinScreeningDeadline() {
        val startedAt = System.nanoTime()
        screeningAction(CallDirection.INCOMING, isBlockingEnabled = true)
        val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000

        assertTrue("Decision took ${elapsedMillis}ms", elapsedMillis < 5_000)
    }
}
