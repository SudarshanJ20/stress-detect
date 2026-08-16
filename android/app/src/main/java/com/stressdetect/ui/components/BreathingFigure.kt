package com.stressdetect.ui.components

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.stressdetect.ui.content.Band
import com.stressdetect.ui.theme.LocalCalmColors

/**
 * A quiet line-drawn face that breathes.
 *
 * **What varies with the score is only the mouth, and only within gentle-smile → straight.**
 * [Band.mouthCurve] is clamped at 0 by construction, so no score can render a frown, a
 * downturned mouth, or anything a person having a bad week could read as the app looking
 * sad at them. The highest band gets a level mouth and a slower breath — the visual
 * difference between bands is deliberately small.
 *
 * Motion is one breath (a ~2% scale swell) plus an occasional blink. Nothing else moves,
 * and the whole thing stops if the user has asked the system for less animation.
 */
@Composable
fun BreathingFigure(
    band: Band,
    modifier: Modifier = Modifier,
    sizeDp: Int = 132,
) {
    val calm = LocalCalmColors.current
    val context = LocalContext.current

    // Respect the system "remove animations" setting. Verified against the SDK source:
    // ANIMATOR_DURATION_SCALE is "Scaling factor for Animator-based animations… Setting to
    // 0.0f will cause animations to end immediately." Someone who has turned animations off
    // has asked for stillness; a breathing face is exactly what they meant.
    val animate = remember {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f,
            ) != 0f
        }.getOrDefault(true)
    }

    // A slower breath at the top band: about 5.5s per cycle against 4s. Rest, not alarm.
    val breathMillis = if (band == Band.HIGH) 5500 else 4000

    val scale: Float
    val blink: Float
    if (animate) {
        val transition = rememberInfiniteTransition(label = "breath")
        scale = transition.animateFloat(
            initialValue = 1f,
            targetValue = 1.02f,
            animationSpec = infiniteRepeatable(
                animation = tween(breathMillis, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "scale",
        ).value
        // Eyes are open almost all the time; the blink is a brief dip in eye height.
        blink = transition.animateFloat(
            initialValue = 1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = keyframes {
                    durationMillis = 6000
                    1f at 0
                    1f at 5600
                    0.1f at 5750
                    1f at 5900
                },
            ),
            label = "blink",
        ).value
    } else {
        scale = 1f
        blink = 1f
    }

    Box(modifier.size(sizeDp.dp)) {
        Canvas(Modifier.size(sizeDp.dp)) {
            drawFace(
                stroke = calm.ink,
                accent = calm.accent,
                scale = scale,
                blink = blink,
                mouthCurve = band.mouthCurve,
            )
        }
    }
}

private fun DrawScope.drawFace(
    stroke: Color,
    accent: Color,
    scale: Float,
    blink: Float,
    mouthCurve: Float,
) {
    val centre = Offset(size.width / 2f, size.height / 2f)
    val radius = (size.minDimension / 2f) * 0.78f * scale
    val lineWidth = size.minDimension * 0.018f

    // A soft halo that swells with the breath — carries most of the motion, so the face
    // itself barely moves.
    drawCircle(
        color = accent.copy(alpha = 0.12f),
        radius = radius * 1.16f,
        center = centre,
    )
    drawCircle(
        color = stroke.copy(alpha = 0.75f),
        radius = radius,
        center = centre,
        style = Stroke(width = lineWidth),
    )

    // Eyes: short vertical strokes that shorten to a dot on a blink.
    val eyeOffsetX = radius * 0.38f
    val eyeY = centre.y - radius * 0.18f
    val eyeHeight = radius * 0.16f * blink.coerceIn(0.08f, 1f)
    for (direction in listOf(-1f, 1f)) {
        val x = centre.x + direction * eyeOffsetX
        drawLine(
            color = stroke.copy(alpha = 0.85f),
            start = Offset(x, eyeY - eyeHeight),
            end = Offset(x, eyeY + eyeHeight),
            strokeWidth = lineWidth * 1.1f,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
    }

    // Mouth: a quadratic curve whose control point only ever sits at or BELOW the
    // endpoints on screen (positive y = downward in canvas space = an upward smile).
    val mouthHalfWidth = radius * 0.34f
    val mouthY = centre.y + radius * 0.34f
    val lift = radius * 0.20f * mouthCurve.coerceIn(0f, 1f)
    val path = Path().apply {
        moveTo(centre.x - mouthHalfWidth, mouthY)
        quadraticBezierTo(
            centre.x, mouthY + lift * 2f,
            centre.x + mouthHalfWidth, mouthY,
        )
    }
    drawPath(
        path = path,
        color = stroke.copy(alpha = 0.85f),
        style = Stroke(width = lineWidth * 1.1f, cap = androidx.compose.ui.graphics.StrokeCap.Round),
    )
}
