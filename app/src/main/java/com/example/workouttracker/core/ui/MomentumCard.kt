package com.example.workouttracker.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role

@Immutable
internal data class MomentumCardColors(
    val container: Color,
    val content: Color,
    val border: Color,
    val ledge: Color,
)

internal object MomentumCardDefaults {
    /** Cards share the buttons' radius family so every surface in the app rounds the same way. */
    val CornerRadius = MomentumButtonDefaults.CornerRadius
    val LedgeHeight = MomentumButtonDefaults.LedgeHeight
    val BorderWidth = MomentumButtonDefaults.BorderWidth

    /**
     * Inside a [ViewTheme] the scheme already carries the view's identity, so a card picks up the
     * feature's surfaces without any feature-specific code here.
     */
    @Composable
    fun colors(): MomentumCardColors {
        val scheme = MaterialTheme.colorScheme
        return MomentumCardColors(
            container = scheme.surfaceContainerLowest,
            content = scheme.onSurface,
            border = scheme.outlineVariant,
            // The ledge stays neutral: depth is structure, not a signal about the card's content.
            ledge = scheme.outlineVariant,
        )
    }

    /**
     * A chosen card: outline and ledge both take the accent, because the ledge is part of the
     * card's edge and a selection that stops at the face looks unfinished. [accent] defaults to
     * the view identity; pass an item's own color when the choice has one.
     */
    @Composable
    fun selectedColors(accent: Color = MaterialTheme.colorScheme.primary): MomentumCardColors {
        val scheme = MaterialTheme.colorScheme
        return MomentumCardColors(
            container = accent.copy(alpha = SelectedTintAlpha).compositeOver(scheme.surfaceContainerLowest),
            content = scheme.onSurface,
            border = accent,
            ledge = accent,
        )
    }

    private const val SelectedTintAlpha = 0.16f
}

/**
 * A quiet, flat group for content that would be ambiguous without a boundary, such as a chart
 * or a calendar grid. It has no outline and no ledge: in Momentum a ledge means "you can press
 * this", so static content never gets one. Prefer no container at all when spacing can group.
 */
@Composable
internal fun MomentumCard(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(MomentumCardDefaults.CornerRadius),
        color = color,
        contentColor = contentColor,
        content = { Column(content = content) },
    )
}

/**
 * A card you can press. It sinks toward its ledge exactly like [MomentumButton], and [testTag]
 * lands on the pressable node so tests address the same element the user touches.
 *
 * Pass [selected] to make it a choice: it then reports selection to accessibility, wears
 * [selectedColors], and pops once when it becomes chosen. Leave it null for plain actions.
 */
@Composable
internal fun MomentumCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean? = null,
    colors: MomentumCardColors = MomentumCardDefaults.colors(),
    selectedColors: MomentumCardColors = MomentumCardDefaults.selectedColors(),
    enabled: Boolean = true,
    role: Role = Role.Button,
    motionEnabled: Boolean = platformAnimationsEnabled(),
    testTag: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val press = rememberMomentumPressState(
        interactionSource = interactionSource,
        enabled = enabled,
        motionEnabled = motionEnabled,
    )
    val pop = remember { Animatable(1f) }
    // Only a change made here pops; a card that opens already chosen stays still.
    var wasSelected by remember { mutableStateOf(selected) }
    LaunchedEffect(selected) {
        val becameSelected = selected == true && wasSelected == false
        wasSelected = selected
        if (becameSelected && motionEnabled) {
            pop.snapTo(SelectPopStartScale)
            pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 600f))
        }
    }
    val activeColors = if (selected == true) selectedColors else colors
    val pressable = if (selected == null) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            enabled = enabled,
            role = role,
            onClick = onClick,
        )
    } else {
        Modifier.selectable(
            selected = selected,
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            enabled = enabled,
            role = role,
            onClick = onClick,
        )
    }

    CardFootprint(modifier = modifier, colors = activeColors, raised = enabled) { faceModifier ->
        CardFace(
            modifier = faceModifier
                .momentumPress(press)
                .graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                }
                .clip(RoundedCornerShape(MomentumCardDefaults.CornerRadius))
                .then(pressable)
                .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
            colors = activeColors,
            content = content,
        )
    }
}

private const val SelectPopStartScale = 0.94f

/**
 * Reserves room for the ledge and draws it under the face. The face is handed the modifier that
 * fills that reserved box so both forms lay out identically.
 */
@Composable
private fun CardFootprint(
    modifier: Modifier,
    colors: MomentumCardColors,
    raised: Boolean,
    face: @Composable (Modifier) -> Unit,
) {
    Box(
        modifier = modifier.padding(bottom = MomentumCardDefaults.LedgeHeight),
        propagateMinConstraints = true,
    ) {
        if (raised) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .offset(y = MomentumCardDefaults.LedgeHeight)
                    .clip(RoundedCornerShape(MomentumCardDefaults.CornerRadius))
                    .background(colors.ledge),
            )
        }
        face(Modifier)
    }
}

@Composable
private fun CardFace(
    modifier: Modifier,
    colors: MomentumCardColors,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(MomentumCardDefaults.CornerRadius),
        color = colors.container,
        contentColor = colors.content,
        border = BorderStroke(MomentumCardDefaults.BorderWidth, colors.border),
        content = { Column(content = content) },
    )
}
