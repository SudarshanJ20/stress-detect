package com.stressdetect.sensing

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings

/**
 * Grant state for `PACKAGE_USAGE_STATS`.
 *
 * It is a SPECIAL permission, not a runtime one: `requestPermissions()` does nothing for
 * it. The user must toggle it in Settings → Special app access → Usage access, and the
 * grant is readable only through `AppOpsManager`. Without it `queryEvents` returns an
 * EMPTY stream rather than throwing — which would otherwise look exactly like "this user
 * never unlocked their phone", so the check must happen before extraction, not after.
 *
 * NEEDS-VERIFICATION against current official docs before the participant build.
 */
object UsageAccess {

    fun isGranted(context: Context): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Settings screen where the user grants usage access; there is no in-app dialog. */
    fun settingsIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
}
