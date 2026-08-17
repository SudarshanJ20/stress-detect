package com.stressdetect.ui.screens

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import com.stressdetect.ui.components.Body
import com.stressdetect.ui.components.Caption
import com.stressdetect.ui.components.LinkText
import com.stressdetect.ui.components.PrimaryButton
import com.stressdetect.ui.components.ScreenScaffold
import com.stressdetect.ui.components.ScreenTitle
import com.stressdetect.ui.theme.Space
import com.stressdetect.ui.theme.rememberAnimationsEnabled
import java.time.LocalTime

/**
 * The front door: a greeting, one button, two links.
 *
 * The content sits in a weighted, scrolling region with the links BELOW it, so the links are
 * always visible and the content scrolls behind them at large font sizes rather than running
 * off the bottom edge — the overflow bug this pass fixes.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    lastScore: Int?,
    onCheckIn: () -> Unit,
    onHistory: () -> Unit,
    onAbout: () -> Unit,
) {
    val animate = rememberAnimationsEnabled()
    var entered by remember { mutableStateOf(!animate) }
    LaunchedEffect(Unit) { entered = true }

    val progress by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = 400, easing = LinearOutSlowInEasing),
        label = "entry",
    )

    // fillHeight + a weighted scroll region, NOT a scrolling column with a weighted spacer:
    // inside a vertical scroll the height is unbounded, so weight() distributes nothing and
    // the links end up wherever the content leaves them. This is the overflow fix.
    ScreenScaffold(fillHeight = true) {
        // Only the prose scrolls. The button sits OUTSIDE the scroll region so the primary
        // action is on screen at any font size — at 2.0x the greeting alone fills the
        // viewport, and a "Check in" button you have to scroll to find is a broken front door.
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(Space.section))

            // Fades in with a 12dp rise, both driven off the same progress value so they can
            // never disagree — and both collapse to nothing when motion is off.
            ScreenTitle(
                text = greeting(),
                modifier = Modifier
                    .alpha(progress)
                    .padding(top = (1f - progress) * 12.dp),
            )
            Spacer(Modifier.height(Space.item))
            Body(
                text = "Four short questions about how things have been. It takes about a minute.",
                muted = true,
                modifier = Modifier.alpha(progress),
            )

            if (lastScore != null) {
                Spacer(Modifier.height(Space.block))
                Caption(
                    "You last checked in recently. There's no need to do it often — " +
                        "whenever you feel like it is fine."
                )
            }

            Spacer(Modifier.height(Space.block))
        }

        PrimaryButton("Check in", onCheckIn)

        // FlowRow, not Row: at large font sizes two links do not fit side by side, and a
        // plain Row breaks the second one mid-word ("Abo / ut"). FlowRow moves the whole
        // link to the next line instead.
        FlowRow(
            Modifier
                .fillMaxWidth()
                .padding(top = Space.item),
            horizontalArrangement = Arrangement.spacedBy(Space.section),
        ) {
            LinkText("Your history", onHistory)
            LinkText("About", onAbout)
        }
        Spacer(Modifier.height(Space.screen))
    }
}

/**
 * Time-of-day greeting. Warm but not chirpy — "Good evening" at 3am reads as a machine that
 * isn't paying attention.
 */
private fun greeting(now: LocalTime = LocalTime.now()): String = when (now.hour) {
    in 0..4 -> "Still up?"
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    in 18..21 -> "Good evening"
    else -> "Winding down?"
}
