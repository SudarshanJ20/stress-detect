package com.stressdetect.features

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class AnalysisWindowTest {

    private val zone = ZoneId.of(SpecConstants.PARITY_TIMEZONE)

    @Test
    fun `window is seven days ending at local midnight`() {
        val window = AnalysisWindow.endingAtMidnightOf(LocalDate.of(2013, 4, 15), zone)
        assertEquals(1365998400L, window.endUtc)
        assertEquals(1365393600L, window.startUtc)
        assertEquals(7 * 86_400L, window.endUtc - window.startUtc)
    }

    /**
     * Across a DST start, `w0` is NOT local midnight — it is 23:00 the previous evening,
     * because pandas' `Timedelta(days=7)` is an ABSOLUTE 7 × 86400 s duration. Python
     * produces exactly these epochs, so we must too; using `minusDays` here would shift the
     * window by an hour twice a year and silently break parity.
     */
    @Test
    fun `window start uses absolute duration, matching pandas Timedelta across a DST change`() {
        // US DST began 2013-03-10, inside this window.
        val window = AnalysisWindow.endingAtMidnightOf(LocalDate.of(2013, 3, 11), zone)
        assertEquals(1362974400L, window.endUtc)
        assertEquals(1362369600L, window.startUtc)

        val startLocal = Instant.ofEpochSecond(window.startUtc).atZone(zone)
        assertEquals(23, startLocal.hour)
        assertEquals(LocalDate.of(2013, 3, 3), startLocal.toLocalDate())
    }

    @Test
    fun `the same label date in a different zone yields a different window`() {
        // Proof the zone is genuinely a parameter: if this ever stops differing, something
        // is reading an ambient default instead of the argument it was given.
        val eastern = AnalysisWindow.endingAtMidnightOf(LocalDate.of(2013, 4, 15), zone)
        val kolkata = AnalysisWindow.endingAtMidnightOf(
            LocalDate.of(2013, 4, 15), ZoneId.of("Asia/Kolkata"),
        )
        assertEquals(9 * 3600 + 1800, eastern.endUtc - kolkata.endUtc)
    }
}
