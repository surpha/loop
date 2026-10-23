package com.closetheloop.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object TimeFormat {
    /** "45s", "45m", "2h 30m", "1d 3h"; rounds up so a running timer never shows 0 early. */
    fun remaining(ms: Long): String {
        if (ms <= 0) return "Now"
        if (ms < 60_000) return "${(ms + 999) / 1000}s"
        val totalMin = (ms + 59_999) / 60_000
        val d = totalMin / 1440
        val h = totalMin % 1440 / 60
        val m = totalMin % 60
        return when {
            d > 0 -> if (h > 0) "${d}d ${h}h" else "${d}d"
            h > 0 -> if (m > 0) "${h}h ${m}m" else "${h}h"
            else -> "${m}m"
        }
    }

    /** "6:00 PM", "Tomorrow 9:00 AM", "Sun 9:00 AM" (within a week), "18 Nov 9:00 AM", "17 Oct 2027 5:00 PM". */
    fun clock(deadline: Long, now: Long, zone: ZoneId, is24h: Boolean, locale: Locale = Locale.getDefault()): String {
        val t = Instant.ofEpochMilli(deadline).atZone(zone)
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val date = t.toLocalDate()
        val time = t.format(DateTimeFormatter.ofPattern(if (is24h) "HH:mm" else "h:mm a", locale))
        val day = when {
            date == today -> return time
            date == today.plusDays(1) -> "Tomorrow"
            date.isAfter(today) && date.isBefore(today.plusDays(7)) -> t.format(DateTimeFormatter.ofPattern("EEE", locale))
            date.year == today.year -> t.format(DateTimeFormatter.ofPattern("d MMM", locale))
            else -> t.format(DateTimeFormatter.ofPattern("d MMM yyyy", locale))
        }
        return "$day $time"
    }
}
