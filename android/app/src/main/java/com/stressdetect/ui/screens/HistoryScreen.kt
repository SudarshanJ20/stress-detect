package com.stressdetect.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import com.stressdetect.data.CheckInRepository
import com.stressdetect.survey.Pss4
import com.stressdetect.ui.components.Body
import com.stressdetect.ui.components.CalmCard
import com.stressdetect.ui.components.Caption
import com.stressdetect.ui.components.Eyebrow
import com.stressdetect.ui.components.QuietButton
import com.stressdetect.ui.components.ScreenScaffold
import com.stressdetect.ui.components.ScreenTitle
import com.stressdetect.ui.components.Sparkline
import com.stressdetect.ui.content.HistoryStats
import com.stressdetect.ui.theme.Space
import java.time.format.DateTimeFormatter

/**
 * Past check-ins.
 *
 * The chart is fixed to the full 0–16 range rather than auto-scaled, so a steady run of
 * scores looks steady. Auto-scaling would turn one-point wobble into dramatic peaks — the
 * chart would look most alarming exactly when someone was most stable.
 *
 * No trend line and no "you're improving". The stat row does now show the change from last
 * week, but as a bare point difference: no colour, no arrow, no word for which way it went.
 * Two check-ins are still not a trend, which is why nothing appears at all until there are
 * three of them — and this app has no basis for telling anyone which direction is up.
 */
@Composable
fun HistoryScreen(
    entries: List<CheckInRepository.Entry>,
    isDemo: Boolean,
    onBack: () -> Unit,
) {
    val dateFormat = remember { DateTimeFormatter.ofPattern("d MMM") }

    ScreenScaffold(
        Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Space.block),
    ) {
        Spacer(Modifier.height(Space.block))
        ScreenTitle("Your history")

        // A chart needs at least three points on DIFFERENT days before it says anything —
        // two dots joined by a line invite reading a trend into noise. Demo mode is exempt:
        // every demo check-in lands on today, so the rule would leave the chart permanently
        // hidden and undemonstrable.
        val distinctDays = entries.map { it.takenAt }.distinct().size
        if (!isDemo && distinctDays < 3) {
            Body(
                "Come back after a few check-ins and you'll see how things move.",
                muted = true,
            )
            if (entries.isNotEmpty()) {
                Spacer(Modifier.height(Space.block))
                Caption(
                    if (entries.size == 1) "One so far."
                    else "${entries.size} so far, on $distinctDays day(s)."
                )
            }
            Spacer(Modifier.height(Space.section))
            QuietButton("Back", onBack)
            return@ScreenScaffold
        }
        if (entries.isEmpty()) {
            Body("Come back after a few check-ins and you'll see how things move.", muted = true)
            Spacer(Modifier.height(Space.section))
            QuietButton("Back", onBack)
            return@ScreenScaffold
        }

        HistoryStats.build(entries, isDemo)?.let { StatRow(it) }

        CalmCard {
            Sparkline(scores = entries.map { it.score }, maxScore = Pss4.MAX_SCORE)
            Spacer(Modifier.height(Space.item))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Caption(entries.first().takenAt.format(dateFormat))
                Caption(entries.last().takenAt.format(dateFormat))
            }
        }

        Body(
            text = if (entries.size == 1) {
                "One check-in so far. There'll be more to see next time."
            } else {
                "${entries.size} check-ins. Scores move around week to week — that is " +
                    "normal, and a single high or low one on its own does not mean much."
            },
            muted = true,
        )

        Spacer(Modifier.height(Space.tight))
        entries.takeLast(6).reversed().forEach { entry ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Body(entry.takenAt.format(dateFormat))
                Body("${Pss4.percentOfMaximum(entry.score)}%", muted = true)
            }
        }

        Spacer(Modifier.height(Space.block))
        QuietButton("Back", onBack)
    }
}

/**
 * Weekly average, the change from the week before, and how many check-ins there have been.
 *
 * Three plain columns — no card, no dividers. The chart below is already in a card, and a
 * second bordered box above it would turn a quiet screen into a dashboard.
 *
 * Past a font scale of 1.3 the three stack instead: side by side they would each be a couple
 * of characters wide and wrap into an unreadable stack anyway, so they may as well do it
 * deliberately.
 */
@Composable
private fun StatRow(stats: HistoryStats.Stats) {
    // The change is the one muted value: no green, no red, no arrow. Which direction is
    // "good" on this scale is not something this app can say.
    val items = listOfNotNull(
        Triple("Weekly average", "${stats.averagePercent}%", false),
        stats.changePoints?.let {
            Triple("Since last week", HistoryStats.formatChange(it), true)
        },
        Triple("Check-ins", "${stats.total}", false),
    )

    if (LocalDensity.current.fontScale > 1.3f) {
        // Wider gaps than between a label and its own value, so stacked items still read as
        // three separate figures rather than one list.
        Column(verticalArrangement = Arrangement.spacedBy(Space.block)) {
            items.forEach { (label, value, muted) -> Stat(label, value, muted) }
        }
    } else {
        // Bottom-aligned: on a narrow screen "Weekly average" wraps to two lines and
        // "Check-ins" does not, and three figures sitting at three different heights looks
        // like a mistake. Aligning the bottoms puts the figures — the part being read — on
        // one line whatever the labels do.
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Space.item),
            verticalAlignment = Alignment.Bottom,
        ) {
            items.forEach { (label, value, muted) ->
                Column(Modifier.weight(1f)) { Stat(label, value, muted) }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, muted: Boolean) {
    Eyebrow(label)
    Spacer(Modifier.height(Space.tight))
    Body(value, muted = muted)
}
