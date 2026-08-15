package com.stressdetect.sensing

/**
 * A single screen/lock/power event read back from `UsageStatsManager.queryEvents`,
 * normalized to epoch SECONDS (the unit the whole feature pipeline uses).
 *
 * Deliberately a plain data class rather than an `android.app.usage.UsageEvents.Event`:
 * the Android type never leaves this package (enforced by `ArchitectureTest`), and keeping
 * the normalized form Android-free makes [LockedIntervalDerivation] unit-testable on the
 * JVM without an emulator.
 */
data class RawUsageEvent(val epochSeconds: Long, val type: UsageEventType)

/**
 * The event types we read. Values are mapped from `UsageEvents.Event` constants in
 * [UsageEventsSource] — verified API levels (SDK `api-versions.xml`): KEYGUARD_* and
 * SCREEN_* are API 28, DEVICE_SHUTDOWN/STARTUP are API 29. `minSdk = 29` covers all six.
 */
enum class UsageEventType {
    /** Device locked — the START of a locked interval. */
    KEYGUARD_SHOWN,

    /** Device unlocked — the END of a locked interval, i.e. an "unlock". */
    KEYGUARD_HIDDEN,

    /**
     * Display on. NOT used to derive locked intervals (feature-spec §8): the probe saw
     * 2007 of these vs 1138 unlocks, the excess being notification glances and ambient
     * display, which are not device use. Persisted only so the decision can be revisited
     * without re-querying — `queryEvents` retention is ~10 days and unrecoverable after.
     */
    SCREEN_INTERACTIVE,

    /** Display off. Persisted, not used — see [SCREEN_INTERACTIVE]. */
    SCREEN_NON_INTERACTIVE,

    /** Power off — closes any open lock as UNKNOWN (a data gap, never sleep). */
    DEVICE_SHUTDOWN,

    /** Power on — begins a fresh lock state. */
    DEVICE_STARTUP,
}
