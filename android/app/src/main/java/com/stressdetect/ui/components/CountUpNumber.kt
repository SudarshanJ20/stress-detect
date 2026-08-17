package com.stressdetect.ui.components

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import com.stressdetect.ui.theme.CalmType
import com.stressdetect.ui.theme.LocalCalmColors
import com.stressdetect.ui.theme.rememberAnimationsEnabled

/**
 * The hero number, counting up from zero on first appearance.
 *
 * Decelerating rather than linear, so it settles onto the final value instead of stopping
 * dead. It counts once, on first composition — not on every recomposition, which would make
 * the number twitch whenever anything else on the screen changed.
 *
 * With animations switched off it renders the final value immediately: someone who asked for
 * no motion should not watch a number tick.
 */
@Composable
fun CountUpNumber(
    value: Int,
    modifier: Modifier = Modifier,
    suffix: String = "%",
    durationMillis: Int = 600,
) {
    val calm = LocalCalmColors.current
    val animate = rememberAnimationsEnabled()

    var target by remember { mutableIntStateOf(if (animate) 0 else value) }
    LaunchedEffect(value) { target = value }

    val shown by animateIntAsState(
        targetValue = target,
        animationSpec = tween(
            durationMillis = if (animate) durationMillis else 0,
            easing = LinearOutSlowInEasing,
        ),
        label = "countUp",
    )

    // The hero grows with the system font scale, but only up to 1.3x. At 2.0x an uncapped
    // 76sp "100%" needs about 320dp of width and the screen has 272dp — it would clip, and a
    // clipped score is worse for accessibility than one that stopped growing. Body text still
    // scales all the way, which is where the accessibility need actually is.
    val cap = heroFontScaleCap(LocalDensity.current.fontScale)

    Text(
        text = "$shown$suffix",
        style = CalmType.hero.copy(
            fontSize = CalmType.hero.fontSize * cap,
            lineHeight = CalmType.hero.lineHeight * cap,
        ),
        color = calm.ink,
        maxLines = 1,
        textAlign = TextAlign.Start,
        modifier = modifier,
    )
}

private const val HERO_MAX_FONT_SCALE = 1.3f

/**
 * Ratio to multiply the hero's declared size by so it stops growing past
 * [HERO_MAX_FONT_SCALE] — 1f below the threshold, shrinking above it so the rendered size
 * stays put. Extracted so `HeroScaleTest` can check it actually fits the narrowest screen.
 */
internal fun heroFontScaleCap(fontScale: Float): Float =
    if (fontScale > HERO_MAX_FONT_SCALE) HERO_MAX_FONT_SCALE / fontScale else 1f
