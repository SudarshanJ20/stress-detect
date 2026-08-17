package com.stressdetect.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.stressdetect.ui.components.Body
import com.stressdetect.ui.components.Caption
import com.stressdetect.ui.components.PrimaryButton
import com.stressdetect.ui.components.ScreenScaffold
import com.stressdetect.ui.components.ScreenTitle
import com.stressdetect.ui.theme.Space

/**
 * Shown once, before the first check-in.
 *
 * The short version of what the app reads. It is not the full disclosure — that is in About,
 * unchanged and complete — but everything here is true and sufficient to make an informed
 * first decision. Three bullets and two sentences, because a wall of text at the front door
 * gets scrolled past, which is worse for informed consent than a short version people
 * actually read.
 */
@Composable
fun FirstRunScreen(onContinue: () -> Unit) {
    ScreenScaffold(
        Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Space.block),
    ) {
        Spacer(Modifier.height(Space.section))
        ScreenTitle("Before you start")

        Body("Alongside your answers, this app can look at things your phone has already " +
            "recorded:")

        Body("·   when your screen went on and off", Modifier.padding(start = Space.tight))
        Body("·   when calls and messages happened — the times only, never who with, " +
            "never what was said", Modifier.padding(start = Space.tight))

        Body("None of it leaves your phone. The app has no internet permission, so it " +
            "cannot send anything anywhere even if it tried.")

        Caption("You can read the longer version any time under About. This is a research " +
            "prototype, not a medical device.")

        Spacer(Modifier.height(Space.tight))
        PrimaryButton("Got it", onContinue)
    }
}
