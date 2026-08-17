package com.stressdetect.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stressdetect.ui.theme.CalmType
import com.stressdetect.ui.theme.LocalCalmColors
import com.stressdetect.ui.theme.Space
import com.stressdetect.ui.theme.rememberAnimationsEnabled

/**
 * The shared vocabulary. Screens compose these rather than styling their own widgets, so the
 * palette rules — no traffic lights, terracotta never under text, AA-safe button fills —
 * hold everywhere by construction.
 */

/**
 * Standard screen frame: 24dp gutters, capped at 440dp and centred.
 *
 * The cap is not decoration. A single column of body text past ~440dp is measurably harder
 * to read, and on a tablet an uncapped column stretches edge to edge.
 */
@Composable
fun ScreenScaffold(
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    // Only for screens that do NOT scroll: inside a vertical scroll the height constraint is
    // unbounded, and fillMaxHeight there is meaningless.
    fillHeight: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = Space.maxContentWidth)
                .fillMaxWidth()
                .then(if (fillHeight) Modifier.fillMaxHeight() else Modifier)
                .padding(horizontal = Space.screen),
            horizontalAlignment = horizontalAlignment,
            verticalArrangement = verticalArrangement,
            content = content,
        )
    }
}

/** Small letter-spaced eyebrow. */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, alt: Boolean = false) {
    val calm = LocalCalmColors.current
    Text(
        text = text.uppercase(),
        style = CalmType.eyebrow,
        color = if (alt) calm.secondary else calm.mutedInk,
        modifier = modifier,
    )
}

/**
 * A magnitude bar. Length is the ONLY encoding — no colour change at any value, so a high
 * reading never becomes a warning.
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
            .background(calm.divider),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(height.dp)
                .clip(RoundedCornerShape(height.dp))
                .background(if (alt) calm.secondary else calm.accent),
        )
    }
}

/** Flat card, 20dp radius, hairline border. No elevation — nothing floats in this design. */
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
        shape = RoundedCornerShape(Space.cardRadius),
        border = BorderStroke(
            1.dp,
            if (dashed) calm.secondary.copy(alpha = 0.4f) else calm.cardBorder,
        ),
    ) {
        Column(Modifier.padding(Space.card), content = { content() })
    }
}

/**
 * Filled button.
 *
 * Fill and label come from the theme's `buttonFill`/`onButton` pair rather than the accent,
 * because no single pairing passes AA in both themes: light mode is white on deep terracotta
 * (4.62:1), dark mode inverts to dark text on the bright accent (7.61:1). White on the plain
 * accent measures 3.16:1 and fails. `ContrastTest` locks both in.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val calm = LocalCalmColors.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val animate = rememberAnimationsEnabled()
    val scale by animateFloatAsState(
        targetValue = if (pressed && animate) 0.98f else 1f,
        label = "press",
    )

    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        modifier = modifier
            .fillMaxWidth()
            .height(Space.buttonHeight)
            .scale(scale),
        shape = RoundedCornerShape(Space.buttonRadius),
        colors = ButtonDefaults.buttonColors(
            containerColor = calm.buttonFill,
            contentColor = calm.onButton,
            disabledContainerColor = calm.divider,
            disabledContentColor = calm.mutedInk,
        ),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** A plain text link. Teal, which is the one accent that passes AA as text (4.95:1). */
@Composable
fun LinkText(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val calm = LocalCalmColors.current
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = calm.secondary,
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = Space.item),
    )
}

/** Kept for call sites that want a quieter, non-link action. */
@Composable
fun QuietButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) =
    LinkText(text = text, onClick = onClick, modifier = modifier)

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

@Composable
fun SectionHeading(text: String, modifier: Modifier = Modifier) {
    val calm = LocalCalmColors.current
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = calm.ink,
        modifier = modifier,
    )
}

/** Segmented option used by the appearance control in About. */
@Composable
fun ThemeOption(label: String, selected: Boolean, onClick: () -> Unit) {
    val calm = LocalCalmColors.current
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        color = if (selected) calm.accentMuted else calm.card,
        shape = RoundedCornerShape(Space.buttonRadius),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) calm.accent else calm.cardBorder),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = calm.ink,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = Space.item),
        )
    }
}

/**
 * Permanent DEMO DATA marker, on every screen once demo mode is on — in a viva someone may
 * scroll back, and a demo reading must never be mistakable for a real one.
 */
@Composable
fun DemoBanner(modifier: Modifier = Modifier) {
    val calm = LocalCalmColors.current
    Surface(modifier = modifier.fillMaxWidth(), color = calm.secondaryMuted) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.screen, vertical = Space.tight),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "DEMO DATA — replayed sample, not this phone",
                style = CalmType.eyebrow,
                color = if (calm.isDark) calm.ink else calm.secondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}
