package com.stressdetect.ui

import com.stressdetect.data.CheckInRepository
import com.stressdetect.ui.content.HistoryStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * The three figures above the history chart.
 *
 * They summarise the one number in this app that came from the person rather than from their
 * phone, so the arithmetic has to be plainly checkable — and the gate that hides them has to
 * hold, because a "change" computed from two check-ins is the exact thing this screen was
 * built not to imply.
 */
class HistoryStatsTest {

    private val today: LocalDate = LocalDate.of(2026, 8, 17)

    private fun entries(vararg daysAgoToScore: Pair<Long, Int>) =
        daysAgoToScore.map { (daysAgo, score) ->
            CheckInRepository.Entry(takenAt = today.minusDays(daysAgo), score = score)
        }

    @Test
    fun `nothing at all until three check-ins`() {
        assertNull(HistoryStats.build(entries(), isDemo = false))
        assertNull(HistoryStats.build(entries(0L to 8), isDemo = false))
        assertNull(HistoryStats.build(entries(0L to 8, 3L to 6), isDemo = false))
    }

    @Test
    fun `demo mode shows its check-ins immediately`() {
        // Every demo check-in lands on today, so the three-check-in rule would hide these
        // permanently — the same exemption the chart already makes.
        val stats = HistoryStats.build(entries(0L to 8, 0L to 4), isDemo = true)
        assertEquals(2, stats!!.total)
    }

    @Test
    fun `the average is the last seven days as a percentage of the maximum`() {
        // 8, 4 and 12 of 16 → mean 8 → 50%.
        val stats = HistoryStats.build(entries(0L to 8, 2L to 4, 5L to 12), isDemo = false)!!
        assertEquals(50, stats.averagePercent)
        assertEquals(3, stats.total)
    }

    @Test
    fun `check-ins older than a week do not drag the weekly average`() {
        val stats = HistoryStats.build(
            entries(0L to 8, 2L to 8, 20L to 0), isDemo = false,
        )!!
        assertEquals(50, stats.averagePercent)
        // ...but they are still check-ins, and the total says so.
        assertEquals(3, stats.total)
    }

    @Test
    fun `the change is a signed point difference against the week before`() {
        // this week mean 8 → 50%; the week before mean 4 → 25%.
        val stats = HistoryStats.build(
            entries(0L to 8, 2L to 8, 8L to 4, 10L to 4), isDemo = false,
        )!!
        assertEquals(50, stats.averagePercent)
        assertEquals(25, stats.changePoints)
        assertEquals("+25 pts", HistoryStats.formatChange(stats.changePoints!!))
    }

    @Test
    fun `an earlier week with no check-ins reports no change rather than zero`() {
        val stats = HistoryStats.build(entries(0L to 8, 1L to 8, 30L to 8), isDemo = false)!!
        assertNull("a gap is not a change of zero", stats.changePoints)
    }

    @Test
    fun `the figures on screen reconcile`() {
        // Whatever the rounding, the change must be the difference of the two rounded
        // percentages — someone who subtracts what they can see must get what they are shown.
        val stats = HistoryStats.build(
            entries(0L to 7, 1L to 8, 8L to 5, 9L to 6), isDemo = false,
        )!!
        val lastWeek = HistoryStats.build(
            entries(0L to 5, 1L to 6, 3L to 5, 4L to 6), isDemo = false,
        )!!.averagePercent
        assertEquals(stats.averagePercent - lastWeek, stats.changePoints)
    }

    @Test
    fun `the change reads as points, and a fall uses a minus sign`() {
        assertEquals("+13 pts", HistoryStats.formatChange(13))
        assertEquals("−2 pts", HistoryStats.formatChange(-2))
        // "+0 pts" is not a thing anyone says.
        assertEquals("No change", HistoryStats.formatChange(0))
    }

    @Test
    fun `the change carries no verdict`() {
        val words = listOf("better", "worse", "improv", "up", "down", "rising", "falling")
        for (points in listOf(-9, -1, 0, 1, 9)) {
            val text = HistoryStats.formatChange(points).lowercase()
            for (word in words) {
                assert(!text.contains(word)) { "'$word' in '$text' — the app cannot say which way is good" }
            }
        }
    }
}
