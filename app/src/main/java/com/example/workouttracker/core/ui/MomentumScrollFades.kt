package com.example.workouttracker.core.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The content of a scrolling area fades into the screen's background wherever more lies beyond
 * its edge: at the bottom while it can scroll down, at the top once it has. A list that scrolls
 * must say so before anyone tries.
 *
 * Put it before the scroll modifier, so it is drawn over the visible part and not the content.
 * [bleed] carries the fade sideways past the area to the screen's edges, across the padding around
 * it, so it never stops short of the sides. It is drawn, never laid out, so nothing moves when it
 * comes and goes.
 */
@Composable
internal fun Modifier.momentumScrollFades(state: ScrollState, bleed: Dp = 0.dp): Modifier {
    val background = MaterialTheme.colorScheme.background
    val top by animateFloatAsState(if (state.canScrollBackward) 1f else 0f, tween(FadeMillis), label = "scroll fade top")
    val bottom by animateFloatAsState(if (state.canScrollForward) 1f else 0f, tween(FadeMillis), label = "scroll fade bottom")
    return drawWithContent {
        drawContent()
        val depth = MomentumScrollFadeDefaults.Depth.toPx()
        val side = bleed.toPx()
        val width = size.width + 2 * side
        val clear = background.copy(alpha = 0f)
        if (top > 0f) drawRect(
            Brush.verticalGradient(0f to background, 1f to clear, startY = 0f, endY = depth),
            Offset(-side, 0f),
            Size(width, depth),
            alpha = top,
        )
        if (bottom > 0f) drawRect(
            Brush.verticalGradient(0f to clear, 1f to background, startY = size.height - depth, endY = size.height),
            Offset(-side, size.height - depth),
            Size(width, depth),
            alpha = bottom,
        )
    }
}

internal object MomentumScrollFadeDefaults {
    val Depth = 48.dp
}

private const val FadeMillis = 150
