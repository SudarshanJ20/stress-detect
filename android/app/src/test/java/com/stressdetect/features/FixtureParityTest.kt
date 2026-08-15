package com.stressdetect.features

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.util.TimeZone

/**
 * Kotlin half of the cross-language parity contract. The Python half is
 * `ml/tests/test_parity_fixture.py`; both read `fixtures/synthetic_trace.json`.
 *
 * `expected_features` was produced by the PYTHON extractor, so this is the test that
 * actually establishes Kotlin ≡ Python. The other unit tests only show the Kotlin side is
 * internally consistent.
 *
 * The zone comes from the fixture and is passed EXPLICITLY on every call — see
 * [parity computation ignores the machine's default zone] and
 * [running under the device zone instead of the spec zone fails], which prove the
 * assertions are not passing by environmental accident.
 */
class FixtureParityTest {

    private val trace = ParityFixture.load()
    private val zone: ZoneId = ZoneId.of(trace.parityTimezone)

    @Test
    fun `fixture targets the implemented spec version`() {
        assertEquals(
            "fixture targets ${trace.specVersion} but the Kotlin extractor implements " +
                "${SpecConstants.SPEC_VERSION} — regenerate the fixture after a spec bump",
            SpecConstants.SPEC_VERSION,
            trace.specVersion,
        )
    }

    @Test
    fun `fixture declares the spec parity timezone`() {
        assertEquals(SpecConstants.PARITY_TIMEZONE, trace.parityTimezone)
    }

    @Test
    fun `every case matches the python extractor`() {
        val failures = mutableListOf<String>()
        for (case in trace.cases) {
            val computed = compute(case, zone)
            for ((name, expected) in case.expectedFeatures) {
                val actual = computed.getValue(name)
                if (expected.isNaN()) {
                    if (!actual.isNaN()) failures += "${case.name}/$name: expected NaN, got $actual"
                } else {
                    val delta = kotlin.math.abs(actual - expected)
                    if (!(delta <= trace.tolerance)) {
                        failures += "${case.name}/$name: expected $expected, got $actual " +
                            "(delta $delta > ${trace.tolerance})\n      case exercises: ${case.exercises}"
                    }
                }
            }
        }
        assertTrue(
            "PARITY BREAK — Kotlin disagrees with the Python extractor:\n  " +
                failures.joinToString("\n  "),
            failures.isEmpty(),
        )
    }

    @Test
    fun `window bounds match the fixture for every case`() {
        // Pins the ABSOLUTE (not calendar) day arithmetic; the DST cases are where a
        // calendar-aware implementation diverges.
        for (case in trace.cases) {
            val window = AnalysisWindow.endingAtMidnightOf(case.labelDate, zone)
            assertEquals("${case.name} window start", case.expectedWindowStart, window.startUtc)
            assertEquals("${case.name} window end", case.expectedWindowEnd, window.endUtc)
        }
    }

    @Test
    fun `coverage gate agrees with the fixture for every case`() {
        for (case in trace.cases) {
            val vector = extract(case, zone)
            assertEquals(
                "${case.name} coverage gate", case.expectedMeetsCoverage, vector.meetsCoverage,
            )
        }
    }

    @Test
    fun `fixture covers the whole feature vector`() {
        for (case in trace.cases) {
            assertEquals(
                "${case.name} does not pin every feature",
                trace.featureNames.toSet(),
                case.expectedFeatures.keys,
            )
        }
        assertEquals(trace.featureNames, FeatureExtractor.FEATURE_NAMES)
    }

    @Test
    fun `fixture covers every edge case the contract requires`() {
        val required = setOf(
            "midnight_wrap_sleep", "sleep_band_edge_inside", "sleep_band_edge_outside",
            "session_cutoff_edges", "below_coverage_gate", "interval_open_at_window_start",
            "interval_open_at_window_end", "empty_window", "dst_spring_forward",
            "dst_fall_back", "days_with_data_eight",
        )
        assertTrue(
            "fixture is missing required cases: ${required - trace.cases.map { it.name }.toSet()}",
            trace.cases.map { it.name }.containsAll(required),
        )
    }

    // ── the zone must come from the contract, not the environment ───────────────────────

    /**
     * Runs the whole contract with the JVM's default zone forced to something else. If any
     * code path read an ambient zone instead of the argument it was handed, these
     * assertions would break.
     */
    @Test
    fun `parity computation ignores the machine's default zone`() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"))
            for (case in trace.cases) {
                val computed = compute(case, zone)
                for ((name, expected) in case.expectedFeatures) {
                    val actual = computed.getValue(name)
                    if (expected.isNaN()) {
                        assertTrue("${case.name}/$name", actual.isNaN())
                    } else {
                        assertEquals("${case.name}/$name", expected, actual, trace.tolerance)
                    }
                }
            }
        } finally {
            TimeZone.setDefault(original)
        }
    }

    /**
     * The other half of that proof: running the contract under a DIFFERENT zone must
     * FAIL. If it passed, the fixture would be insensitive to the timezone and a zone bug
     * on either side would go undetected — the test would be decoration.
     */
    @Test
    fun `running under the device zone instead of the spec zone fails`() {
        val wrongZone = ZoneId.of("Asia/Kolkata")
        assertNotEquals(
            "the 'wrong' zone must actually differ from the spec zone", zone, wrongZone,
        )

        val mismatches = trace.cases.count { case ->
            val computed = compute(case, wrongZone)
            case.expectedFeatures.any { (name, expected) ->
                val actual = computed.getValue(name)
                if (expected.isNaN()) !actual.isNaN()
                else !(kotlin.math.abs(actual - expected) <= trace.tolerance)
            }
        }
        assertTrue(
            "computing the fixture under Asia/Kolkata produced the SAME vectors as under " +
                "${trace.parityTimezone} for every case — the contract does not pin the " +
                "timezone at all, so it cannot catch a zone bug",
            mismatches > 0,
        )

        // And specifically: the DST cases must differ, since that is what they exist for.
        for (name in listOf("dst_spring_forward", "dst_fall_back")) {
            val case = trace.cases.first { it.name == name }
            val window = AnalysisWindow.endingAtMidnightOf(case.labelDate, wrongZone)
            assertNotEquals(
                "$name: window under the wrong zone equals the expected window",
                case.expectedWindowStart,
                window.startUtc,
            )
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────

    private fun compute(case: ParityFixture.Case, withZone: ZoneId): Map<String, Double> =
        extract(case, withZone).values

    private fun extract(case: ParityFixture.Case, withZone: ZoneId): WindowFeatureVector =
        FeatureExtractor.extract(
            locked = case.lockedIntervals,
            callTimestamps = case.calls,
            smsTimestamps = case.sms,
            labelDate = case.labelDate,
            zone = withZone,
            hasCalls = case.hasCalls,
            hasSms = case.hasSms,
        )
}
