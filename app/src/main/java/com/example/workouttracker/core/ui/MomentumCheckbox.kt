package com.example.workouttracker.core.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.drawBehind
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Immutable
internal data class MomentumCheckboxColors(
    val checkedContainer: Color,
    val checkedBorder: Color,
    val checkedLedge: Color,
    val checkmark: Color,
    val uncheckedContainer: Color,
    val uncheckedBorder: Color,
    val uncheckedLedge: Color,
    val disabledContainer: Color,
    val disabledBorder: Color,
    val disabledContent: Color,
    val burst: Color,
    /** An assumed check: the mark in the view's color on an empty face, quieter than a recorded one. */
    val assumedBorder: Color,
    val assumedCheckmark: Color,
    /** A cross on the empty face: neutral, because a miss is a recorded fact and not a fault. */
    val cross: Color,
)

/**
 * What a box shows. A caller names one of the four, so no box is ever asked for two at once: a
 * cross is never checked and never assumed.
 */
internal enum class MomentumCheckboxLook {
    Empty,

    /** A check that was recorded: the mark on a face filled with the view's color. */
    Recorded,

    /** A check kept without a record, as an item that logs only misses is until it is marked missed. */
    Assumed,

    /** A cross: what the box asks about did not happen, and that is what was recorded. */
    Crossed,
    ;

    /** What a toggle and TalkBack read: a cross is as unchecked as the empty box. */
    val isChecked: Boolean get() = this == Recorded || this == Assumed
}

/** The look of a box that only ticks: a recorded check, or none. */
internal fun MomentumCheckboxLook(checked: Boolean): MomentumCheckboxLook =
    if (checked) MomentumCheckboxLook.Recorded else MomentumCheckboxLook.Empty

internal object MomentumCheckboxDefaults {
    val BoxSize = 28.dp

    /** Smaller than a button face, so it rounds less to keep the same softness. */
    val CornerRadius = 9.dp
    val LedgeHeight = 3.dp
    val BorderWidth = MomentumButtonDefaults.BorderWidth
    val PressFeedback = MomentumPressFeedback(scale = 0.92f, offset = 2.dp)

    /**
     * Inside a [ViewTheme] `primary` is the view identity, so a checkbox takes the feature's color
     * without any feature-specific code here.
     */
    @Composable
    fun colors(): MomentumCheckboxColors {
        val scheme = MaterialTheme.colorScheme
        val accent = scheme.primary
        return MomentumCheckboxColors(
            checkedContainer = accent,
            checkedBorder = accent,
            checkedLedge = LocalViewColors.current.ledge,
            checkmark = scheme.onPrimary,
            uncheckedContainer = scheme.surfaceContainerLowest,
            uncheckedBorder = scheme.outlineVariant,
            uncheckedLedge = scheme.outlineVariant,
            disabledContainer = scheme.surfaceContainerHighest,
            disabledBorder = scheme.outlineVariant,
            disabledContent = scheme.onSurfaceVariant,
            burst = accent,
            assumedBorder = scheme.outlineVariant,
            assumedCheckmark = accent,
            // The box's own border is too faint to be read as a mark; `outline` keeps 3:1 against
            // the empty face, which PaletteContrastTest holds it to.
            cross = scheme.outline,
        )
    }
}

/**
 * The single checkbox surface of the app. It is a small pressable face on a ledge, like every other
 * Momentum surface, and its mark draws itself instead of appearing.
 *
 * [look] is one of four. A check that is assumed rather than recorded, as an item that logs only
 * misses is kept until it is marked missed, writes its mark in the view's color on an empty face:
 * quieter than a recorded tick, and without the pop and burst a recorded one earns. A cross is
 * written on the empty face in a neutral color, and nothing celebrates it either.
 *
 * Pass `onCheckedChange = null` when a `toggleable` row already owns the toggle, so the whole row
 * stays the touch target and accessibility reads one element; hand that row's [interactionSource]
 * over too and the box still sinks while the row is held.
 */
@Composable
internal fun MomentumCheckbox(
    look: MomentumCheckboxLook,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: MomentumCheckboxColors = MomentumCheckboxDefaults.colors(),
    interactionSource: MutableInteractionSource? = null,
    motionEnabled: Boolean = platformAnimationsEnabled(),
    testTag: String? = null,
) {
    val checked = look.isChecked
    val ownInteractionSource = remember { MutableInteractionSource() }
    val source = interactionSource ?: ownInteractionSource
    val press = rememberMomentumPressState(
        interactionSource = source,
        enabled = enabled,
        feedback = MomentumCheckboxDefaults.PressFeedback,
        motionEnabled = motionEnabled,
    )
    val haptics = LocalHapticFeedback.current

    // One signed progress drives both marks, the fill and the stroke: 1 is a whole check, -1 a
    // whole cross, and the way from one to the other leads through the empty box. So the two are
    // never drawn together, and a mark can never run ahead of its color.
    val draw = remember { Animatable(look.drawn) }
    val pop = remember { Animatable(1f) }
    val burst = remember { Animatable(1f) }
    var settled by remember { mutableStateOf(look) }
    // A check keeps its kind while it is erased, so an assumed one that gives way to a cross
    // never flashes as a recorded one on its way out.
    var assumedCheck by remember { mutableStateOf(look == MomentumCheckboxLook.Assumed) }
    if (checked) assumedCheck = look == MomentumCheckboxLook.Assumed

    LaunchedEffect(look, motionEnabled) {
        val from = settled
        settled = look
        if (look == from || !motionEnabled) {
            draw.snapTo(look.drawn)
            burst.snapTo(1f)
            return@LaunchedEffect
        }
        if (look == MomentumCheckboxLook.Recorded && !from.isChecked) {
            // The celebration is one gesture that starts with the press, so a cross gives way at once.
            if (draw.value < 0f) draw.snapTo(0f)
            haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
            coroutineScope {
                launch { draw.animateTo(1f, tween(CheckMillis, easing = LinearOutSlowInEasing)) }
                launch {
                    pop.snapTo(PopStartScale)
                    pop.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 900f))
                }
                launch {
                    burst.snapTo(0f)
                    burst.animateTo(1f, tween(BurstMillis, easing = LinearEasing))
                }
            }
            return@LaunchedEffect
        }
        // Everything else is a correction, not an achievement: an undone tick, a miss marked or
        // taken back. What is left of the other mark is erased quickly, the new one is written,
        // and nothing is said.
        burst.snapTo(1f)
        val target = look.drawn
        if (target == 0f || draw.value * target < 0f) draw.animateTo(0f, tween(UncheckMillis, easing = LinearEasing))
        if (target != 0f) draw.animateTo(target, tween(CheckMillis, easing = LinearOutSlowInEasing))
    }

    val shape = RoundedCornerShape(MomentumCheckboxDefaults.CornerRadius)
    // Read while drawing, so the 160 ms of a check repaint the box without recomposing its row.
    // The face fills first and the mark is written into it, the way a pen follows the paper. A
    // cross leaves the face empty.
    fun fill(): Float = (draw.value * FillLead).coerceIn(0f, 1f)
    // A recorded check fills the face with the view's color; an assumed one leaves the face empty
    // and writes the mark in that color instead.
    val checkedFace = if (assumedCheck) colors.uncheckedContainer else colors.checkedContainer
    val checkedOutline = if (assumedCheck) colors.assumedBorder else colors.checkedBorder
    val checkedLedge = if (assumedCheck) colors.uncheckedLedge else colors.checkedLedge
    val checkmark = when {
        !enabled -> colors.disabledContent
        assumedCheck -> colors.assumedCheckmark
        else -> colors.checkmark
    }
    val cross = if (enabled) colors.cross else colors.disabledContent

    val toggle = if (onCheckedChange != null) {
        Modifier.toggleable(
            value = checked,
            interactionSource = source,
            // The sink and the drawing mark are the feedback; a ripple on top only muddies them.
            indication = null,
            enabled = enabled,
            role = Role.Checkbox,
            onValueChange = onCheckedChange,
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .then(toggle)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Box(modifier = Modifier.padding(bottom = MomentumCheckboxDefaults.LedgeHeight)) {
            // A disabled box sits flat: a ledge promises a press that cannot happen.
            if (enabled) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .offset(y = MomentumCheckboxDefaults.LedgeHeight)
                        .clip(shape)
                        .drawBehind {
                            drawRect(lerp(colors.uncheckedLedge, checkedLedge, fill()))
                        },
                )
            }
            Box(
                modifier = Modifier
                    .size(MomentumCheckboxDefaults.BoxSize)
                    .momentumPress(press)
                    .graphicsLayer {
                        scaleX = pop.value
                        scaleY = pop.value
                    }
                    .clip(shape)
                    .drawBehind {
                        val fill = fill()
                        drawFace(
                            face = if (enabled) {
                                lerp(colors.uncheckedContainer, checkedFace, fill)
                            } else {
                                colors.disabledContainer
                            },
                            outline = if (enabled) {
                                lerp(colors.uncheckedBorder, checkedOutline, fill)
                            } else {
                                colors.disabledBorder
                            },
                        )
                    },
            ) {
                val path = remember { Path() }
                val segment = remember { Path() }
                val measure = remember { PathMeasure() }
                Canvas(Modifier.fillMaxSize()) {
                    val progress = draw.value
                    if (progress > 0f) {
                        drawCheckmark(progress, checkmark, path, measure, segment)
                    } else if (progress < 0f) {
                        drawCross(-progress, cross)
                    }
                }
            }
        }
        if (motionEnabled) {
            CheckBurst(
                progress = { burst.value },
                color = colors.burst,
                // The burst fills the parent and draws past its edges. Giving it its own larger
                // size would grow the row for the length of the animation and shift the screen.
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

/** The box as most callers need it: a recorded check, or none. */
@Composable
internal fun MomentumCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: MomentumCheckboxColors = MomentumCheckboxDefaults.colors(),
    interactionSource: MutableInteractionSource? = null,
    motionEnabled: Boolean = platformAnimationsEnabled(),
    testTag: String? = null,
) {
    MomentumCheckbox(
        look = MomentumCheckboxLook(checked),
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource,
        motionEnabled = motionEnabled,
        testTag = testTag,
    )
}

/** Where a box's progress rests for a look: 1 is a whole check, -1 a whole cross, 0 neither. */
private val MomentumCheckboxLook.drawn: Float
    get() = when (this) {
        MomentumCheckboxLook.Empty -> 0f
        MomentumCheckboxLook.Recorded, MomentumCheckboxLook.Assumed -> 1f
        MomentumCheckboxLook.Crossed -> -1f
    }

private const val CheckMillis = 160
private const val UncheckMillis = 90
private const val BurstMillis = 240
private const val PopStartScale = 0.84f
private const val FillLead = 2f
private const val BurstReach = 2f
private const val BurstRayCount = 6

/** The check and the cross are written with the same pen. */
private val MarkStrokeWidth = 2.6.dp

/** How far the cross keeps from the box's edges, as a share of its side: as tall as the check. */
private const val CrossInset = 0.31f

/**
 * The mark is trimmed from a two-segment path rather than faded in, so it reads as being written.
 * The scratch [path], [measure] and [segment] are hoisted because this runs on every frame.
 */
private fun DrawScope.drawCheckmark(
    progress: Float,
    color: Color,
    path: Path,
    measure: PathMeasure,
    segment: Path,
) {
    if (progress <= 0f) return
    path.setCheckMark(topLeft = Offset.Zero, size = size)
    measure.setPath(path, false)
    segment.rewind()
    measure.getSegment(0f, measure.length * progress, segment, true)
    drawPath(
        path = segment,
        color = color,
        style = Stroke(
            width = MarkStrokeWidth.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        ),
    )
}

/** The cross is written like the check: one stroke, then the other across it. */
private fun DrawScope.drawCross(progress: Float, color: Color) {
    val near = size.minDimension * CrossInset
    val far = size.minDimension - near
    val width = MarkStrokeWidth.toPx()
    fun stroke(from: Offset, to: Offset, written: Float) {
        if (written > 0f) drawLine(color, from, lerp(from, to, written.coerceAtMost(1f)), strokeWidth = width, cap = StrokeCap.Round)
    }
    stroke(Offset(near, near), Offset(far, far), progress * 2f)
    stroke(Offset(far, near), Offset(near, far), progress * 2f - 1f)
}

/**
 * The face and its outline in one draw call. The stroke sits fully inside the clipped shape, the
 * way `Modifier.border` lays it, so the box looks the same while its colors animate in the draw
 * phase.
 */
private fun DrawScope.drawFace(face: Color, outline: Color) {
    val stroke = MomentumCheckboxDefaults.BorderWidth.toPx()
    val radius = MomentumCheckboxDefaults.CornerRadius.toPx()
    drawRect(face)
    drawRoundRect(
        color = outline,
        topLeft = Offset(stroke / 2f, stroke / 2f),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius((radius - stroke / 2f).coerceAtLeast(0f)),
        style = Stroke(width = stroke),
    )
}

/**
 * A few short rays that leave as fast as they arrive: this can happen ten times in a row. It stays
 * composed and draws nothing at rest, so a check never adds or removes a node.
 */
@Composable
private fun CheckBurst(progress: () -> Float, color: Color, modifier: Modifier) {
    val colors = remember(color) { listOf(color) }
    Canvas(modifier.clearAndSetSemantics {}) {
        val current = progress()
        val reach = MomentumCheckboxDefaults.BoxSize.toPx() * BurstReach
        drawBurst(
            progress = current,
            colors = colors,
            rayCount = BurstRayCount,
            innerRadius = reach * (0.34f + current * 0.12f),
            outerRadius = reach * (0.42f + current * 0.18f),
            strokeWidth = 2.dp.toPx(),
        )
    }
}
