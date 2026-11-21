package com.example.workouttracker.feature.mo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.workouttracker.R
import com.example.workouttracker.core.ui.MomentumCard
import com.example.workouttracker.core.ui.MomentumCardColors
import com.example.workouttracker.core.ui.theme.MoFreezeChip
import com.example.workouttracker.core.ui.theme.MoFreezeChipLedge
import com.example.workouttracker.core.ui.theme.MoFreezeChipText
import com.example.workouttracker.core.ui.theme.MoStreakChip
import com.example.workouttracker.core.ui.theme.MoStreakChipLedge
import com.example.workouttracker.core.ui.theme.MoStreakChipText
import com.example.workouttracker.domain.mo.MoStreak

/**
 * The streak and the freezes, always on top of Home like a score. Both chips open the explanation
 * of how the streak works, which is why they carry a ledge: they can be pressed.
 */
@Composable
internal fun MoStreakBar(
    streak: MoStreak,
    onExplain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val freezes = pluralStringResource(R.plurals.mo_freezes_of, streak.freezeCapacity, streak.freezes, streak.freezeCapacity)
        ScoreChip(
            value = streak.days.toString(),
            description = pluralStringResource(R.plurals.mo_streak_days, streak.days, streak.days),
            colors = MomentumCardColors(MoStreakChip, MoStreakChipText, MoStreakChipLedge, MoStreakChipLedge),
            testTag = "home_streak",
            onClick = onExplain,
            icon = { drawStreakCoin() },
        )
        // The freezes as the objects they are: a cube in each slot the bank has filled and a faded
        // one in each it has not, so an empty bank reads as room to fill, not as nothing.
        MomentumCard(
            onClick = onExplain,
            modifier = Modifier.semantics(mergeDescendants = true) {
                contentDescription = freezes
            },
            colors = MomentumCardColors(MoFreezeChip, MoFreezeChipText, MoFreezeChipLedge, MoFreezeChipLedge),
            testTag = "home_freezes",
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                repeat(streak.freezeCapacity) { slot ->
                    FreezeToken(Modifier.size(26.dp), empty = slot >= streak.freezes)
                }
            }
        }
    }
}

@Composable
private fun ScoreChip(
    value: String,
    description: String,
    colors: MomentumCardColors,
    testTag: String,
    onClick: () -> Unit,
    icon: DrawScope.() -> Unit,
) {
    MomentumCard(
        onClick = onClick,
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = description },
        colors = colors,
        testTag = testTag,
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Canvas(Modifier.size(28.dp)) { icon() }
            Spacer(Modifier.width(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = colors.content,
            )
        }
    }
}
