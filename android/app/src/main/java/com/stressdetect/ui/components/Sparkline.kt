package com.stressdetect.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
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

        fun yFor(score: Int): Float =
            padding + usableHeight * (1f - (score.toFloat() / maxScore))

        // Hairline gridlines at quarters of the scale. Plain, unlabelled, and identical —
        // none of them is a threshold anyone should worry about crossing.
        for (fraction in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
            val y = padding + usableHeight * (1f - fraction)
            drawLine(
                color = calm.divider,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx(),
            )
        }

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
                color = calm.secondary.copy(alpha = 0.4f),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
            )
        }

        // Dots in teal at 8dp; the most recent one solid, the rest slightly back.
        points.forEachIndexed { index, point ->
            val isLatest = index == points.lastIndex
            drawCircle(
                color = if (isLatest) calm.secondary else calm.secondary.copy(alpha = 0.55f),
                radius = 4.dp.toPx(),
                center = point,
            )
        }
    }
}
