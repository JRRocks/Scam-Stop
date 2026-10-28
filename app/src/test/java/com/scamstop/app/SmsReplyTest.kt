package com.scamstop.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsReplyTest {
    private val hour = 60 * 60 * 1000L
    private val now = 1_000_000_000L

    @Test
    fun everyFormOfAFrenchMobileGivesTheSameKey() {
        listOf("0612345678", "06 12 34 56 78", "06.12.34.56.78", "+33612345678", "+33 6 12 34 56 78", "0033612345678", "+33 (0)6 12 34 56 78")
            .forEach { assertEquals(it, "612345678", frenchMobileKey(it)) }
        assertEquals("712345678", frenchMobileKey("07 12 34 56 78"))
    }

    @Test
    fun numbersThatMayCostMoneyHaveNoKey() {
        listOf(
            "0145678901", // landline
            "0899123456", // premium rate
            "36179", // short number
            "+447700900123", // foreign mobile
            "+262692123456", // overseas department
            "061234567", // one digit short
            "",
        ).forEach { assertNull(it, frenchMobileKey(it)) }
        assertNull(frenchMobileKey(null))
    }

    @Test
    fun newFrenchMobileGetsAReply() {
        assertTrue(canReplyBySms("612345678", roamingAbroad = false, sent = emptyMap(), now = now))
    }

    @Test
    fun noReplyAbroadOrToOtherNumbers() {
        assertFalse(canReplyBySms("612345678", roamingAbroad = true, sent = emptyMap(), now = now))
        assertFalse(canReplyBySms(null, roamingAbroad = false, sent = emptyMap(), now = now))
    }

    @Test
    fun aNumberIsTextedOnlyOnce() {
        val sent = mapOf("612345678" to now - 400 * 24 * hour)
        assertFalse(canReplyBySms("612345678", roamingAbroad = false, sent = sent, now = now))
    }

    @Test
    fun dailyLimitCountsTheLast24Hours() {
        val full = (1..SMS_REPLY_DAILY_LIMIT).associate { "60000000$it" to now - it * hour }
        assertFalse(canReplyBySms("612345678", roamingAbroad = false, sent = full, now = now))
        assertTrue(canReplyBySms("612345678", roamingAbroad = false, sent = full, now = now + 24 * hour))
    }

    @Test
    fun sentRepliesSurviveStorage() {
        val sent = mapOf("612345678" to 123L, "712345678" to 456L)
        assertEquals(sent, RecordCodec.decodeSmsReplies(RecordCodec.encodeSmsReplies(sent)))
        assertEquals(emptyMap<String, Long>(), RecordCodec.decodeSmsReplies(null))
    }
}
