package com.stressdetect.ui

import com.stressdetect.ui.content.HomeSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * The front door says two things it did not say before, and both are claims: when someone
 * last checked in, and what kind of week their phone has had.
 *
 * The second one matters most. Home compares a week to earlier weeks, which the result screen
 * also does, and the test that earns its keep here is the one showing they agree — the same
 * inputs that draw an up arrow on the result screen must produce "busier than usual" here.
 */
class HomeSummaryTest {

    private val today: LocalDate = LocalDate.of(2026, 8, 18)

    private val week = mapOf(
        "days_with_data" to 7.0,
        "screen_on_fraction" to 0.25,       // 6 hours a day
        "sleep_duration_median" to 7.5,
        "circadian_regularity" to 0.6,
        "call_present" to 0.0,
        "sms_present" to 0.0,
    )

    // ── when ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the last check-in is dated in words`() {
        assertEquals("today", HomeSummary.relativeDay(today, today))
        assertEquals("yesterday", HomeSummary.relativeDay(today.minusDays(1), today))
        assertEquals("3 days ago", HomeSummary.relativeDay(today.minusDays(3), today))
        assertEquals("6 days ago", HomeSummary.relativeDay(today.minusDays(6), today))
        assertEquals("last week", HomeSummary.relativeDay(today.minusDays(7), today))
        assertEquals("last week", HomeSummary.relativeDay(today.minusDays(13), today))
        assertEquals("2 weeks ago", HomeSummary.relativeDay(today.minusDays(14), today))
        assertEquals("4 weeks ago", HomeSummary.relativeDay(today.minusDays(30), today))
    }

    @Test
    fun `a check-in dated in the future still reads as today`() {
        // A timezone change or a clock adjustment can put a stored check-in a few hours
        // ahead. "in -1 days" is not something anyone should ever be shown.
        assertEquals("today", HomeSummary.relativeDay(today.plusDays(1), today))
    }

    // ── the week ────────────────────────────────────────────────────────────────────────

    @Test
    fun `a busier week than usual says so`() {
        val line = HomeSummary.phoneLine(week, week + mapOf("screen_on_fraction" to 0.15))
        assertEquals("Your phone's been busier than usual this week.", line)
    }

    @Test
    fun `a quieter week than usual says so`() {
        val line = HomeSummary.phoneLine(week, week + mapOf("screen_on_fraction" to 0.40))
        assertEquals("Your phone's been quieter than usual this week.", line)
    }

    @Test
    fun `an ordinary week is not dressed up as a change`() {
        assertEquals(
            "Your phone's been about as busy as usual this week.",
            HomeSummary.phoneLine(week, week),
        )
    }

    @Test
    fun `it agrees with the result screen, because it asks the same question`() {
        // The whole reason the line goes through WeekSummary. If Home ever grew its own
        // threshold, this is the test that would catch the two screens drifting apart.
        val prior = week + mapOf("screen_on_fraction" to 0.15)
        val rows = com.stressdetect.ui.content.WeekSummary.build(
            weekValues = week, priorWeekValues = prior,
            dailyValues = emptyMap(), staticValues = emptyMap(),
            usageAccessMissing = false, meetsCoverage = true, daysWithData = 7.0,
        )
        val screenRow = rows.rows.first { it.id == "screen" }
        assertEquals(com.stressdetect.ui.content.WeekSummary.Direction.UP, screenRow.direction)
        assertEquals(
            "Your phone's been busier than usual this week.",
            HomeSummary.phoneLine(week, prior),
        )
    }

    @Test
    fun `with no earlier weeks it states the week instead of comparing it`() {
        val line = HomeSummary.phoneLine(week, emptyMap())
        assertEquals("Your phone's been on about 6 hours a day this week.", line)
    }

    // ── silence ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `a phone that was never read says nothing at all`() {
        assertNull(HomeSummary.phoneLine(emptyMap(), emptyMap()))
    }

    @Test
    fun `a window too thin to summarise says nothing at all`() {
        val thin = week + mapOf("days_with_data" to 2.0)
        assertNull("two days is not a week to describe", HomeSummary.phoneLine(thin, emptyMap()))
    }

    @Test
    fun `a missing screen feature says nothing rather than guessing`() {
        assertNull(HomeSummary.phoneLine(week - "screen_on_fraction", emptyMap()))
        assertNull(HomeSummary.phoneLine(week + mapOf("screen_on_fraction" to Double.NaN), emptyMap()))
    }

    @Test
    fun `the coverage gate is the same one the result screen uses`() {
        val atGate = week + mapOf("days_with_data" to 3.0)
        assertNotNull("three days is the documented minimum", HomeSummary.phoneLine(atGate, emptyMap()))
    }

    // ── copy ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `nothing on the front door compares the person to anyone else`() {
        val population = listOf("average person", "other people", "than most", "percentile", "normal for")
        for (copy in HomeSummary.allCopyStrings()) {
            for (phrase in population) {
                assertFalse("'$phrase' appears in: $copy", copy.lowercase().contains(phrase))
            }
        }
    }

    @Test
    fun `no research or clinical vocabulary reaches the front door`() {
        val banned = listOf(
            "questionnaire", "validated", "model", "spec", "baseline", "feature",
            "diagnos", "symptom", "disorder", "severe", "abnormal", "at risk",
        )
        for (copy in HomeSummary.allCopyStrings()) {
            for (word in banned) {
                assertFalse("'$word' appears in: $copy", copy.lowercase().contains(word))
            }
        }
    }

    @Test
    fun `every duration on the front door names its unit`() {
        val busy = week + mapOf("screen_on_fraction" to 0.2799)   // 6h 43m
        val line = HomeSummary.phoneLine(busy, emptyMap())!!
        assertEquals("Your phone's been on about 6 hours 43 minutes a day this week.", line)
    }
}
