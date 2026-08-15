package com.stressdetect.features

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Cross-language check of the Kotlin port against the PYTHON extractor.
 *
 * Every expected number below was produced by running
 * `ml/src/features/screenlock_features.py` (and `aux_features.py`, `sequence_dataset.py`)
 * over the exact interval list in [INTERVALS] — they are not hand arithmetic, so a
 * disagreement here is a genuine parity break.
 *
 * This is the Phase-5 unit-level guard. The full contract test over
 * `fixtures/synthetic_trace.json`, which both suites read, lands in Phase 6.
 *
 * The zone is passed EXPLICITLY as [SpecConstants.PARITY_TIMEZONE]; see [zoneIsExplicit].
 */
class ScreenLockFeaturesParityTest {

    private val zone: ZoneId = ZoneId.of(SpecConstants.PARITY_TIMEZONE)

    /** Window = the 7 days ending at local midnight starting 2013-04-15. */
    private val labelDate: LocalDate = LocalDate.of(2013, 4, 15)
    private val w0 = 1365393600L
    private val w1 = 1365998400L

    private val tolerance = 1e-9

    @Test
    fun `window bounds match the python _window_bounds`() {
        val window = AnalysisWindow.endingAtMidnightOf(labelDate, zone)
        assertEquals(w0, window.startUtc)
        assertEquals(w1, window.endUtc)
    }

    @Test
    fun `backbone features match the python extractor`() {
        val actual = ScreenLockFeatures.windowFeatures(INTERVALS, w0, w1, zone)
        assertFeatures(PYTHON_FEATURES, actual)
    }

    @Test
    fun `auxiliary features match the python extractor`() {
        val actual = AuxFeatures.windowFeatures(CALLS, SMS, w0, w1, hasCalls = true, hasSms = true)
        assertFeatures(PYTHON_AUX, actual)
    }

    @Test
    fun `a missing stream yields value 0 AND present 0, never a silent zero`() {
        val actual = AuxFeatures.windowFeatures(CALLS, SMS, w0, w1, hasCalls = false, hasSms = true)
        // The subject HAS calls in the window, but the stream is unavailable: the value
        // must be 0 and the flag must say so. Feature-spec §1 forbids emitting one without
        // the other, because "no calls" and "cannot see calls" are different states.
        assertEquals(0.0, actual.getValue("call_count_per_day"), tolerance)
        assertEquals(0.0, actual.getValue("call_present"), tolerance)
        assertEquals(0.5714285714285714, actual.getValue("sms_count_per_day"), tolerance)
        assertEquals(1.0, actual.getValue("sms_present"), tolerance)
    }

    @Test
    fun `an empty window is all-NaN with days_with_data preserved`() {
        val actual = ScreenLockFeatures.windowFeatures(emptyList(), w0, w1, zone)
        assertEquals(0.0, actual.getValue("days_with_data"), tolerance)
        for (name in ScreenLockFeatures.FEATURE_NAMES.filter { it != "days_with_data" }) {
            assertTrue("$name should be NaN for an empty window", actual.getValue(name).isNaN())
        }
    }

    @Test
    fun `daily sequence matches sequence_dataset _daily_vector`() {
        val actual = SequenceFeatures.dynamicSequence(INTERVALS, CALLS, SMS, labelDate, zone)
        assertEquals(SpecConstants.WINDOW_DAYS, actual.size)
        for (day in PYTHON_SEQUENCE.indices) {
            for (feature in PYTHON_SEQUENCE[day].indices) {
                val expected = PYTHON_SEQUENCE[day][feature]
                val got = actual[day][feature]
                val label = "day $day / ${SequenceFeatures.DYNAMIC_FEATURE_NAMES[feature]}"
                if (expected.isNaN()) assertTrue("$label should be NaN, was $got", got.isNaN())
                else assertEquals(label, expected, got, tolerance)
            }
        }
    }

    @Test
    fun `feature order matches the python feature_names`() {
        assertEquals(
            listOf(
                "days_with_data", "n_sleep_nights", "sleep_duration_median", "sleep_onset_hours",
                "sleep_wake_hours", "sleep_onset_regularity", "sleep_midpoint_regularity",
                "unlock_count_per_day_mean", "unlock_count_sd", "session_count_per_day_mean",
                "session_duration_median", "session_duration_iqr", "screen_on_fraction",
                "nighttime_use_fraction_personal", "nighttime_unlock_per_day_personal",
                "nighttime_use_fraction_fixed", "nighttime_unlock_per_day_fixed",
                "circadian_regularity",
                "call_count_per_day", "call_present", "sms_count_per_day", "sms_present",
            ),
            FeatureExtractor.FEATURE_NAMES,
        )
    }

    /**
     * The zone must be supplied, not inherited from the environment. If this class ever
     * relied on `ZoneId.systemDefault()`, the parity assertions above would pass or fail
     * depending on the machine running them — which would make them worthless as a parity
     * check (feature-spec §8).
     */
    @Test
    fun zoneIsExplicit() {
        assertEquals("America/New_York", SpecConstants.PARITY_TIMEZONE)
        assertEquals(SpecConstants.PARITY_TIMEZONE, zone.id)
    }

    private fun assertFeatures(expected: Map<String, Double>, actual: Map<String, Double>) {
        assertEquals("feature set differs", expected.keys, actual.keys)
        for ((name, want) in expected) {
            val got = actual.getValue(name)
            if (want.isNaN()) assertTrue("$name should be NaN, was $got", got.isNaN())
            else assertEquals(name, want, got, tolerance)
        }
    }

    private companion object {

        /**
         * Synthetic locked intervals: 8 nightly sleeps (one straddling each window edge, one
         * with an after-midnight onset), daytime locks producing use-sessions — including a
         * 190-minute gap that MUST be rejected by `MAX_SESSION_MINUTES` — and one 02:30
         * night-time unlock.
         */
        val INTERVALS = listOf(
            LockedInterval(1365390720L, 1365419040L),   // sleep straddling w0
            LockedInterval(1365423300L, 1365428400L),
            LockedInterval(1365437100L, 1365440400L),
            LockedInterval(1365460200L, 1365465600L),
            LockedInterval(1365478860L, 1365506520L),
            LockedInterval(1365512400L, 1365521400L),
            LockedInterval(1365531600L, 1365534300L),
            LockedInterval(1365562680L, 1365590820L),
            LockedInterval(1365575400L, 1365576600L),   // 02:30 night lock
            LockedInterval(1365595800L, 1365598500L),
            LockedInterval(1365615900L, 1365630600L),
            LockedInterval(1365651300L, 1365679860L),
            LockedInterval(1365654000L, 1365681900L),
            LockedInterval(1365687000L, 1365689700L),
            LockedInterval(1365710400L, 1365711600L),
            LockedInterval(1365767100L, 1365769800L),
            LockedInterval(1365807600L, 1365814800L),
            LockedInterval(1365822300L, 1365849300L),
            LockedInterval(1365861600L, 1365863100L),
            LockedInterval(1365881400L, 1365885600L),
            LockedInterval(1365911400L, 1365937800L),
            LockedInterval(1365945120L, 1365947400L),
            LockedInterval(1365958800L, 1365989400L),
            LockedInterval(1365993600L, 1366023300L),   // sleep straddling w1
        )

        val CALLS = listOf(1365516000L, 1365703200L, 1365897600L)

        /** The last one is deliberately OUTSIDE the window and must not be counted. */
        val SMS = listOf(1365433200L, 1365435000L, 1365771600L, 1365973200L, 1366146000L)

        /** Verbatim output of `screenlock_features.screenlock_window_features`. */
        val PYTHON_FEATURES = mapOf(
            // NOTE: 8, not 7 — an interval crossing the window's end clips to the label
            // day's local midnight, whose DATE is the label day, adding an 8th coverage
            // day to a 7-day window. Mirrored from Python deliberately (it is the
            // denominator of every per-day rate); recorded in feature-spec.md §8.
            "days_with_data" to 8.0,
            "n_sleep_nights" to 7.0,
            "sleep_duration_median" to 7.816666666666666,
            "sleep_onset_hours" to 23.28814899102648,
            "sleep_wake_hours" to 7.057145571682186,
            "sleep_onset_regularity" to 0.3929899301123012,
            "sleep_midpoint_regularity" to 0.32303655410155696,
            "unlock_count_per_day_mean" to 2.875,
            "unlock_count_sd" to 1.2686114456365274,
            "session_count_per_day_mean" to 1.0,
            "session_duration_median" to 110.0,
            "session_duration_iqr" to 48.5,
            "screen_on_fraction" to 0.07690972222222223,
            "nighttime_use_fraction_personal" to 0.0,
            "nighttime_unlock_per_day_personal" to 0.375,
            "nighttime_use_fraction_fixed" to 0.0,
            "nighttime_unlock_per_day_fixed" to 0.125,
            "circadian_regularity" to 0.20760668740319388,
        )

        /** Verbatim output of `aux_features.aux_window_features`. */
        val PYTHON_AUX = mapOf(
            "call_count_per_day" to 0.42857142857142855,
            "call_present" to 1.0,
            "sms_count_per_day" to 0.5714285714285714,
            "sms_present" to 1.0,
        )

        /** Verbatim output of `sequence_dataset._daily_vector`, oldest day → newest. */
        val PYTHON_SEQUENCE = arrayOf(
            doubleArrayOf(2.0, 1.0, 108.0, 0.075, 0.0, 0.0, 2.0, 1.0),
            doubleArrayOf(1.5, 1.0, 134.0, 0.09305555555555556, 0.0, 1.0, 0.0, 1.0),
            doubleArrayOf(2.0, 0.0, Double.NaN, 0.0, Double.NaN, 0.0, 0.0, 1.0),
            doubleArrayOf(4.0, 1.0, 85.0, 0.059027777777777776, 0.0, 1.0, 0.0, 1.0),
            doubleArrayOf(1.0, 0.5, 125.0, 0.043402777777777776, 0.0, 0.0, 1.0, 1.0),
            doubleArrayOf(1.5, 0.0, Double.NaN, 0.0, Double.NaN, 1.0, 0.0, 1.0),
            doubleArrayOf(1.5, 1.0, 96.0, 0.06666666666666667, 0.0, 0.0, 1.0, 1.0),
        )
    }
}
