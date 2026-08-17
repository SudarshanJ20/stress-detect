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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import com.stressdetect.data.CheckInRepository
import com.stressdetect.data.WeekContext
import com.stressdetect.survey.Pss4
import com.stressdetect.ui.components.Body
import com.stressdetect.ui.components.BreathingFigure
import com.stressdetect.ui.components.Eyebrow
import com.stressdetect.ui.components.LinkText
import com.stressdetect.ui.components.PrimaryButton
import com.stressdetect.ui.components.ScreenScaffold
import com.stressdetect.ui.components.ScreenTitle
import com.stressdetect.ui.components.Sparkline
import com.stressdetect.ui.content.Band
import com.stressdetect.ui.content.HomeSummary
import com.stressdetect.ui.theme.Space
import com.stressdetect.ui.theme.rememberAnimationsEnabled
import java.time.LocalDate
import java.time.LocalTime

/**
 * The front door: a greeting, what the app already knows, one button, two links.
 *
 * The content sits in a weighted, scrolling region with the button and links BELOW it, so the
 * primary action is on screen at any font size and the content scrolls behind it rather than
 * running off the bottom edge.
 *
 * Everything added here is something the app already had: the last check-in it stored, and
 * the cached feature vector from the last time it read the phone. **Nothing on this screen
 * queries anything or asks for a permission** — a front door that collected data to fill
 * itself in would be the opposite of what this app promises.
 *
 * Each part disappears rather than degrading into a placeholder. A first-time visitor sees a
 * greeting, a figure and a button, which is a calm and honest empty state; "no data yet" in
 * the space where a fact belongs is neither.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    history: List<CheckInRepository.Entry>,
    weekContext: WeekContext?,
    onCheckIn: () -> Unit,
    onHistory: () -> Unit,
    onAbout: () -> Unit,
) {
    val today = remember { LocalDate.now() }
    val last = history.lastOrNull()
    // No check-ins yet: the middle band, never the calmest one. Drawing the settled face for
    // someone who has told us nothing would be the app claiming something it cannot know.
    val band = last?.let { Band.forScore(it.score) } ?: Band.SOME
    val phoneLine = weekContext?.let { HomeSummary.phoneLine(it.values, it.priorValues) }
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

            Spacer(Modifier.height(Space.block))
            // Small — 60dp against the result screen's 148 — because here it is a bit of life
            // on the screen, not the thing being read. It does not scale with the font, so it
            // costs the same at every text size.
            BreathingFigure(
                band = band,
                sizeDp = HOME_FIGURE_DP,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .alpha(progress),
            )

            if (last != null) {
                Spacer(Modifier.height(Space.block))
                Eyebrow("Last check-in")
                Spacer(Modifier.height(Space.tight))
                Body(
                    "${Pss4.percentOfMaximum(last.score)}% · " +
                        HomeSummary.relativeDay(last.takenAt, today)
                )

                // One dot is not a line. From two check-ins the sparkline says something.
                val recent = history.takeLast(SPARK_SCORES)
                if (recent.size >= 2) {
                    Spacer(Modifier.height(Space.item))
                    Sparkline(
                        scores = recent.map { it.score },
                        maxScore = Pss4.MAX_SCORE,
                        heightDp = SPARK_HEIGHT_DP,
                    )
                }
            }

            if (phoneLine != null) {
                Spacer(Modifier.height(Space.block))
                Body(phoneLine, muted = true)
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

/** 40% of the result screen's figure: present, but not the thing being read. */
private const val HOME_FIGURE_DP = 60

/** A week of check-ins at most, and short enough to read as a glance rather than a chart. */
private const val SPARK_SCORES = 7
private const val SPARK_HEIGHT_DP = 44
