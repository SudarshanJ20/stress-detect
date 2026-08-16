package com.stressdetect.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.stressdetect.survey.Pss4
import com.stressdetect.ui.components.Caption
import com.stressdetect.ui.components.PrimaryButton
import com.stressdetect.ui.components.QuietButton
import com.stressdetect.ui.theme.LocalCalmColors
import com.stressdetect.ui.theme.Space

/**
 * One PSS-4 item per screen: four options on a page is a calmer read than sixteen, and it
 * keeps the long published sentence at a comfortable size.
 *
 * The item text comes verbatim from [Pss4] and is never reformatted, trimmed or reworded
 * here — including the "in the last month" stem, which does not match this app's 7-day
 * phone window. That mismatch is disclosed on the result screen rather than papered over by
 * editing a validated instrument.
 */
@Composable
fun Pss4Screen(
    itemIndex: Int,
    selected: Int?,
    onSelect: (Int) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    val calm = LocalCalmColors.current
    val item = Pss4.ITEMS[itemIndex]

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Space.screen),
        verticalArrangement = Arrangement.spacedBy(Space.block),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Space.tight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Pss4.ITEMS.indices.forEach { index ->
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (index == itemIndex) calm.accent else calm.track),
                )
            }
            Spacer(Modifier.weight(1f))
            Caption("${itemIndex + 1} of ${Pss4.ITEMS.size}")
        }

        Spacer(Modifier.height(Space.tight))
        Text(
            text = item.text,
            style = MaterialTheme.typography.headlineSmall,
            color = calm.ink,
        )

        Column(verticalArrangement = Arrangement.spacedBy(Space.tight)) {
            Pss4.ANCHORS.forEachIndexed { value, anchor ->
                AnchorRow(
                    label = anchor,
                    isSelected = selected == value,
                    onClick = { onSelect(value) },
                )
            }
        }

        Caption(Pss4.CITATION)

        Spacer(Modifier.height(Space.tight))
        PrimaryButton(
            text = if (itemIndex == Pss4.ITEMS.lastIndex) "See your result" else "Next",
            onClick = onNext,
            enabled = selected != null,
        )
        QuietButton(if (itemIndex == 0) "Back" else "Previous question", onBack)
    }
}

@Composable
private fun AnchorRow(label: String, isSelected: Boolean, onClick: () -> Unit) {
    val calm = LocalCalmColors.current
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = if (isSelected) calm.accent.copy(alpha = if (calm.isDark) 0.22f else 0.14f) else calm.card,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (isSelected) calm.accent else calm.track),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.block, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = calm.ink,
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .size(if (isSelected) 12.dp else 10.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) calm.accent else calm.track),
            )
        }
    }
}
