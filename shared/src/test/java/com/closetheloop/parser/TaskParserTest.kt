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

    // Trailing durations (no "in"/"for") and seconds
    @Test fun trailingHrs() = assertParsed("Study 4 hrs", "Study", minutes(240))
    @Test fun trailingHoursAndMinutes() = assertParsed("Nap 1 hr 30 mins", "Nap", minutes(90))
    @Test fun trailingWordNumber() = assertParsed("Deep work two hours", "Deep work", minutes(120))
    @Test fun trailingWithPunctuation() = assertParsed("Stretch 10 minutes.", "Stretch", minutes(10))
    @Test fun trailingSeconds() = assertParsed("Plank 90 seconds", "Plank", now + 90_000)
    @Test fun inMinutesAndSeconds() = assertParsed("Tea in 3 minutes 30 seconds", "Tea", now + 210_000)
    @Test fun forSecs() = assertParsed("Hold breath for 45 secs", "Hold breath", now + 45_000)
    @Test fun bareLetterUnitNotTrailing() = assertParsed("Buy 2 m cable", "Buy 2 m cable", null)
    @Test fun midSentenceNumberIsNotDuration() = assertParsed("Read 2 hours worth of notes", "Read 2 hours worth of notes", null)

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

    // Calendar dates
    @Test fun dayMonthWithTime() =
        assertParsed("Book flight tickets by 17th October 8pm", "Book flight tickets", at(2026, 10, 17, 20, 0))
    @Test fun monthDayAtAmbiguousMorning() =
        assertParsed("Book flights on October 20 at 9", "Book flights", at(2026, 10, 20, 9, 0))
    @Test fun theNthOfMonthAfternoonDefault() =
        assertParsed("Dentist on the 3rd of November at 4", "Dentist", at(2026, 11, 3, 16, 0))
    @Test fun dateWithoutTimeDefaultsToNine() =
        assertParsed("Pay rent on 1st November", "Pay rent", at(2026, 11, 1, 9, 0))
    @Test fun timeBeforeDate() = assertParsed("Call Raj at 8pm on 18 Oct", "Call Raj", at(2026, 10, 18, 20, 0))
    @Test fun pastDateRollsToNextYear() = assertParsed("Renew visa by 5 March", "Renew visa", at(2027, 3, 5, 9, 0))
    @Test fun explicitYear() = assertParsed("Submit by Oct 17, 2027 at 5 pm", "Submit", at(2027, 10, 17, 17, 0))
    @Test fun ordinalOnlyThisMonth() = assertParsed("Pay card bill by the 25th", "Pay card bill", at(2026, 10, 25, 9, 0))
    @Test fun ordinalOnlyRollsToNextMonth() = assertParsed("Water filter on the 2nd", "Water filter", at(2026, 11, 2, 9, 0))
    @Test fun invalidDateIgnored() = assertParsed("Party on 31 November", "Party on 31 November", null)
    @Test fun mayAsVerbIsNotADate() = assertParsed("I may call him", "I may call him", null)

    // Weekdays (now = Friday 16 Oct, 10:00)
    @Test fun weekdayWithTime() = assertParsed("Gym on Monday at 7", "Gym", at(2026, 10, 19, 7, 0))
    @Test fun nextWeekdayNoTime() = assertParsed("Call dad next Monday", "Call dad", at(2026, 10, 19, 9, 0))
    @Test fun byWeekdayPm() = assertParsed("Submit report by Wednesday 5pm", "Submit report", at(2026, 10, 21, 17, 0))
    @Test fun todayWeekdayLaterTime() = assertParsed("Team lunch Friday at 1", "Team lunch", at(2026, 10, 16, 13, 0))
    @Test fun todayWeekdayPassedTimeRollsAWeek() =
        assertParsed("Standup Friday at 9am", "Standup", at(2026, 10, 23, 9, 0))
    @Test fun nextSameWeekdayIsNextWeek() = assertParsed("Plan trip next Friday", "Plan trip", at(2026, 10, 23, 9, 0))
    @Test fun thisWeekday() = assertParsed("Dinner this Saturday 8pm", "Dinner", at(2026, 10, 17, 20, 0))
    @Test fun weekdayBeforeDateIsDropped() =
        assertParsed("Flights on Friday 23rd October at 6pm", "Flights", at(2026, 10, 23, 18, 0))

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
