package com.stressdetect.sensing

import com.stressdetect.features.LockedInterval

/**
 * Turns a stream of [RawUsageEvent]s into the LOCKED intervals the feature layer consumes
 * — the on-device reconstruction of StudentLife's `phonelock` stream.
 *
 * The rules are normative and justified in `docs/feature-spec.md` §8; they are repeated
 * here only as they are applied. Pure function, no Android, no I/O — so every rule below
 * is directly unit-testable.
 */
object LockedIntervalDerivation {

    /**
     * @param events any order; typically the raw result of `queryEvents`.
     * @return locked intervals `[KEYGUARD_SHOWN, KEYGUARD_HIDDEN)`, ascending by start.
     */
    fun derive(events: List<RawUsageEvent>): List<LockedInterval> {
        // queryEvents order is not contractual. Sort by time; ties keep insertion order so
        // a SHOWN/HIDDEN pair recorded in the same second still resolves in arrival order.
        val ordered = events.sortedBy { it.epochSeconds }
        val intervals = ArrayList<LockedInterval>()
        var openLockStart: Long? = null

        for (event in ordered) {
            when (event.type) {
                // Consecutive duplicates collapse: the FIRST SHOWN opens the interval, a
                // repeat is a redundant re-post of the same state, not a new lock.
                UsageEventType.KEYGUARD_SHOWN ->
                    if (openLockStart == null) openLockStart = event.epochSeconds

                UsageEventType.KEYGUARD_HIDDEN -> {
                    val start = openLockStart
                    // start == null → an unlock with no matching lock: the interval was
                    // open before the query window began. DROPPED, never clamped to the
                    // window edge — clamping would fabricate a multi-hour interval out of
                    // a missing event, which can land in SLEEP_MIDPOINT_BAND and invent a
                    // night of sleep, corrupting sleep_duration_median (feature-spec §8).
                    if (start != null && event.epochSeconds > start) {
                        // The `>` also drops degenerate/zero-length locks, mirroring the
                        // Python ETL's `end_utc > start_utc` filter.
                        intervals.add(LockedInterval(start, event.epochSeconds))
                    }
                    openLockStart = null
                }

                // A powered-off phone is a DATA GAP, not a lock. Counting it as one would
                // be the same fabrication as clamping, so any open lock is discarded and
                // the state restarts clean.
                UsageEventType.DEVICE_SHUTDOWN,
                UsageEventType.DEVICE_STARTUP -> openLockStart = null

                // Screen events are persisted but never define an interval (feature-spec §8).
                UsageEventType.SCREEN_INTERACTIVE,
                UsageEventType.SCREEN_NON_INTERACTIVE -> Unit
            }
        }

        // An interval still open at the end of the query is likewise DROPPED: the device is
        // locked right now and the interval has no end yet. Losing at most one real
        // interval at each window edge beats fabricating one.
        return intervals
    }
}
