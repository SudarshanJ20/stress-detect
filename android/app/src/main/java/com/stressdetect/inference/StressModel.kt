package com.stressdetect.inference

import com.stressdetect.features.SequenceFeatures
import com.stressdetect.features.SpecConstants

/**
 * ONNX Runtime Mobile inference — **Phase 6, deliberately not implemented here.** Phase 5
 * is the data layer only; this interface exists so the package boundary (and the Konsist
 * rule that `inference` may depend on `features` but never the reverse) is real from the
 * start.
 *
 * The input contract is fixed by `docs/feature-spec.md` §7 and is embedded in the exported
 * model's `metadata_props`:
 * ```
 * input  seq    : float32 (1, 7, 8)   # dynamic, standardized
 * input  static : float32 (1, 9)      # static, standardized
 * output stress : float32 (1,)        # 0–100, 100 = most stressed
 * ```
 * Batch is always 1 — the app scores one user-window at a time.
 *
 * Two obligations for whoever implements this:
 * 1. **Verify `spec_version` in the model metadata equals [SpecConstants.SPEC_VERSION]** and
 *    refuse to run on mismatch. A model trained under different feature definitions will
 *    happily produce a confident, meaningless number.
 * 2. **Standardize with the mean/sd exported alongside the model, then map NaN → 0** (in
 *    that order — 0 means "the train-fold mean", not "zero hours"). [SequenceFeatures]
 *    deliberately emits raw values with NaN intact.
 */
interface StressModel {

    /**
     * @param sequence `[7][8]` dynamic features, oldest day → newest
     *   ([SequenceFeatures.DYNAMIC_FEATURE_NAMES]).
     * @param static `[9]` window-level features ([SequenceFeatures.STATIC_FEATURE_NAMES]).
     * @return stress score 0–100, where 100 is most stressed.
     *
     * The score is only ever shown to the user as a delta against their OWN baseline —
     * never as an absolute or population percentile (root CLAUDE.md).
     */
    fun predict(sequence: Array<DoubleArray>, static: DoubleArray): Float
}
