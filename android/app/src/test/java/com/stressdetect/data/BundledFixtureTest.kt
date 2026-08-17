package com.stressdetect.data

import com.stressdetect.features.ParityFixture
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * The app ships its own copy of `fixtures/synthetic_trace.json` in `assets/`, because an APK
 * cannot read a file that lives outside it. Two copies of anything drift, and this one drifts
 * silently: every test in the repo reads the `fixtures/` original, so a stale asset passes the
 * whole suite and then crashes demo mode on the device.
 *
 * That is not hypothetical. It happened the first time cases were added to the trace — the
 * suite was green and the app died looking for `demo_prior_week_1`.
 *
 * Refresh with:
 *   cp fixtures/synthetic_trace.json android/app/src/main/assets/synthetic_trace.json
 */
class BundledFixtureTest {

    private fun bundled(): File =
        ParityFixture.file().parentFile!!.parentFile!!
            .resolve("android/app/src/main/assets/synthetic_trace.json")

    @Test
    fun `the bundled asset is the committed fixture, byte for byte`() {
        val source = ParityFixture.file()
        val asset = bundled()
        assertEquals(
            "assets/synthetic_trace.json has drifted from fixtures/synthetic_trace.json — " +
                "copy it across; demo mode reads the asset, every test reads the original",
            source.readText(),
            asset.readText(),
        )
    }
}
