package de.nicolasmahn.momentum.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.nicolasmahn.momentum.core.ui.theme.MomentumReward

@Immutable
internal data class MomentumRewardBadgeColors(
    val face: Color,
    val ledge: Color,
    val ink: Color,
    /** Separates the coin from whatever it sits on, usually the surface behind it. */
    val ring: Color,
)

internal object MomentumRewardBadgeDefaults {
    val Size = 36.dp

    @Composable
    fun colors(ring: Color = MaterialTheme.colorScheme.background): MomentumRewardBadgeColors =
        MomentumRewardBadgeColors(
            face = MomentumReward.face,
            ledge = MomentumReward.ledge,
            ink = MomentumReward.ink,
            ring = ring,
        )
}

/**
 * A gold coin with a check on it: the app's one "you did this" mark, raised on its own darker
 * ledge like every other Momentum surface.
 *
 * It is a drawing, not a control: it carries no semantics, so whatever it decorates keeps saying
 * what it means. It draws past its bounds while it pops in, so it can be laid over a corner
 * without ever changing what it sits on. Give it a [MomentumRewardBadgeColors.ring] matching the
 * surface behind it and the coin reads as sitting on top rather than being part of it.
 */
@Composable
internal fun MomentumRewardBadge(
    modifier: Modifier = Modifier,
    size: Dp = MomentumRewardBadgeDefaults.Size,
    colors: MomentumRewardBadgeColors = MomentumRewardBadgeDefaults.colors(),
    motionEnabled: Boolean = platformAnimationsEnabled(),
) {
    // The coin arrives with the reward, so it pops once when it appears and then stays still.
    val pop = remember { Animatable(if (motionEnabled) PopStartScale else 1f) }
    LaunchedEffect(motionEnabled) {
        if (motionEnabled) {
            pop.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 520f))
        } else {
            pop.snapTo(1f)
        }
    }
    val mark = remember { Path() }

    Canvas(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
            },
    ) {
        drawRewardCoin(colors, mark)
    }
}

private const val PopStartScale = 0.45f
private val RingWidth = 3.dp
private val LedgeHeight = 3.dp

/** Every shape is derived from the coin's radius, so one [size] scales the whole badge. */
private fun DrawScope.drawRewardCoin(colors: MomentumRewardBadgeColors, mark: Path) {
    val ring = RingWidth.toPx()
    val drop = LedgeHeight.toPx()
    val radius = (size.minDimension - 2f * ring - drop) / 2f
    if (radius <= 0f) return
    val face = Offset(size.width / 2f, ring + radius)
    val ledge = Offset(face.x, face.y + drop)

    // Two rings, one per center: together they outline the whole coin-and-lip silhouette.
    drawCircle(colors.ring, radius + ring, face)
    drawCircle(colors.ring, radius + ring, ledge)
    drawCircle(colors.ledge, radius, ledge)
    drawCircle(colors.face, radius, face)

    mark.setCheckMark(
        topLeft = Offset(face.x - radius, face.y - radius),
        size = Size(radius * 2f, radius * 2f),
    )
    drawPath(
        path = mark,
        color = colors.ink,
        style = Stroke(
            width = radius * 0.22f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        ),
    )
}

/**
 * The socket the reward coin will sit in: a task that is not finished today. It is flat and hollow,
 * so it reads as a place for something rather than as something, and a view that is not a task
 * simply has no socket. [optional] dims it for a task a free day would still cover.
 *
 * A task whose day has several [parts] gets a ring of that many segments, and each part that is
 * [done] turns its segment gold. The coin itself only arrives when every part is done, so nothing
 * here needs to know what the parts are.
 *
 * Like the coin it is a drawing, not a control, and carries no semantics.
 */
@Composable
internal fun MomentumOpenTaskBadge(
    outline: Color,
    modifier: Modifier = Modifier,
    size: Dp = MomentumRewardBadgeDefaults.Size,
    ring: Color = MaterialTheme.colorScheme.background,
    optional: Boolean = false,
    done: Int = 0,
    parts: Int = 1,
) {
    val mark = remember { Path() }
    val gold = MomentumReward.face
    Canvas(modifier = modifier.size(size)) {
        val ringWidth = RingWidth.toPx()
        val radius = (this.size.minDimension - 2f * ringWidth - LedgeHeight.toPx()) / 2f
        if (radius <= 0f) return@Canvas
        // Centred where the coin's face will be, so the coin lands exactly in its socket.
        val centre = Offset(this.size.width / 2f, ringWidth + radius)
        val ink = outline.copy(alpha = if (optional) OptionalSocketAlpha else 1f)
        drawCircle(ring, radius + ringWidth, centre)

        // One drawing for every task: a day of one part is simply a ring of one segment, so a
        // one-part socket and a two-part socket are the same object.
        val trackRadius = radius - ringWidth / 2f
        val segments = parts.coerceAtLeast(1)
        val sweep = 360f / segments
        val gap = if (segments > 1) SegmentGapDegrees else 0f
        repeat(segments) { index ->
            drawArc(
                color = if (index < done) gold else ink,
                // Segments start at the top and run clockwise, with a gap between neighbours.
                startAngle = -90f + index * sweep + gap / 2f,
                sweepAngle = sweep - gap,
                useCenter = false,
                topLeft = Offset(centre.x - trackRadius, centre.y - trackRadius),
                size = Size(trackRadius * 2f, trackRadius * 2f),
                style = Stroke(width = ringWidth * SegmentWidthFactor, cap = StrokeCap.Round),
            )
        }
        mark.setCheckMark(
            topLeft = Offset(centre.x - radius, centre.y - radius),
            size = Size(radius * 2f, radius * 2f),
        )
        drawPath(
            path = mark,
            color = ink.copy(alpha = ink.alpha * SocketCheckAlpha),
            style = Stroke(width = radius * 0.2f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

private const val OptionalSocketAlpha = 0.45f
private const val SocketCheckAlpha = 0.55f
private const val SegmentGapDegrees = 26f
private const val SegmentWidthFactor = 1.25f
