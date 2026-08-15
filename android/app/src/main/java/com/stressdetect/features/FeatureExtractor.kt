package com.stressdetect.features

import java.time.LocalDate
import java.time.ZoneId

/**
 * The full window feature vector: backbone (screen/lock) + auxiliary (call/SMS).
 *
 * [meetsCoverage] is the `COVERAGE_MIN_DAYS` gate from `build_dataset.py`. A window below
 * it is NOT scored — too little data to summarize. The vector is still returned (and
 * cached) so the shortfall is inspectable rather than silently swallowed.
 */
data class WindowFeatureVector(
    val labelDate: LocalDate,
    val window: AnalysisWindow,
    val zoneId: String,
    val specVersion: String,
    val values: Map<String, Double>,
) {
    val daysWithData: Double get() = values.getValue("days_with_data")

    val meetsCoverage: Boolean get() = daysWithData >= SpecConstants.COVERAGE_MIN_DAYS

    fun asArray(): DoubleArray =
        DoubleArray(FeatureExtractor.FEATURE_NAMES.size) { i ->
            values.getValue(FeatureExtractor.FEATURE_NAMES[i])
        }
}

/**
 * Assembles one window's feature vector, mirroring the per-sample body of
 * `ml/src/features/build_dataset.py::build_dataset`.
 *
 * Pure JVM: no Android types, no I/O. `sensing` supplies the intervals and timestamps,
 * `data` caches the result — this layer only computes.
 */
object FeatureExtractor {

    /** Column order = Python's `screenlock_features.feature_names() + aux_features.feature_names()`. */
    val FEATURE_NAMES: List<String> = ScreenLockFeatures.FEATURE_NAMES + AuxFeatures.FEATURE_NAMES

    fun extract(
        locked: List<LockedInterval>,
        callTimestamps: List<Long>,
        smsTimestamps: List<Long>,
        labelDate: LocalDate,
        zone: ZoneId,
        hasCalls: Boolean,
        hasSms: Boolean,
    ): WindowFeatureVector {
        val window = AnalysisWindow.endingAtMidnightOf(labelDate, zone)
        val backbone = ScreenLockFeatures.windowFeatures(locked, window.startUtc, window.endUtc, zone)
        val aux = AuxFeatures.windowFeatures(
            callTimestamps, smsTimestamps, window.startUtc, window.endUtc, hasCalls, hasSms,
        )
        return WindowFeatureVector(
            labelDate = labelDate,
            window = window,
            zoneId = zone.id,
            specVersion = SpecConstants.SPEC_VERSION,
            values = backbone + aux,
        )
    }
}
