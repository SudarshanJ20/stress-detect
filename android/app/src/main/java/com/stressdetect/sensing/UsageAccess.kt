package com.stressdetect.sensing

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
        return isGrantedFrom(mode, hasPermission = holdsPermission(context))
    }

    /**
     * The decision itself, separated from the platform lookup so it can be tested.
     *
     * **`MODE_DEFAULT` is not a refusal.** AOSP: "The permission's grant state is only
     * considered if the app-op's mode is `MODE_DEFAULT`. This allows to have default grants
     * while still being overridden by the app-op." So the op is the override and the
     * permission is the fallback — reading anything other than `MODE_ALLOWED` as "denied"
     * reports a granted permission as missing on any device that leaves the op at its
     * default, which is what happened on the S24.
     *
     * An EXPLICIT `MODE_IGNORED`/`MODE_ERRORED` still wins over the permission; that is what
     * makes it an override.
     *
     * https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/permission/Permissions.md
     */
    internal fun isGrantedFrom(mode: Int, hasPermission: Boolean): Boolean = when (mode) {
        AppOpsManager.MODE_DEFAULT -> hasPermission
        else -> mode == AppOpsManager.MODE_ALLOWED
    }

    private fun holdsPermission(context: Context): Boolean =
        context.checkPermission(
            Manifest.permission.PACKAGE_USAGE_STATS,
            Process.myPid(),
            Process.myUid(),
        ) == PackageManager.PERMISSION_GRANTED

    /** Settings screen where the user grants usage access; there is no in-app dialog. */
    fun settingsIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
}
