package com.closetheloop.domain

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuickTimeTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun at(d: Int, h: Int, m: Int) = LocalDateTime.of(2026, 10, d, h, m).atZone(zone).toInstant().toEpochMilli()
    private val now = at(16, 10, 0)

    @Test
    fun durations() {
        assertEquals(now + 15 * 60_000, QuickTime.MIN_15.deadline(now, zone))
        assertEquals(now + 2 * 3_600_000, QuickTime.HOUR_2.deadline(now, zone))
    }

    @Test
    fun dayPresets() {
        assertEquals(at(16, 21, 0), QuickTime.TONIGHT.deadline(now, zone))
        assertEquals(at(17, 9, 0), QuickTime.TOMORROW.deadline(now, zone))
    }

    @Test
    fun noTimeIsLongTerm() = assertNull(QuickTime.NO_TIME.deadline(now, zone))
}
