package com.stressdetect.ui.screens

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.stressdetect.ui.components.Body
import com.stressdetect.ui.components.Caption
import com.stressdetect.ui.components.Eyebrow
import com.stressdetect.ui.components.PrimaryButton
import com.stressdetect.ui.components.ScreenTitle
import com.stressdetect.ui.theme.LocalCalmColors
import com.stressdetect.ui.theme.Space
import com.stressdetect.ui.theme.ThemeChoice

/**
 * First screen. Its job is disclosure before anything is read — what is taken, what is
 * never taken, and that nothing leaves the phone.
 *
 * The claims here are architectural facts, not promises: there is no INTERNET permission in
 * the manifest, and the comms queries read a DATE column only. Keep them true if either
 * changes.
 */
@Composable
fun OnboardingScreen(
    onContinue: () -> Unit,
    onSecretDemoToggle: () -> Unit,
    themeChoice: ThemeChoice,
    onThemeChange: (ThemeChoice) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Space.screen),
        verticalArrangement = Arrangement.spacedBy(Space.block),
    ) {
        Spacer(Modifier.height(Space.block))
        ScreenTitle("A look at your week")

        Body(
            "This app reads three things that are already stored on your phone. It does " +
                "not switch anything on or start recording."
        )

        Bullet("when your screen locked and unlocked")
        Bullet("when calls happened — the times only, never numbers or who they were with")
        Bullet("when messages arrived — the times only, never any content")

        Body(
            "Nothing leaves this phone. The app has no internet permission at all, so it " +
                "is not able to send anything anywhere."
        )

        Body(
            "You will answer four short questions first. Those answers are what the result " +
                "is based on."
        )

        Caption(
            "Research prototype. Not a medical device, and not a diagnosis of anything. " +
                "You can stop at any point."
        )

        Spacer(Modifier.height(Space.tight))
        PrimaryButton("Continue", onContinue)

        // Appearance follows the system by default; this is the manual override.
        Spacer(Modifier.height(Space.tight))
        Eyebrow("Appearance")
        Spacer(Modifier.height(Space.tight))
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

        // Hidden demo toggle: a long-press on the version line. Deliberately undiscoverable
        // by accident, and the resulting banner is impossible to miss.
        Caption(
            text = "Version 0.1.0 · spec v0.7.0",
            modifier = Modifier
                .padding(top = Space.tight)
                .pointerInput(Unit) {
                    detectTapGestures(onLongPress = { onSecretDemoToggle() })
                },
        )
    }
}

@Composable
private fun Bullet(text: String) {
    Body("·   $text", modifier = Modifier.padding(start = Space.tight))
}

@Composable
private fun ThemeOption(label: String, selected: Boolean, onClick: () -> Unit) {
    val calm = LocalCalmColors.current
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        color = if (selected) calm.accent.copy(alpha = if (calm.isDark) 0.22f else 0.14f) else calm.card,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (selected) calm.accent else calm.track),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = calm.ink,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
        )
    }
}
