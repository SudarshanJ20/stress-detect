package com.stressdetect.ui.screens

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.input.pointer.pointerInput
import com.stressdetect.data.AnalysisResult
import com.stressdetect.features.SpecConstants
import com.stressdetect.survey.Pss4
import com.stressdetect.ui.components.Body
import com.stressdetect.ui.components.CalmCard
import com.stressdetect.ui.components.Caption
import com.stressdetect.ui.components.Eyebrow
import com.stressdetect.ui.components.QuietButton
import com.stressdetect.ui.components.ScreenTitle
import com.stressdetect.ui.components.ThemeOption
import com.stressdetect.ui.content.Factors
import com.stressdetect.ui.theme.LocalCalmColors
import com.stressdetect.ui.theme.Space
import com.stressdetect.ui.theme.ThemeChoice

/**
 * Everything technically true about this app, in full.
 *
 * The redesign moved this content off the first screen; **it did not soften or remove any
 * of it.** In particular the honest account of the model's evaluation — that it lost to a
 * per-person mean baseline with a negative rank correlation — appears here in the same
 * terms it did when it was on the result screen. If a future edit trims it, that is a
 * change of substance, not of presentation.
 *
 * This is also the only screen permitted to use research vocabulary; `CopyRulesTest`
 * enforces that the rest of the app does not.
 */
@Composable
fun AboutScreen(
    lastResult: AnalysisResult?,
    themeChoice: ThemeChoice,
    onThemeChange: (ThemeChoice) -> Unit,
    onOpenUsageSettings: () -> Unit,
    onDeleteHistory: () -> Unit,
    onSecretDemoToggle: () -> Unit,
    onBack: () -> Unit,
) {
    val calm = LocalCalmColors.current

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Space.screen),
        verticalArrangement = Arrangement.spacedBy(Space.block),
    ) {
        Spacer(Modifier.height(Space.block))
        ScreenTitle("About this app")

        Body("A research prototype from a student project. It is not a medical device, it " +
            "does not diagnose anything, and it is not a substitute for talking to someone.")

        // ── the score ────────────────────────────────────────────────────────────────
        Section("Where your score comes from")
        Body("The percentage you see is your answers to four questions, added up and " +
            "divided by the maximum of ${Pss4.MAX_SCORE}. Nothing else feeds into it.")
        Body(Pss4.CITATION)
        Caption("Two of the four questions are positively worded and are scored in " +
            "reverse, which is why answering \"never\" to everything gives a middle score " +
            "rather than zero.")
        Caption("The questions ask about the last month. The phone data below covers the " +
            "last 7 days, so the two describe different stretches of time.")
        Caption("The scale has no official cut-offs — nobody has established a score at " +
            "which stress becomes \"moderate\" or \"high\". The bands this app shows are " +
            "our own plain-language grouping, meant only to put words next to a number.")

        // ── the model ────────────────────────────────────────────────────────────────
        Section("The prediction model, and why it isn't your score")
        Body("This project also trained a model to estimate stress from phone activity " +
            "alone. It is not used for the number you see, and here is the honest reason.")
        CalmCard(dashed = true) {
            Eyebrow("Evaluation result", alt = true)
            Spacer(Modifier.height(Space.tight))
            Body("Tested on 1157 windows from 48 people, never testing on someone the " +
                "model had already seen.", muted = true)
            Spacer(Modifier.height(Space.tight))
            Caption("Average error 20.8 points. Guessing each person's own average scored " +
                "16.9; guessing everyone's average scored 19.9 — so the model was beaten " +
                "by both. Rank correlation with reported stress was −0.12, where 0 would " +
                "mean no relationship at all.")
            Spacer(Modifier.height(Space.tight))
            Caption("A model that loses to a per-person mean and ranks slightly worse than " +
                "chance should not be shown to anyone as an estimate of how they feel, and " +
                "blending it into the questionnaire score would only make that score worse. " +
                "It stays in the project as a documented negative result.")
        }

        // ── the model, on THIS week ──────────────────────────────────────────────────
        // The per-window attribution from the previous design lives here now rather than
        // being deleted: it is real work and it is honest, it simply has no business in
        // front of someone who just answered four questions about their life.
        if (lastResult?.modelEstimate != null) {
            Section("What the model made of your last check-in")
            Body("It estimated ${lastResult.modelEstimate.toInt()} out of 100 from your " +
                "phone activity. Your score above is unaffected by this number.", muted = true)
            if (lastResult.contributions.isNotEmpty()) {
                Spacer(Modifier.height(Space.tight))
                Caption("The inputs it reacted to most, largest first. This explains the " +
                    "model, not you — and under the evaluation above, the model has no " +
                    "demonstrated relationship with stress.")
                lastResult.contributions.take(4).forEach { contribution ->
                    Caption("·   ${Factors.label(contribution.featureName)}")
                }
            }
        }

        // ── the data ─────────────────────────────────────────────────────────────────
        Section("What this app reads")
        Body("·   when your screen went on and off, and when your phone was locked")
        Body("·   when calls and messages happened — times only. Never numbers, never " +
            "contacts, never any content. The queries read a single date column.")
        Body("It looks back over the last 7 days of history your phone already stores.")
        Body("Nothing leaves this phone. There is no internet permission in this app, so " +
            "it is not capable of sending anything anywhere.")
        Caption("Your check-in scores are kept on this phone so the history chart can show " +
            "them. Only the total and the date — not your individual answers.")

        QuietButton("Change screen-activity permission", onOpenUsageSettings)
        QuietButton("Delete my check-in history", onDeleteHistory)

        // ── appearance ───────────────────────────────────────────────────────────────
        Section("Appearance")
        Row(horizontalArrangement = Arrangement.spacedBy(Space.tight)) {
            ThemeChoice.entries.forEach { choice ->
                ThemeOption(
                    label = when (choice) {
                        ThemeChoice.SYSTEM -> "Auto"
                        ThemeChoice.LIGHT -> "Light"
                        ThemeChoice.DARK -> "Dark"
                    },
                    selected = choice == themeChoice,
                    onClick = { onThemeChange(choice) },
                )
            }
        }

        Section("Version")
        // The demo toggle lives on the version line, as it did before the redesign:
        // undiscoverable by accident, and the banner it raises is impossible to miss.
        Text(
            text = "App 0.2.0 · data spec ${SpecConstants.SPEC_VERSION}",
            style = MaterialTheme.typography.bodyMedium,
            color = calm.mutedInk,
            modifier = Modifier.pointerInput(Unit) {
                detectTapGestures(onLongPress = { onSecretDemoToggle() })
            },
        )
        Caption("The feature definitions, the fixture that both implementations are tested " +
            "against, and the full evaluation write-up are in the project repository.")

        Spacer(Modifier.height(Space.block))
        QuietButton("Back", onBack)
        Spacer(Modifier.height(Space.block))
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(Space.item))
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = LocalCalmColors.current.ink,
    )
}
