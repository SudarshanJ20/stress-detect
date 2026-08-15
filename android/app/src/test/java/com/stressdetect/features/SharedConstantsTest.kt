package com.stressdetect.features

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Every constant that BOTH extractors implement must hold the same value on both sides.
 *
 * This reads `ml/src/features/spec_constants.py` directly and compares it against
 * [SpecConstants], so "mirrored 1:1" is checked rather than trusted. The Phase-6 fixture
 * found a bug caused by exactly this class of drift — a rule applied on one side only —
 * and the remedy (`BAND_EDGE_EPS`) is itself now a shared constant, so it needs the same
 * protection it was introduced to provide.
 *
 * A threshold that exists on only one side, or drifts by a digit, changes feature values
 * silently: no crash, no exception, just a model scoring inputs it was not fitted on.
 */
class SharedConstantsTest {

    private val source: String by lazy {
        val file = repoRoot().resolve("ml/src/features/spec_constants.py")
        assertTrue("cannot find ${file.path}", file.isFile)
        file.readText()
    }

    @Test
    fun `numeric thresholds match the python extractor`() {
        assertEquals("WINDOW_DAYS", pythonNumber("WINDOW_DAYS"), SpecConstants.WINDOW_DAYS.toDouble(), 0.0)
        assertEquals("COVERAGE_MIN_DAYS", pythonNumber("COVERAGE_MIN_DAYS"), SpecConstants.COVERAGE_MIN_DAYS.toDouble(), 0.0)
        assertEquals("MAX_SESSION_MINUTES", pythonNumber("MAX_SESSION_MINUTES"), SpecConstants.MAX_SESSION_MINUTES.toDouble(), 0.0)
        assertEquals("MIN_SLEEP_MINUTES", pythonNumber("MIN_SLEEP_MINUTES"), SpecConstants.MIN_SLEEP_MINUTES.toDouble(), 0.0)
        assertEquals("CIRCADIAN_BINS", pythonNumber("CIRCADIAN_BINS"), SpecConstants.CIRCADIAN_BINS.toDouble(), 0.0)
    }

    @Test
    fun `band edge epsilon matches the python extractor`() {
        // The fix for the JVM-vs-numpy ULP divergence (feature-spec §9). If these two ever
        // differ, the two extractors resolve band edges differently — which is the exact
        // failure the constant was introduced to remove.
        assertEquals(
            "BAND_EDGE_EPS", pythonNumber("BAND_EDGE_EPS"), SpecConstants.BAND_EDGE_EPS, 0.0,
        )
    }

    @Test
    fun `clock bands match the python extractor`() {
        val sleep = pythonTuple("SLEEP_MIDPOINT_BAND")
        assertEquals("SLEEP_MIDPOINT_BAND low", sleep.first, SpecConstants.SLEEP_MIDPOINT_BAND.startHour, 0.0)
        assertEquals("SLEEP_MIDPOINT_BAND high", sleep.second, SpecConstants.SLEEP_MIDPOINT_BAND.endHour, 0.0)

        val night = pythonTuple("NIGHT_FIXED_BAND")
        assertEquals("NIGHT_FIXED_BAND low", night.first, SpecConstants.NIGHT_FIXED_BAND.startHour, 0.0)
        assertEquals("NIGHT_FIXED_BAND high", night.second, SpecConstants.NIGHT_FIXED_BAND.endHour, 0.0)
    }

    @Test
    fun `parity timezone matches the python TIMEZONE`() {
        val match = Regex("""TIMEZONE\s*=\s*"([^"]+)"""").find(source)
        assertNotNull("no TIMEZONE in spec_constants.py", match)
        assertEquals(match!!.groupValues[1], SpecConstants.PARITY_TIMEZONE)
    }

    private fun pythonNumber(name: String): Double {
        val match = Regex("""^$name\s*=\s*([0-9.eE+-]+)""", RegexOption.MULTILINE).find(source)
        assertNotNull("no $name in spec_constants.py", match)
        return match!!.groupValues[1].toDouble()
    }

    private fun pythonTuple(name: String): Pair<Double, Double> {
        val match = Regex("""^$name\s*=\s*\(\s*([0-9.]+)\s*,\s*([0-9.]+)\s*\)""", RegexOption.MULTILINE)
            .find(source)
        assertNotNull("no $name tuple in spec_constants.py", match)
        return match!!.groupValues[1].toDouble() to match.groupValues[2].toDouble()
    }

    private fun repoRoot(): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            if (File(dir, "ml/src/features/spec_constants.py").isFile) return dir
            dir = dir.parentFile
        }
        throw AssertionError("could not locate the repo root from ${System.getProperty("user.dir")}")
    }
}
