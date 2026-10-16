package com.closetheloop.parser

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.Period
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime

data class ParsedTask(val title: String, val deadline: Long?)

/** Offline, rule-based parser: "Call mom by 6 PM" → ParsedTask("Call mom", <18:00 today>). */
object TaskParser {
    private val UNITS = listOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
        "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen",
    )
    private val TENS = listOf("twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety")

    private val NUM = "(?:(?:${TENS.joinToString("|")})(?:[- ](?:${UNITS.subList(1, 10).joinToString("|")}))?" +
        "|${UNITS.reversed().joinToString("|")}|\\d+(?:\\.\\d+)?|an?)"
    private const val HOURS = """(?:hours?|hrs?|h)\b"""
    private const val MINS = """(?:minutes?|mins?|m)\b"""
    private const val DAY = "(tomorrow|today|tonight)"
    private val HOUR_NUM = "(\\d{1,2}|${UNITS.subList(1, 13).reversed().joinToString("|")})"
    private val HOUR = "(\\d{1,2}|noon|midnight|${UNITS.subList(1, 13).reversed().joinToString("|")})"
    private const val AMPM = """(a\.?\s?m\.?|p\.?\s?m\.?)"""
    private val IC = RegexOption.IGNORE_CASE

    // Groups: 1 half-hour, 2 hours, 3 "and a half" (before unit), 4 "and a half" (after unit), 5 extra minutes, 6 minutes only.
    private val RELATIVE = Regex(
        """\b(?:in|for|within|after)\s+(?:(half\s+an?\s+hour)\b|($NUM)\s*(and\s+a\s+half\s+)?$HOURS(\s+and\s+a\s+half\b)?(?:\s*,?\s*(?:and\s+)?($NUM)\s*$MINS)?|($NUM)\s*$MINS)""",
        IC,
    )

    // Groups: 1 day before, 2 hour, 3 minute, 4 am/pm, 5 day after.
    private val ABSOLUTE = Regex(
        """(?:\b$DAY\s+)?\b(?:by|at|before|until|till|around)\s+$HOUR(?::(\d{2}))?(?:\s*$AMPM)?(?![\w:])(?:\s+$DAY\b)?""",
        IC,
    )
    private val ABSOLUTE_BARE = Regex(
        """(?:\b$DAY\s+)?\b$HOUR_NUM(?::(\d{2}))?\s*$AMPM(?!\w)(?:\s+$DAY\b)?""",
        IC,
    )
    private val DAY_ONLY = Regex("""\b(tonight|tomorrow)\b""", IC)

    private val MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
    private const val MONTH =
        "jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|june?|july?|aug(?:ust)?|sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?"
    private const val DATE_PREP = """(?:(?:on|by|before|until|till|due)\s+)"""
    private const val ORD = "(?:st|nd|rd|th)"

    // "17th October", "the 3rd of Nov 2027". Groups: 1 day, 2 month, 3 year.
    private val DATE_DAY_MONTH = Regex(
        """\b$DATE_PREP?(?:the\s+)?(\d{1,2})$ORD?(?:\s+of)?\s+($MONTH)\b(?:,?\s+(\d{4}))?""",
        IC,
    )
    // "October 17", "Oct 17th, 2027". Groups: 1 month, 2 day, 3 year.
    private val DATE_MONTH_DAY = Regex(
        """\b$DATE_PREP?($MONTH)\s+(?:the\s+)?(\d{1,2})$ORD?\b(?:,?\s+(\d{4}))?""",
        IC,
    )
    // "by the 25th": next occurrence of that day of month. Group 1 day.
    private val DATE_ORDINAL = Regex("""\b$DATE_PREP(?:the\s+)(\d{1,2})$ORD\b""", IC)

    /** [roll] is added when the resolved time is already past (null = explicit year, never roll). */
    private data class DateMatch(val date: LocalDate, val range: IntRange, val roll: Period?)

    private val LEAD_IN = Regex(
        """^(?:(?:please\s+)?remind\s+me\s+to|i\s+(?:need|have|want)\s+to|i\s+(?:should|must)|don'?t\s+forget\s+to|to)\s+""",
        IC,
    )
    private val DANGLING = Regex("""\s+(?:by|at|in|for|before|until|till|around)$""", IC)
    private val TRIM_CHARS = charArrayOf(' ', ',', '.', '!', '?', ';', ':', '-')

    fun parse(text: String, now: Long, zone: ZoneId): ParsedTask {
        val nowZ = Instant.ofEpochMilli(now).atZone(zone)

        RELATIVE.find(text)?.let { m ->
            val minutes = relativeMinutes(m)
            if (minutes != null && minutes > 0) {
                return ParsedTask(title(text, m.range), now + (minutes * 60_000).toLong())
            }
        }
        findDate(text, nowZ.toLocalDate())?.let { d ->
            val rest = text.replaceRange(d.range, " ")
            var title = title(rest, null)
            var time = at(d.date, 9, 0, zone)
            for (regex in listOf(ABSOLUTE, ABSOLUTE_BARE)) {
                val m = regex.find(rest) ?: continue
                time = clockOnDate(m, d.date, zone) ?: continue
                title = title(rest, m.range)
                break
            }
            if (!time.isAfter(nowZ) && d.roll != null) time = time.plus(d.roll)
            return ParsedTask(title, time.toInstant().toEpochMilli())
        }
        for (regex in listOf(ABSOLUTE, ABSOLUTE_BARE)) {
            regex.find(text)?.let { m ->
                resolveClock(m, nowZ)?.let { return ParsedTask(title(text, m.range), it.toInstant().toEpochMilli()) }
            }
        }
        DAY_ONLY.find(text)?.let { m ->
            return ParsedTask(title(text, m.range), resolveDayOnly(m.value.lowercase(), nowZ).toInstant().toEpochMilli())
        }
        return ParsedTask(title(text, null), null)
    }

    private fun relativeMinutes(m: MatchResult): Double? {
        val g = m.groupValues
        if (g[1].isNotEmpty()) return 30.0
        if (g[2].isNotEmpty()) {
            var total = (number(g[2]) ?: return null) * 60
            if (g[3].isNotEmpty() || g[4].isNotEmpty()) total += 30
            if (g[5].isNotEmpty()) total += number(g[5]) ?: return null
            return total
        }
        return number(g[6])
    }

    private fun resolveClock(m: MatchResult, now: ZonedDateTime): ZonedDateTime? {
        val g = m.groupValues
        val day = g[1].ifEmpty { g[5] }.lowercase()
        val token = g[2].lowercase()
        val minute = g[3].toIntOrNull() ?: 0
        val meridiem = g[4].lowercase().firstOrNull()
        val hour = hourOf(token)
        if (hour !in 0..23 || minute !in 0..59) return null
        if (meridiem != null && hour > 12) return null

        val today = now.toLocalDate()
        val explicitHour: Int? = when {
            token == "noon" || token == "midnight" -> hour
            meridiem == 'p' -> hour % 12 + 12
            meridiem == 'a' -> hour % 12
            hour == 0 || hour > 12 -> hour
            day == "tonight" -> hour % 12 + 12
            // "tomorrow at 3" most likely means the afternoon; "tomorrow at 9" the morning.
            day == "tomorrow" -> if (hour in 1..6) hour + 12 else hour
            else -> null
        }

        if (explicitHour == null) {
            // Ambiguous "at 9" with no day: pick the soonest upcoming 9:00 or 21:00.
            val base = hour % 12
            return listOf(
                at(today, base, minute, now.zone),
                at(today, base + 12, minute, now.zone),
                at(today.plusDays(1), base, minute, now.zone),
            ).first { it.isAfter(now) }
        }

        val date = if (day == "tomorrow") today.plusDays(1) else today
        val candidate = at(date, explicitHour, minute, now.zone)
        return if (day != "tomorrow" && !candidate.isAfter(now)) candidate.plusDays(1) else candidate
    }

    private fun resolveDayOnly(day: String, now: ZonedDateTime): ZonedDateTime {
        val today = now.toLocalDate()
        if (day == "tomorrow") return at(today.plusDays(1), 9, 0, now.zone)
        val tonight = at(today, 21, 0, now.zone)
        return if (tonight.isAfter(now)) tonight else at(today, 23, 59, now.zone)
    }

    private fun at(date: LocalDate, hour: Int, minute: Int, zone: ZoneId): ZonedDateTime =
        ZonedDateTime.of(date, LocalTime.of(hour, minute), zone)

    private fun hourOf(token: String): Int = when (token) {
        "noon" -> 12
        "midnight" -> 0
        else -> token.toIntOrNull() ?: UNITS.indexOf(token)
    }

    private fun findDate(text: String, today: LocalDate): DateMatch? {
        DATE_DAY_MONTH.find(text)?.let { m ->
            val (day, month, year) = m.destructured
            monthDate(day, month, year, m.range, today)?.let { return it }
        }
        DATE_MONTH_DAY.find(text)?.let { m ->
            val (month, day, year) = m.destructured
            monthDate(day, month, year, m.range, today)?.let { return it }
        }
        DATE_ORDINAL.find(text)?.let { m ->
            val day = m.groupValues[1].toInt()
            val thisMonth = YearMonth.from(today)
            for (i in 0..12) {
                val ym = thisMonth.plusMonths(i.toLong())
                if (day !in 1..ym.lengthOfMonth()) continue
                val date = ym.atDay(day)
                if (!date.isBefore(today)) return DateMatch(date, m.range, Period.ofMonths(1))
            }
        }
        return null
    }

    private fun monthDate(day: String, month: String, year: String, range: IntRange, today: LocalDate): DateMatch? {
        val monthNumber = MONTHS.indexOfFirst { month.lowercase().startsWith(it) } + 1
        val explicitYear = year.toIntOrNull()
        val date = runCatching { LocalDate.of(explicitYear ?: today.year, monthNumber, day.toInt()) }.getOrNull()
            ?: return null
        if (explicitYear != null) return DateMatch(date, range, null)
        return DateMatch(if (date.isBefore(today)) date.plusYears(1) else date, range, Period.ofYears(1))
    }

    /** A clock time on a known date: unmarked 1–6 means PM ("on the 3rd at 4"). */
    private fun clockOnDate(m: MatchResult, date: LocalDate, zone: ZoneId): ZonedDateTime? {
        val g = m.groupValues
        val token = g[2].lowercase()
        val minute = g[3].toIntOrNull() ?: 0
        val meridiem = g[4].lowercase().firstOrNull()
        val hour = hourOf(token)
        if (hour !in 0..23 || minute !in 0..59 || (meridiem != null && hour > 12)) return null
        val h = when {
            token == "noon" || token == "midnight" -> hour
            meridiem == 'p' -> hour % 12 + 12
            meridiem == 'a' -> hour % 12
            hour in 1..6 -> hour + 12
            else -> hour
        }
        return at(date, h, minute, zone)
    }

    private fun number(token: String): Double? {
        val t = token.lowercase().trim()
        t.toDoubleOrNull()?.let { return it }
        if (t == "a" || t == "an") return 1.0
        UNITS.indexOf(t).takeIf { it >= 0 }?.let { return it.toDouble() }
        val parts = t.split('-', ' ')
        val tens = TENS.indexOf(parts[0]).takeIf { it >= 0 }?.let { (it + 2) * 10 } ?: return null
        val unit = parts.getOrNull(1)?.let { u -> UNITS.indexOf(u).takeIf { it in 1..9 } ?: return null } ?: 0
        return (tens + unit).toDouble()
    }

    private fun title(text: String, timeRange: IntRange?): String {
        val withoutTime = if (timeRange == null) text else text.removeRange(timeRange)
        val cleaned = withoutTime
            .replace(Regex("\\s+"), " ")
            .trim(*TRIM_CHARS)
            .replace(LEAD_IN, "")
            .replace(DANGLING, "")
            .trim(*TRIM_CHARS)
        return cleaned.ifEmpty { text.trim() }.replaceFirstChar { it.uppercase() }
    }
}
