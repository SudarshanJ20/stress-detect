package com.stressdetect.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.stressdetect.data.CheckInRepository
import com.stressdetect.survey.Pss4
import com.stressdetect.ui.components.Body
import com.stressdetect.ui.components.CalmCard
import com.stressdetect.ui.components.Caption
import com.stressdetect.ui.components.QuietButton
import com.stressdetect.ui.components.ScreenTitle
import com.stressdetect.ui.components.Sparkline
import com.stressdetect.ui.theme.Space
import java.time.format.DateTimeFormatter

/**
 * Past check-ins.
 *
 * The chart is fixed to the full 0–16 range rather than auto-scaled, so a steady run of
 * scores looks steady. Auto-scaling would turn one-point wobble into dramatic peaks — the
 * chart would look most alarming exactly when someone was most stable.
 *
 * No trend line, no "you're improving", no week-on-week percentage. Two check-ins are not a
 * trend, and this app has no basis for telling anyone which direction they are heading.
 */
@Composable
fun HistoryScreen(
    entries: List<CheckInRepository.Entry>,
    onBack: () -> Unit,
) {
    val dateFormat = remember { DateTimeFormatter.ofPattern("d MMM") }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Space.screen),
        verticalArrangement = Arrangement.spacedBy(Space.block),
    ) {
        Spacer(Modifier.height(Space.block))
        ScreenTitle("Your history")

        if (entries.isEmpty()) {
            Body("Nothing here yet. After a few check-ins you'll be able to see how " +
                "things move about.", muted = true)
            Spacer(Modifier.height(Space.block))
            QuietButton("Back", onBack)
            return@Column
        }

        CalmCard {
            Sparkline(scores = entries.map { it.score }, maxScore = Pss4.MAX_SCORE)
            Spacer(Modifier.height(Space.item))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Caption(entries.first().takenAt.format(dateFormat))
                Caption(entries.last().takenAt.format(dateFormat))
            }
        }

        Body(
            text = if (entries.size == 1) {
                "One check-in so far. There'll be more to see next time."
            } else {
                "${entries.size} check-ins. Scores move around week to week — that is " +
                    "normal, and a single high or low one on its own does not mean much."
            },
            muted = true,
        )

        Spacer(Modifier.height(Space.tight))
        entries.takeLast(6).reversed().forEach { entry ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Body(entry.takenAt.format(dateFormat))
                Body("${Pss4.percentOfMaximum(entry.score)}%", muted = true)
            }
        }

        Spacer(Modifier.height(Space.block))
        QuietButton("Back", onBack)
    }
}
