package com.stressdetect.survey

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the instrument itself.
 *
 * Two distinct risks, both silent: someone paraphrases an item to make it read better (the
 * scale stops being PSS-4 while still looking like it), or the reverse-scoring is dropped
 * or applied to the wrong items (every score comes out wrong, and plausibly so — an
 * all-"very often" respondent would score 16 either way if you reversed the wrong pair).
 */
class Pss4Test {

    @Test
    fun `items are the published wording, verbatim`() {
        // Verified 2026-08 against Cohen's PSS-4 (Harvard) and PMC5791241. If you are here
        // because this test failed: the fix is to restore the wording, not to update the
        // expectation.
        assertEquals(
            "In the last month, how often have you felt that you were unable to control " +
                "the important things in your life?",
            Pss4.ITEMS[0].text,
        )
        assertEquals(
            "In the last month, how often have you felt confident about your ability to " +
                "handle your personal problems?",
            Pss4.ITEMS[1].text,
        )
        assertEquals(
            "In the last month, how often have you felt that things were going your way?",
            Pss4.ITEMS[2].text,
        )
        assertEquals(
            "In the last month, how often have you felt difficulties were piling up so " +
                "high that you could not overcome them?",
            Pss4.ITEMS[3].text,
        )
    }

    @Test
    fun `there are four items and five anchors in published order`() {
        assertEquals(4, Pss4.ITEMS.size)
        assertEquals(
            listOf("Never", "Almost never", "Sometimes", "Fairly often", "Very often"),
            Pss4.ANCHORS,
        )
        assertEquals(Pss4.MAX_ANCHOR + 1, Pss4.ANCHORS.size)
    }

    @Test
    fun `items 2 and 3 are reverse-scored and the others are not`() {
        assertFalse("item 1 is negatively worded", Pss4.ITEMS[0].reverseScored)
        assertTrue("item 2 is positively worded", Pss4.ITEMS[1].reverseScored)
        assertTrue("item 3 is positively worded", Pss4.ITEMS[2].reverseScored)
        assertFalse("item 4 is negatively worded", Pss4.ITEMS[3].reverseScored)
    }

    @Test
    fun `reverse-scored items invert each anchor`() {
        val reversed = Pss4.ITEMS[1]
        assertEquals(4, reversed.contribution(0))
        assertEquals(3, reversed.contribution(1))
        assertEquals(2, reversed.contribution(2))   // the midpoint maps to itself
        assertEquals(1, reversed.contribution(3))
        assertEquals(0, reversed.contribution(4))
    }

    @Test
    fun `forward-scored items pass the anchor through`() {
        val forward = Pss4.ITEMS[0]
        for (raw in 0..4) assertEquals(raw, forward.contribution(raw))
    }

    @Test
    fun `all-never scores 8, not 0 — the reversal is what makes the midpoint`() {
        // The clearest evidence the reversal is applied: answering "Never" to everything
        // means never out of control AND never confident, which is mid-scale, not calm.
        // If reverse-scoring were dropped this would be 0.
        assertEquals(8, Pss4.score(listOf(0, 0, 0, 0)))
    }

    @Test
    fun `all-very-often also scores 8`() {
        assertEquals(8, Pss4.score(listOf(4, 4, 4, 4)))
    }

    @Test
    fun `the maximum-stress and minimum-stress response patterns hit the bounds`() {
        // Most stressed: always out of control / overwhelmed, never confident / going well.
        assertEquals(Pss4.MAX_SCORE, Pss4.score(listOf(4, 0, 0, 4)))
        // Least stressed: the mirror image.
        assertEquals(Pss4.MIN_SCORE, Pss4.score(listOf(0, 4, 4, 0)))
    }

    @Test
    fun `a worked example scores as the published rule dictates`() {
        // raw [2, 1, 3, 0] -> 2 + (4-1) + (4-3) + 0 = 6
        assertEquals(6, Pss4.score(listOf(2, 1, 3, 0)))
    }

    @Test
    fun `percentage is of the scale maximum, never a percentile`() {
        assertEquals(0, Pss4.percentOfMaximum(0))
        assertEquals(50, Pss4.percentOfMaximum(8))
        assertEquals(100, Pss4.percentOfMaximum(16))
        assertEquals(44, Pss4.percentOfMaximum(7))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a short response list is rejected rather than scored`() {
        Pss4.score(listOf(1, 2, 3))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `an out-of-range anchor is rejected`() {
        Pss4.ITEMS[0].contribution(5)
    }
}
