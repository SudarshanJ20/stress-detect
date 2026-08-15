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
    const val SPEC_VERSION: String = "v0.7.0"

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

    // ── Clock-band edge resolution (cross-language determinism) ──────────────────────
    /**
     * A clock hour within this many hours of a band bound is resolved by CONVENTION
     * rather than by the raw comparison: on the INCLUSIVE low edge → INSIDE, on the
     * EXCLUSIVE high edge → OUTSIDE.
     *
     * Why this exists: the person-relative night band's bounds are CIRCULAR MEANS — they
     * come out of `cos`/`sin`/`atan2`, and the JVM and numpy's libm differ by ~1 ULP
     * there. When a subject's unlock time coincides with their own mean wake time (common,
     * not exotic), that 1 ULP flipped the in/out decision and changed
     * `nighttime_unlock_per_day_personal` (0.0 vs 0.75). A boolean flip cannot be absorbed
     * by a numerical tolerance, so the COMPARISON itself must be deterministic. Found by
     * the Phase-6 parity fixture; see `docs/feature-spec.md` §9.
     *
     * 1e-9 h = 3.6 µs — far below the minute resolution of the underlying clock features
     * (`hour + minute/60`, seconds dropped) and far above the ~1e-16 noise it absorbs.
     *
     * Mirrors `ml/src/features/spec_constants.py::BAND_EDGE_EPS`. Changing it on one side
     * only is precisely the divergence SPEC_VERSION exists to prevent.
     */
    const val BAND_EDGE_EPS: Double = 1e-9

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

    /**
     * Half-open `[startHour, endHour)`, wrapping past midnight, with SNAPPED edges:
     * exactly on the low edge is INSIDE, exactly on the high edge is OUTSIDE
     * ([SpecConstants.BAND_EDGE_EPS]). Identical rule to Python's `_in_band`.
     */
    fun contains(hour: Double): Boolean {
        if (kotlin.math.abs(hour - startHour) < SpecConstants.BAND_EDGE_EPS) return true
        if (kotlin.math.abs(hour - endHour) < SpecConstants.BAND_EDGE_EPS) return false
        return if (startHour < endHour) hour >= startHour && hour < endHour
        else hour >= startHour || hour < endHour
    }
}
