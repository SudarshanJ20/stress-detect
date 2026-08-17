package com.stressdetect.ui

import com.stressdetect.ui.content.Band
import com.stressdetect.ui.content.Observations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The observations are the app's main user-facing claim about someone's week, so what is
 * tested here is mostly whether the SENTENCES are true and safe — the failure mode recorded
 * in `docs/feature-spec.md` §10.3, where correct numbers were wrapped in a false clause.
 */
class ObservationsTest {

    private fun week(vararg pairs: Pair<String, List<Double>>) = pairs.toMap()

    @Test
    fun `late nights counts nights with any after-midnight use`() {
        val result = Observations.build(
            dailyValues = week(
                "nighttime_use_fraction_fixed" to listOf(0.0, 0.3, 0.0, 0.1, 0.2, 0.0, 0.4),
            ),
            staticValues = emptyMap(),
            usageAccessMissing = false, meetsCoverage = true, daysWithData = 7.0,
        )
        val sentence = result.observations.first { it.id.startsWith("night:") }.sentence
        assertTrue("expected 4 nights, got: $sentence", sentence.contains("on 4 nights"))
        assertTrue(sentence.contains("after midnight"))
    }

    @Test
    fun `a single late night does not fire — one night is not a pattern`() {
        val result = Observations.build(
            week("nighttime_use_fraction_fixed" to listOf(0.0, 0.0, 0.3, 0.0, 0.0, 0.0, 0.0)),
            mapOf("sleep_onset_hours" to 22.0),
            usageAccessMissing = false, meetsCoverage = true, daysWithData = 7.0,
        )
        assertTrue(result.observations.none { it.id.startsWith("night:") })
    }

    @Test
    fun `NaN days are not counted as late nights`() {
        val result = Observations.build(
            week("nighttime_use_fraction_fixed" to listOf(Double.NaN, Double.NaN, 0.2, 0.3)),
            emptyMap(), usageAccessMissing = false, meetsCoverage = true, daysWithData = 7.0,
        )
        val sentence = result.observations.first { it.id.startsWith("night:") }.sentence
        assertTrue(sentence.contains("on 2 nights"))
    }

    @Test
    fun `at most three observations, and never two about the same thing`() {
        val result = Observations.build(
            week(
                "nighttime_use_fraction_fixed" to List(7) { 0.2 },
                "screen_on_fraction" to List(7) { 0.35 },
                "unlock_count" to List(7) { 80.0 },
            ),
            mapOf(
                "sleep_duration_median" to 5.0, "sleep_onset_hours" to 2.0,
                "sleep_onset_regularity" to 2.0, "circadian_regularity" to 0.1,
            ),
            usageAccessMissing = false, meetsCoverage = true, daysWithData = 7.0,
        )
        assertTrue(result.observations.size <= 3)
        val topics = result.observations.map { it.id.substringBefore(':') }
        assertEquals("one observation per topic", topics.distinct(), topics)
    }

    @Test
    fun `a quiet week still produces something to read`() {
        // Nothing notable fires; the fallbacks must keep the section from being empty.
        val result = Observations.build(
            week("screen_on_fraction" to List(7) { 0.08 }, "unlock_count" to List(7) { 20.0 }),
            mapOf(
                "sleep_duration_median" to 8.0, "sleep_onset_hours" to 22.5,
                "sleep_onset_regularity" to 0.3, "circadian_regularity" to 0.7,
            ),
            usageAccessMissing = false, meetsCoverage = true, daysWithData = 7.0,
        )
        assertNull(result.unavailableReason)
        assertTrue(result.observations.isNotEmpty())
    }

    @Test
    fun `every observation carries a suggestion`() {
        val result = Observations.build(
            week("nighttime_use_fraction_fixed" to List(7) { 0.2 }),
            mapOf("sleep_duration_median" to 5.0, "sleep_onset_hours" to 1.0),
            usageAccessMissing = false, meetsCoverage = true, daysWithData = 7.0,
        )
        for (observation in result.observations) {
            assertTrue("empty suggestion for ${observation.id}", observation.suggestion.isNotBlank())
        }
    }

    @Test
    fun `missing usage access explains itself instead of showing nothing`() {
        val result = Observations.build(
            emptyMap(), emptyMap(),
            usageAccessMissing = true, meetsCoverage = false, daysWithData = 0.0,
        )
        assertTrue(result.observations.isEmpty())
        assertNotNull(result.unavailableReason)
        // Must point at the fix and reassure that the check-in itself still worked.
        assertTrue(result.unavailableReason!!.contains("About"))
        assertTrue(result.unavailableReason.contains("works either way"))
    }

    @Test
    fun `thin coverage says so in days, not in jargon`() {
        val result = Observations.build(
            emptyMap(), emptyMap(),
            usageAccessMissing = false, meetsCoverage = false, daysWithData = 2.0,
        )
        assertTrue(result.unavailableReason!!.contains("2 day"))
        assertFalse(result.unavailableReason.contains("coverage"))
    }

    @Test
    fun `durations read as everyday words`() {
        assertEquals("7 and a half hours", Observations.duration(7.5))
        assertEquals("8 hours", Observations.duration(8.0))
        assertEquals("45 minutes", Observations.duration(0.75))
        assertEquals("1 hour", Observations.duration(1.0))
    }

    @Test
    fun `every duration names its unit`() {
        // This expectation used to be "6 hours 10", and the app duly printed "Around 6 hours
        // 43 a day" on a real screen. A number with no unit beside it is the one thing a
        // reader cannot guess at, so the rule is now asserted rather than assumed.
        assertEquals("6 hours 10 minutes", Observations.duration(6.1667))
        assertEquals("6 hours 43 minutes", Observations.duration(6.7167))
        for (hours in listOf(0.0, 0.017, 0.75, 1.0, 1.017, 3.9, 6.1667, 7.5, 12.75, 23.99)) {
            val text = Observations.duration(hours)
            val last = text.substringAfterLast(' ')
            assertTrue(
                "'$text' ends in a bare number — no unit",
                last == "minute" || last == "minutes" || last == "hour" || last == "hours",
            )
        }
    }

    @Test
    fun `a single minute is not plural`() {
        assertEquals("1 minute", Observations.duration(1.0 / 60))
        assertEquals("6 hours 1 minute", Observations.duration(6.0 + 1.0 / 60))
        assertEquals("0 minutes", Observations.duration(0.0))
    }

    @Test
    fun `the half-hour form still wins where it reads better`() {
        assertEquals("7 and a half hours", Observations.duration(7.5))
        assertEquals("7 and a half hours", Observations.duration(7.42))   // 25 min, the edge
        assertEquals("7 and a half hours", Observations.duration(7.58))   // 35 min, the edge
        assertEquals("7 hours 24 minutes", Observations.duration(7.4))    // just outside
        assertEquals("7 hours 36 minutes", Observations.duration(7.6))
    }

    @Test
    fun `clock times read as everyday times`() {
        assertEquals("11:50pm", Observations.clock(23.8333))
        assertEquals("midnight", Observations.clock(0.0))
        assertEquals("midday", Observations.clock(12.0))
        assertEquals("7:10am", Observations.clock(7.1667))
        assertEquals("10pm", Observations.clock(22.0))
        // 23:59:40 rounds up to midnight rather than printing "11:60pm"
        assertEquals("midnight", Observations.clock(23.9945))
    }

    @Test
    fun `no observation or suggestion uses clinical or research vocabulary`() {
        val banned = listOf(
            "questionnaire", "validated", "model", "attribution", "spec", "construct",
            "diagnos", "clinical", "symptom", "disorder", "treat", "therapy", "baseline",
            "percentile", "average person", "feature", "correlat",
        )
        for (copy in Observations.allCopyStrings()) {
            for (word in banned) {
                assertFalse("'$word' appears in: $copy", copy.lowercase().contains(word))
            }
        }
    }

    @Test
    fun `bands cover the whole scale and never alarm at the top`() {
        assertEquals(Band.LOW, Band.forScore(0))
        assertEquals(Band.LOW, Band.forScore(4))
        assertEquals(Band.SOME, Band.forScore(5))
        assertEquals(Band.SOME, Band.forScore(8))
        assertEquals(Band.MODERATE, Band.forScore(9))
        assertEquals(Band.MODERATE, Band.forScore(12))
        assertEquals(Band.HIGH, Band.forScore(13))
        assertEquals(Band.HIGH, Band.forScore(16))

        val alarming = listOf("severe", "danger", "critical", "warning", "risk", "abnormal", "urgent")
        for (band in Band.entries) {
            val text = "${band.label} ${band.blurb}".lowercase()
            for (word in alarming) assertFalse("'$word' in ${band.name}", text.contains(word))
            // The mouth never turns down, whatever the score.
            assertTrue("${band.name} mouth must not frown", band.mouthCurve >= 0f)
        }
    }
}
