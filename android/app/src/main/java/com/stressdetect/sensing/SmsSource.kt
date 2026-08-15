package com.stressdetect.sensing

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat

/**
 * AUXILIARY retrospective source: message *timestamps*.
 *
 * ## Privacy
 * The projection is `[Telephony.Sms.DATE]` and nothing else — never `BODY`, never
 * `ADDRESS`, never `PERSON`. Message content is not read, cannot be stored, and has no
 * downstream representation: the query returns `List<Long>`.
 *
 * The probe read ~10 months / 1,759 rows, which looks like genuine retention rather than a
 * cap (unlike CallLog — see [CallLogSource]). Still AUXILIARY: sparse in StudentLife
 * (23/49), so it may never be a backbone feature and must carry its `sms_present` flag
 * (feature-spec §1).
 *
 * NEEDS-VERIFICATION: `READ_SMS` is fine for a sideloaded study build, but Play Store
 * distribution restricts it. Confirm before any store release.
 */
class SmsSource(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * @return message timestamps in epoch SECONDS within `[windowStartUtc, windowEndUtc)`,
     *   or [SmsQueryResult.unavailable] when the permission is not granted.
     */
    fun query(windowStartUtc: Long, windowEndUtc: Long): SmsQueryResult {
        if (!hasPermission()) return SmsQueryResult.unavailable()

        val timestamps = ArrayList<Long>()
        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms.DATE),                      // DATE ONLY — see class doc
            "${Telephony.Sms.DATE} >= ? AND ${Telephony.Sms.DATE} < ?",
            arrayOf((windowStartUtc * 1_000).toString(), (windowEndUtc * 1_000).toString()),
            "${Telephony.Sms.DATE} ASC",
        )?.use { cursor ->
            val dateColumn = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
            while (cursor.moveToNext()) {
                timestamps.add(Math.floorDiv(cursor.getLong(dateColumn), 1_000L))
            }
        } ?: return SmsQueryResult.unavailable()

        return SmsQueryResult(available = true, timestamps = timestamps)
    }
}

/** See [CallLogQueryResult] — `available == false` means "cannot see", not "none". */
data class SmsQueryResult(
    val available: Boolean,
    val timestamps: List<Long>,
) {
    val rowCount: Int get() = timestamps.size

    companion object {
        fun unavailable() = SmsQueryResult(available = false, timestamps = emptyList())
    }
}
