package com.stressdetect.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.stressdetect.ui.components.Body
import com.stressdetect.ui.components.Caption
import com.stressdetect.ui.components.PrimaryButton
import com.stressdetect.ui.components.QuietButton
import com.stressdetect.ui.theme.LocalCalmColors
import com.stressdetect.ui.theme.Space
import java.time.LocalTime

/**
 * The front door.
 *
 * Deliberately almost empty. The old first screen was a wall of consent text, which is the
 * right content in the wrong place — it made the app feel like a form to be completed
 * rather than something to open. That text still exists, in full, in About; a first-time
 * user sees a short version once before their first check-in.
 */
@Composable
fun HomeScreen(
    lastScore: Int?,
    onCheckIn: () -> Unit,
    onHistory: () -> Unit,
    onAbout: () -> Unit,
) {
    val calm = LocalCalmColors.current

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Space.screen),
        verticalArrangement = Arrangement.spacedBy(Space.block),
    ) {
        Spacer(Modifier.height(Space.section))

        Text(
            text = greeting(),
            style = MaterialTheme.typography.headlineSmall,
            color = calm.ink,
        )
        Body(
            text = "Four short questions about how things have been. It takes about a minute.",
            muted = true,
        )

        Spacer(Modifier.height(Space.block))
        PrimaryButton("Check in", onCheckIn)

        if (lastScore != null) {
            Spacer(Modifier.height(Space.tight))
            Caption("You last checked in recently. There is no need to do it often — " +
                "whenever you feel like it is fine.")
        }

        Spacer(Modifier.height(Space.section))
        QuietButton("Your history", onHistory)
        QuietButton("About this app", onAbout)
    }
}

/**
 * Time-of-day greeting. Warm but not chirpy — someone opening this at 2am does not need
 * enthusiasm, and "Good evening" at 3am reads as a machine that isn't paying attention.
 */
private fun greeting(now: LocalTime = LocalTime.now()): String = when (now.hour) {
    in 0..4 -> "Still up?"
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    in 18..21 -> "Good evening"
    else -> "Winding down?"
}
