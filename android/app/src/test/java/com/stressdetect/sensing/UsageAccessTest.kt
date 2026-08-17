package com.stressdetect.sensing

import android.app.AppOpsManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How the app decides whether it holds usage access.
 *
 * This is the check the whole backbone hangs off: `queryEvents` returns an EMPTY stream
 * without the grant instead of throwing, so a wrong answer here is indistinguishable from
 * "this person never unlocked their phone" — and the app quietly reports having found
 * nothing rather than reporting that it was not allowed to look.
 *
 * **`MODE_ALLOWED` is not the only granted state.** Per AOSP's own description of app-op
 * permissions: "The permission's grant state is only considered if the app-op's mode is
 * `MODE_DEFAULT`. This allows to have default grants while still being overridden by the
 * app-op." So `MODE_DEFAULT` means "no explicit app-op decision — ask the permission", and
 * treating it as a denial reports a granted permission as missing on any device that leaves
 * the op at its default.
 *
 * https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/permission/Permissions.md
 */
class UsageAccessTest {

    @Test
    fun `an explicitly allowed app-op is granted`() {
        assertTrue(UsageAccess.isGrantedFrom(AppOpsManager.MODE_ALLOWED, hasPermission = false))
        assertTrue(UsageAccess.isGrantedFrom(AppOpsManager.MODE_ALLOWED, hasPermission = true))
    }

    @Test
    fun `an explicitly refused app-op is not granted, whatever the permission says`() {
        // An explicit app-op decision OVERRIDES the permission — that is the point of it.
        assertFalse(UsageAccess.isGrantedFrom(AppOpsManager.MODE_IGNORED, hasPermission = true))
        assertFalse(UsageAccess.isGrantedFrom(AppOpsManager.MODE_ERRORED, hasPermission = true))
    }

    @Test
    fun `MODE_DEFAULT defers to the permission rather than counting as a refusal`() {
        // The reported bug: on a device that leaves the op at MODE_DEFAULT, usage access is
        // granted in Settings and the app still behaves as though it were denied — no rows,
        // and a permissions screen still offering "Continue anyway".
        assertTrue(
            "MODE_DEFAULT with the permission held is GRANTED — the app-op has expressed no " +
                "opinion, so the permission decides",
            UsageAccess.isGrantedFrom(AppOpsManager.MODE_DEFAULT, hasPermission = true),
        )
    }

    @Test
    fun `MODE_DEFAULT without the permission is still not granted`() {
        assertFalse(UsageAccess.isGrantedFrom(AppOpsManager.MODE_DEFAULT, hasPermission = false))
    }
}
