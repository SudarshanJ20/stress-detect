package com.stressdetect.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stressdetect.ui.theme.LocalCalmColors
import com.stressdetect.ui.theme.Space

/**
 * The shared vocabulary. Screens compose these rather than styling their own widgets, so
 * the "no semantic colour, no alarm" rules hold everywhere by construction.
 */

/** Small letter-spaced eyebrow above a section. Never bold, never shouty. */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, alt: Boolean = false) {
    val calm = LocalCalmColors.current
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
        color = if (alt) calm.accentAlt else calm.mutedInk,
        modifier = modifier,
    )
}

/**
 * A magnitude bar. Length is the ONLY encoding — there is no colour change at any value,
 * so a high reading never turns into a warning.
 */
@Composable
fun Track(
    fraction: Float,
    modifier: Modifier = Modifier,
    alt: Boolean = false,
    height: Int = 6,
) {
    val calm = LocalCalmColors.current
    Box(
        modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(RoundedCornerShape(height.dp))
            .background(calm.track),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(height.dp)
                .clip(RoundedCornerShape(height.dp))
                .background(if (alt) calm.accentAlt else calm.accent),
        )
    }
}

/** Flat card with a hairline border — no elevation, no shadow, nothing floating. */
@Composable
fun CalmCard(
    modifier: Modifier = Modifier,
    dashed: Boolean = false,
    content: @Composable () -> Unit,
) {
    val calm = LocalCalmColors.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = calm.card,
        shape = RoundedCornerShape(14.dp),
        // The model card is visually demoted with a lighter border: at a glance it should
        // read as secondary to the questionnaire card above it.
        border = BorderStroke(1.dp, if (dashed) calm.accentAlt.copy(alpha = 0.45f) else calm.track),
    ) {
        Column(Modifier.padding(Space.block), content = { content() })
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val calm = LocalCalmColors.current
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = calm.accent,
            contentColor = if (calm.isDark) calm.paper else androidx.compose.ui.graphics.Color.White,
            disabledContainerColor = calm.track,
            disabledContentColor = calm.mutedInk,
        ),
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun QuietButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val calm = LocalCalmColors.current
    TextButton(onClick = onClick, modifier = modifier) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = calm.mutedInk)
    }
}

@Composable
fun Body(text: String, modifier: Modifier = Modifier, muted: Boolean = false) {
    val calm = LocalCalmColors.current
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = if (muted) calm.mutedInk else calm.ink,
        modifier = modifier,
    )
}

@Composable
fun Caption(text: String, modifier: Modifier = Modifier) {
    val calm = LocalCalmColors.current
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = calm.mutedInk,
        modifier = modifier,
    )
}

@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    val calm = LocalCalmColors.current
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        color = calm.ink,
        modifier = modifier,
    )
}

/**
 * Permanent DEMO DATA marker.
 *
 * Shown on EVERY screen once demo mode is on, not just the result: in a viva someone may
 * scroll back, and a demo reading must never be mistakable for a real one at any point.
 */
@Composable
fun DemoBanner(modifier: Modifier = Modifier) {
    val calm = LocalCalmColors.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = calm.accentAlt.copy(alpha = if (calm.isDark) 0.22f else 0.16f),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.screen, vertical = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "DEMO DATA — replayed sample, not this phone",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp),
                color = calm.ink,
                textAlign = TextAlign.Center,
            )
        }
    }
}
