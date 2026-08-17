package com.stressdetect.survey

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The selections must reach the sum unaltered, and an incomplete set must never be scored.
 *
 * The screen used to hand `responses.map { it ?: 0 }` to the scorer. That default is the one
 * way the reported symptom could have been a code fault rather than the scale's arithmetic:
 * under reverse scoring, anchor 0 contributes 0 to items 1 and 4 and 4 to items 2 and 3, so
 * a questionnaire that lost all four selections scores **8 out of 16 — 50%**, and a
 * questionnaire that lost one lands a point or two either side of it. A stuck 44–50% is
 * exactly the fingerprint of answers not arriving.
 *
 * `Pss4RangeTest` proves the sum itself spans 0–16. This proves nothing gets between the
 * taps and the sum.
 */
class Pss4AnswersTest {

    @Test
    fun `a complete set passes through in item order, unchanged`() {
        assertEquals(listOf(4, 0, 0, 4), Pss4.completedResponses(listOf(4, 0, 0, 4)))
        assertEquals(listOf(0, 1, 2, 3), Pss4.completedResponses(listOf(0, 1, 2, 3)))
    }

    @Test
    fun `an unanswered item refuses to become a score`() {
        for (missing in 0 until Pss4.ITEMS.size) {
            val responses = MutableList<Int?>(Pss4.ITEMS.size) { 2 }.also { it[missing] = null }
            assertNull(
                "item ${missing + 1} was unanswered and the set was scored anyway",
                Pss4.completedResponses(responses),
            )
        }
        assertNull(Pss4.completedResponses(List(Pss4.ITEMS.size) { null }))
    }

    @Test
    fun `a short or long list is not an answer set`() {
        assertNull(Pss4.completedResponses(listOf(1, 2, 3)))
        assertNull(Pss4.completedResponses(listOf(1, 2, 3, 4, 0)))
    }

    /**
     * What the old default would have produced. Kept as an assertion rather than a comment
     * because it is the number that was reported from the device, and it explains why a
     * silent default is worse here than a crash would have been.
     */
    @Test
    fun `defaulting every missing answer to zero would have scored a plausible 50 percent`() {
        val allMissing = List<Int?>(Pss4.ITEMS.size) { null }
        val defaulted = allMissing.map { it ?: 0 }
        assertEquals(8, Pss4.score(defaulted))
        assertEquals(50, Pss4.percentOfMaximum(Pss4.score(defaulted)))
        // ...and the app now cannot get there at all.
        assertNull(Pss4.completedResponses(allMissing))
    }
}
