package com.stressdetect.features

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Backbone features from LOCKED intervals over one 7-day window.
 *
 * **This is a line-for-line port of `ml/src/features/screenlock_features.py`.** Both sides
 * must produce identical vectors from identical input (root CLAUDE.md constraint 2), so
 * any change here requires the same change there, in `docs/feature-spec.md`, and a
 * SPEC_VERSION bump — never one alone.
 *
 * Clock/night/sleep quantities are computed in LOCAL time and clock-time central tendency
 * uses CIRCULAR statistics so the midnight wrap is handled. The zone is a PARAMETER, not a
 * constant: the app passes the device zone, parity tests pass
 * [SpecConstants.PARITY_TIMEZONE] explicitly (feature-spec §8).
 *
 * Missing values are [Double.NaN], mirroring the Python `np.nan` (native to XGBoost).
 */
object ScreenLockFeatures {

    private const val SECONDS_PER_HOUR = 3600.0

    /** Feature order — mirrors `_FEATURE_KEYS` in the Python module exactly. */
    val FEATURE_NAMES: List<String> = listOf(
        "days_with_data", "n_sleep_nights", "sleep_duration_median", "sleep_onset_hours",
        "sleep_wake_hours", "sleep_onset_regularity", "sleep_midpoint_regularity",
        "unlock_count_per_day_mean", "unlock_count_sd", "session_count_per_day_mean",
        "session_duration_median", "session_duration_iqr", "screen_on_fraction",
        "nighttime_use_fraction_personal", "nighttime_unlock_per_day_personal",
        "nighttime_use_fraction_fixed", "nighttime_unlock_per_day_fixed", "circadian_regularity",
    )

    /**
     * Compute the backbone feature map for the window `[w0, w1)` (epoch seconds).
     *
     * @param locked LOCKED intervals for ONE subject, any order.
     * @param zone the zone all clock/date arithmetic is performed in.
     */
    fun windowFeatures(
        locked: List<LockedInterval>,
        w0: Long,
        w1: Long,
        zone: ZoneId,
    ): Map<String, Double> {
        // Intervals OVERLAPPING the window, ascending by start (queryEvents order is not
        // contractual; the Python side argsorts too).
        val iv = locked
            .filter { it.endUtc > w0 && it.startUtc < w1 }
            .sortedBy { it.startUtc }

        // ── coverage: distinct local dates touched by any locked interval, CLIPPED ──────
        // NOTE: dates are calendar days (LocalDate.plusDays), matching pandas' tz-aware
        // `date_range(freq="D")`, which preserves wall-clock time across a DST change.
        val dates = LinkedHashSet<LocalDate>()
        for (interval in iv) {
            val startClipped = maxOf(interval.startUtc, w0)
            val endClipped = minOf(interval.endUtc, w1)
            var day = localDate(startClipped, zone)
            val lastDay = localDate(endClipped, zone)
            while (!day.isAfter(lastDay)) {
                dates.add(day)
                day = day.plusDays(1)
            }
        }
        val daysWithData = dates.size
        if (iv.isEmpty() || daysWithData == 0) return emptyLike(daysWithData)

        val features = LinkedHashMap<String, Double>()
        features["days_with_data"] = daysWithData.toDouble()

        val startsLocal = iv.map { zoned(it.startUtc, zone) }
        val endsLocal = iv.map { zoned(it.endUtc, zone) }
        val durationHours = iv.map { it.durationSeconds / SECONDS_PER_HOUR }

        // ── sleep: longest qualifying locked interval per night ────────────────────────
        // Integer division truncates, matching numpy's `((start + end) / 2).astype(int64)`
        // for positive epochs.
        val midpointsLocal = iv.map { zoned((it.startUtc + it.endUtc) / 2, zone) }
        val midpointHours = midpointsLocal.map { clockHour(it) }

        // night_key groups a sleep interval with the DAY IT STARTED: shifting the midpoint
        // back 12 h puts a 02:00 midpoint on the previous calendar date.
        val nights = LinkedHashMap<LocalDate, Night>()
        for (i in iv.indices) {
            val qualifies = durationHours[i] * 60 >= SpecConstants.MIN_SLEEP_MINUTES &&
                SpecConstants.SLEEP_MIDPOINT_BAND.contains(midpointHours[i])
            if (!qualifies) continue
            val nightKey = midpointsLocal[i].toInstant()
                .minusSeconds(12 * 3600)
                .atZone(zone)
                .toLocalDate()
            val candidate = Night(
                durationHours = durationHours[i],
                onsetHour = clockHour(startsLocal[i]),
                wakeHour = clockHour(endsLocal[i]),
                midpointHour = midpointHours[i],
            )
            val existing = nights[nightKey]
            // Strictly greater — the FIRST interval wins a tie, as in Python.
            if (existing == null || candidate.durationHours > existing.durationHours) {
                nights[nightKey] = candidate
            }
        }

        if (nights.isNotEmpty()) {
            val values = nights.values.toList()
            features["n_sleep_nights"] = nights.size.toDouble()
            features["sleep_duration_median"] =
                NumPyCompat.median(values.map { it.durationHours }.toDoubleArray())
            val onset = circular(values.map { it.onsetHour }.toDoubleArray())
            val wake = circular(values.map { it.wakeHour }.toDoubleArray())
            val midpoint = circular(values.map { it.midpointHour }.toDoubleArray())
            features["sleep_onset_hours"] = onset.mean
            features["sleep_wake_hours"] = wake.mean
            features["sleep_onset_regularity"] = onset.sd   // circular SD, lower = more regular
            features["sleep_midpoint_regularity"] = midpoint.sd
        } else {
            for (key in SLEEP_KEYS) features[key] = Double.NaN
        }

        // ── unlocks = the END of each locked interval that truly ends inside the window ─
        val unlockTimes = iv.map { it.endUtc }.filter { it >= w0 && it < w1 }
        val unlockLocal = unlockTimes.map { zoned(it, zone) }
        features["unlock_count_per_day_mean"] = unlockTimes.size.toDouble() / daysWithData
        val unlocksByDate = unlockLocal.groupingBy { it.toLocalDate() }.eachCount()
        features["unlock_count_sd"] = NumPyCompat.stdPopulation(
            dates.map { (unlocksByDate[it] ?: 0).toDouble() }.toDoubleArray()
        )

        // ── use-sessions = gaps between consecutive locked intervals ───────────────────
        val maxSessionSeconds = SpecConstants.MAX_SESSION_MINUTES * 60
        val sessions = ArrayList<LongRangePair>()
        for (a in 0 until iv.size - 1) {
            val gapStart = maxOf(iv[a].endUtc, w0)
            val gapEnd = minOf(iv[a + 1].startUtc, w1)
            // Implausibly long gaps are a data gap / phone-off, not real use.
            if (gapStart < gapEnd && gapEnd <= gapStart + maxSessionSeconds) {
                sessions.add(LongRangePair(gapStart, gapEnd))
            }
        }
        val sessionMinutes = sessions.map { (it.end - it.start) / 60.0 }.toDoubleArray()
        features["session_count_per_day_mean"] = sessions.size.toDouble() / daysWithData
        features["session_duration_median"] =
            if (sessions.isEmpty()) Double.NaN else NumPyCompat.median(sessionMinutes)
        features["session_duration_iqr"] =
            if (sessions.isEmpty()) Double.NaN else NumPyCompat.iqr(sessionMinutes)

        // per-day use seconds → mean daily fraction over days-with-data
        val useByDay = HashMap<LocalDate, Double>()
        for (session in sessions) {
            val day = localDate(session.start, zone)
            useByDay[day] = (useByDay[day] ?: 0.0) + (session.end - session.start)
        }
        features["screen_on_fraction"] = dates
            .map { minOf((useByDay[it] ?: 0.0) / SpecConstants.SECONDS_PER_DAY, 1.0) }
            .average()

        // ── night-time use: person-relative (primary) + fixed (ablation) ───────────────
        val sessionStartHours = sessions.map { clockHour(zoned(it.start, zone)) }
        val totalUseSeconds = sessions.sumOf { (it.end - it.start).toDouble() }

        fun nightUseFraction(band: ClockBand): Double {
            if (sessions.isEmpty() || totalUseSeconds == 0.0) return Double.NaN
            var inBand = 0.0
            for (i in sessions.indices) {
                if (band.contains(sessionStartHours[i])) {
                    inBand += (sessions[i].end - sessions[i].start).toDouble()
                }
            }
            return inBand / totalUseSeconds
        }

        fun nightUnlocksPerDay(band: ClockBand): Double {
            if (unlockTimes.isEmpty()) return 0.0
            val count = unlockLocal.count { band.contains(clockHour(it)) }
            return count.toDouble() / daysWithData
        }

        // Personal band = [circular-mean onset, circular-mean wake]; falls back to the
        // fixed band when this window detected no sleep at all.
        val onsetHours = features["sleep_onset_hours"]!!
        val wakeHours = features["sleep_wake_hours"]!!
        val personalBand =
            if (!onsetHours.isNaN() && !wakeHours.isNaN()) ClockBand(onsetHours, wakeHours)
            else SpecConstants.NIGHT_FIXED_BAND

        features["nighttime_use_fraction_personal"] = nightUseFraction(personalBand)
        features["nighttime_unlock_per_day_personal"] = nightUnlocksPerDay(personalBand)
        features["nighttime_use_fraction_fixed"] = nightUseFraction(SpecConstants.NIGHT_FIXED_BAND)
        features["nighttime_unlock_per_day_fixed"] = nightUnlocksPerDay(SpecConstants.NIGHT_FIXED_BAND)

        // ── circadian regularity: mean pairwise corr of daily hourly-use profiles ──────
        val profiles = LinkedHashMap<LocalDate, DoubleArray>()
        for (session in sessions) {
            var cursor = session.start
            while (cursor < session.end) {
                val at = zoned(cursor, zone)
                val hour = at.hour
                val midnight = at.toLocalDate().atStartOfDay(zone).toInstant().epochSecond
                val next = minOf(session.end, midnight + (hour + 1) * 3600L)
                // Guard: a DST fall-back repeats local hours, which can make `next` fail to
                // advance. Python would spin forever here; stop instead of hanging.
                if (next <= cursor) break
                profiles.getOrPut(at.toLocalDate()) { DoubleArray(SpecConstants.CIRCADIAN_BINS) }[hour] +=
                    (next - cursor).toDouble()
                cursor = next
            }
        }
        val varyingProfiles = profiles.values.filter { NumPyCompat.stdPopulation(it) > 0 }
        features["circadian_regularity"] =
            if (varyingProfiles.size >= 2) NumPyCompat.meanPairwiseCorrelation(varyingProfiles)
            else Double.NaN

        return orderedVector(features)
    }

    // ── helpers ──────────────────────────────────────────────────────────────────────

    private val SLEEP_KEYS = listOf(
        "n_sleep_nights", "sleep_duration_median", "sleep_onset_hours",
        "sleep_wake_hours", "sleep_onset_regularity", "sleep_midpoint_regularity",
    )

    private data class Night(
        val durationHours: Double,
        val onsetHour: Double,
        val wakeHour: Double,
        val midpointHour: Double,
    )

    private data class LongRangePair(val start: Long, val end: Long)

    /** Circular mean / SD of clock hours (period 24), via the mean-resultant vector. */
    internal data class Circular(val mean: Double, val sd: Double)

    internal fun circular(hours: DoubleArray): Circular {
        if (hours.isEmpty()) return Circular(Double.NaN, Double.NaN)
        var cosSum = 0.0
        var sinSum = 0.0
        for (h in hours) {
            val angle = 2 * PI * (((h % 24) + 24) % 24) / 24.0
            cosSum += cos(angle)
            sinSum += sin(angle)
        }
        val c = cosSum / hours.size
        val s = sinSum / hours.size
        // The mean-resultant length is <= 1 by construction; a value above 1 is pure
        // floating-point error. Without this clamp, `sqrt(-2 ln R)` sees a negative
        // argument and returns NaN where Python returns ~0 — a hard parity break that the
        // 1e-6 tolerance cannot absorb, because NaN is not close to anything. The JVM and
        // numpy's libm differ by ~1 ULP in cos/sin/atan2, which is enough to cross 1.0.
        // Verified inert on the real corpus: R > 1.0 occurs in 0 of 1161 StudentLife
        // samples (R == 1.0 exactly in 21), so this changes no trained-on value.
        val r = minOf(hypot(c, s), 1.0)
        var mean = atan2(s, c) % (2 * PI)
        if (mean < 0) mean += 2 * PI                       // Python's `%` is always non-negative
        val meanHours = mean * 24 / (2 * PI)
        val sdHours = if (r > 0) sqrt(-2.0 * ln(r)) * 24 / (2 * PI) else Double.NaN
        return Circular(meanHours, sdHours)
    }

    /** Local clock hour as `hour + minute/60` — seconds are DROPPED, as in pandas. */
    private fun clockHour(at: ZonedDateTime): Double = at.hour + at.minute / 60.0

    private fun zoned(epochSeconds: Long, zone: ZoneId): ZonedDateTime =
        Instant.ofEpochSecond(epochSeconds).atZone(zone)

    private fun localDate(epochSeconds: Long, zone: ZoneId): LocalDate =
        zoned(epochSeconds, zone).toLocalDate()

    /** All-NaN vector, preserving `days_with_data` — mirrors `_empty_like`. */
    private fun emptyLike(daysWithData: Int): Map<String, Double> {
        val out = LinkedHashMap<String, Double>()
        for (name in FEATURE_NAMES) out[name] = Double.NaN
        out["days_with_data"] = daysWithData.toDouble()
        return out
    }

    private fun orderedVector(features: Map<String, Double>): Map<String, Double> {
        val out = LinkedHashMap<String, Double>(FEATURE_NAMES.size)
        for (name in FEATURE_NAMES) {
            out[name] = features[name]
                ?: error("feature '$name' was never computed — FEATURE_NAMES is out of sync")
        }
        return out
    }
}
