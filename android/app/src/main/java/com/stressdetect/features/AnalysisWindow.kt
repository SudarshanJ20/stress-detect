package com.stressdetect.features

import java.time.LocalDate
import java.time.ZoneId

/**
 * The 7-day analysis window `[startUtc, endUtc)` in epoch seconds.
 *
 * Mirrors `_window_bounds` in `ml/src/features/build_dataset.py`: the window ENDS at a
 * day's local midnight, so that day's own behaviour never enters its own features.
 *
 * ⚠️ **Parity trap.** Python computes `w0 = w1 - pd.Timedelta(days=7)`, and a pandas
 * `Timedelta` is an ABSOLUTE duration (exactly 7 × 86400 s), not a calendar offset. Across
 * a DST transition `w0` is therefore NOT local midnight. We reproduce that deliberately
 * with second arithmetic — using `minusDays` (a calendar offset) would silently shift the
 * window by an hour twice a year and break parity.
 */
data class AnalysisWindow(val startUtc: Long, val endUtc: Long) {

    val days: Int get() = SpecConstants.WINDOW_DAYS

    companion object {
        /** The window whose end is local midnight starting [labelDate], in [zone]. */
        fun endingAtMidnightOf(labelDate: LocalDate, zone: ZoneId): AnalysisWindow {
            val end = labelDate.atStartOfDay(zone).toInstant().epochSecond
            val start = end - SpecConstants.WINDOW_DAYS * SpecConstants.SECONDS_PER_DAY
            return AnalysisWindow(start, end)
        }

        /**
         * The most recent COMPLETE window on the device: the 7 days ending at last local
         * midnight. This is the same construction the training samples use, so what the
         * app scores has the same shape as what the model was fitted on.
         */
        fun mostRecentComplete(today: LocalDate, zone: ZoneId): AnalysisWindow =
            endingAtMidnightOf(today, zone)
    }
}
