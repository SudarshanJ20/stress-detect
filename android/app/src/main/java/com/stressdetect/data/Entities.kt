package com.stressdetect.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entities.
 *
 * Everything stored here is behavioural metadata — timestamps, durations, counts. No
 * message content, no phone numbers, no typed characters, no location. The Call/SMS tables
 * hold a single `epochSeconds` column each, so there is nowhere for content to go even by
 * accident.
 *
 * Raw events are persisted (not just the derived intervals) because `queryEvents` retention
 * is ~10 days: whatever is not captured at install is gone permanently, so re-deriving
 * under a revised rule later would otherwise be impossible.
 */

@Entity(
    tableName = "raw_usage_event",
    indices = [Index("epochSeconds")],
)
data class RawUsageEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochSeconds: Long,
    /** [com.stressdetect.sensing.UsageEventType] name — stored as text to survive reordering. */
    val type: String,
)

@Entity(
    tableName = "locked_interval",
    indices = [Index(value = ["startUtc", "endUtc"], unique = true)],
)
data class LockedIntervalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startUtc: Long,
    val endUtc: Long,
)

/** A call or SMS event reduced to its timestamp. `kind` is "call" or "sms". */
@Entity(
    tableName = "comm_event",
    indices = [Index(value = ["kind", "epochSeconds"], unique = true)],
)
data class CommEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val epochSeconds: Long,
)

/**
 * Per-app daily foreground bucket. Context only — no feature may be built on it under the
 * current SPEC_VERSION (feature-spec §2/§8).
 */
@Entity(
    tableName = "daily_app_usage",
    indices = [Index(value = ["bucketStartUtc", "packageName"], unique = true)],
)
data class DailyAppUsageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val bucketStartUtc: Long,
    val totalForegroundSeconds: Long,
    val lastTimeUsedUtc: Long,
)

/**
 * A cached window feature vector.
 *
 * The primary key is `(labelDate, specVersion)`: a SPEC_VERSION bump means the feature
 * definitions changed, so old rows must never be silently reused — they are simply a
 * different key, and [FeatureVectorDao.deleteOtherSpecVersions] can retire them.
 *
 * Feature columns are nullable; `null` is the storage form of `NaN` (the Python missing
 * value), because SQLite cannot round-trip NaN reliably.
 */
@Entity(tableName = "feature_vector", primaryKeys = ["labelDate", "specVersion"])
data class FeatureVectorEntity(
    /** ISO-8601 local date whose midnight ENDS the window. */
    val labelDate: String,
    val specVersion: String,
    val zoneId: String,
    val windowStartUtc: Long,
    val windowEndUtc: Long,
    val computedAtUtc: Long,
    /** `days_with_data >= COVERAGE_MIN_DAYS`; false rows are cached but must not be scored. */
    val meetsCoverage: Boolean,

    // ── backbone: coverage + sleep ────────────────────────────────────────────────
    val daysWithData: Double?,
    val nSleepNights: Double?,
    val sleepDurationMedian: Double?,
    val sleepOnsetHours: Double?,
    val sleepWakeHours: Double?,
    val sleepOnsetRegularity: Double?,
    val sleepMidpointRegularity: Double?,

    // ── backbone: usage ──────────────────────────────────────────────────────────
    val unlockCountPerDayMean: Double?,
    val unlockCountSd: Double?,
    val sessionCountPerDayMean: Double?,
    val sessionDurationMedian: Double?,
    val sessionDurationIqr: Double?,
    val screenOnFraction: Double?,

    // ── backbone: night + circadian ──────────────────────────────────────────────
    val nighttimeUseFractionPersonal: Double?,
    val nighttimeUnlockPerDayPersonal: Double?,
    val nighttimeUseFractionFixed: Double?,
    val nighttimeUnlockPerDayFixed: Double?,
    val circadianRegularity: Double?,

    // ── auxiliary (each with its mandatory presence flag) ────────────────────────
    val callCountPerDay: Double?,
    val callPresent: Double?,
    val smsCountPerDay: Double?,
    val smsPresent: Double?,
)

/**
 * One retrospective extraction attempt. Kept so a thin window can be explained after the
 * fact — how far back the OS actually answered, which permissions were held, whether the
 * CallLog row cap looks like it was hit (device-probe §3).
 */
@Entity(tableName = "extraction_run")
data class ExtractionRunEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ranAtUtc: Long,
    val specVersion: String,
    val zoneId: String,
    val windowStartUtc: Long,
    val windowEndUtc: Long,
    val usageAccessGranted: Boolean,
    val callLogPermission: Boolean,
    val smsPermission: Boolean,
    val rawEventCount: Int,
    val lockedIntervalCount: Int,
    val earliestEventUtc: Long?,
    val callRowCount: Int,
    val smsRowCount: Int,
    val daysWithData: Double?,
    val meetsCoverage: Boolean,
)
