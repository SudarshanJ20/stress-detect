package com.stressdetect.ui.content

import com.stressdetect.survey.Pss4

/**
 * Score → a short, plain band label.
 *
 * ⚠️ **These bands are our own descriptive grouping, not a clinical instrument.** The
 * underlying 4-item scale has **no published cut-offs** — nobody has validated a threshold
 * at which a score becomes "moderate" or "high". They exist only so a number has some words
 * next to it. About states this in as many words; if that line ever disappears, these bands
 * become a quiet clinical claim we cannot support.
 *
 * Two rules on the copy:
 *  - the top band is never alarming. Someone genuinely struggling reads this.
 *  - no band implies a diagnosis, a trend, or that anything is wrong with the person.
 */
enum class Band(
    val label: String,
    /** One sentence. The result screen adds at most one more. */
    val blurb: String,
    /**
     * How much the figure's mouth curves upward, 0..1. **Never negative** — the range is
     * gentle-smile to straight, never a frown. See `BreathingFigure`.
     */
    val mouthCurve: Float,
) {
    LOW(
        label = "Low",
        blurb = "That's a settled reading — nothing here needs fixing.",
        mouthCurve = 1.0f,
    ),
    SOME(
        label = "Some — fairly ordinary",
        blurb = "That's around where a lot of weeks land.",
        mouthCurve = 0.55f,
    ),
    MODERATE(
        label = "Moderate — this is common",
        blurb = "Plenty of people sit here, and it tends to move about from week to week.",
        mouthCurve = 0.2f,
    ),
    HIGH(
        label = "High — be kind to yourself",
        blurb = "That sounds like a heavy stretch. Go gently this week.",
        mouthCurve = 0.0f,
    );

    companion object {
        fun forScore(score: Int): Band {
            require(score in Pss4.MIN_SCORE..Pss4.MAX_SCORE) { "score out of range: $score" }
            return when (score) {
                in 0..4 -> LOW
                in 5..8 -> SOME
                in 9..12 -> MODERATE
                else -> HIGH
            }
        }
    }
}
