package com.stressdetect.sensing

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import com.stressdetect.features.SpecConstants

/**
 * BACKBONE retrospective source: `UsageStatsManager.queryEvents`.
 *
 * The probe (`docs/device-probe-results.md`) measured ~10 days of raw-event retention —
 * the tightest constraint in the whole design, and the reason the analysis window is 7
 * days. Everything this returns is unrecoverable once the OS prunes it, so the caller
 * persists the raw events on first run.
 */
class UsageEventsSource(private val context: Context) {

    /**
     * Query margin BEFORE the analysis window start.
     *
     * A lock that began before the window but ended inside it is a real interval the
     * feature layer must see (it filters on overlap). Without the margin that interval
     * would present as an unmatched KEYGUARD_HIDDEN and be dropped, losing (typically) the
     * first night of sleep in the window. 7 + 3 = 10 days spends exactly the retention the
     * probe measured, no more.
     */
    private val queryMarginDays = 3

    /**
     * Read screen/lock/power events covering [windowStartUtc, windowEndUtc) plus the
     * margin. Epoch SECONDS in, epoch SECONDS out — the platform API works in millis.
     *
     * @return events in the order the OS returned them; [LockedIntervalDerivation] sorts.
     */
    fun queryEvents(windowStartUtc: Long, windowEndUtc: Long): List<RawUsageEvent> {
        val manager = context.getSystemService(UsageStatsManager::class.java)
            ?: return emptyList()

        val beginMillis =
            (windowStartUtc - queryMarginDays * SpecConstants.SECONDS_PER_DAY) * 1_000
        val endMillis = windowEndUtc * 1_000

        val results = ArrayList<RawUsageEvent>()
        val stream: UsageEvents = manager.queryEvents(beginMillis, endMillis)
        val event = UsageEvents.Event()
        while (stream.hasNextEvent()) {
            stream.getNextEvent(event)
            val type = event.eventType.toUsageEventType() ?: continue
            // floorDiv, not `/`: integer division truncates toward zero, which would round
            // pre-epoch timestamps the wrong way. Defensive, but free.
            results.add(RawUsageEvent(Math.floorDiv(event.timeStamp, 1_000L), type))
        }
        return results
    }

    /** Maps the platform constants; anything else (app foreground, etc.) is ignored. */
    private fun Int.toUsageEventType(): UsageEventType? = when (this) {
        UsageEvents.Event.KEYGUARD_SHOWN -> UsageEventType.KEYGUARD_SHOWN
        UsageEvents.Event.KEYGUARD_HIDDEN -> UsageEventType.KEYGUARD_HIDDEN
        UsageEvents.Event.SCREEN_INTERACTIVE -> UsageEventType.SCREEN_INTERACTIVE
        UsageEvents.Event.SCREEN_NON_INTERACTIVE -> UsageEventType.SCREEN_NON_INTERACTIVE
        UsageEvents.Event.DEVICE_SHUTDOWN -> UsageEventType.DEVICE_SHUTDOWN
        UsageEvents.Event.DEVICE_STARTUP -> UsageEventType.DEVICE_STARTUP
        else -> null
    }
}
