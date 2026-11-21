package com.example.workouttracker.core.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/**
 * How far a face has sunk toward its ledge right now. The values stay [State] so they are read in
 * the layout and draw lambdas of [momentumPress]: a press moves pixels without recomposing the
 * surface that is being pressed.
 */
@Stable
internal class MomentumPressState(
    private val scaleState: State<Float>,
    private val offsetState: State<Dp>,
) {
    val scale: Float get() = scaleState.value
    val offset: Dp get() = offsetState.value
}

private const val PressDurationMillis = 90

/**
 * The press-and-sink motion shared by every raised Momentum surface.
 *
 * [hasLedge] is separate from [enabled] because a flat face may still compress, but only a face
 * with depth below it has somewhere to sink to.
 */
@Composable
internal fun rememberMomentumPressState(
    interactionSource: InteractionSource,
    enabled: Boolean = true,
    hasLedge: Boolean = true,
    feedback: MomentumPressFeedback = MomentumButtonDefaults.PressFeedback,
    motionEnabled: Boolean = platformAnimationsEnabled(),
): MomentumPressState {
    val isPressed by interactionSource.collectIsPressedAsState()
    val sinks = motionEnabled && isPressed && enabled
    val scale = animateFloatAsState(
        targetValue = if (sinks) feedback.scale else 1f,
        animationSpec = if (motionEnabled) tween(PressDurationMillis) else snap(),
        label = "momentum press scale",
    )
    val offset = animateDpAsState(
        targetValue = if (sinks && hasLedge) feedback.offset else 0.dp,
        animationSpec = if (motionEnabled) tween(PressDurationMillis) else snap(),
        label = "momentum press depth",
    )
    return remember(scale, offset) { MomentumPressState(scale, offset) }
}

/**
 * Sinking is drawn, not laid out: nothing around the surface may shift while it is held.
 */
internal fun Modifier.momentumPress(state: MomentumPressState): Modifier = this
    .offset { IntOffset(x = 0, y = state.offset.roundToPx()) }
    .graphicsLayer {
        scaleX = state.scale
        scaleY = state.scale
    }
