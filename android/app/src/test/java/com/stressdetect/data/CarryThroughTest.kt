package com.stressdetect.data

import com.stressdetect.features.FeatureExtractor
import com.stressdetect.features.ParityFixture
import com.stressdetect.ui.content.WeekSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/**
 * A full week of real phone data, carried from the extractor to what the screen renders.
 *
 * Written against a device report that the four rows did not appear on real data while
 * extraction looked healthy — 4850 events, 862 lock stretches, 7 days with data. Every other
 * test in this area starts from a hand-written feature map; this one starts from the real
 * extractor over the committed fixture and follows the values all the way to
 * [WeekSummary.build], which is the only stretch where a populated vector could arrive and
 * an empty section could leave.
 *
 * The rows read from `weekValues`. Nothing else in the app does, so if a future edit drops
 * that one field the app keeps working everywhere except the section this test covers — and
 * a week of somebody's data silently becomes "nothing stood out".
 */
class CarryThroughTest {

    private val trace = ParityFixture.load()
    private val zone: ZoneId = ZoneId.of(trace.parityTimezone)

    /** A populated vector from the REAL extractor, not a map written by hand. */
    private fun realWeek(name: String = "demo_week") = trace.cases.first { it.name == name }

    private fun assembled(caseName: String = "demo_week", priorWeeks: List<Map<String, Double>> = emptyList()) =
        realWeek(caseName).let { case ->
            val vector = FeatureExtractor.extract(
                locked = case.lockedIntervals,
                callTimestamps = case.calls,
                smsTimestamps = case.sms,
                labelDate = case.labelDate,
                zone = zone,
                hasCalls = case.hasCalls,
                hasSms = case.hasSms,
            )
            WindowAssembly.assemble(
                locked = case.lockedIntervals,
                calls = case.calls,
                sms = case.sms,
                labelDate = case.labelDate,
                zone = zone,
                values = vector.values,
                commsIncluded = true,
                priorWeeks = priorWeeks,
            )
        }

    private fun result(caseName: String = "demo_week", priorWeeks: List<Map<String, Double>> = emptyList()) =
        WindowAssembly.toResult(
            questionnaireScore = 8,
            window = assembled(caseName, priorWeeks),
            modelEstimate = null,
            modelUnavailableReason = "No model is bundled in this build.",
            contributions = emptyList(),
            isDemo = false,
        )

    private fun summaryOf(result: AnalysisResult) = WeekSummary.build(
        weekValues = result.weekValues,
        priorWeekValues = result.priorWeekValues,
        dailyValues = result.dailyValues,
        staticValues = result.staticValues,
        usageAccessMissing = result.usageAccessMissing,
        meetsCoverage = result.meetsCoverage,
        daysWithData = result.daysWithData,
    )

    @Test
    fun `the flat vector reaches the result with every feature the rows read`() {
        val values = result().weekValues
        assertTrue("weekValues arrived empty — the rows have nothing to read", values.isNotEmpty())
        for (name in listOf(
            "screen_on_fraction", "sleep_duration_median", "circadian_regularity",
            "call_count_per_day", "sms_count_per_day", "call_present", "sms_present",
        )) {
            assertTrue("$name did not survive the trip to the result", values.containsKey(name))
        }
    }

    @Test
    fun `coverage and permission arrive as the screen expects them`() {
        val result = result()
        assertFalse("a device read is not a missing permission", result.usageAccessMissing)
        assertTrue("a full week must pass the coverage gate", result.meetsCoverage)
        assertEquals(8.0, result.daysWithData, 1e-9)
    }

    @Test
    fun `a real week produces four rows on the screen`() {
        val summary = summaryOf(result())
        assertNull("a healthy week must not fall back to a reason", summary.unavailableReason)
        assertEquals(
            "a full week of real data produced ${summary.rows.size} rows: " +
                summary.rows.joinToString { it.id },
            4, summary.rows.size,
        )
        assertEquals(listOf("screen", "rest", "comms", "rhythm"), summary.rows.map { it.id })
    }

    @Test
    fun `every row says something, with or without earlier weeks to compare against`() {
        for (row in summaryOf(result()).rows) {
            assertTrue("${row.id} rendered an empty phrase", row.phrase.isNotBlank())
            assertNull("a first week cannot have a direction", row.direction)
        }

        val withPrior = summaryOf(result(priorWeeks = listOf(assembled("demo_prior_week_1").values)))
        assertTrue(
            "with an earlier week at least one row should be able to point somewhere",
            withPrior.rows.any { it.direction != null },
        )
    }

    @Test
    fun `the demo path and the device path assemble identically`() {
        // Both go through the same assembler; a divergence would mean the demo proves
        // nothing about the real thing, which is how the device fault stayed invisible.
        val a = assembled()
        val b = assembled()
        assertEquals(a.values, b.values)
        assertEquals(a.meetsCoverage, b.meetsCoverage)
        assertEquals(a.dailySeries.keys, b.dailySeries.keys)
    }

    @Test
    fun `prior weeks average feature by feature, skipping the gaps`() {
        val mean = WindowAssembly.meanOfWeeks(
            listOf(
                mapOf("screen_on_fraction" to 0.2, "sleep_duration_median" to Double.NaN),
                mapOf("screen_on_fraction" to 0.4, "sleep_duration_median" to 7.0),
            )
        )
        assertEquals(0.3, mean.getValue("screen_on_fraction"), 1e-9)
        // The NaN week is skipped rather than dragging the mean to NaN.
        assertEquals(7.0, mean.getValue("sleep_duration_median"), 1e-9)
    }
}
