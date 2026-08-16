package com.stressdetect.inference

import com.stressdetect.features.SequenceFeatures

/**
 * Local per-feature attribution for ONE window, by occlusion.
 *
 * For each feature we replace it with the training mean (which is 0 after standardization,
 * so occluding = "as if this person were average here") and re-run the model. The shift in
 * the output is that feature's contribution to *this* estimate. About 17 extra forward
 * passes — milliseconds.
 *
 * **This is occlusion attribution, not SHAP.** The Phase-4 pipeline computes SHAP, but that
 * yields GLOBAL importances — what the model responds to on average across StudentLife —
 * which cannot answer "what stood out for this person this week". Ranking by global SHAP ×
 * distance-from-the-training-distribution would answer it only by comparing the user to a
 * population, which this project forbids (root CLAUDE.md: self-baseline only). Occlusion is
 * local by construction and needs no population comparison, so that is what ships. The UI
 * must not call it SHAP.
 *
 * What it explains is **the model**, not stress. Under the Phase-4 null the model carries
 * no validated stress signal, so these rankings are faithful to the model and say nothing
 * causal about the person. The result screen states this.
 */
object OcclusionAttribution {

    /**
     * @param excludedFeatures names to leave out of the ranking entirely — e.g. call/SMS
     *   features when the user declined that permission. They are excluded rather than
     *   scored as 0, because "absent" is not "average", and the caller is expected to TELL
     *   the user they were left out rather than quietly shrink the list.
     */
    fun rank(
        model: StressModel,
        sequence: Array<DoubleArray>,
        static: DoubleArray,
        excludedFeatures: Set<String> = emptySet(),
    ): List<FeatureContribution> {
        val baseline = model.predict(sequence, static).toDouble()
        val contributions = mutableListOf<FeatureContribution>()

        // Dynamic features are occluded across ALL 7 days at once: the question is "did
        // this person's night-time use matter to the estimate", not "did Tuesday matter".
        SequenceFeatures.DYNAMIC_FEATURE_NAMES.forEachIndexed { index, name ->
            if (name in excludedFeatures || name == HAS_DATA_CHANNEL) return@forEachIndexed
            val occluded = sequence.map { it.copyOf() }.toTypedArray()
            for (day in occluded) day[index] = Double.NaN   // NaN → the training mean
            contributions += FeatureContribution(
                featureName = name,
                delta = model.predict(occluded, static).toDouble() - baseline,
            )
        }

        SequenceFeatures.STATIC_FEATURE_NAMES.forEachIndexed { index, name ->
            if (name in excludedFeatures || name in NON_BEHAVIOURAL_STATIC) return@forEachIndexed
            val occluded = static.copyOf()
            occluded[index] = Double.NaN
            contributions += FeatureContribution(
                featureName = name,
                delta = model.predict(sequence, occluded).toDouble() - baseline,
            )
        }

        return contributions.sortedByDescending { kotlin.math.abs(it.delta) }
    }

    /**
     * The mask channel and the presence/coverage flags describe DATA AVAILABILITY, not
     * behaviour. Ranking "we could see your calls" as a factor in someone's week would be
     * meaningless to read and faintly absurd.
     */
    private const val HAS_DATA_CHANNEL = "has_data"
    private val NON_BEHAVIOURAL_STATIC = setOf("days_with_data", "call_present", "sms_present")
}

/**
 * One feature's effect on this window's estimate.
 *
 * [delta] is (occluded − actual): NEGATIVE means removing the feature LOWERED the estimate,
 * i.e. the feature pushed the estimate UP. Magnitude is in stress-score points.
 */
data class FeatureContribution(
    val featureName: String,
    val delta: Double,
) {
    val magnitude: Double get() = kotlin.math.abs(delta)

    /** True when this feature pushed the model's estimate upward for this window. */
    val pushedEstimateUp: Boolean get() = delta < 0
}
