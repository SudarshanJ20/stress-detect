package com.stressdetect.sensing

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import androidx.core.content.ContextCompat

/**
 * AUXILIARY retrospective source: call *timestamps*.
 *
 * ## Privacy
 * The projection is `[CallLog.Calls.DATE]` and nothing else. No number, no contact name, no
 * duration, no call type is ever read, so no field exists downstream that could hold one —
 * the same "architecturally incapable" rule the typing collector follows. Do not widen this
 * projection to "just check something"; the returned type is `List<Long>` precisely so
 * there is nowhere to put it.
 *
 * ## The 1998-row caveat (feature-spec §4, device-probe §3)
 * The probe returned exactly 1998 rows — suspiciously near a round 2000, so probably a
 * provider row CAP rather than true ~2-month retention. If it is a cap, lookback depth
 * shrinks as call volume rises, making any CallLog feature's effective window
 * user-dependent and confounded with call frequency. Mitigation applied here: the query is
 * bounded by a fixed TIME span (a `DATE >= ?` selection), never by a row count, and
 * [CallLogQueryResult.rowCount] is reported so a cap can be detected in the field.
 * Still NEEDS-VERIFICATION.
 */
class CallLogSource(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * @return call timestamps in epoch SECONDS within `[windowStartUtc, windowEndUtc)`, or
     *   [CallLogQueryResult.unavailable] when the permission is not granted — which is NOT
     *   the same as "no calls", and drives the mandatory `call_present` flag.
     */
    fun query(windowStartUtc: Long, windowEndUtc: Long): CallLogQueryResult {
        if (!hasPermission()) return CallLogQueryResult.unavailable()

        val timestamps = ArrayList<Long>()
        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(CallLog.Calls.DATE),                      // DATE ONLY — see class doc
            "${CallLog.Calls.DATE} >= ? AND ${CallLog.Calls.DATE} < ?",
            arrayOf((windowStartUtc * 1_000).toString(), (windowEndUtc * 1_000).toString()),
            "${CallLog.Calls.DATE} ASC",
        )?.use { cursor ->
            val dateColumn = cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)
            while (cursor.moveToNext()) {
                timestamps.add(Math.floorDiv(cursor.getLong(dateColumn), 1_000L))
            }
        } ?: return CallLogQueryResult.unavailable()

        return CallLogQueryResult(available = true, timestamps = timestamps)
    }
}

/**
 * [available] distinguishes "the stream is readable and had no calls in the window" from
 * "we cannot see calls at all". Feature-spec §1 forbids collapsing those two into a silent
 * zero — hence the `call_present` companion feature.
 */
data class CallLogQueryResult(
    val available: Boolean,
    val timestamps: List<Long>,
) {
    val rowCount: Int get() = timestamps.size

    companion object {
        fun unavailable() = CallLogQueryResult(available = false, timestamps = emptyList())
    }
}
