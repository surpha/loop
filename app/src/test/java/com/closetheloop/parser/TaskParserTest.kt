package com.closetheloop.parser

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskParserTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val now = at(2026, 10, 16, 10, 0) // Friday 10:00

    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int): Long =
        LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toInstant().toEpochMilli()

    private fun parse(text: String) = TaskParser.parse(text, now, zone)

    private fun assertParsed(text: String, title: String, deadline: Long?) {
        val result = parse(text)
        assertEquals("title for '$text'", title, result.title)
        assertEquals("deadline for '$text'", deadline, result.deadline)
    }

    private fun minutes(m: Long) = now + m * 60_000

    // Relative
    @Test fun inMinutes() = assertParsed("Send the report in 45 minutes", "Send the report", minutes(45))
    @Test fun forMin() = assertParsed("Stretch for 30 min", "Stretch", minutes(30))
    @Test fun inHours() = assertParsed("Email Raj in 2 hours", "Email Raj", minutes(120))
    @Test fun decimalHours() = assertParsed("Email Raj in 2.5 hours", "Email Raj", minutes(150))
    @Test fun hourAndAHalf() = assertParsed("Finish slides in an hour and a half", "Finish slides", minutes(90))
    @Test fun twoAndAHalfHours() = assertParsed("Clean garage in two and a half hours", "Clean garage", minutes(150))
    @Test fun hoursAndMinutes() = assertParsed("Book flights in 1 hour 30 minutes", "Book flights", minutes(90))
    @Test fun halfAnHour() = assertParsed("Remind me to pay rent in half an hour", "Pay rent", minutes(30))
    @Test fun wordNumbers() = assertParsed("Write essay in twenty five minutes", "Write essay", minutes(25))
    @Test fun anHour() = assertParsed("Call back in an hour", "Call back", minutes(60))

    // Absolute
    @Test fun byPm() = assertParsed("Call mom by 6 PM", "Call mom", at(2026, 10, 16, 18, 0))
    @Test fun atPmNoSpace() = assertParsed("Call mom at 6pm", "Call mom", at(2026, 10, 16, 18, 0))
    @Test fun dottedMeridiem() = assertParsed("Call dentist at 9 a.m.", "Call dentist", at(2026, 10, 17, 9, 0))
    @Test fun twentyFourHour() = assertParsed("Gym at 18:30", "Gym", at(2026, 10, 16, 18, 30))
    @Test fun bareTimeWithMeridiem() = assertParsed("Meeting 3:30 pm", "Meeting", at(2026, 10, 16, 15, 30))
    @Test fun ambiguousPicksNextOccurrence() = assertParsed("Water plants at 9", "Water plants", at(2026, 10, 16, 21, 0))
    @Test fun noon() = assertParsed("Lunch at noon", "Lunch", at(2026, 10, 16, 12, 0))
    @Test fun midnightRollsToTomorrow() = assertParsed("Submit form by midnight", "Submit form", at(2026, 10, 17, 0, 0))
    @Test fun tomorrowAtMorning() = assertParsed("Standup tomorrow at 9", "Standup", at(2026, 10, 17, 9, 0))
    @Test fun dayAfterTime() = assertParsed("Call him at 5 tomorrow", "Call him", at(2026, 10, 17, 17, 0))
    @Test fun tonightAt() = assertParsed("Watch film tonight at 8", "Watch film", at(2026, 10, 16, 20, 0))
    @Test fun pastTimeRollsToTomorrow() = assertParsed("Take pills at 8 am", "Take pills", at(2026, 10, 17, 8, 0))

    // Day words alone
    @Test fun tonightAlone() = assertParsed("Read tonight", "Read", at(2026, 10, 16, 21, 0))
    @Test fun tomorrowAlone() = assertParsed("Renew passport tomorrow", "Renew passport", at(2026, 10, 17, 9, 0))

    // No time → long-term
    @Test fun noTime() = assertParsed("Buy milk", "Buy milk", null)
    @Test fun noFalsePositiveOnIn() = assertParsed("Join a meeting", "Join a meeting", null)
    @Test fun noFalsePositiveOnHurry() = assertParsed("Finish this in a hurry", "Finish this in a hurry", null)
    @Test fun leadInStrippedForLongTerm() = assertParsed("I need to learn Spanish", "Learn Spanish", null)

    @Test
    fun onlyTimeKeepsTextAsTitle() {
        val result = parse("in 10 minutes")
        assertEquals("In 10 minutes", result.title)
        assertEquals(minutes(10), result.deadline)
    }

    @Test
    fun invalidMinuteIsNotATime() = assertNull(parse("Code at 9:75").deadline)
}
