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
import com.stressdetect.ui.components.CalmCard
import com.stressdetect.ui.components.Caption
import com.stressdetect.ui.components.Eyebrow
import com.stressdetect.ui.components.PrimaryButton
import com.stressdetect.ui.components.QuietButton
import com.stressdetect.ui.components.ScreenScaffold
import com.stressdetect.ui.components.ScreenTitle
import com.stressdetect.ui.theme.Space

/**
 * Permissions, with the awkward truth stated plainly: usage access cannot be granted from a
 * dialog. Android only exposes it through a Settings screen, so the button opens Settings
 * and the copy says why — otherwise being bounced out of the app looks like a bug.
 */
@Composable
fun PermissionsScreen(
    usageAccessGranted: Boolean,
    commsGranted: Boolean,
    demoMode: Boolean,
    onOpenUsageSettings: () -> Unit,
    onRequestComms: () -> Unit,
    onContinue: () -> Unit,
) {
    ScreenScaffold(
        Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Space.block),
    ) {
        Spacer(Modifier.height(Space.tight))
        ScreenTitle("Two permissions")

        CalmCard {
            Eyebrow("Required")
            Spacer(Modifier.height(Space.tight))
            Body("Usage access")
            Spacer(Modifier.height(Space.tight))
            Caption(
                "This one opens your Settings — Android has no in-app dialog for it. Find " +
                    "this app in the list and turn it on, then come back."
            )
            Spacer(Modifier.height(Space.item))
            if (usageAccessGranted) {
                Caption("Granted.")
            } else {
                PrimaryButton("Open Settings", onOpenUsageSettings)
            }
        }

        CalmCard {
            Eyebrow("Optional")
            Spacer(Modifier.height(Space.tight))
            Body("Calls and messages")
            Spacer(Modifier.height(Space.tight))
            Caption(
                "Times only — never numbers, contacts or content. The app works without " +
                    "this, and if you skip it the result says which parts were left out."
            )
            Spacer(Modifier.height(Space.item))
            if (commsGranted) {
                Caption("Granted.")
            } else {
                QuietButton("Allow calls and messages", onRequestComms)
            }
        }

        Spacer(Modifier.height(Space.tight))
        PrimaryButton(
            text = if (usageAccessGranted || demoMode) "Continue" else "Continue anyway",
            onClick = onContinue,
        )
        if (demoMode) {
            // Without this, a demo run tells the presenter their result will be
            // questionnaire-only, which is the opposite of what demo mode does.
            Caption(
                "Demo mode is on, so these permissions are not used — the app will replay " +
                    "the built-in sample week instead of reading this phone."
            )
        } else if (!usageAccessGranted) {
            Caption(
                "Without usage access there is no phone data to look at, so you will get " +
                    "your check-in result on its own."
            )
        }
    }
}
