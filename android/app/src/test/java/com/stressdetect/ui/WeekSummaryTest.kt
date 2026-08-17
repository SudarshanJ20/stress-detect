package com.stressdetect.ui

import com.stressdetect.features.FeatureExtractor
import com.stressdetect.features.ParityFixture
import com.stressdetect.ui.content.WeekSummary
import com.stressdetect.ui.content.WeekSummary.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/**
 * The rows are the app's only week-on-week claim about someone, so what is tested here is
 * whether each claim is ENTAILED by its inputs — the same standard `ObservationsTest` holds
 * the sentences to.
 *
 * The claim that matters most is the one about whose weeks are being compared. Every
 * assertion below feeds the person's own earlier weeks and nothing else, because there is
 * nothing else: this app has never seen another person's data.
 */
class WeekSummaryTest {

    private val week = mapOf(
        "screen_on_fraction" to 0.25,       // 6 hours a day
        "sleep_duration_median" to 7.5,
        "circadian_regularity" to 0.62,
        "call_count_per_day" to 2.0,
        "sms_count_per_day" to 3.0,
        "call_present" to 1.0,
        "sms_present" to 1.0,
    )

    private fun build(
        current: Map<String, Double> = week,
        prior: Map<String, Double> = emptyMap(),
    ) = WeekSummary.build(
        weekValues = current,
        priorWeekValues = prior,
        dailyValues = emptyMap(),
        staticValues = emptyMap(),
        usageAccessMissing = false,
        meetsCoverage = true,
        daysWithData = 7.0,
    )

    private fun row(result: WeekSummary.Result, id: String) =
        result.rows.firstOrNull { it.id == id }

    // ── the first week: no comparison exists, so none is made ───────────────────────────

    @Test
    fun `with no earlier weeks there is no direction at all`() {
        val result = build()
        assertEquals(4, result.rows.size)
        for (row in result.rows) {
            assertNull("${row.id} invented a direction from one week", row.direction)
        }
    }

    @Test
    fun `with no earlier weeks the rows say what the values were`() {
        val result = build()
        assertEquals("Around 6 hours a day", row(result, "screen")!!.phrase)
        assertEquals("About 7 and a half hours a night", row(result, "rest")!!.phrase)
        assertEquals("About 5 a day", row(result, "comms")!!.phrase)
        // Regularity has no unit anyone would recognise, so the level is described instead.
        assertEquals("Fairly steady", row(result, "rhythm")!!.phrase)
    }

    @Test
    fun `a week of unlike days is not called steady`() {
        val result = build(current = week + mapOf("circadian_regularity" to 0.1))
        assertEquals("Varied day to day", row(result, "rhythm")!!.phrase)
    }

    // ── direction ───────────────────────────────────────────────────────────────────────

    @Test
    fun `more screen time than usual points up`() {
        val result = build(prior = week + mapOf("screen_on_fraction" to 0.20))
        assertEquals(Direction.UP, row(result, "screen")!!.direction)
    }

    @Test
    fun `less screen time than usual points down`() {
        val result = build(prior = week + mapOf("screen_on_fraction" to 0.35))
        assertEquals(Direction.DOWN, row(result, "screen")!!.direction)
    }

    @Test
    fun `a difference inside the deadband is level, not a change`() {
        // 5% under usual: half an hour on a six-hour day. Without a deadband this would draw
        // an arrow, and the arrows would flicker week to week on nothing.
        val result = build(prior = week + mapOf("screen_on_fraction" to 0.2625))
        assertEquals(Direction.LEVEL, row(result, "screen")!!.direction)
        assertEquals("Around your normal range", row(result, "screen")!!.phrase)
    }

    @Test
    fun `a change too big to be slight is not called slight`() {
        val doubled = build(prior = week + mapOf("screen_on_fraction" to 0.10))
        assertEquals("More than your usual", row(doubled, "screen")!!.phrase)

        val nudge = build(prior = week + mapOf("screen_on_fraction" to 0.21))
        assertEquals("Slightly more than your usual", row(nudge, "screen")!!.phrase)
    }

    @Test
    fun `rest moves in hours, not in percent`() {
        // 20 minutes is an ordinary night; an hour and a half is not.
        val ordinary = build(prior = week + mapOf("sleep_duration_median" to 7.17))
        assertEquals(Direction.LEVEL, row(ordinary, "rest")!!.direction)

        val shorter = build(prior = week + mapOf("sleep_duration_median" to 9.0))
        assertEquals(Direction.DOWN, row(shorter, "rest")!!.direction)
        assertEquals("Shorter than your usual", row(shorter, "rest")!!.phrase)
    }

    @Test
    fun `contact needs to move by more than a call a day on small volumes`() {
        // 15% of five a day is 0.75 — under the floor, so this is still an ordinary week.
        val ordinary = build(prior = week + mapOf("call_count_per_day" to 2.9))
        assertEquals(Direction.LEVEL, row(ordinary, "comms")!!.direction)

        val quieter = build(prior = week + mapOf("sms_count_per_day" to 5.0))
        assertEquals(Direction.DOWN, row(quieter, "comms")!!.direction)
        assertEquals("Quieter than your usual", row(quieter, "comms")!!.phrase)
    }

    @Test
    fun `an unchanged rhythm describes the level rather than claiming steadiness`() {
        val varied = week + mapOf("circadian_regularity" to 0.2)
        val result = build(current = varied, prior = varied)
        assertEquals(Direction.LEVEL, row(result, "rhythm")!!.direction)
        // "Fairly steady" for a week of consistently unlike days would simply be false.
        assertEquals("About as varied as usual", row(result, "rhythm")!!.phrase)
    }

    // ── absence ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `a missing feature drops its row instead of reading zero`() {
        val result = build(current = week - "sleep_duration_median")
        assertNull("a rest row appeared for a week with no nights in it", row(result, "rest"))
        assertEquals(3, result.rows.size)
    }

    @Test
    fun `NaN is missing, not a measurement`() {
        val result = build(current = week + mapOf("screen_on_fraction" to Double.NaN))
        assertNull(row(result, "screen"))
    }

    @Test
    fun `an unread call log is not a quiet week`() {
        val declined = week + mapOf("call_present" to 0.0, "sms_present" to 0.0)
        assertNull("a comms row appeared for permissions we never had", row(build(declined), "comms"))
    }

    @Test
    fun `a missing earlier value leaves that row without a direction`() {
        // The other rows still compare — one gap must not silence the whole section.
        val result = build(prior = mapOf("screen_on_fraction" to 0.20))
        assertEquals(Direction.UP, row(result, "screen")!!.direction)
        assertNull(row(result, "rest")!!.direction)
    }

    @Test
    fun `missing usage access explains itself instead of showing rows`() {
        val result = WeekSummary.build(
            emptyMap(), emptyMap(), emptyMap(), emptyMap(),
            usageAccessMissing = true, meetsCoverage = false, daysWithData = 0.0,
        )
        assertTrue(result.rows.isEmpty())
        assertNotNull(result.unavailableReason)
        assertTrue(result.unavailableReason!!.contains("works either way"))
    }

    /**
     * The reported symptom on the device was "nothing appears at all". Whatever the state of
     * the phone, this section owes the reader either rows or a reason — a heading with empty
     * space under it is the one outcome that tells them nothing and looks broken.
     */
    @Test
    fun `there is never a heading with nothing under it`() {
        val states = listOf(
            // usageAccessMissing, meetsCoverage, days, values
            Triple(true, false, 0.0) to emptyMap<String, Double>(),
            Triple(false, false, 2.0) to week,
            Triple(false, true, 7.0) to emptyMap(),                       // granted, no features
            Triple(false, true, 7.0) to week.mapValues { Double.NaN },    // granted, all missing
            Triple(false, true, 7.0) to week,                             // the ordinary case
        )
        for ((flags, values) in states) {
            val (missing, coverage, days) = flags
            val result = WeekSummary.build(
                weekValues = values,
                priorWeekValues = emptyMap(),
                dailyValues = emptyMap(),
                staticValues = emptyMap(),
                usageAccessMissing = missing,
                meetsCoverage = coverage,
                daysWithData = days,
            )
            assertTrue(
                "missing=$missing coverage=$coverage values=${values.size} produced neither " +
                    "a row nor a reason — the section would render as an empty heading",
                result.rows.isNotEmpty() || !result.unavailableReason.isNullOrBlank(),
            )
        }
    }

    @Test
    fun `thin coverage says so in days`() {
        val result = WeekSummary.build(
            week, emptyMap(), emptyMap(), emptyMap(),
            usageAccessMissing = false, meetsCoverage = false, daysWithData = 2.0,
        )
        assertTrue(result.rows.isEmpty())
        assertTrue(result.unavailableReason!!.contains("2 day"))
    }

    // ── suggestions ─────────────────────────────────────────────────────────────────────

    @Test
    fun `each row carries the suggestion tied to its own topic`() {
        val result = WeekSummary.build(
            weekValues = week,
            priorWeekValues = emptyMap(),
            dailyValues = mapOf(
                "screen_on_fraction" to List(7) { 0.30 },
                "unlock_count" to List(7) { 70.0 },
            ),
            staticValues = mapOf(
                "sleep_duration_median" to 5.0,
                "circadian_regularity" to 0.1,
            ),
            usageAccessMissing = false, meetsCoverage = true, daysWithData = 7.0,
        )
        assertTrue(row(result, "screen")!!.suggestion!!.contains("app"))
        assertTrue(row(result, "rest")!!.suggestion!!.contains("bed"))
        // Nothing useful to suggest about how many calls someone got, so nothing is said.
        assertNull(row(result, "comms")!!.suggestion)
    }

    @Test
    fun `at most two rows carry advice, and the ones that moved get it first`() {
        val daily = mapOf(
            "screen_on_fraction" to List(7) { 0.30 },
            "unlock_count" to List(7) { 70.0 },
        )
        val static = mapOf("sleep_duration_median" to 5.0, "circadian_regularity" to 0.1)
        val result = WeekSummary.build(
            weekValues = week,
            // Only the rhythm row moves; the other two are steady on their usual.
            priorWeekValues = week + mapOf("circadian_regularity" to 0.2),
            dailyValues = daily,
            staticValues = static,
            usageAccessMissing = false, meetsCoverage = true, daysWithData = 7.0,
        )
        val withAdvice = result.rows.filter { it.suggestion != null }.map { it.id }
        assertTrue("four rows of advice is an advice column", withAdvice.size <= 2)
        assertTrue(
            "the row that actually moved lost its suggestion to a row that did not: $withAdvice",
            "rhythm" in withAdvice,
        )
    }

    // ── copy ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `no row compares the person to anybody else`() {
        val population = listOf(
            "average person", "other people", "than most", "than average", "percentile",
            "compared to people", "typical person", "population",
        )
        for (copy in WeekSummary.allCopyStrings()) {
            for (phrase in population) {
                assertFalse("'$phrase' appears in: $copy", copy.lowercase().contains(phrase))
            }
        }
    }

    @Test
    fun `no row uses research or clinical vocabulary`() {
        val banned = listOf(
            "questionnaire", "validated", "model", "attribution", "spec", "construct",
            "diagnos", "clinical", "symptom", "disorder", "treat", "therapy", "baseline",
            "feature", "correlat", "severe", "abnormal", "risk", "critical",
        )
        for (copy in WeekSummary.allCopyStrings()) {
            for (word in banned) {
                assertFalse("'$word' appears in: $copy", copy.lowercase().contains(word))
            }
        }
    }

    // ── the demo actually demonstrates it ───────────────────────────────────────────────

    /**
     * The reason the two prior weeks were added to the fixture: without them a viva demo
     * shows four values and no directions, and the comparison — the thing being demonstrated
     * — is invisible. This pins that the fixture still produces one of each.
     */
    @Test
    fun `the demo fixture produces a direction on every row`() {
        val trace = ParityFixture.load()
        val zone = ZoneId.of(trace.parityTimezone)
        fun values(name: String): Map<String, Double> {
            val case = trace.cases.first { it.name == name }
            return FeatureExtractor.extract(
                locked = case.lockedIntervals,
                callTimestamps = case.calls,
                smsTimestamps = case.sms,
                labelDate = case.labelDate,
                zone = zone,
                hasCalls = case.hasCalls,
                hasSms = case.hasSms,
            ).values
        }

        val current = values("demo_week")
        val prior = listOf(values("demo_prior_week_1"), values("demo_prior_week_2"))
        val usual = prior.first().keys.associateWith { name ->
            prior.mapNotNull { it[name]?.takeIf { value -> !value.isNaN() } }.average()
        }

        val result = WeekSummary.build(
            weekValues = current, priorWeekValues = usual,
            dailyValues = emptyMap(), staticValues = emptyMap(),
            usageAccessMissing = false, meetsCoverage = true, daysWithData = 7.0,
        )

        assertEquals(4, result.rows.size)
        assertEquals(Direction.UP, row(result, "screen")!!.direction)
        assertEquals(Direction.LEVEL, row(result, "rest")!!.direction)
        assertEquals(Direction.DOWN, row(result, "comms")!!.direction)
        assertEquals(Direction.LEVEL, row(result, "rhythm")!!.direction)
    }
}
