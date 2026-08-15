package com.stressdetect.features

/**
 * Port of `ml/src/features/aux_features.py`.
 *
 * Auxiliary call/SMS features. Per `docs/feature-spec.md` §1/§4 these are **never
 * backbone**, and every value MUST travel with an explicit `*_present` missingness flag: a
 * user whose stream is unavailable gets value 0 AND present 0, so the model can tell
 * "no calls this week" apart from "we cannot see calls". Emitting the value without the
 * flag is a spec violation, not a style choice.
 *
 * On-device, "has the stream" means the runtime permission is granted AND the provider is
 * readable — see `sensing/CommsSource`. On StudentLife it means the participant had a
 * non-empty call/SMS stream.
 */
object AuxFeatures {

    val FEATURE_NAMES: List<String> =
        listOf("call_count_per_day", "call_present", "sms_count_per_day", "sms_present")

    fun windowFeatures(
        callTimestamps: List<Long>,
        smsTimestamps: List<Long>,
        w0: Long,
        w1: Long,
        hasCalls: Boolean,
        hasSms: Boolean,
    ): Map<String, Double> {
        val (callRate, callPresent) = rate(callTimestamps, w0, w1, hasCalls)
        val (smsRate, smsPresent) = rate(smsTimestamps, w0, w1, hasSms)
        return linkedMapOf(
            "call_count_per_day" to callRate,
            "call_present" to callPresent,
            "sms_count_per_day" to smsRate,
            "sms_present" to smsPresent,
        )
    }

    /** Returns (value, present). Note the rate divides by WINDOW_DAYS, not days-with-data. */
    private fun rate(
        timestamps: List<Long>,
        w0: Long,
        w1: Long,
        hasStream: Boolean,
    ): Pair<Double, Double> {
        if (!hasStream) return 0.0 to 0.0
        val n = timestamps.count { it >= w0 && it < w1 }
        return n.toDouble() / SpecConstants.WINDOW_DAYS to 1.0
    }
}
