package de.nicolasmahn.momentum.feature.mo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.DrawScope
import de.nicolasmahn.momentum.core.ui.platformAnimationsEnabled

/** The proportions of the box [drawMo] fills; give Mo a height and he takes the matching width. */
internal const val MoAspectRatio = 320f / 432f

/**
 * Mo, alive. His idle motion never ends, and it is only ever drawn: the clock is read in the draw
 * phase, so a frame redraws him without recomposing or measuring anything.
 */
@Composable
internal fun Mo(
    look: MoLook,
    modifier: Modifier = Modifier,
    /** How far off the floor he is right now, in pixels. Read while drawing, so a leap redraws and nothing else. */
    lift: DrawScope.() -> Float = { 0f },
    /**
     * A new value starts his time over, so that what he begins then begins at its first moment.
     * He stays the same node: a screen reader resting on him is not thrown off.
     */
    restart: Any? = null,
) {
    val ground = MaterialTheme.colorScheme.background
    val seconds = rememberMoClock(restart)
    Canvas(modifier.aspectRatio(MoAspectRatio)) {
        drawMo(look, seconds.floatValue, ground, lift())
    }
}

/** Seconds since he appeared, or since [restart] last changed. With system animations off it stays 0, which is his resting pose. */
@Composable
private fun rememberMoClock(restart: Any?): FloatState {
    val seconds = remember(restart) { mutableFloatStateOf(0f) }
    if (platformAnimationsEnabled()) {
        LaunchedEffect(seconds) {
            // The infinite variant lets a Compose test cancel it, exactly like an infinite transition;
            // a plain frame loop would keep every screen with Mo on it from ever becoming idle.
            val start = withInfiniteAnimationFrameNanos { it }
            while (true) withInfiniteAnimationFrameNanos { seconds.floatValue = (it - start) / 1_000_000_000f }
        }
    }
    return seconds
}
