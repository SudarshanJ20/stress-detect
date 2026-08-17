package com.stressdetect.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * What the app currently believes about its two permissions.
 *
 * Usage access is a SPECIAL permission: it is granted on a Settings screen, in another app,
 * with no callback and no result to await. Reading it once — on the way out of the intro, as
 * this app did — means the answer is fixed for the life of the process, and someone who goes
 * and grants it comes back to a screen still asking them to.
 *
 * So the grants are read through functions rather than copied into fields, and [refresh] is
 * called every time the app returns to the foreground. Nothing is cached that the user can
 * change behind our back.
 *
 * The lambdas keep this testable: `ExtractionGateway` needs a `Context`, and the bug being
 * fixed is about WHEN the question is asked, not who answers it.
 */
class PermissionState(
    private val usageAccess: () -> Boolean,
    private val comms: () -> Boolean,
) {
    /** `PACKAGE_USAGE_STATS` — the backbone depends on it; everything else degrades. */
    var usageAccessGranted by mutableStateOf(usageAccess())
        private set

    /** Call log and/or SMS: auxiliary, and declining is a supported path. */
    var commsGranted by mutableStateOf(comms())
        private set

    fun refresh() {
        usageAccessGranted = usageAccess()
        commsGranted = comms()
    }
}
