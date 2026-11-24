package de.nicolasmahn.momentum.core.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * Something together with its folded settings, such as a checklist item and when it is due. While
 * [open] it pops out of the page as a button does: the same dark face, grey outline and grey
 * ledge, so the one thing being set stands up and everything else stays flat. Only one pops out
 * at a time inside a [MomentumPopOutGroup], and Back folds it before it leaves the page; [onClose]
 * is what they ask for, and a pop-out that must stay open (while it shows an error) may ignore it.
 *
 * The face reaches past the content sideways into the page's margin and up and down into room the
 * pop-out always keeps, and it rises by a draw-phase offset, so popping out moves nothing. Pop-outs do not nest: a group of them is a section with a
 * heading, never a pop-out itself.
 */
@Composable
internal fun MomentumPopOut(
    open: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    motionEnabled: Boolean = platformAnimationsEnabled(),
    content: @Composable () -> Unit,
) {
    val group = LocalPopOutGroup.current
    val close by rememberUpdatedState(onClose)
    val self = remember { PoppedOut(close = { close() }) }
    if (group != null) {
        DisposableEffect(group, open) {
            if (open) group.take(self)
            onDispose { group.release(self) }
        }
    }
    val lift by animateFloatAsState(
        targetValue = if (open) 1f else 0f,
        animationSpec = if (motionEnabled) spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow) else snap(),
        label = "pop out",
    )
    val face = MaterialTheme.colorScheme.surfaceContainerLowest
    val grey = MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier = modifier
            // Room for the face above and below is kept open or closed, so popping out never
            // reaches over a neighbour, and closing never pulls the page up.
            .padding(
                top = MomentumPopOutDefaults.ReachUp + MomentumButtonDefaults.LedgeHeight,
                bottom = MomentumPopOutDefaults.ReachUp,
            )
            .drawBehind {
                // The ledge stays where the face rested; the face rises off it.
                if (lift > 0f) slab(grey, alpha = lift.coerceIn(0f, 1f))
            },
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer { translationY = -MomentumButtonDefaults.LedgeHeight.toPx() * lift }
                .drawBehind {
                    if (lift <= 0f) return@drawBehind
                    val alpha = lift.coerceIn(0f, 1f)
                    slab(face, alpha)
                    slab(grey, alpha, outline = MomentumButtonDefaults.BorderWidth.toPx())
                },
        ) {
            content()
        }
    }
}

/**
 * The page a set of [MomentumPopOut]s share: it keeps one popped out at a time, and lets Back fold
 * it before Back leaves the page.
 */
@Composable
internal fun MomentumPopOutGroup(content: @Composable () -> Unit) {
    val group = remember { PopOutGroup() }
    BackHandler(enabled = group.open != null) { group.open?.close?.invoke() }
    CompositionLocalProvider(LocalPopOutGroup provides group, content = content)
}

/**
 * The slab a pop-out stands on, reaching [MomentumPopOutDefaults.Reach] past its bounds sideways
 * and [MomentumPopOutDefaults.ReachUp] above and below.
 */
private fun DrawScope.slab(color: Color, alpha: Float, outline: Float? = null) {
    val reach = MomentumPopOutDefaults.Reach.toPx()
    val reachUp = MomentumPopOutDefaults.ReachUp.toPx()
    val inset = (outline ?: 0f) / 2
    drawRoundRect(
        color = color,
        topLeft = Offset(-reach + inset, -reachUp + inset),
        size = Size(size.width + 2 * reach - 2 * inset, size.height + 2 * reachUp - 2 * inset),
        cornerRadius = CornerRadius(MomentumButtonDefaults.CornerRadius.toPx()),
        alpha = alpha,
        style = if (outline != null) Stroke(outline) else Fill,
    )
}

@Stable
private class PoppedOut(val close: () -> Unit)

@Stable
private class PopOutGroup {
    var open by mutableStateOf<PoppedOut?>(null)
        private set

    fun take(popOut: PoppedOut) {
        val before = open
        open = popOut
        if (before != null && before !== popOut) before.close()
    }

    fun release(popOut: PoppedOut) {
        if (open === popOut) open = null
    }
}

private val LocalPopOutGroup = staticCompositionLocalOf<PopOutGroup?> { null }

internal object MomentumPopOutDefaults {
    /** How far the face reaches past what it holds sideways, so a field's outline never touches its edge. */
    val Reach = 12.dp

    /**
     * How far it reaches above and below, which the pop-out keeps free open or closed. Less than
     * sideways: what a pop-out holds begins with a row taller than its words, and that row's own
     * room already parts the words from the edge.
     */
    val ReachUp = 6.dp
}
