package com.stressdetect.data

import com.stressdetect.features.FeatureExtractor
import com.stressdetect.features.WindowFeatureVector
import java.time.format.DateTimeFormatter

/**
 * Maps between the spec's feature MAP (snake_case names, [Double.NaN] for missing) and the
 * Room row (typed columns, `null` for missing).
 *
 * NaN is converted to `null` on the way in and back to NaN on the way out. SQLite has no
 * reliable NaN representation, and a NaN silently persisted as `0.0` would read back as a
 * real measurement — "this user slept 0 hours" instead of "we don't know".
 */
internal object FeatureVectorMapper {

    private val DATE = DateTimeFormatter.ISO_LOCAL_DATE

    fun toEntity(vector: WindowFeatureVector, computedAtUtc: Long): FeatureVectorEntity {
        val v = vector.values
        return FeatureVectorEntity(
            labelDate = vector.labelDate.format(DATE),
            specVersion = vector.specVersion,
            zoneId = vector.zoneId,
            windowStartUtc = vector.window.startUtc,
            windowEndUtc = vector.window.endUtc,
            computedAtUtc = computedAtUtc,
            meetsCoverage = vector.meetsCoverage,

            daysWithData = v.nullable("days_with_data"),
            nSleepNights = v.nullable("n_sleep_nights"),
            sleepDurationMedian = v.nullable("sleep_duration_median"),
            sleepOnsetHours = v.nullable("sleep_onset_hours"),
            sleepWakeHours = v.nullable("sleep_wake_hours"),
            sleepOnsetRegularity = v.nullable("sleep_onset_regularity"),
            sleepMidpointRegularity = v.nullable("sleep_midpoint_regularity"),

            unlockCountPerDayMean = v.nullable("unlock_count_per_day_mean"),
            unlockCountSd = v.nullable("unlock_count_sd"),
            sessionCountPerDayMean = v.nullable("session_count_per_day_mean"),
            sessionDurationMedian = v.nullable("session_duration_median"),
            sessionDurationIqr = v.nullable("session_duration_iqr"),
            screenOnFraction = v.nullable("screen_on_fraction"),

            nighttimeUseFractionPersonal = v.nullable("nighttime_use_fraction_personal"),
            nighttimeUnlockPerDayPersonal = v.nullable("nighttime_unlock_per_day_personal"),
            nighttimeUseFractionFixed = v.nullable("nighttime_use_fraction_fixed"),
            nighttimeUnlockPerDayFixed = v.nullable("nighttime_unlock_per_day_fixed"),
            circadianRegularity = v.nullable("circadian_regularity"),

            callCountPerDay = v.nullable("call_count_per_day"),
            callPresent = v.nullable("call_present"),
            smsCountPerDay = v.nullable("sms_count_per_day"),
            smsPresent = v.nullable("sms_present"),
        )
    }

    /** Feature map in the canonical [FeatureExtractor.FEATURE_NAMES] order. */
    fun toValues(entity: FeatureVectorEntity): Map<String, Double> {
        val byName = mapOf(
            "days_with_data" to entity.daysWithData,
            "n_sleep_nights" to entity.nSleepNights,
            "sleep_duration_median" to entity.sleepDurationMedian,
            "sleep_onset_hours" to entity.sleepOnsetHours,
            "sleep_wake_hours" to entity.sleepWakeHours,
            "sleep_onset_regularity" to entity.sleepOnsetRegularity,
            "sleep_midpoint_regularity" to entity.sleepMidpointRegularity,
            "unlock_count_per_day_mean" to entity.unlockCountPerDayMean,
            "unlock_count_sd" to entity.unlockCountSd,
            "session_count_per_day_mean" to entity.sessionCountPerDayMean,
            "session_duration_median" to entity.sessionDurationMedian,
            "session_duration_iqr" to entity.sessionDurationIqr,
            "screen_on_fraction" to entity.screenOnFraction,
            "nighttime_use_fraction_personal" to entity.nighttimeUseFractionPersonal,
            "nighttime_unlock_per_day_personal" to entity.nighttimeUnlockPerDayPersonal,
            "nighttime_use_fraction_fixed" to entity.nighttimeUseFractionFixed,
            "nighttime_unlock_per_day_fixed" to entity.nighttimeUnlockPerDayFixed,
            "circadian_regularity" to entity.circadianRegularity,
            "call_count_per_day" to entity.callCountPerDay,
            "call_present" to entity.callPresent,
            "sms_count_per_day" to entity.smsCountPerDay,
            "sms_present" to entity.smsPresent,
        )
        val out = LinkedHashMap<String, Double>(FeatureExtractor.FEATURE_NAMES.size)
        for (name in FeatureExtractor.FEATURE_NAMES) {
            out[name] = byName[name] ?: Double.NaN
        }
        return out
    }

    private fun Map<String, Double>.nullable(name: String): Double? {
        val value = this[name] ?: error("feature '$name' missing from the computed vector")
        return if (value.isNaN()) null else value
    }
}
