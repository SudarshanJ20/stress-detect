package com.stressdetect.ui

import com.stressdetect.ui.content.Band
import com.stressdetect.ui.content.ThingsThatHelp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one section on the result screen that is not about the person reading it.
 *
 * Which is the property worth testing: if this list ever starts varying with the score, the
 * app is implying it worked something out about somebody, and it did not.
 */
class ThingsThatHelpTest {

    @Test
    fun `the same four things, whatever the score`() {
        assertEquals(4, ThingsThatHelp.ITEMS.size)
        // Nothing in the API takes a score, a week, or a feature — but pin the list too, so
        // that reordering it by anything becomes a deliberate act with a failing test.
        assertEquals(
            listOf(
                "Ten minutes outside, ideally in daylight",
                "Slow breathing for a couple of minutes: longer out-breath than in-breath",
                "Moving your body, even a short walk",
                "Talking to someone you trust",
            ),
            ThingsThatHelp.ITEMS,
        )
    }

    @Test
    fun `the extra line appears at the top band and nowhere else`() {
        assertNotNull(ThingsThatHelp.extraFor(Band.HIGH))
        for (band in Band.entries - Band.HIGH) {
            assertNull("${band.name} must not get the extra line", ThingsThatHelp.extraFor(band))
        }
    }

    @Test
    fun `the extra line points past the app without alarming anyone`() {
        val extra = ThingsThatHelp.extraFor(Band.HIGH)!!
        assertTrue("it must name someone to talk to", extra.contains("talking to someone"))
        assertTrue("...including one they can actually reach", extra.contains("counsellor"))
        // It is a pointer to ordinary support, not a referral or a warning.
        for (word in listOf("urgent", "immediately", "help line", "emergency", "must", "should")) {
            assertFalse("'$word' turns a suggestion into an instruction", extra.lowercase().contains(word))
        }
    }

    @Test
    fun `none of it reads as research, clinical or alarming`() {
        val banned = listOf(
            "questionnaire", "validated", "model", "spec", "baseline", "percentile",
            "diagnos", "disorder", "symptom", "patient", "illness", "treatment", "therapy",
            "severe", "dangerous", "abnormal", "at risk", "concerning", "critical",
        )
        for (copy in ThingsThatHelp.allCopyStrings()) {
            for (word in banned) {
                assertFalse("'$word' appears in: $copy", copy.lowercase().contains(word))
            }
        }
    }

    @Test
    fun `nothing claims the app chose these for you`() {
        val personal = listOf("for you", "based on", "we suggest", "your score", "because you")
        for (copy in ThingsThatHelp.allCopyStrings()) {
            for (phrase in personal) {
                assertFalse(
                    "'$phrase' implies these were selected for this person: $copy",
                    copy.lowercase().contains(phrase),
                )
            }
        }
    }
}
