package com.example.workouttracker.feature.logactivity.gym

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.workouttracker.core.ui.theme.MomentumReward

internal data class GymActionAccent(
    val containerColor: Color,
    val contentColor: Color,
)

/** Adding weight is a reward, so the badge takes the same gold pair as a completed action. */
internal val IncreaseWeightAccent = GymActionAccent(
    containerColor = MomentumReward.face,
    contentColor = MomentumReward.ink,
)

/** A short fact on a pill, in the colors of what it belongs to: a day's place, an exercise's progress, a reward. */
@Composable
internal fun GymInfoPill(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
