package com.stressdetect.ui

import com.stressdetect.ui.components.heroFontScaleCap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The hero number must not clip at large system font sizes.
 *
 * At 2.0x an uncapped 76sp "100%" needs roughly 320dp of width; a 320dp-wide phone has 272dp
 * between the gutters, so it would be cut off. A clipped score is worse for accessibility
 * than one that stopped growing — and body text, which is where the accessibility need
 * actually lies, still scales the whole way.
 *
 * These numbers are checked here rather than by eye because the failure only appears at a
 * font scale nobody tests by default.
 */
class HeroScaleTest {

    /** Width of the widest value, "100%", as a multiple of the font size. Measured from the
     * Fraunces advance widths: three digits at ~0.56em plus "%" at ~1.0em. */
    private val heroWidthPerSp = 0.56 * 3 + 1.0

    @Test
    fun `no cap applies at or below the threshold`() {
        assertEquals(1f, heroFontScaleCap(1.0f), 1e-6f)
        assertEquals(1f, heroFontScaleCap(1.15f), 1e-6f)
        assertEquals(1f, heroFontScaleCap(1.3f), 1e-6f)
    }

    @Test
    fun `above the threshold the hero stops growing`() {
        // The cap is a ratio applied to the declared size, so effective size is constant.
        val effectiveAt2x = 76f * heroFontScaleCap(2.0f) * 2.0f
        val effectiveAt13x = 76f * heroFontScaleCap(1.3f) * 1.3f
        assertEquals(
            "the hero should render at the same physical size once capped",
            effectiveAt13x.toDouble(), effectiveAt2x.toDouble(), 0.5,
        )
    }

    @Test
    fun `the widest hero fits the narrowest supported screen at every font scale`() {
        // 320dp screen, 24dp gutters each side.
        val available = 320.0 - 24.0 * 2
        for (scale in listOf(1.0f, 1.15f, 1.3f, 1.5f, 1.8f, 2.0f)) {
            val renderedSp = 76.0 * heroFontScaleCap(scale) * scale
            val widthDp = renderedSp * heroWidthPerSp
            assertTrue(
                "at font scale $scale the hero needs %.0fdp but only %.0fdp is available"
                    .format(widthDp, available),
                widthDp <= available,
            )
        }
    }

    @Test
    fun `an uncapped hero would have clipped — this is what the cap prevents`() {
        // Documents the bug rather than just the fix: without the cap, 2.0x overflows.
        val uncappedWidth = 76.0 * 2.0 * heroWidthPerSp
        assertTrue(
            "uncapped 2.0x should exceed the available width, else the cap is unnecessary",
            uncappedWidth > 320.0 - 48.0,
        )
    }
}
