package com.stressdetect.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.stressdetect.ui.theme.LocalCalmColors

/**
 * Past check-in scores over time: a soft line with a dot per check-in.
 *
 * Fixed to the full 0–16 range rather than auto-scaling to the data. Auto-scaling would
 * magnify small week-to-week wobble into dramatic peaks and troughs — the chart would look
 * alarming precisely when someone's scores were actually stable. The line is drawn in the
 * one accent colour at every height; nothing changes colour as the score rises.
 */
@Composable
fun Sparkline(
    scores: List<Int>,
    maxScore: Int,
    modifier: Modifier = Modifier,
    heightDp: Int = 140,
) {
    val calm = LocalCalmColors.current

    Canvas(
        modifier
            .fillMaxWidth()
            .height(heightDp.dp),
    ) {
        if (scores.isEmpty()) return@Canvas

        val padding = size.height * 0.12f
        val usableHeight = size.height - padding * 2
        val dotRadius = size.height * 0.035f

        fun yFor(score: Int): Float =
            padding + usableHeight * (1f - (score.toFloat() / maxScore))

        // A single reference line at the scale's midpoint, so a dot has something to sit
        // against without implying a threshold anyone should worry about crossing.
        drawLine(
            color = calm.track,
            start = Offset(0f, yFor(maxScore / 2)),
            end = Offset(size.width, yFor(maxScore / 2)),
            strokeWidth = size.height * 0.008f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 10f)),
        )

        val step = if (scores.size == 1) 0f else size.width / (scores.size - 1).toFloat()
        val points = scores.mapIndexed { index, score ->
            Offset(
                x = if (scores.size == 1) size.width / 2f else index * step,
                y = yFor(score),
            )
        }

        if (points.size >= 2) {
            val path = Path().apply {
                moveTo(points.first().x, points.first().y)
                for (i in 1 until points.size) {
                    // Smooth the joins so the line reads as a drift rather than a spike.
                    val previous = points[i - 1]
                    val current = points[i]
                    val midX = (previous.x + current.x) / 2f
                    cubicTo(midX, previous.y, midX, current.y, current.x, current.y)
                }
            }
            drawPath(
                path = path,
                color = calm.accent.copy(alpha = 0.7f),
                style = Stroke(width = size.height * 0.018f, cap = StrokeCap.Round),
            )
        }

        points.forEachIndexed { index, point ->
            val isLatest = index == points.lastIndex
            drawCircle(
                color = if (isLatest) calm.accent else calm.accent.copy(alpha = 0.45f),
                radius = if (isLatest) dotRadius * 1.4f else dotRadius,
                center = point,
            )
        }
    }
}
