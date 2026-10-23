package com.closetheloop.domain

import com.closetheloop.parser.TaskParser
import java.time.ZoneId

/** One-tap deadlines for the phone composer; resolved through TaskParser so rules stay identical to speech. */
enum class QuickTime(val label: String, private val phrase: String?) {
    MIN_15("15 min", "in 15 minutes"),
    MIN_30("30 min", "in 30 minutes"),
    HOUR_1("1 hr", "in 1 hour"),
    HOUR_2("2 hrs", "in 2 hours"),
    TONIGHT("Tonight", "tonight"),
    TOMORROW("Tomorrow", "tomorrow"),
    NO_TIME("No time", null);

    fun deadline(now: Long, zone: ZoneId): Long? = phrase?.let { TaskParser.parse("x $it", now, zone).deadline }
}
