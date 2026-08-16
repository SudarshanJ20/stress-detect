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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stressdetect.data.AnalysisResult
import com.stressdetect.survey.Pss4
import com.stressdetect.ui.components.Body
import com.stressdetect.ui.components.CalmCard
import com.stressdetect.ui.components.Caption
import com.stressdetect.ui.components.Eyebrow
import com.stressdetect.ui.components.PrimaryButton
import com.stressdetect.ui.components.QuietButton
import com.stressdetect.ui.components.ScreenTitle
import com.stressdetect.ui.components.Track
import com.stressdetect.ui.content.Factors
import com.stressdetect.ui.theme.LocalCalmColors
import com.stressdetect.ui.theme.Space
import kotlin.math.roundToInt

/**
 * The result. Ordering is the argument: the questionnaire is first, largest and stated as
 * *the* result; the model estimate is second, visually demoted, and carries its own
 * invalidation inline; the behavioural factors are last and framed as context.
 *
 * Every claim required by the brief appears in the UI itself, not in a help page nobody
 * opens.
 */
@Composable
fun ResultScreen(result: AnalysisResult, onRestart: () -> Unit) {
    val calm = LocalCalmColors.current

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Space.screen),
        verticalArrangement = Arrangement.spacedBy(Space.block),
    ) {
        Spacer(Modifier.height(Space.tight))
        ScreenTitle("Your week")

        // ── 1. the questionnaire: this is the result ──────────────────────────────────
        CalmCard {
            Eyebrow("From your answers")
            Spacer(Modifier.height(Space.item))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "${result.questionnaireScore} of ${Pss4.MAX_SCORE}",
                    style = MaterialTheme.typography.displaySmall,
                    color = calm.ink,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${result.questionnairePercent}%",
                    style = MaterialTheme.typography.headlineSmall,
                    color = calm.mutedInk,
                )
            }
            Spacer(Modifier.height(Space.item))
            Track(fraction = result.questionnaireScore / Pss4.MAX_SCORE.toFloat())
            Spacer(Modifier.height(Space.item))
            Caption(
                "This is your result. It comes from the PSS-4, a short questionnaire that " +
                    "has been validated in published research. The percentage is simply " +
                    "your score out of ${Pss4.MAX_SCORE} — it is not a comparison with " +
                    "other people."
            )
            Spacer(Modifier.height(Space.tight))
            Caption(
                "Note the questionnaire asks about the last month, while the phone data " +
                    "below covers 7 days. They describe different stretches of time."
            )
        }

        // ── 2. the model: shown for comparison, and demoted ───────────────────────────
        ModelCard(result)

        // ── 3. behavioural factors ────────────────────────────────────────────────────
        FactorsSection(result)

        Spacer(Modifier.height(Space.tight))
        PrimaryButton("Start over", onRestart)
        Caption(
            "Research prototype. Nothing here is a diagnosis, a medical opinion, or advice " +
                "about treatment. If you are worried about how you feel, talk to someone " +
                "you trust or a professional."
        )
    }
}

@Composable
private fun ModelCard(result: AnalysisResult) {
    val calm = LocalCalmColors.current
    var expanded by remember { mutableStateOf(false) }

    CalmCard(dashed = true) {
        Eyebrow("From phone data alone — not validated", alt = true)
        Spacer(Modifier.height(Space.item))

        if (result.modelEstimate == null) {
            Body(result.modelUnavailableReason ?: "No estimate is available.", muted = true)
            Spacer(Modifier.height(Space.tight))
            Caption("Your questionnaire result above is unaffected by this.")
            return@CalmCard
        }

        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "${result.modelEstimate.roundToInt()}",
                style = MaterialTheme.typography.headlineSmall,
                color = calm.mutedInk,
            )
            Text(
                text = " / 100",
                style = MaterialTheme.typography.bodyLarge,
                color = calm.mutedInk,
            )
        }
        Spacer(Modifier.height(Space.item))
        Track(fraction = (result.modelEstimate / 100.0).toFloat(), alt = true, height = 4)
        Spacer(Modifier.height(Space.item))

        Body(
            "This is a guess made from your phone data alone, without your answers. It is " +
                "shown for comparison only.",
            muted = true,
        )
        Spacer(Modifier.height(Space.tight))
        Caption(
            "In our own evaluation this model did not work. It was less accurate than " +
                "simply assuming a person is average, and its ranking of who was more " +
                "stressed was slightly worse than chance. Please do not read it as an " +
                "estimate of how you actually feel."
        )

        Spacer(Modifier.height(Space.tight))
        QuietButton(
            text = if (expanded) "Hide the numbers" else "Why is it not validated?",
            onClick = { expanded = !expanded },
        )
        if (expanded) {
            Caption(
                "Tested on 1157 windows from 48 people, never testing on someone the model " +
                    "had already seen. Average error was 20.8 points; guessing each " +
                    "person's own average scored 16.9, and guessing everyone's average " +
                    "scored 19.9 — so the model was beaten by both. Rank correlation with " +
                    "reported stress was −0.12, where 0 would mean no relationship at all. " +
                    "We report this because a number on a screen looks equally confident " +
                    "whether or not it means anything."
            )
        }
    }
}

@Composable
private fun FactorsSection(result: AnalysisResult) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.item)) {
        Spacer(Modifier.height(Space.tight))
        Text(
            text = "What stood out this week",
            style = MaterialTheme.typography.titleMedium,
            color = LocalCalmColors.current.ink,
        )

        // Honest degradation: say what was left out and why, rather than quietly ranking a
        // smaller set of features as though it were the whole picture.
        if (result.usageAccessMissing) {
            Caption(
                "There is nothing to show here — usage access was not granted, so no phone " +
                    "data was read."
            )
            return@Column
        }
        if (!result.meetsCoverage) {
            Caption(
                "Not enough of the week could be reconstructed to say anything useful: " +
                    "${result.daysWithData.roundToInt()} day(s) of screen history were " +
                    "available, and this needs at least 3. This is normal on a phone that " +
                    "was recently set up."
            )
            return@Column
        }
        if (result.contributions.isEmpty()) {
            Caption("No factors could be worked out for this week.")
            return@Column
        }

        Caption(Factors.FACTOR_DISCLAIMER)

        if (!result.commsIncluded) {
            Caption(
                "Calls and messages were left out of this list, because access to them was " +
                    "not granted. Everything below is based on screen and lock activity only."
            )
        }

        val top = result.contributions.take(3)
        val largest = top.firstOrNull()?.magnitude ?: 1.0

        top.forEach { contribution ->
            val name = contribution.featureName
            Spacer(Modifier.height(Space.tight))
            Body(Factors.label(name))
            Spacer(Modifier.height(Space.tight))
            Track(
                fraction = if (largest > 0) (contribution.magnitude / largest).toFloat() else 0f,
                height = 4,
            )
            Spacer(Modifier.height(Space.tight))
            Caption(
                Factors.describe(
                    featureName = name,
                    dailyValues = result.dailyValues[name],
                    windowValue = result.staticValues[name],
                )
            )
        }

        Spacer(Modifier.height(Space.block))
        Text(
            text = "Something to try",
            style = MaterialTheme.typography.titleMedium,
            color = LocalCalmColors.current.ink,
        )
        top.mapNotNull { Factors.suggestion(it.featureName) }.distinct().take(2).forEach {
            Spacer(Modifier.height(Space.tight))
            Body("·   $it")
        }
        Spacer(Modifier.height(Space.tight))
        Caption(Factors.SUGGESTION_DISCLAIMER)
    }
}
