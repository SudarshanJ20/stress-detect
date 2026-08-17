package com.stressdetect.ui

import com.stressdetect.survey.Pss4
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The answers travel WITH the destination.
 *
 * They used to live beside it: the analysing screen read questionnaire state held elsewhere,
 * a deliberate pause later, and filled any gap with anchor 0. Under reverse scoring that
 * default is not neutral — a set that lost all four selections scores 8 of 16, which is a
 * perfectly ordinary-looking 50%.
 *
 * Carrying them in the destination means there is no window in which they can be reset, and
 * requiring them complete means an incomplete set cannot be scored at all.
 */
class NavigationTest {

    @Test
    fun `an analysing destination carries the four answers it will be scored on`() {
        val screen = Screen.Analysing(listOf(4, 0, 0, 4))
        assertEquals(listOf(4, 0, 0, 4), screen.answers)
        assertEquals(Pss4.MAX_SCORE, Pss4.score(screen.answers))
    }

    @Test
    fun `two different answer sets give two different destinations`() {
        // The reported symptom was a score that barely moved. If the destinations for the
        // two extremes were ever equal, the answers would not be reaching the scorer.
        val most = Screen.Analysing(listOf(4, 0, 0, 4))
        val least = Screen.Analysing(listOf(0, 4, 4, 0))
        assert(most != least)
        assertEquals(Pss4.MAX_SCORE, Pss4.score(most.answers))
        assertEquals(Pss4.MIN_SCORE, Pss4.score(least.answers))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `an incomplete set cannot become a destination`() {
        Screen.Analysing(listOf(4, 0, 0))
    }

    @Test
    fun `submitting is not reversible`() {
        // Unchanged behaviour, re-pinned because Analysing gained a payload: backing into a
        // submitted questionnaire would show stale answers and invite a second submission.
        val stack = BackStack(Screen.Home)
        stack.push(Screen.CheckIn(0))
        stack.push(Screen.Analysing(listOf(1, 2, 3, 4)))
        stack.replaceAll(Screen.Home)
        assertEquals(false, stack.canGoBack)
    }
}
