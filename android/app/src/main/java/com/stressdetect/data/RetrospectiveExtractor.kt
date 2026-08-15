package com.stressdetect.data

import android.content.Context
import com.stressdetect.features.AnalysisWindow
import com.stressdetect.features.FeatureExtractor
import com.stressdetect.features.LockedInterval
import com.stressdetect.features.SpecConstants
import com.stressdetect.features.WindowFeatureVector
import com.stressdetect.sensing.CallLogSource
import com.stressdetect.sensing.LockedIntervalDerivation
import com.stressdetect.sensing.SmsSource
import com.stressdetect.sensing.UsageAccess
import com.stressdetect.sensing.UsageEventsSource
import com.stressdetect.sensing.UsageStatsSource
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/** Kinds stored in `comm_event.kind` — mirrors the Python ETL's `kind` column. */
private const val KIND_CALL = "call"
private const val KIND_SMS = "sms"

/**
 * Runs the install-time retrospective backfill: query history → persist → compute features
 * → cache.
 *
 * Features are computed FROM ROOM, never from the live query results (android/CLAUDE.md).
 * That is not ceremony: it means a recompute after a SPEC_VERSION bump produces the same
 * vector from the same stored evidence, which is what makes the cache invalidation
 * meaningful and the whole run reproducible.
 */
class RetrospectiveExtractor(
    private val context: Context,
    private val database: StressDetectDatabase = StressDetectDatabase.get(context),
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val clock: Clock = Clock.system(zone),
    private val usageEventsSource: UsageEventsSource = UsageEventsSource(context),
    private val usageStatsSource: UsageStatsSource = UsageStatsSource(context),
    private val callLogSource: CallLogSource = CallLogSource(context),
    private val smsSource: SmsSource = SmsSource(context),
) {

    suspend fun run(): ExtractionOutcome {
        val today = LocalDate.now(clock)
        val window = AnalysisWindow.mostRecentComplete(today, zone)
        val now = clock.instant().epochSecond

        if (!UsageAccess.isGranted(context)) {
            // queryEvents returns an EMPTY stream without this permission rather than
            // failing, which is indistinguishable from "never unlocked the phone". Refuse
            // to compute rather than cache a fabricated empty baseline.
            database.extractionRunDao().insert(
                ExtractionRunEntity(
                    ranAtUtc = now,
                    specVersion = SpecConstants.SPEC_VERSION,
                    zoneId = zone.id,
                    windowStartUtc = window.startUtc,
                    windowEndUtc = window.endUtc,
                    usageAccessGranted = false,
                    callLogPermission = callLogSource.hasPermission(),
                    smsPermission = smsSource.hasPermission(),
                    rawEventCount = 0,
                    lockedIntervalCount = 0,
                    earliestEventUtc = null,
                    callRowCount = 0,
                    smsRowCount = 0,
                    daysWithData = null,
                    meetsCoverage = false,
                )
            )
            return ExtractionOutcome.MissingUsageAccess
        }

        // ── 1. query history and persist it verbatim ────────────────────────────────
        val rawEvents = usageEventsSource.queryEvents(window.startUtc, window.endUtc)
        database.rawUsageEventDao().insertAll(
            rawEvents.map { RawUsageEventEntity(epochSeconds = it.epochSeconds, type = it.type.name) }
        )

        val derived = LockedIntervalDerivation.derive(rawEvents)
        database.lockedIntervalDao().insertAll(
            derived.map { LockedIntervalEntity(startUtc = it.startUtc, endUtc = it.endUtc) }
        )

        val calls = callLogSource.query(window.startUtc, window.endUtc)
        val sms = smsSource.query(window.startUtc, window.endUtc)
        database.commEventDao().insertAll(
            calls.timestamps.map { CommEventEntity(kind = KIND_CALL, epochSeconds = it) } +
                sms.timestamps.map { CommEventEntity(kind = KIND_SMS, epochSeconds = it) }
        )

        database.dailyAppUsageDao().insertAll(
            usageStatsSource.queryDailyBuckets(window.startUtc, window.endUtc).map {
                DailyAppUsageEntity(
                    packageName = it.packageName,
                    bucketStartUtc = it.bucketStartUtc,
                    totalForegroundSeconds = it.totalForegroundSeconds,
                    lastTimeUsedUtc = it.lastTimeUsedUtc,
                )
            }
        )

        // ── 2. compute features FROM ROOM ───────────────────────────────────────────
        val vector = computeFromStore(
            labelDate = today,
            hasCalls = calls.available,
            hasSms = sms.available,
        )

        // ── 3. cache, retiring anything computed under a different spec ─────────────
        database.featureVectorDao().deleteOtherSpecVersions(SpecConstants.SPEC_VERSION)
        database.featureVectorDao().upsert(FeatureVectorMapper.toEntity(vector, now))

        database.extractionRunDao().insert(
            ExtractionRunEntity(
                ranAtUtc = now,
                specVersion = SpecConstants.SPEC_VERSION,
                zoneId = zone.id,
                windowStartUtc = window.startUtc,
                windowEndUtc = window.endUtc,
                usageAccessGranted = true,
                callLogPermission = calls.available,
                smsPermission = sms.available,
                rawEventCount = rawEvents.size,
                lockedIntervalCount = derived.size,
                earliestEventUtc = database.rawUsageEventDao().earliestEventUtc(),
                callRowCount = calls.rowCount,
                smsRowCount = sms.rowCount,
                daysWithData = vector.daysWithData.takeUnless { it.isNaN() },
                meetsCoverage = vector.meetsCoverage,
            )
        )

        return if (vector.meetsCoverage) {
            ExtractionOutcome.Success(vector)
        } else {
            // Not an error: a fresh device, a user with no lock screen, or OS pruning can
            // all leave < COVERAGE_MIN_DAYS. Surfaced rather than scored.
            ExtractionOutcome.InsufficientCoverage(vector)
        }
    }

    /**
     * Recompute the window vector from stored evidence, without touching the OS. Used by
     * [run] and available on its own after a SPEC_VERSION bump.
     */
    suspend fun computeFromStore(
        labelDate: LocalDate,
        hasCalls: Boolean,
        hasSms: Boolean,
    ): WindowFeatureVector {
        val window = AnalysisWindow.endingAtMidnightOf(labelDate, zone)
        val locked = database.lockedIntervalDao()
            .overlapping(window.startUtc, window.endUtc)
            .map { LockedInterval(it.startUtc, it.endUtc) }
        val callTimestamps =
            database.commEventDao().timestamps(KIND_CALL, window.startUtc, window.endUtc)
        val smsTimestamps =
            database.commEventDao().timestamps(KIND_SMS, window.startUtc, window.endUtc)

        return FeatureExtractor.extract(
            locked = locked,
            callTimestamps = callTimestamps,
            smsTimestamps = smsTimestamps,
            labelDate = labelDate,
            zone = zone,
            hasCalls = hasCalls,
            hasSms = hasSms,
        )
    }

    /** Latest cached vector for the current SPEC_VERSION, or null if none. */
    suspend fun latestCachedVector(): FeatureVectorEntity? =
        database.featureVectorDao().allForSpecVersion(SpecConstants.SPEC_VERSION).firstOrNull()
}

sealed interface ExtractionOutcome {
    /** `PACKAGE_USAGE_STATS` not granted — nothing was computed. */
    data object MissingUsageAccess : ExtractionOutcome

    /** Window has < `COVERAGE_MIN_DAYS` of data; the vector is cached but must not be scored. */
    data class InsufficientCoverage(val vector: WindowFeatureVector) : ExtractionOutcome

    data class Success(val vector: WindowFeatureVector) : ExtractionOutcome
}
