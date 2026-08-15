package com.stressdetect.features

/**
 * Kotlin mirror of `ml/src/features/spec_constants.py`.
 *
 * Every threshold here is mirrored 1:1 by a row in `docs/feature-spec.md` §6 with a
 * rationale. Code must not introduce a magic number that is not also in the spec, and no
 * value may be changed on one side only — that silently breaks feature parity, which is
 * the whole point of SPEC_VERSION.
 *
 * `SpecVersionTest` asserts [SPEC_VERSION] equals the value declared in
 * `docs/feature-spec.md`, mirroring `ml/tests/test_spec_version.py`.
 */
object SpecConstants {

    /** Bump together with `docs/feature-spec.md` AND `ml/src/features/spec_constants.py`. */
    const val SPEC_VERSION: String = "v0.6.0"

    /**
     * The zone the PYTHON extractor hardcodes (`spec_constants.TIMEZONE`), because
     * StudentLife is Dartmouth/Hanover NH, spring 2013 = US Eastern.
     *
     * That is a property of the bootstrap corpus, NOT of the feature definitions, so the
     * Kotlin extractor takes its zone as a parameter instead (feature-spec §8): the app
     * passes the device zone, and the parity test / any StudentLife replay passes THIS
     * value explicitly. Never read it from an ambient default in a parity test — a test
     * that passes only because both sides happened to agree on the environment's zone
     * verifies nothing.
     */
    const val PARITY_TIMEZONE: String = "America/New_York"

    // ── Windowing ────────────────────────────────────────────────────────────────────
    /** Analysis window (spec §5): ~10 d `queryEvents` retention is the binding constraint. */
    const val WINDOW_DAYS: Int = 7

    /** Drop a window with fewer than this many days of lock data — too little to summarize. */
    const val COVERAGE_MIN_DAYS: Int = 3

    /**
     * A gap between consecutive LOCKED intervals counts as phone-in-use only if it is at
     * most this long. Screens auto-lock after minutes, so a multi-hour "unlocked" gap is
     * missing events / phone-off, not real use — counting it inflates screen-on time.
     */
    const val MAX_SESSION_MINUTES: Long = 180

    // ── Sleep detection (from LOCKED intervals) ──────────────────────────────────────
    /** A locked interval shorter than this is a nap/idle, not main sleep. */
    const val MIN_SLEEP_MINUTES: Long = 90

    /**
     * Main nightly sleep = the longest locked interval whose LOCAL midpoint falls in this
     * band. The band wraps past midnight: 20:00 → 12:00 next day.
     */
    val SLEEP_MIDPOINT_BAND: ClockBand = ClockBand(20.0, 12.0)

    // ── Night-time usage ─────────────────────────────────────────────────────────────
    /** Fixed clock band 00:00–06:00 — the ABLATION feature, not the primary one. */
    val NIGHT_FIXED_BAND: ClockBand = ClockBand(0.0, 6.0)

    // ── Circadian regularity ─────────────────────────────────────────────────────────
    /** Hourly use-profile bins per day; regularity = mean pairwise correlation. */
    const val CIRCADIAN_BINS: Int = 24

    /** Seconds in a day, used for the ABSOLUTE-duration day arithmetic pandas performs. */
    const val SECONDS_PER_DAY: Long = 86_400
}

/**
 * A clock-hour band `[start, end)` in local hours, which may wrap past midnight
 * (e.g. 20.0 → 12.0). Mirrors `_in_band` in `screenlock_features.py`.
 */
data class ClockBand(val startHour: Double, val endHour: Double) {
    fun contains(hour: Double): Boolean =
        if (startHour < endHour) hour >= startHour && hour < endHour
        else hour >= startHour || hour < endHour
}
