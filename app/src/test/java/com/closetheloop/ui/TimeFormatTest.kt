package com.closetheloop.ui

import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFormatTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun at(d: Int, h: Int, m: Int) = LocalDateTime.of(2026, 10, d, h, m).atZone(zone).toInstant().toEpochMilli()
    private val now = at(16, 10, 0)

    @Test
    fun remaining() {
        assertEquals("Now", TimeFormat.remaining(0))
        assertEquals("1s", TimeFormat.remaining(1))
        assertEquals("45s", TimeFormat.remaining(45_000))
        assertEquals("1m", TimeFormat.remaining(60_000))
        assertEquals("2m", TimeFormat.remaining(60_001))
        assertEquals("45m", TimeFormat.remaining(45 * 60_000L))
        assertEquals("2h", TimeFormat.remaining(120 * 60_000L))
        assertEquals("2h 30m", TimeFormat.remaining(150 * 60_000L))
        assertEquals("1d 3h", TimeFormat.remaining((27 * 60) * 60_000L))
    }

    @Test
    fun clock() {
        assertEquals("6:00 PM", TimeFormat.clock(at(16, 18, 0), now, zone, false, Locale.US))
        assertEquals("18:00", TimeFormat.clock(at(16, 18, 0), now, zone, true, Locale.US))
        assertEquals("Tomorrow 9:00 AM", TimeFormat.clock(at(17, 9, 0), now, zone, false, Locale.US))
        assertEquals("Sun 9:00 AM", TimeFormat.clock(at(18, 9, 0), now, zone, false, Locale.US))
    }
}
