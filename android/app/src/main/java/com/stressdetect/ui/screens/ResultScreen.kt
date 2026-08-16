package com.stressdetect.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.stressdetect.data.AnalysisResult
import com.stressdetect.survey.Pss4
import com.stressdetect.ui.components.Body
import com.stressdetect.ui.components.BreathingFigure
import com.stressdetect.ui.components.Caption
import com.stressdetect.ui.components.PrimaryButton
import com.stressdetect.ui.components.QuietButton
import com.stressdetect.ui.content.Band
import com.stressdetect.ui.content.Observations
import com.stressdetect.ui.theme.LocalCalmColors
import com.stressdetect.ui.theme.Space

/**
 * What someone sees after checking in.
 *
 * **The number on this screen is their own answers, and nothing else.** It is the PSS-4
 * total over 16, expressed as a percentage. The model's estimate is deliberately absent
 * here: our own evaluation found it lost to a per-person mean with negative rank
 * correlation, so blending or even displaying it beside this number would make the one
 * defensible figure on the screen less trustworthy. It, and the full account of why, live
 * in About.
 *
 * Nothing on this screen names the scale, the window, the version, or the model. That is
 * not concealment — every one of those is in About, unchanged — it is a judgement that a
 * tired person reading a number about themselves is owed plain language first.
 */
@Composable
fun ResultScreen(
    result: AnalysisResult,
    onDone: () -> Unit,
    onAbout: () -> Unit,
) {
    val calm = LocalCalmColors.current
    val band = Band.forScore(result.questionnaireScore)
    val observations = Observations.build(
        dailyValues = result.dailyValues,
        staticValues = result.staticValues,
        usageAccessMissing = result.usageAccessMissing,
        meetsCoverage = result.meetsCoverage,
        daysWithData = result.daysWithData,
    )

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Space.screen),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(Space.block))
        BreathingFigure(band = band)

        Spacer(Modifier.height(Space.block))
        Text(
            text = "${result.questionnairePercent}%",
            style = MaterialTheme.typography.displaySmall.copy(fontSize = 56.sp),
            color = calm.ink,
        )
        Spacer(Modifier.height(Space.tight))
        Text(
            text = band.label,
            style = MaterialTheme.typography.titleMedium,
            color = calm.mutedInk,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(Space.block))
        Text(
            text = band.blurb,
            style = MaterialTheme.typography.bodyLarge,
            color = calm.ink,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Space.tight))
        Text(
            text = "This comes from the four answers you just gave.",
            style = MaterialTheme.typography.bodyMedium,
            color = calm.mutedInk,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(Space.section))
        WhatsGoingOn(observations)

        if (observations.observations.isNotEmpty()) {
            Spacer(Modifier.height(Space.section))
            WhatMightHelp(observations)
        }

        Spacer(Modifier.height(Space.section))
        PrimaryButton("Done", onDone)
        QuietButton("How this works", onAbout)
        Spacer(Modifier.height(Space.block))
    }
}

@Composable
private fun WhatsGoingOn(observations: Observations.Result) {
    val calm = LocalCalmColors.current

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.item)) {
        Text(
            text = "What's been going on",
            style = MaterialTheme.typography.titleMedium,
            color = calm.ink,
        )

        val reason = observations.unavailableReason
        if (reason != null) {
            // The degraded path is written to look deliberate rather than broken: it says
            // what is missing, what to do about it, and that the check-in itself is fine.
            Body(reason, muted = true)
            return@Column
        }

        // Sentences only — no bars. A bar next to "you were up after midnight on four
        // nights" implies a measurement scale that does not exist for a count of nights.
        observations.observations.forEach { observation ->
            Spacer(Modifier.height(Space.tight))
            Body(observation.sentence)
        }
    }
}

@Composable
private fun WhatMightHelp(observations: Observations.Result) {
    val calm = LocalCalmColors.current

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.item)) {
        Text(
            text = "What might help",
            style = MaterialTheme.typography.titleMedium,
            color = calm.ink,
        )
        // Each suggestion is tied to the observation directly above it in the list, so the
        // advice is about this week rather than generic wellbeing copy.
        observations.observations.forEach { observation ->
            Spacer(Modifier.height(Space.tight))
            Body(observation.suggestion)
        }
        Spacer(Modifier.height(Space.tight))
        Caption("Small things, and none of them are advice about your health.")
    }
}

/** Kept for the About screen's technical section. */
internal fun questionnaireMaximum(): Int = Pss4.MAX_SCORE
