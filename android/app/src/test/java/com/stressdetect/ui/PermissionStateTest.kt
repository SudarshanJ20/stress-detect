package com.stressdetect.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Usage access is granted in Settings, outside this app, so there is no callback and no
 * result to await. The only way the app can know is to look again when it comes back to the
 * foreground.
 *
 * The reported symptom: grant usage access in Settings, return to the app, and the
 * permissions screen still offers "Continue anyway" — because the answer was read once, on
 * the way out of the intro, and never again for the life of the process.
 *
 * What this pins is that the state re-reads its source. That the re-read is actually
 * triggered on ON_RESUME is wiring in `MainActivity`, which needs an instrumented test to
 * assert (there is no Compose test dependency in this module) — it was verified by hand on a
 * device instead: background the app, flip the grant, foreground it, watch the screen change.
 */
class PermissionStateTest {

    @Test
    fun `it re-reads the grant instead of remembering the first answer`() {
        var granted = false
        val state = PermissionState(usageAccess = { granted }, comms = { false })
        assertFalse(state.usageAccessGranted)

        granted = true      // the user has just turned it on in Settings
        assertFalse("nothing should change until the app looks again", state.usageAccessGranted)

        state.refresh()     // ...which is what returning to the foreground does
        assertTrue(
            "after coming back from Settings the app must see the new grant",
            state.usageAccessGranted,
        )
    }

    @Test
    fun `a revoked grant is picked up just as readily`() {
        var granted = true
        val state = PermissionState(usageAccess = { granted }, comms = { true })
        state.refresh()
        assertTrue(state.usageAccessGranted)

        granted = false
        state.refresh()
        assertFalse("a permission taken away must stop being reported as held", state.usageAccessGranted)
    }

    @Test
    fun `both permissions refresh together`() {
        var usage = false
        var comms = false
        val state = PermissionState(usageAccess = { usage }, comms = { comms })

        usage = true
        comms = true
        state.refresh()

        assertTrue(state.usageAccessGranted)
        assertTrue(state.commsGranted)
    }

    @Test
    fun `the first read happens up front, so a returning user is not told to grant again`() {
        // Someone who granted usage access weeks ago opens the app straight onto Home. The
        // state must already be right without waiting for a resume.
        val state = PermissionState(usageAccess = { true }, comms = { true })
        assertTrue(state.usageAccessGranted)
        assertTrue(state.commsGranted)
    }
}
