package com.stressdetect.sensing

import android.app.usage.UsageStatsManager
import android.content.Context

/**
 * Per-app daily foreground buckets from `queryUsageStats(INTERVAL_DAILY)` (~10 days on the
 * probe device; WEEKLY ~3.5 wk and MONTHLY ~5 mo reach further but are coarser).
 *
 * ⚠️ **No feature may be built on this under the current SPEC_VERSION.** `docs/feature-spec.md`
 * §2 records that StudentLife's `app_usage` is a `getRunningTasks()` POLL, not a foreground
 * usage timeline, so it is not equivalent to these buckets; §8 declines to justify that
 * mapping. Cached as low-resolution context only — promoting it to a feature requires a
 * documented justification in the spec and a version bump first.
 */
class UsageStatsSource(private val context: Context) {

    fun queryDailyBuckets(windowStartUtc: Long, windowEndUtc: Long): List<DailyAppUsage> {
        val manager = context.getSystemService(UsageStatsManager::class.java)
            ?: return emptyList()
        return manager
            .queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                windowStartUtc * 1_000,
                windowEndUtc * 1_000,
            )
            .orEmpty()
            .map {
                DailyAppUsage(
                    packageName = it.packageName,
                    bucketStartUtc = Math.floorDiv(it.firstTimeStamp, 1_000L),
                    totalForegroundSeconds = Math.floorDiv(it.totalTimeInForeground, 1_000L),
                    lastTimeUsedUtc = Math.floorDiv(it.lastTimeUsed, 1_000L),
                )
            }
    }
}

/** One `UsageStats` bucket, normalized to epoch/duration SECONDS. */
data class DailyAppUsage(
    val packageName: String,
    val bucketStartUtc: Long,
    val totalForegroundSeconds: Long,
    val lastTimeUsedUtc: Long,
)
