package com.stressdetect.features

import com.stressdetect.sensing.LockedIntervalDerivation
import com.stressdetect.sensing.RawUsageEvent
import com.stressdetect.sensing.UsageEventType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The `queryEvents` → LOCKED interval rules from `docs/feature-spec.md` §8.
 *
 * These rules exist to protect `sleep_duration_median`: every one of them is about NOT
 * inventing an interval that the events do not actually support.
 */
class LockedIntervalDerivationTest {

    private fun event(second: Long, type: UsageEventType) = RawUsageEvent(second, type)

    @Test
    fun `a shown-hidden pair becomes one locked interval`() {
        val intervals = LockedIntervalDerivation.derive(
            listOf(
                event(1_000, UsageEventType.KEYGUARD_SHOWN),
                event(4_600, UsageEventType.KEYGUARD_HIDDEN),
            )
        )
        assertEquals(listOf(LockedInterval(1_000, 4_600)), intervals)
    }

    @Test
    fun `an unlock with no matching lock is dropped, not clamped to the window edge`() {
        // This is the window-start case: the phone was locked before the query began.
        // Clamping would fabricate an interval starting at the query edge — potentially
        // hours long, potentially landing in SLEEP_MIDPOINT_BAND, inventing a night of
        // sleep that never happened.
        val intervals = LockedIntervalDerivation.derive(
            listOf(
                event(500, UsageEventType.KEYGUARD_HIDDEN),
                event(1_000, UsageEventType.KEYGUARD_SHOWN),
                event(4_600, UsageEventType.KEYGUARD_HIDDEN),
            )
        )
        assertEquals(listOf(LockedInterval(1_000, 4_600)), intervals)
    }

    @Test
    fun `an interval still open at the end of the query is dropped`() {
        val intervals = LockedIntervalDerivation.derive(
            listOf(
                event(1_000, UsageEventType.KEYGUARD_SHOWN),
                event(4_600, UsageEventType.KEYGUARD_HIDDEN),
                event(9_000, UsageEventType.KEYGUARD_SHOWN),   // never unlocked
            )
        )
        assertEquals(listOf(LockedInterval(1_000, 4_600)), intervals)
    }

    @Test
    fun `consecutive duplicate locks collapse and the first one opens the interval`() {
        val intervals = LockedIntervalDerivation.derive(
            listOf(
                event(1_000, UsageEventType.KEYGUARD_SHOWN),
                event(1_050, UsageEventType.KEYGUARD_SHOWN),
                event(1_090, UsageEventType.KEYGUARD_SHOWN),
                event(4_600, UsageEventType.KEYGUARD_HIDDEN),
            )
        )
        assertEquals(listOf(LockedInterval(1_000, 4_600)), intervals)
    }

    @Test
    fun `zero-length locks are dropped, mirroring the python end greater-than start filter`() {
        val intervals = LockedIntervalDerivation.derive(
            listOf(
                event(1_000, UsageEventType.KEYGUARD_SHOWN),
                event(1_000, UsageEventType.KEYGUARD_HIDDEN),
            )
        )
        assertEquals(emptyList<LockedInterval>(), intervals)
    }

    @Test
    fun `a shutdown discards the open lock — a powered-off phone is a data gap, not sleep`() {
        val intervals = LockedIntervalDerivation.derive(
            listOf(
                event(1_000, UsageEventType.KEYGUARD_SHOWN),
                event(2_000, UsageEventType.DEVICE_SHUTDOWN),
                event(30_000, UsageEventType.DEVICE_STARTUP),
                event(31_000, UsageEventType.KEYGUARD_HIDDEN),   // unmatched after restart
                event(40_000, UsageEventType.KEYGUARD_SHOWN),
                event(44_000, UsageEventType.KEYGUARD_HIDDEN),
            )
        )
        assertEquals(listOf(LockedInterval(40_000, 44_000)), intervals)
    }

    @Test
    fun `screen events never define an interval`() {
        val intervals = LockedIntervalDerivation.derive(
            listOf(
                event(1_000, UsageEventType.SCREEN_NON_INTERACTIVE),
                event(2_000, UsageEventType.SCREEN_INTERACTIVE),
                event(3_000, UsageEventType.SCREEN_NON_INTERACTIVE),
            )
        )
        // The probe saw ~1.8x more screen events than unlocks (notification glances,
        // ambient display). Treating them as locks would fragment sleep — feature-spec §8.
        assertEquals(emptyList<LockedInterval>(), intervals)
    }

    @Test
    fun `screen events interleaved with keyguard events do not split the lock`() {
        // The realistic sleep case: a notification lights the screen at 03:00 while the
        // phone stays locked. One interval must survive, not two.
        val intervals = LockedIntervalDerivation.derive(
            listOf(
                event(0, UsageEventType.KEYGUARD_SHOWN),
                event(100, UsageEventType.SCREEN_NON_INTERACTIVE),
                event(10_000, UsageEventType.SCREEN_INTERACTIVE),
                event(10_050, UsageEventType.SCREEN_NON_INTERACTIVE),
                event(28_800, UsageEventType.KEYGUARD_HIDDEN),
            )
        )
        assertEquals(listOf(LockedInterval(0, 28_800)), intervals)
    }

    @Test
    fun `events are sorted before pairing`() {
        val intervals = LockedIntervalDerivation.derive(
            listOf(
                event(4_600, UsageEventType.KEYGUARD_HIDDEN),
                event(1_000, UsageEventType.KEYGUARD_SHOWN),
            )
        )
        assertEquals(listOf(LockedInterval(1_000, 4_600)), intervals)
    }
}
