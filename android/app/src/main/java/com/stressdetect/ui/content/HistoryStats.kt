package com.stressdetect.ui.content

import com.stressdetect.data.CheckInRepository
import com.stressdetect.survey.Pss4
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * The three figures above the history chart.
 *
 * **All three come from stored check-in answers and nothing else.** No phone data, no
 * prediction: this is the one number in the app someone gave us themselves, summarised.
 *
 * Two deliberate restraints:
 *
 *  1. **Nothing appears under three check-ins.** Two points is not a week and the "change"
 *     between them is not a change; showing it would invite reading a trend into the gap
 *     between someone's first two answers. Demo mode is exempt for the same reason the chart
 *     is — every demo check-in lands on today, so the rule would hide it permanently.
 *  2. **The change is a point difference, never a direction.** It is not coloured, not
 *     arrowed, and carries no word like "better": the app has no basis for telling anyone
 *     which way is up on a four-question scale with no published cut-offs.
 */
object HistoryStats {

    /** Under this many check-ins there is nothing worth summarising. */
    const val MIN_CHECK_INS = 3

    /** Seven days ending at the most recent check-in — "this week" as a person means it. */
    private const val WEEK_DAYS = 7L

    data class Stats(
        /** Mean of the last seven days' check-ins, as a percentage of the scale's maximum. */
        val averagePercent: Int,
        /**
         * Percentage POINTS between this week's mean and the week before it. `null` when
         * there were no check-ins in that earlier week — there is no change to report, and
         * zero would claim there was none.
         */
        val changePoints: Int?,
        val total: Int,
    )

    fun build(entries: List<CheckInRepository.Entry>, isDemo: Boolean): Stats? {
        if (entries.isEmpty()) return null
        if (!isDemo && entries.size < MIN_CHECK_INS) return null

        val latest = entries.maxOf { it.takenAt }
        val thisWeek = entries.filter { it.takenAt > latest.minusDays(WEEK_DAYS) }
        val lastWeek = entries.filter {
            it.takenAt > latest.minusDays(WEEK_DAYS * 2) &&
                it.takenAt <= latest.minusDays(WEEK_DAYS)
        }

        val average = meanPercent(thisWeek) ?: return null
        return Stats(
            averagePercent = average,
            // Both sides are rounded BEFORE subtracting so the three figures on screen
            // reconcile: a reader who works out 44 − 31 must get the 13 they are shown.
            changePoints = meanPercent(lastWeek)?.let { average - it },
            total = entries.size,
        )
    }

    private fun meanPercent(entries: List<CheckInRepository.Entry>): Int? {
        if (entries.isEmpty()) return null
        val mean = entries.sumOf { it.score }.toDouble() / entries.size
        return (mean * 100.0 / Pss4.MAX_SCORE).roundToInt()
    }

    /**
     * How many check-ins there have been, in words.
     *
     * This used to render "7 so far, on 1 day(s)." — a placeholder plural, on screen, in an
     * app whose whole voice is that it does not read like a lab tool. Days are counted rather
     * than pluralised because "all today" is what someone actually wants to know when the
     * chart is still hiding.
     */
    fun countSummary(entries: List<CheckInRepository.Entry>, today: LocalDate): String {
        if (entries.isEmpty()) return ""
        if (entries.size == 1) return "One so far."
        val days = entries.map { it.takenAt }.distinct()
        return when {
            days.size == 1 && days.single() == today -> "${entries.size} check-ins, all today"
            days.size == 1 -> "${entries.size} check-ins, all on one day"
            else -> "${entries.size} check-ins across ${days.size} days"
        }
    }

    /** "+13 pts" / "−2 pts" / "No change" — points, because "+13%" would read as a ratio. */
    fun formatChange(points: Int): String = when {
        points > 0 -> "+$points pts"
        // A true minus sign, not a hyphen: "-2" beside a percentage looks like a typo.
        points < 0 -> "−${-points} pts"
        else -> "No change"
    }
}
