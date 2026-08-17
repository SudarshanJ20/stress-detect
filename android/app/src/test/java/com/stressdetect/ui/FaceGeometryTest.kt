package com.stressdetect.ui

import com.stressdetect.ui.components.MouthGeometry
import com.stressdetect.ui.content.Band
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two things about the face that cannot be checked by looking at a Canvas call, and that
 * both matter:
 *
 *  1. **Every band draws a mouth.** The reported bug was a blank circle with two eyes. The
 *     level mouth used to be a quadratic Bézier with its control point exactly on the
 *     baseline — a degenerate curve a renderer may legitimately drop.
 *  2. **No band draws a frown.** Someone having a bad week must not be shown a sad face.
 */
class FaceGeometryTest {

    private val faceRadius = 100f
    private val centreY = 200f

    @Test
    fun `every band produces a mouth with real width`() {
        for (band in Band.entries) {
            val mouth = MouthGeometry.of(band.mouthCurve, faceRadius, centreY)
            assertTrue(
                "${band.name} has no mouth width — it would render as nothing",
                mouth.halfWidth > 0f,
            )
        }
    }

    @Test
    fun `the level mouth is flagged for line drawing, not a degenerate curve`() {
        // Band.HIGH is mouthCurve 0. It must take the drawLine path; expressed as a Bézier
        // with the control point on the baseline it can vanish entirely.
        val level = MouthGeometry.of(Band.HIGH.mouthCurve, faceRadius, centreY)
        assertTrue("the top band's mouth must be drawn as a line", level.isLevel)
    }

    @Test
    fun `a smiling band is drawn as a curve, not flattened to a line`() {
        val smile = MouthGeometry.of(Band.LOW.mouthCurve, faceRadius, centreY)
        assertTrue("the low band should curve", !smile.isLevel)
        assertTrue("the curve should be visible", smile.controlOffsetY > 2f)
    }

    @Test
    fun `no band curves the mouth upward into a frown`() {
        for (band in Band.entries) {
            val mouth = MouthGeometry.of(band.mouthCurve, faceRadius, centreY)
            // Canvas y grows downward, so the control point must be at or BELOW the
            // baseline. A negative offset would arc the stroke into a frown.
            assertTrue(
                "${band.name} would render a downturned mouth",
                mouth.controlOffsetY >= 0f,
            )
        }
    }

    @Test
    fun `a hostile curve value cannot produce a frown`() {
        // Defence in depth: Band clamps at 0, and the geometry coerces again. A future band
        // added with a negative curve still cannot make the face look sad.
        val hostile = MouthGeometry.of(-5f, faceRadius, centreY)
        assertTrue(hostile.controlOffsetY >= 0f)
        assertTrue("a negative curve must fall back to a level line", hostile.isLevel)
        assertTrue(hostile.halfWidth > 0f)
    }

    @Test
    fun `mouth sits below the eyes, inside the face`() {
        for (band in Band.entries) {
            val mouth = MouthGeometry.of(band.mouthCurve, faceRadius, centreY)
            assertTrue("${band.name} mouth is above centre", mouth.baselineY > centreY)
            val lowestPoint = mouth.baselineY + mouth.controlOffsetY / 2f
            assertTrue(
                "${band.name} mouth escapes the face circle",
                lowestPoint < centreY + faceRadius,
            )
        }
    }
}
