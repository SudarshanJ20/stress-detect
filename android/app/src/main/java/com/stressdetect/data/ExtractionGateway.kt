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
}
