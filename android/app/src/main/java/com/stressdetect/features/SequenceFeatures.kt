package com.stressdetect.features

import java.time.LocalDate
import java.time.ZoneId

/**
 * Port of `ml/src/features/sequence_dataset.py` — the (7, 8) daily sequence + (9,) static
 * vector the Phase-4 temporal model consumes (`docs/feature-spec.md` §7).
 *
 * The same window and the same [ScreenLockFeatures] logic back both this and the flat
 * vector; the sequence just applies the window features to ONE day at a time so the model
 * sees the daily usage rhythm. Sleep / regularity / circadian stay static because a single
 * calendar day cannot define them (sleep crosses midnight).
 *
 * **Raw values only.** §7 describes a missing day as "0", but that refers to the value
 * AFTER standardization — `ml/src/training/loso_torch.py` standardizes with train-fold
 * statistics and only then maps NaN to 0 (i.e. to the train mean). This class therefore
 * emits raw values with [Double.NaN] intact, exactly like `_daily_vector`; standardizing
 * and NaN-filling belong to the inference step, using the mean/sd exported with the model.
 */
object SequenceFeatures {

    val DYNAMIC_FEATURE_NAMES: List<String> = listOf(
        "unlock_count", "session_count", "session_duration_median", "screen_on_fraction",
        "nighttime_use_fraction_fixed", "call_count", "sms_count", "has_data",
    )

    val STATIC_FEATURE_NAMES: List<String> = listOf(
        "sleep_duration_median", "sleep_onset_hours", "sleep_wake_hours",
        "sleep_onset_regularity", "sleep_midpoint_regularity", "circadian_regularity",
        "days_with_data", "call_present", "sms_present",
    )

    /**
     * @return `[7][8]` dynamic sequence, oldest day → newest, mirroring
     *   `days = [local_date - timedelta(days=WINDOW_DAYS - k) for k in range(WINDOW_DAYS)]`.
     *   Note those are CALENDAR day steps, while each day's bounds are midnight + 86400 s
     *   absolute — both reproduced here.
     */
    fun dynamicSequence(
        locked: List<LockedInterval>,
        callTimestamps: List<Long>,
        smsTimestamps: List<Long>,
        labelDate: LocalDate,
        zone: ZoneId,
    ): Array<DoubleArray> = Array(SpecConstants.WINDOW_DAYS) { k ->
        val day = labelDate.minusDays((SpecConstants.WINDOW_DAYS - k).toLong())
        dailyVector(locked, callTimestamps, smsTimestamps, day, zone)
    }

    /** Mirrors `_daily_vector`: the window features over a single local day. */
    fun dailyVector(
        locked: List<LockedInterval>,
        callTimestamps: List<Long>,
        smsTimestamps: List<Long>,
        day: LocalDate,
        zone: ZoneId,
    ): DoubleArray {
        val dayStart = day.atStartOfDay(zone).toInstant().epochSecond
        val dayEnd = dayStart + SpecConstants.SECONDS_PER_DAY
        val f = ScreenLockFeatures.windowFeatures(locked, dayStart, dayEnd, zone)
        val hasData = f.getValue("days_with_data") >= 1
        return doubleArrayOf(
            f.getValue("unlock_count_per_day_mean"),
            f.getValue("session_count_per_day_mean"),
            f.getValue("session_duration_median"),
            f.getValue("screen_on_fraction"),
            f.getValue("nighttime_use_fraction_fixed"),
            callTimestamps.count { it >= dayStart && it < dayEnd }.toDouble(),
            smsTimestamps.count { it >= dayStart && it < dayEnd }.toDouble(),
            if (hasData) 1.0 else 0.0,
        )
    }

    /** The (9,) static vector, pulled from the window-level features by name. */
    fun staticVector(
        windowFeatures: Map<String, Double>,
        auxFeatures: Map<String, Double>,
    ): DoubleArray {
        val all = windowFeatures + auxFeatures
        return DoubleArray(STATIC_FEATURE_NAMES.size) { i ->
            val name = STATIC_FEATURE_NAMES[i]
            all[name] ?: error("static feature '$name' is missing from the window vector")
        }
    }
}
