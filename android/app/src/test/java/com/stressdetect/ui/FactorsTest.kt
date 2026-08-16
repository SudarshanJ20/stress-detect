package com.stressdetect.ui

import com.stressdetect.features.SequenceFeatures
import com.stressdetect.ui.content.Factors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The factor list is user-facing text keyed by feature name. Two failure modes worth a
 * test: a feature the model can rank but the UI has no words for (the screen would show a
 * raw column name like `nighttime_use_fraction_fixed`), and copy that drifts into advice or
 * causal claims.
 */
class FactorsTest {

    @Test
    fun `every rankable feature has a label and a suggestion`() {
        val missingLabels = Factors.rankableFeatures().filter { Factors.label(it) == it.replace('_', ' ') }
        assertTrue("features with no human label: $missingLabels", missingLabels.isEmpty())

        val missingSuggestions = Factors.rankableFeatures().filter { Factors.suggestion(it) == null }
        assertTrue("features with no suggestion: $missingSuggestions", missingSuggestions.isEmpty())
    }

    @Test
    fun `availability flags are never presented as behaviour`() {
        // "We could see your calls" is not a thing that happened in someone's week.
        val nonBehavioural = listOf("has_data", "days_with_data", "call_present", "sms_present")
        for (name in nonBehavioural) {
            assertFalse("$name must not be rankable", name in Factors.rankableFeatures())
        }
    }

    @Test
    fun `rankable features are exactly the behavioural ones`() {
        val expected = (SequenceFeatures.DYNAMIC_FEATURE_NAMES + SequenceFeatures.STATIC_FEATURE_NAMES)
            .filter { it !in setOf("has_data", "days_with_data", "call_present", "sms_present") }
        assertEquals(expected, Factors.rankableFeatures())
    }

    @Test
    fun `dynamic features are described against the user's own week`() {
        // 7 daily values; the description must reference their own average, not a cohort.
        val text = Factors.describe(
            featureName = "unlock_count",
            dailyValues = listOf(30.0, 32.0, 31.0, 29.0, 30.0, 31.0, 60.0),
            windowValue = null,
        )
        assertTrue("should compare to their own week: $text", text.contains("your own week"))
        assertFalse("must not compare to other people", text.contains("average person"))
        assertFalse(text.contains("percentile"))
    }

    @Test
    fun `static features state a value without inventing a comparison`() {
        // No within-window baseline exists for these, so no comparison may be implied.
        val text = Factors.describe("sleep_onset_hours", dailyValues = null, windowValue = 23.5)
        assertTrue("should state the clock time: $text", text.contains("11:30 pm"))
        assertFalse("must not imply a comparison", text.contains("more than"))
        assertFalse(text.contains("your own week"))
    }

    @Test
    fun `a feature with no usable data says so rather than guessing`() {
        val text = Factors.describe("unlock_count", dailyValues = listOf(Double.NaN), windowValue = null)
        assertTrue(text.contains("Not enough data"))
    }

    @Test
    fun `no suggestion uses clinical or prescriptive language`() {
        val forbidden = listOf(
            "diagnos", "treat", "therapy", "disorder", "symptom", "patient", "clinical",
            "prescri", "cure", "medication", "you must", "you should", "you need to",
        )
        for (feature in Factors.rankableFeatures()) {
            val suggestion = Factors.suggestion(feature)!!.lowercase()
            for (word in forbidden) {
                assertFalse("'$word' in suggestion for $feature: $suggestion", suggestion.contains(word))
            }
        }
    }

    @Test
    fun `the framing disclaimers say context not cause, and not personal advice`() {
        assertTrue(Factors.FACTOR_DISCLAIMER.contains("context, not"))
        assertTrue(Factors.FACTOR_DISCLAIMER.contains("causes"))
        assertNotNull(Factors.SUGGESTION_DISCLAIMER)
        assertTrue(Factors.SUGGESTION_DISCLAIMER.contains("not advice for you personally"))
    }

    @Test
    fun `clock formatting handles midnight and noon correctly`() {
        assertTrue(Factors.describe("sleep_onset_hours", null, 0.0).contains("12:00 am"))
        assertTrue(Factors.describe("sleep_onset_hours", null, 12.0).contains("12:00 pm"))
        assertTrue(Factors.describe("sleep_wake_hours", null, 7.166666).contains("7:10 am"))
    }
}
