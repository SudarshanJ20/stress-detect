package com.stressdetect.ui

import androidx.compose.ui.graphics.Color
import com.stressdetect.ui.theme.CalmColors
import com.stressdetect.ui.theme.CalmPalette
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * WCAG 2.1 contrast, computed from the real palette constants.
 *
 * This exists because the palette as first specified failed: white on the terracotta accent
 * (`#E2704A`) measures **3.16:1**, below the 4.5:1 AA needs for a 16sp button label. Buttons
 * therefore fill with `accentDeep` (`#C1532F`, 4.62:1). Without a test, the next person to
 * "make the buttons a bit warmer" reintroduces an accessibility failure that looks like a
 * design preference.
 *
 * Thresholds: 4.5:1 for normal text, 3:1 for graphical objects such as the figure's stroke.
 */
class ContrastTest {

    private fun relativeLuminance(color: Color): Double {
        fun channel(value: Float): Double {
            val v = value.toDouble()
            return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(color.red) +
            0.7152 * channel(color.green) +
            0.0722 * channel(color.blue)
    }

    private fun ratio(a: Color, b: Color): Double {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        val lighter = maxOf(la, lb)
        val darker = minOf(la, lb)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun assertText(name: String, foreground: Color, background: Color) {
        val r = ratio(foreground, background)
        assertTrue(
            "$name is %.2f:1 — below the 4.5:1 AA minimum for normal text".format(r),
            r >= 4.5,
        )
    }

    private fun assertGraphical(name: String, foreground: Color, background: Color) {
        val r = ratio(foreground, background)
        assertTrue(
            "$name is %.2f:1 — below the 3:1 minimum for graphical objects".format(r),
            r >= 3.0,
        )
    }

    private fun checkTheme(label: String, c: CalmColors) {
        // Body and secondary text on both backgrounds it can sit on.
        assertText("$label: primary text on paper", c.ink, c.paper)
        assertText("$label: primary text on card", c.ink, c.card)
        assertText("$label: secondary text on paper", c.mutedInk, c.paper)
        assertText("$label: secondary text on card", c.mutedInk, c.card)

        // Links and suggestion text use the teal.
        assertText("$label: link on paper", c.secondary, c.paper)
        assertText("$label: link on card", c.secondary, c.card)

        // Button labels. THIS is the pair that failed twice — once in light mode with the
        // plain accent, once in dark mode where white fails on every terracotta.
        assertText("$label: button label on button fill", c.onButton, c.buttonFill)

        // The figure: its stroke against the wash behind it, and the wash against the paper.
        assertGraphical("$label: figure stroke on its wash", c.accentDeep, c.accentMuted)
        assertGraphical("$label: figure stroke on paper", c.accentDeep, c.paper)

        // Selected answer rows: 2dp accent border against the card it sits on.
        assertGraphical("$label: selected option border on card", c.accent, c.card)

        // The trend arrows. They are DRAWN marks, not glyphs, so the 3:1 graphical threshold
        // is the right one — see `TrendArrow` and the test below for why that distinction is
        // load-bearing rather than a convenience.
        assertGraphical("$label: trend arrow on paper", c.accentDeep, c.paper)
    }

    @Test
    fun `light theme meets AA for every text pair in use`() = checkTheme("light", CalmPalette.light)

    @Test
    fun `dark theme meets AA for every text pair in use`() = checkTheme("dark", CalmPalette.dark)

    /**
     * Why the trend arrows are drawn rather than typed.
     *
     * As a text glyph, "↑" in `accentDeep` would be normal text on paper and would need
     * 4.5:1. In light mode it measures 4.42:1 and fails. Drawn, it is a graphical object at
     * 3:1 and passes with room to spare — and the direction is carried by the shape and by
     * the words beside it either way, so no meaning rests on the colour.
     */
    @Test
    fun `the arrow colour would FAIL as text in light mode — this is why it is drawn`() {
        val r = ratio(CalmPalette.light.accentDeep, CalmPalette.light.paper)
        assertTrue(
            "accentDeep on paper now measures %.2f:1; if that is >= 4.5 the arrow could be ".format(r) +
                "a text glyph again — change it deliberately, not by accident",
            r < 4.5,
        )
    }

    @Test
    fun `the plain accent is NOT safe under white text — this is why buttons use accentDeep`() {
        // Documents the measurement rather than asserting a preference. If this ever starts
        // passing, the accent changed and the button fill can be revisited.
        val r = ratio(Color.White, CalmPalette.light.accent)
        assertTrue(
            "white on the light accent now measures %.2f:1; if that is >= 4.5 the button fill ".format(r) +
                "could go back to `accent` — check deliberately rather than by accident",
            r < 4.5,
        )
    }
}
