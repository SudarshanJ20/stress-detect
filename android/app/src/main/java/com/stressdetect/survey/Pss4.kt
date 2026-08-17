package com.stressdetect.survey

/**
 * The Perceived Stress Scale — 4 item (PSS-4), Cohen, Kamarck & Mermelstein (1983).
 *
 * **The wording below is the published wording and must not be paraphrased, shortened, or
 * "made friendlier".** The scale's validation belongs to these exact sentences; edit them
 * and the instrument is no longer PSS-4, while still looking like it. `Pss4Test` asserts
 * each string verbatim so a well-meaning copy edit fails the build.
 *
 * Verified against two independent sources (2026-08):
 *  - Cohen PSS-4, scholar.harvard.edu/files/bettina.hoeppner/files/pss-4.pdf
 *  - PSS-4 psychometrics, PMC5791241
 *
 * Scoring: anchors 0–4; **items 2 and 3 are reverse-scored** (they are positively worded,
 * so agreement indicates LESS stress); total 0–16, higher = more perceived stress.
 *
 * ⚠️ Timeframe: the published stem asks about **the last month**, while this app's phone
 * analysis covers **7 days**. The two numbers on the result screen therefore describe
 * different periods, and the UI says so. Do not "fix" this by rewriting the stem to a week
 * — that would void the validation (see docs/feature-spec.md and the Phase-7 notes).
 *
 * Not a diagnostic instrument: PSS-4 measures *perceived* stress and has no clinical
 * cut-off. Nothing in this app may present a band, a threshold, or a diagnosis.
 */
object Pss4 {

    /** Response anchors, in presentation order. The index IS the raw score (0–4). */
    val ANCHORS: List<String> = listOf(
        "Never",
        "Almost never",
        "Sometimes",
        "Fairly often",
        "Very often",
    )

    val ITEMS: List<Pss4Item> = listOf(
        Pss4Item(
            number = 1,
            text = "In the last month, how often have you felt that you were unable to " +
                "control the important things in your life?",
            reverseScored = false,
        ),
        Pss4Item(
            number = 2,
            text = "In the last month, how often have you felt confident about your " +
                "ability to handle your personal problems?",
            reverseScored = true,
        ),
        Pss4Item(
            number = 3,
            text = "In the last month, how often have you felt that things were going " +
                "your way?",
            reverseScored = true,
        ),
        Pss4Item(
            number = 4,
            text = "In the last month, how often have you felt difficulties were piling " +
                "up so high that you could not overcome them?",
            reverseScored = false,
        ),
    )

    const val MIN_SCORE: Int = 0
    const val MAX_SCORE: Int = 16
    const val MAX_ANCHOR: Int = 4

    /** Attribution shown in the UI, so the instrument is never presented as ours. */
    const val CITATION: String =
        "Perceived Stress Scale (PSS-4) — Cohen, Kamarck & Mermelstein, 1983. " +
            "Standard published wording."

    /**
     * @param responses raw anchor indices (0–4) in item order, one per item.
     * @return total 0–16, higher = more perceived stress.
     */
    fun score(responses: List<Int>): Int {
        require(responses.size == ITEMS.size) {
            "PSS-4 needs exactly ${ITEMS.size} responses, got ${responses.size}"
        }
        return responses.mapIndexed { index, raw -> ITEMS[index].contribution(raw) }.sum()
    }

    /**
     * The four selections as answers, or `null` if any item is still unanswered.
     *
     * **There is deliberately no default.** Substituting anchor 0 for a missing answer is not
     * a neutral choice under reverse scoring: it contributes 0 to items 1 and 4 but 4 to
     * items 2 and 3, so a questionnaire that lost every selection scores 8 out of 16 — a
     * perfectly ordinary mid-scale result, indistinguishable on screen from one somebody
     * actually gave. A missing answer must stop the submission, not quietly become one.
     */
    fun completedResponses(responses: List<Int?>): List<Int>? {
        if (responses.size != ITEMS.size) return null
        return responses.map { it ?: return null }
    }

    /** The score as a 0–100 percentage of the scale's maximum. NOT a percentile. */
    fun percentOfMaximum(totalScore: Int): Int {
        require(totalScore in MIN_SCORE..MAX_SCORE) { "PSS-4 total out of range: $totalScore" }
        return Math.round(totalScore * 100f / MAX_SCORE)
    }
}

data class Pss4Item(
    val number: Int,
    val text: String,
    val reverseScored: Boolean,
) {
    /**
     * This item's contribution to the total. Reverse-scored items map 0↔4, 1↔3, 2→2:
     * "very often felt confident" is the LEAST stressed answer, so it must contribute 0.
     */
    fun contribution(rawResponse: Int): Int {
        require(rawResponse in 0..Pss4.MAX_ANCHOR) {
            "PSS-4 response out of range for item $number: $rawResponse"
        }
        return if (reverseScored) Pss4.MAX_ANCHOR - rawResponse else rawResponse
    }
}
