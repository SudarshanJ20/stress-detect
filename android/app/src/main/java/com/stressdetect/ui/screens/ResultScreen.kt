package com.stressdetect.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.stressdetect.ui.components.LinkText
import com.stressdetect.ui.components.PrimaryButton
import com.stressdetect.ui.components.ScreenScaffold
import com.stressdetect.ui.components.SectionHeading
import com.stressdetect.ui.content.Band
import com.stressdetect.ui.content.Observations
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
 */
@Composable
fun ResultScreen(
    result: AnalysisResult,
    onDone: () -> Unit,
    onAbout: () -> Unit,
) {
    val band = Band.forScore(result.questionnaireScore)
    val observations = Observations.build(
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
        CountUpNumber(value = result.questionnairePercent)
        Spacer(Modifier.height(Space.tight))
        Text(
            text = band.label,
            style = MaterialTheme.typography.titleMedium,
            color = LocalCalmColors.current.mutedInk,
        )

        Spacer(Modifier.height(Space.block))
        Body(band.blurb)

        Spacer(Modifier.height(Space.section))
        WhatsGoingOn(observations)

        if (observations.observations.isNotEmpty()) {
            Spacer(Modifier.height(Space.section))
            WhatMightHelp(observations)
        }

        Spacer(Modifier.height(Space.section))
        PrimaryButton("Done", onDone)
        Spacer(Modifier.height(Space.tight))
        LinkText("How this works", onAbout)
        Spacer(Modifier.height(Space.section))
    }
}

@Composable
private fun WhatsGoingOn(observations: Observations.Result) {
    Column(Modifier.fillMaxWidth()) {
        SectionHeading("What's been going on")
        Spacer(Modifier.height(Space.block))

        val reason = observations.unavailableReason
        if (reason != null) {
            // Written to look deliberate rather than broken: what is missing, what to do
            // about it, and that the check-in itself was fine.
            Body(reason, muted = true)
            return@Column
        }

        // Sentences only — no bars. A bar beside "you were up after midnight on four nights"
        // implies a measurement scale that does not exist for a count of nights.
        observations.observations.forEachIndexed { index, observation ->
            if (index > 0) Spacer(Modifier.height(Space.block))
            Body(observation.sentence)
        }
    }
}

@Composable
private fun WhatMightHelp(observations: Observations.Result) {
    val calm = LocalCalmColors.current

    Column(Modifier.fillMaxWidth()) {
        SectionHeading("What might help")
        Spacer(Modifier.height(Space.block))

        // Each suggestion sits indented under the observation it answers, in the secondary
        // colour, so the pairing is visible rather than implied by order alone.
        observations.observations.forEachIndexed { index, observation ->
            if (index > 0) Spacer(Modifier.height(Space.block))
            Body(observation.sentence, muted = true)
            Spacer(Modifier.height(Space.tight))
            Text(
                text = observation.suggestion,
                style = MaterialTheme.typography.bodyLarge,
                color = calm.secondary,
                modifier = Modifier.padding(start = Space.block),
            )
        }

        Spacer(Modifier.height(Space.block))
        Caption("Small things, and none of them are advice about your health.")
    }
}
