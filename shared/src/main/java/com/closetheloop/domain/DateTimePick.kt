package com.closetheloop.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** Bridges Material date pickers (UTC-midnight millis) and local wall-clock deadlines. */
object DateTimePick {
    /** Deadline for [hour]:[minute] local time on the calendar day the picker reported as [dateUtcMillis]. */
    fun combine(dateUtcMillis: Long, hour: Int, minute: Int, zone: ZoneId): Long =
        Instant.ofEpochMilli(dateUtcMillis).atZone(ZoneOffset.UTC).toLocalDate()
            .atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    /** UTC-midnight millis for the local calendar day containing [epochMillis]; what date pickers expect. */
    fun pickerDate(epochMillis: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    fun isSelectableDay(dateUtcMillis: Long, now: Long, zone: ZoneId): Boolean =
        !Instant.ofEpochMilli(dateUtcMillis).atZone(ZoneOffset.UTC).toLocalDate()
            .isBefore(LocalDate.ofInstant(Instant.ofEpochMilli(now), zone))
}
