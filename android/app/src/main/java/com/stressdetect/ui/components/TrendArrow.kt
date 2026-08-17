package com.stressdetect.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stressdetect.ui.content.WeekSummary
import com.stressdetect.ui.theme.LocalCalmColors

/**
 * Which way a week moved against the person's own earlier weeks.
 *
 * **Drawn, not typed.** "↑" as a text glyph would be two things this design cannot accept:
 * a glyph whose shape and metrics depend on whichever font happens to resolve it (the
 * emulator and the phone do not agree), and *text* — which would put it under the 4.5:1 AA
 * rule, where the terracotta measures 4.42:1 on paper and fails. As a drawn mark it is a
 * graphical object at 3:1, which `accentDeep` clears comfortably in both themes.
 *
 * The colour is decoration either way: the direction is in the shape, and again in the words
 * beside it. Nothing here is red — more screen time is not a verdict, and this palette has no
 * traffic lights.
 */
@Composable
fun TrendArrow(
    direction: WeekSummary.Direction,
    modifier: Modifier = Modifier,
    size: Dp = 14.dp,
) {
    val calm = LocalCalmColors.current
    val color = when (direction) {
        WeekSummary.Direction.LEVEL -> calm.mutedInk
        else -> calm.accentDeep
    }

    Canvas(modifier.size(size)) {
        val extent = this.size.minDimension
        val centre = extent / 2f
        val inset = extent * 0.12f
        val head = extent * 0.28f
        val stroke = Stroke(
            width = extent * 0.13f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )

        val path = Path()
        when (direction) {
            WeekSummary.Direction.UP -> {
                val tip = Offset(centre, inset)
                path.moveTo(centre, extent - inset)
                path.lineTo(tip.x, tip.y)
                path.moveTo(tip.x - head, tip.y + head)
                path.lineTo(tip.x, tip.y)
                path.lineTo(tip.x + head, tip.y + head)
            }

            WeekSummary.Direction.DOWN -> {
                val tip = Offset(centre, extent - inset)
                path.moveTo(centre, inset)
                path.lineTo(tip.x, tip.y)
                path.moveTo(tip.x - head, tip.y - head)
                path.lineTo(tip.x, tip.y)
                path.lineTo(tip.x + head, tip.y - head)
            }

            WeekSummary.Direction.LEVEL -> {
                val tip = Offset(extent - inset, centre)
                path.moveTo(inset, centre)
                path.lineTo(tip.x, tip.y)
                path.moveTo(tip.x - head, tip.y - head)
                path.lineTo(tip.x, tip.y)
                path.lineTo(tip.x - head, tip.y + head)
            }
        }
        drawPath(path, color = color, style = stroke)
    }
}
