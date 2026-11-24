package de.nicolasmahn.momentum.core.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.unit.dp

/**
 * How far a count has come toward its goal, as one flat pill. It is static content, so it has no
 * ledge; inside a [ViewTheme] the fill is the view's accent.
 *
 * [description] is what TalkBack reads, in the same words the screen shows next to the bar. The
 * fill is drawn, never laid out, so a change animates without moving anything around it.
 */
@Composable
internal fun MomentumProgressBar(
    progress: Float,
    description: String,
    modifier: Modifier = Modifier,
    color: Color = LocalViewColors.current.accent,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    motionEnabled: Boolean = platformAnimationsEnabled(),
) {
    val target = progress.coerceIn(0f, 1f)
    val shown by animateFloatAsState(
        targetValue = target,
        animationSpec = if (motionEnabled) tween(FillMillis) else snap(),
        label = "progress",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(MomentumProgressBarDefaults.Height)
            .clearAndSetSemantics {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(target, 0f..1f)
            }
            .drawBehind {
                val radius = CornerRadius(size.height / 2)
                drawRoundRect(color = trackColor, cornerRadius = radius)
                if (shown > 0f) {
                    // Never thinner than the pill is tall, so a first minute is a dot, not a sliver.
                    val width = maxOf(size.width * shown, size.height)
                    drawRoundRect(color = color, size = Size(width, size.height), cornerRadius = radius)
                }
            },
    )
}

internal object MomentumProgressBarDefaults {
    val Height = 10.dp
}

private const val FillMillis = 450
