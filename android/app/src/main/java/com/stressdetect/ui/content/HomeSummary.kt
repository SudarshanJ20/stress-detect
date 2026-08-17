package com.stressdetect.ui.content

import com.stressdetect.features.SpecConstants
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * The two pieces of writing on the front door: when the last check-in was, and one line about
 * the week the phone has had.
 *
 * The phone line goes through [WeekSummary] rather than comparing anything itself. Home and
 * the result screen would otherwise each own a threshold for "busier than usual", and the day
 * they disagreed nobody would notice — both screens would still look right, they would simply
 * mean different things by the same word.
 */
object HomeSummary {

    /** "today" / "yesterday" / "3 days ago" / "2 weeks ago". */
    fun relativeDay(date: LocalDate, today: LocalDate): String {
        val days = ChronoUnit.DAYS.between(date, today)
        return when {
            days <= 0L -> "today"
            days == 1L -> "yesterday"
            days < 7L -> "$days days ago"
            days < 14L -> "last week"
            else -> "${days / 7} weeks ago"
        }
    }

    /**
     * One line about the week, or `null` when there is nothing honest to say.
     *
     * Null rather than a placeholder: an empty front door is better than one that says "no
     * data yet" in the space where a fact should be. It returns null when the phone was never
     * read, when the window is too thin to summarise, and when the feature itself is missing.
     */
    fun phoneLine(weekValues: Map<String, Double>, priorValues: Map<String, Double>): String? {
        val days = weekValues["days_with_data"]?.takeIf { !it.isNaN() } ?: return null
        if (days < SpecConstants.COVERAGE_MIN_DAYS) return null

        val summary = WeekSummary.build(
            weekValues = weekValues,
            priorWeekValues = priorValues,
            dailyValues = emptyMap(),
            staticValues = emptyMap(),
            usageAccessMissing = false,
            meetsCoverage = true,
            daysWithData = days,
        )
        val screen = summary.rows.firstOrNull { it.id == "screen" } ?: return null

        return when (screen.direction) {
            WeekSummary.Direction.UP -> "Your phone's been busier than usual this week."
            WeekSummary.Direction.DOWN -> "Your phone's been quieter than usual this week."
            WeekSummary.Direction.LEVEL -> "Your phone's been about as busy as usual this week."
            // No earlier weeks yet, so there is no "usual" — the value is what can be said.
            null -> weekValues["screen_on_fraction"]?.takeIf { !it.isNaN() }?.let {
                "Your phone's been on about ${Observations.duration(it * 24)} a day this week."
            }
        }
    }

    /** Exposed for the copy test: everything a user can read from this file. */
    internal fun allCopyStrings(): List<String> {
        val week = mapOf(
            "days_with_data" to 7.0,
            "screen_on_fraction" to 0.25,
            "sleep_duration_median" to 7.5,
            "circadian_regularity" to 0.6,
            "call_present" to 0.0,
            "sms_present" to 0.0,
        )
        val priors = listOf(
            emptyMap(),
            week,
            week + mapOf("screen_on_fraction" to 0.10),
            week + mapOf("screen_on_fraction" to 0.50),
        )
        val lines = priors.mapNotNull { phoneLine(week, it) }
        val days = listOf(0L, 1L, 3L, 9L, 30L).map {
            relativeDay(LocalDate.of(2026, 8, 18).minusDays(it), LocalDate.of(2026, 8, 18))
        }
        return lines + days
    }
}
