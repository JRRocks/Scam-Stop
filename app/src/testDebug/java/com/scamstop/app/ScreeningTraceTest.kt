package com.scamstop.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreeningTraceTest {
    @Test
    fun eventsSurviveStorage() {
        val events = listOf(
            ScreeningTrace.Event(2_000L, CallDirection.OUTGOING, "0123••••89", null, true, ScreeningTrace.Result.OUTGOING_REMEMBERED, 0L),
            ScreeningTrace.Event(1_000L, CallDirection.INCOMING, "(aucun numéro)", 2, false, ScreeningTrace.Result.REJECTED, 12L),
        )
        assertEquals(events, ScreeningTrace.decode(ScreeningTrace.encode(events)))
        assertEquals(emptyList<ScreeningTrace.Event>(), ScreeningTrace.decode(null))
        assertEquals(emptyList<ScreeningTrace.Event>(), ScreeningTrace.decode("garbage\n1\tSIDEWAYS\t\t\t1\tREJECTED\t0"))
    }

    @Test
    fun storedNumbersHideWhoCalled() {
        assertEquals("+336••••••78", ScreeningTrace.mask("+33 6 12 34 56 78"))
        assertEquals("0123••••89", ScreeningTrace.mask("0123456789"))
        assertEquals("••••", ScreeningTrace.mask("3631"))
        assertEquals("(aucun numéro)", ScreeningTrace.mask(null))
    }
}
