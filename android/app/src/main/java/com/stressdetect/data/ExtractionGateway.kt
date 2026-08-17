package com.stressdetect.data

import android.Manifest
import android.content.Context
import android.content.Intent
import com.stressdetect.sensing.CallLogSource
import com.stressdetect.sensing.SmsSource
import com.stressdetect.sensing.UsageAccess

/**
 * The UI's only door into sensing.
 *
 * `ui` may not import `sensing` (Konsist-enforced, see android/CLAUDE.md), so permission
 * state and the extraction trigger are re-exposed here. That rule is what keeps raw sensor
 * handling out of screens — the UI can ask "may we read usage?" but has no way to read it.
 */
class ExtractionGateway(private val context: Context) {

    fun isUsageAccessGranted(): Boolean = UsageAccess.isGranted(context)

    /** Usage access is a SPECIAL permission — it is granted in Settings, not by a dialog. */
    fun usageAccessSettingsIntent(): Intent = UsageAccess.settingsIntent()

    fun isCallLogGranted(): Boolean = CallLogSource(context).hasPermission()

    fun isSmsGranted(): Boolean = SmsSource(context).hasPermission()

    /**
     * Runtime permissions backing the AUXILIARY call/SMS features only. Declining them is a
     * supported path: the features degrade to value 0 with `*_present = 0` and the model
     * must still work (feature-spec §1).
     */
    fun auxiliaryRuntimePermissions(): Array<String> =
        arrayOf(Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_SMS)

    fun enqueueBackfill() = RetrospectiveBackfillWorker.enqueue(context)

    suspend fun runBackfillNow(): ExtractionOutcome = RetrospectiveExtractor(context).run()

    suspend fun latestCachedVector(): FeatureVectorEntity? =
        RetrospectiveExtractor(context).latestCachedVector()

    /**
     * The most recent week this phone already knows about, and the weeks before it.
     *
     * For Home, which shows a line of context without anyone checking in. Reads the cache
     * only — no query, no permission prompt, nothing collected just to fill a screen. `null`
     * before there is anything cached, which is the honest state on a first run.
     */
    suspend fun weekContext(isDemo: Boolean): WeekContext? =
        WeekFeatures.latest(context, isDemo)?.let {
            WeekContext(values = it.values, priorValues = WindowAssembly.meanOfWeeks(it.prior))
        }

    /**
     * What the OS actually gave us on the last attempt.
     *
     * `extraction_run` has recorded this since the beginning and nothing ever displayed it,
     * so "there wasn't enough history on this phone" was unanswerable from the device: it
     * could mean the permission was refused, that the OS returned nothing, that the events
     * came back but no lock/unlock pair could be derived from them, or that the window was
     * genuinely thin. Those need different fixes and look identical on screen.
     */
    suspend fun lastExtraction(): ExtractionSummary? =
        StressDetectDatabase.get(context).extractionRunDao().latest()?.let {
            ExtractionSummary(
                ranAtUtc = it.ranAtUtc,
                usageAccessGranted = it.usageAccessGranted,
                callLogPermission = it.callLogPermission,
                smsPermission = it.smsPermission,
                rawEventCount = it.rawEventCount,
                lockedIntervalCount = it.lockedIntervalCount,
                earliestEventUtc = it.earliestEventUtc,
                daysWithData = it.daysWithData,
                meetsCoverage = it.meetsCoverage,
            )
        }
}

/**
 * The last extraction attempt, reduced to what a person debugging an empty result needs.
 *
 * Deliberately NOT the Room entity: this crosses into the UI, and the entity is free to grow
 * fields that have no business on a screen.
 */
data class ExtractionSummary(
    val ranAtUtc: Long,
    val usageAccessGranted: Boolean,
    val callLogPermission: Boolean,
    val smsPermission: Boolean,
    /** Raw screen/lock/power events the OS returned over the queried span. */
    val rawEventCount: Int,
    /** Lock→unlock pairs derived from them. Zero with a healthy event count means the OS
     *  reported screen events but no KEYGUARD pairs — a different problem from silence. */
    val lockedIntervalCount: Int,
    val earliestEventUtc: Long?,
    val daysWithData: Double?,
    val meetsCoverage: Boolean,
)

/**
 * A week of features and what it is compared against, as the UI receives it.
 *
 * The same two maps the result screen's rows read, so a line on Home and a row on the result
 * screen cannot disagree about the same week.
 */
data class WeekContext(
    val values: Map<String, Double>,
    /** Feature-wise mean of the earlier weeks; empty when there are none to compare against. */
    val priorValues: Map<String, Double>,
)
