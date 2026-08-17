package com.stressdetect.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.stressdetect.ui.components.Body
import com.stressdetect.ui.components.Caption
import com.stressdetect.ui.components.ScreenScaffold
import com.stressdetect.ui.components.ScreenTitle
import com.stressdetect.ui.theme.LocalCalmColors
import com.stressdetect.ui.theme.Space

/**
 * Progress while the window is extracted and the model runs.
 *
 * A slow, low-contrast pulse rather than a spinner or a percentage bar: the wait is short,
 * and a racing progress indicator sets a tempo this app is trying not to set. The step list
 * doubles as a reminder of what is happening and where.
 */
@Composable
fun AnalysisScreen(completedSteps: Int) {
    val calm = LocalCalmColors.current
    val steps = listOf(
        "Looking at your screen activity",
        "Looking at when calls and messages happened",
        "Working out your patterns",
        "Nearly there",
    )

    ScreenScaffold(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        fillHeight = true,
    ) {
        val transition = rememberInfiniteTransition(label = "pulse")
        Row(horizontalArrangement = Arrangement.spacedBy(Space.item)) {
            repeat(3) { index ->
                val alpha by transition.animateFloat(
                    initialValue = 0.25f,
                    targetValue = 0.9f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(900, delayMillis = index * 220, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "dot$index",
                )
                Box(
                    Modifier.size(8.dp).clip(CircleShape).alpha(alpha).background(calm.accent),
                )
            }
        }

        Spacer(Modifier.height(Space.section))
        ScreenTitle("Reading your last 7 days")
        Spacer(Modifier.height(Space.block))

        Column(verticalArrangement = Arrangement.spacedBy(Space.item)) {
            steps.forEachIndexed { index, step ->
                val done = index < completedSteps
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (done) calm.accent else calm.track),
                    )
                    Spacer(Modifier.size(Space.item))
                    Body(step, muted = !done)
                }
            }
        }

        Spacer(Modifier.height(Space.section))
        Caption("All of this happens on this phone.")
    }
}
