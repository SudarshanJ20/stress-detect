package com.stressdetect.ui.components

/**
 * The figure's mouth geometry, as plain numbers.
 *
 * Extracted from the drawing code so it can be unit-tested. Two properties matter and
 * neither is checkable by looking at a Canvas call:
 *
 *  1. **The mouth always has length.** A level mouth (top band) is a straight horizontal
 *     stroke, not nothing. The previous implementation expressed it as a quadratic Bézier
 *     whose control point sat exactly on the baseline — a degenerate curve, which is the
 *     kind of path a renderer is entitled to drop, leaving two eyes on a blank circle.
 *  2. **The mouth never turns down.** [controlOffsetY] is >= 0, i.e. the control point is on
 *     or below the baseline in canvas coordinates, which is at or below a straight line on
 *     screen. No score can produce a frown.
 */
internal data class MouthGeometry(
    /** Half the mouth's width, in px. Always > 0. */
    val halfWidth: Float,
    /** Vertical baseline of the mouth's endpoints, in px from the top of the canvas. */
    val baselineY: Float,
    /**
     * How far BELOW the baseline the Bézier control point sits, in px (canvas y grows
     * downward, so a positive value curves the stroke upward into a smile). 0 = level.
     */
    val controlOffsetY: Float,
) {
    /** True when the curve is flat enough that it must be stroked as a plain line. */
    val isLevel: Boolean get() = controlOffsetY < LEVEL_THRESHOLD_PX

    internal companion object {
        /**
         * Below this the curve is visually indistinguishable from a line, and expressing it
         * as a Bézier risks the degenerate-path bug above. Drawn as a line instead.
         */
        const val LEVEL_THRESHOLD_PX = 0.75f

        /**
         * @param mouthCurve 0..1 from the band; 0 is level, 1 a gentle smile. Values are
         *   coerced, so a future band cannot produce a downturned mouth even by mistake.
         */
        fun of(mouthCurve: Float, faceRadius: Float, centreY: Float): MouthGeometry =
            MouthGeometry(
                halfWidth = faceRadius * 0.34f,
                baselineY = centreY + faceRadius * 0.30f,
                controlOffsetY = faceRadius * 0.42f * mouthCurve.coerceIn(0f, 1f),
            )
    }
}
