package com.closetheloop.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DateTimePickTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun local(d: Int, h: Int, m: Int) = LocalDateTime.of(2026, 10, d, h, m).atZone(zone).toInstant().toEpochMilli()
    private fun utcDay(d: Int) = LocalDate.of(2026, 10, d).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    @Test
    fun combineUsesLocalWallClockOnPickedDay() =
        assertEquals(local(18, 21, 30), DateTimePick.combine(utcDay(18), 21, 30, zone))

    @Test
    fun pickerDateIsLocalDayAtUtcMidnight() {
        // 00:30 IST on the 17th is still the 16th in UTC; the picker must show the 17th.
        assertEquals(utcDay(17), DateTimePick.pickerDate(local(17, 0, 30), zone))
    }

    @Test
    fun onlyTodayOrLaterIsSelectable() {
        val now = local(16, 10, 0)
        assertFalse(DateTimePick.isSelectableDay(utcDay(15), now, zone))
        assertTrue(DateTimePick.isSelectableDay(utcDay(16), now, zone))
        assertTrue(DateTimePick.isSelectableDay(utcDay(20), now, zone))
    }
}
