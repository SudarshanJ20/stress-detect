package com.stressdetect.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.stressdetect.data.AnalysisResult
import com.stressdetect.ui.components.Body
import com.stressdetect.ui.components.BreathingFigure
import com.stressdetect.ui.components.Caption
import com.stressdetect.ui.components.CountUpNumber
import com.stressdetect.ui.components.Eyebrow
import com.stressdetect.ui.components.LinkText
import com.stressdetect.ui.components.PrimaryButton
import com.stressdetect.ui.components.ScreenScaffold
import com.stressdetect.ui.components.SectionHeading
import com.stressdetect.ui.components.TrendArrow
import com.stressdetect.ui.content.Band
import com.stressdetect.ui.content.WeekSummary
import com.stressdetect.ui.theme.LocalCalmColors
import com.stressdetect.ui.theme.Space

/**
 * What someone sees after checking in.
 *
 * **The number is their own answers and nothing else** — the four responses over the scale's
 * maximum. The prediction is deliberately absent: our evaluation found it lost to a
 * per-person mean with negative rank correlation, so putting it beside the one defensible
 * figure would make that figure less trustworthy. It, and the full account, are in About.
 *
 * Everything is left-aligned except the figure. Centred paragraphs were what made this
 * screen look uneven — ragged on both edges, with nothing for the eye to run down.
 *
 * Below the number, the four things the phone can actually see, each compared to THIS
 * PERSON'S own earlier weeks and to nothing else. No population figure appears here or
 * anywhere else in the app.
 */
@Composable
fun ResultScreen(
    result: AnalysisResult,
    onDone: () -> Unit,
    onAbout: () -> Unit,
) {
    val band = Band.forScore(result.questionnaireScore)
    val summary = WeekSummary.build(
        weekValues = result.weekValues,
        priorWeekValues = result.priorWeekValues,
        dailyValues = result.dailyValues,
        staticValues = result.staticValues,
        usageAccessMissing = result.usageAccessMissing,
        meetsCoverage = result.meetsCoverage,
        daysWithData = result.daysWithData,
    )

    ScreenScaffold(Modifier.verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(Space.section))

        // The one centred element in the app.
        BreathingFigure(band = band, modifier = Modifier.align(Alignment.CenterHorizontally))

        Spacer(Modifier.height(Space.section))
        // "50%" on its own reads as unfinished — a number with nothing saying what of.
        Eyebrow("Stress score")
        Spacer(Modifier.height(Space.tight))
        CountUpNumber(value = result.questionnairePercent)
        Spacer(Modifier.height(Space.tight))
        Text(
            text = band.label,
            style = MaterialTheme.typography.titleMedium,
            color = LocalCalmColors.current.mutedInk,
        )

        Spacer(Modifier.height(Space.block))
        Body(band.blurb)
        Spacer(Modifier.height(Space.tight))
        Body(
            // The second sentence is a promise about what follows, so it is only made when
            // something follows. With no usage access there is nothing below but an
            // explanation of why.
            text = if (summary.rows.isEmpty()) "From your check-in."
            else "From your check-in. Below is what your phone saw this week.",
            muted = true,
        )

        Spacer(Modifier.height(Space.section))
        WhatsGoingOn(summary)

        Spacer(Modifier.height(Space.section))
        PrimaryButton("Done", onDone)
        Spacer(Modifier.height(Space.tight))
        LinkText("How this works", onAbout)
        Spacer(Modifier.height(Space.section))
    }
}

/**
 * The four things the phone can see, each with which way it moved against this person's own
 * earlier weeks, and the one small thing tied to it.
 *
 * There was a separate "What might help" section here. Folding each suggestion under the row
 * it answers removes a second pass over the same material — the pairing is now position, not
 * a heading and an implied order.
 *
 * No cards, borders or dividers: four plain rows in the existing spacing. A bordered box
 * around each would turn a quiet list into a dashboard.
 */
@Composable
private fun WhatsGoingOn(summary: WeekSummary.Result) {
    Column(Modifier.fillMaxWidth()) {
        SectionHeading("What's been going on")
        Spacer(Modifier.height(Space.block))

        val reason = summary.unavailableReason
        if (reason != null) {
            // Written to look deliberate rather than broken: what is missing, what to do
            // about it, and that the check-in itself was fine.
            Body(reason, muted = true)
            return@Column
        }

        summary.rows.forEachIndexed { index, row ->
            if (index > 0) Spacer(Modifier.height(Space.block))
            SummaryRow(row)
        }

        if (summary.rows.any { it.suggestion != null }) {
            Spacer(Modifier.height(Space.block))
            Caption("Small things, and none of them are advice about your health.")
        }
    }
}

/**
 * One row: icon and label, then the direction and the phrase beneath it, then the suggestion.
 *
 * Label and phrase are stacked rather than set side by side. "Screen activity" next to
 * "Slightly more than your usual" needs about 406dp and the column is 392dp at its widest —
 * so a single line would have to be either truncated or a shorter, vaguer phrase, and at a
 * large system font size neither survives. Stacking simply gets taller.
 */
@Composable
private fun SummaryRow(row: WeekSummary.Row) {
    val calm = LocalCalmColors.current

    Row(Modifier.fillMaxWidth()) {
        Text(
            text = row.icon,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(end = Space.item),
        )
        Column(Modifier.weight(1f)) {
            Body(row.label)
            Spacer(Modifier.height(Space.tight))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Absent on a first week: there is nothing to point at, so nothing points.
                row.direction?.let {
                    TrendArrow(it)
                    Spacer(Modifier.width(Space.tight))
                }
                Body(row.phrase, muted = true)
            }
            row.suggestion?.let { suggestion ->
                Spacer(Modifier.height(Space.tight))
                Text(
                    text = suggestion,
                    style = MaterialTheme.typography.bodyLarge,
                    color = calm.secondary,
                )
            }
        }
    }
}
