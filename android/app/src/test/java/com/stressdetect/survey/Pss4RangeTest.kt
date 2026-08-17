package com.stressdetect.survey

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Walks all four items at every option — 5^4 = 625 response sets — and asserts the total
 * covers the whole 0–16 scale.
 *
 * Written against a report from the device that the score only ever moved between 7 and 8
 * (44% and 50%), which would mean either that one item was reaching the sum on its own or
 * that the reversal on items 2 and 3 was cancelling items 1 and 4 out.
 *
 * The arithmetic that decides it: with items 2 and 3 reversed,
 *
 *     total = r1 + (4 − r2) + (4 − r3) + r4  =  8 + (r1 + r4) − (r2 + r3)
 *
 * so the total is 8 whenever the two negatively-worded items and the two positively-worded
 * ones balance — which every UNIFORM answer set does, all-Never and all-Very-often
 * included. 7 and 8 are what a run of near-uniform answers produces, and they are correct.
 * Reaching an end of the scale needs the two pairs pulled APART: [4,0,0,4] and [0,4,4,0].
 *
 * If this test ever fails, the scoring really has collapsed. While it passes, a narrow range
 * on a device is a report about the ANSWERS, not about the sum — which is why
 * [Pss4Answers][com.stressdetect.survey.Pss4Answers] exists to prove the four selections
 * arrive intact.
 */
class Pss4RangeTest {

    private val everyResponseSet: List<List<Int>> =
        (0..Pss4.MAX_ANCHOR).flatMap { a ->
            (0..Pss4.MAX_ANCHOR).flatMap { b ->
                (0..Pss4.MAX_ANCHOR).flatMap { c ->
                    (0..Pss4.MAX_ANCHOR).map { d -> listOf(a, b, c, d) }
                }
            }
        }

    @Test
    fun `every combination is walked`() {
        assertEquals(625, everyResponseSet.size)
    }

    @Test
    fun `the total spans the whole scale, end to end`() {
        val totals = everyResponseSet.map { Pss4.score(it) }
        assertEquals("the scale must reach its floor", Pss4.MIN_SCORE, totals.min())
        assertEquals("the scale must reach its ceiling", Pss4.MAX_SCORE, totals.max())
    }

    @Test
    fun `no point on the scale is unreachable`() {
        val reached = everyResponseSet.map { Pss4.score(it) }.toSortedSet()
        assertEquals(
            "some totals cannot be produced by any answer set",
            (Pss4.MIN_SCORE..Pss4.MAX_SCORE).toSortedSet(),
            reached,
        )
    }

    @Test
    fun `the percentage spans 0 to 100`() {
        val percentages = everyResponseSet.map { Pss4.percentOfMaximum(Pss4.score(it)) }
        assertEquals(0, percentages.min())
        assertEquals(100, percentages.max())
    }

    @Test
    fun `each item moves the total on its own`() {
        // If only one item were reaching the sum, three of these four would be flat. Each is
        // walked with the other three held at the midpoint, which is itself under reversal.
        val midpoint = Pss4.MAX_ANCHOR / 2
        for (item in Pss4.ITEMS.indices) {
            val totals = (0..Pss4.MAX_ANCHOR).map { value ->
                Pss4.score(List(Pss4.ITEMS.size) { if (it == item) value else midpoint })
            }
            assertEquals(
                "item ${item + 1} does not move the total — it spans ${totals.min()}..${totals.max()}",
                Pss4.MAX_ANCHOR,
                totals.max() - totals.min(),
            )
        }
    }

    @Test
    fun `the two ends need the pairs pulled apart, and uniform answers sit at the middle`() {
        // This is the whole explanation of a 7-to-8 range, as an assertion.
        assertEquals(Pss4.MAX_SCORE, Pss4.score(listOf(4, 0, 0, 4)))
        assertEquals(Pss4.MIN_SCORE, Pss4.score(listOf(0, 4, 4, 0)))
        for (anchor in 0..Pss4.MAX_ANCHOR) {
            assertEquals(
                "a uniform answer set is mid-scale by construction, not by accident",
                8, Pss4.score(List(Pss4.ITEMS.size) { anchor }),
            )
        }
    }

    @Test
    fun `a near-uniform answer set is what produces 7`() {
        // 8 + (r1 + r4) − (r2 + r3) = 8 − 1. Reported as a bug; it is the published rule.
        assertEquals(7, Pss4.score(listOf(2, 3, 2, 2)))
        assertTrue(Pss4.percentOfMaximum(7) == 44)
    }
}
