package com.stressdetect.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.stressdetect.ui.content.Band
import com.stressdetect.ui.theme.LocalCalmColors
import com.stressdetect.ui.theme.rememberAnimationsEnabled

/**
 * A line-drawn face that breathes. The one alive thing on the screen.
 *
 * The glow behind it is what stops it reading as flat, and it swells slightly MORE than the
 * face does (1.05 against 1.02), so the movement reads as breathing outward rather than the
 * whole drawing pulsing.
 *
 * **No traffic lights.** Every band draws the same terracotta. Only two things vary: the
 * depth of the wash behind the face, and the mouth — and the mouth's range is gentle-smile
 * to level, never down. [Band.mouthCurve] is clamped at 0 and `MouthGeometry` coerces again,
 * so no score can make this face look sad at someone having a bad week.
 */
@Composable
fun BreathingFigure(
    band: Band,
    modifier: Modifier = Modifier,
    sizeDp: Int = 148,
) {
    val calm = LocalCalmColors.current
    val animate = rememberAnimationsEnabled()

    // A slower breath at the top band — rest, not alarm.
    val breathMillis = if (band == Band.HIGH) 5600 else 4200

    val faceScale: Float
    val glowScale: Float
    val blink: Float
    if (animate) {
        val transition = rememberInfiniteTransition(label = "breath")
        faceScale = transition.animateFloat(
            initialValue = 1f, targetValue = 1.02f,
            animationSpec = infiniteRepeatable(
                animation = tween(breathMillis, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "face",
        ).value
        glowScale = transition.animateFloat(
            initialValue = 1f, targetValue = 1.05f,
            animationSpec = infiniteRepeatable(
                animation = tween(breathMillis, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "glow",
        ).value
        blink = transition.animateFloat(
            initialValue = 1f, targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = keyframes {
                    durationMillis = 6400
                    1f at 0
                    1f at 6000
                    0.12f at 6150
                    1f at 6300
                },
            ),
            label = "blink",
        ).value
    } else {
        faceScale = 1f
        glowScale = 1f
        blink = 1f
    }

    Box(modifier.size(sizeDp.dp)) {
        Canvas(Modifier.size(sizeDp.dp)) {
            drawFigure(
                stroke = calm.accentDeep,
                wash = calm.accentMuted,
                washDepth = washDepth(band),
                faceScale = faceScale,
                glowScale = glowScale,
                blink = blink,
                mouthCurve = band.mouthCurve,
                strokeWidthPx = 3.dp.toPx(),
            )
        }
    }
}

/**
 * How deep the wash behind the face sits, 0..1. This is the ONLY thing the score changes
 * about the colour — a little deeper at the top band, never a different hue.
 */
private fun washDepth(band: Band): Float = when (band) {
    Band.LOW -> 0.55f
    Band.SOME -> 0.70f
    Band.MODERATE -> 0.85f
    Band.HIGH -> 1f
}

private fun DrawScope.drawFigure(
    stroke: Color,
    wash: Color,
    washDepth: Float,
    faceScale: Float,
    glowScale: Float,
    blink: Float,
    mouthCurve: Float,
    strokeWidthPx: Float,
) {
    val centre = Offset(size.width / 2f, size.height / 2f)
    val baseRadius = (size.minDimension / 2f) * 0.66f
    val faceRadius = baseRadius * faceScale
    val glowRadius = baseRadius * 1.4f * glowScale

    // Soft radial glow: the wash at the centre fading to nothing. Drawn first, larger than
    // the head, and breathing wider than it — this is the depth the flat design lacked.
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                wash.copy(alpha = washDepth),
                wash.copy(alpha = washDepth * 0.45f),
                Color.Transparent,
            ),
            center = centre,
            radius = glowRadius,
        ),
        radius = glowRadius,
        center = centre,
    )

    // The head: washed interior so the face is not a hole in the glow, then the outline.
    drawCircle(color = wash.copy(alpha = washDepth * 0.9f), radius = faceRadius, center = centre)
    drawCircle(
        color = stroke,
        radius = faceRadius,
        center = centre,
        style = Stroke(width = strokeWidthPx),
    )

    // Eyes: short vertical strokes, shortening to a dot on a blink.
    val eyeOffsetX = faceRadius * 0.36f
    val eyeY = centre.y - faceRadius * 0.16f
    val eyeHalfHeight = faceRadius * 0.15f * blink.coerceIn(0.1f, 1f)
    for (direction in listOf(-1f, 1f)) {
        val x = centre.x + direction * eyeOffsetX
        drawLine(
            color = stroke,
            start = Offset(x, eyeY - eyeHalfHeight),
            end = Offset(x, eyeY + eyeHalfHeight),
            strokeWidth = strokeWidthPx,
            cap = StrokeCap.Round,
        )
    }

    // Mouth. A level mouth is stroked as a LINE, never as a Bézier with its control point on
    // the baseline — that degenerate path is what could vanish, leaving a blank face.
    val mouth = MouthGeometry.of(mouthCurve, faceRadius, centre.y)
    val left = Offset(centre.x - mouth.halfWidth, mouth.baselineY)
    val right = Offset(centre.x + mouth.halfWidth, mouth.baselineY)
    if (mouth.isLevel) {
        drawLine(
            color = stroke,
            start = left,
            end = right,
            strokeWidth = strokeWidthPx,
            cap = StrokeCap.Round,
        )
    } else {
        drawPath(
            path = Path().apply {
                moveTo(left.x, left.y)
                quadraticBezierTo(
                    centre.x, mouth.baselineY + mouth.controlOffsetY,
                    right.x, right.y,
                )
            },
            color = stroke,
            style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round),
        )
    }
}
