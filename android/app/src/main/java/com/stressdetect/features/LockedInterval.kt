package com.stressdetect.features

/**
 * One LOCKED interval `[startUtc, endUtc)` in epoch SECONDS — the on-device equivalent of
 * a row of StudentLife's `phonelock` stream, and the single input contract of the whole
 * feature layer.
 *
 * It lives in `features` (not `sensing`) on purpose: `features` must stay free of Android
 * types so it is a pure-JVM mirror of `ml/src/features` that unit tests — and the Phase-6
 * parity test — can drive directly. `sensing` depends on `features` to produce these;
 * `features` never depends on `sensing`.
 *
 * Derived from `UsageEvents` KEYGUARD_SHOWN → KEYGUARD_HIDDEN. See `docs/feature-spec.md`
 * §8 for the mapping and why keyguard (not screen) events define it.
 */
data class LockedInterval(val startUtc: Long, val endUtc: Long) {
    init {
        require(endUtc > startUtc) {
            "degenerate locked interval [$startUtc, $endUtc) — the Python ETL drops end <= start"
        }
    }

    val durationSeconds: Long get() = endUtc - startUtc
}
