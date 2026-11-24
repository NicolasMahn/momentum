package de.nicolasmahn.momentum.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.nicolasmahn.momentum.R
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal enum class CompletionCelebrationIntensity {
    Restrained,
    Strong,
}

internal data class CompletionButtonColors(
    val actionContainer: Color,
    val actionContent: Color,
    val completedContainer: Color,
    val completedContent: Color,
    val disabledContainer: Color,
    val disabledContent: Color,
    val depth: Color,
    val burstPrimary: Color,
    val burstSecondary: Color,
    val burstTertiary: Color,
)

internal data class CompletionFeedbackSpec(
    val pressedScale: Float,
    val pressedOffset: Dp,
    val checkStartScale: Float,
    val burstRayCount: Int,
    val burstDurationMillis: Int,
)

internal fun completionFeedbackSpec(
    intensity: CompletionCelebrationIntensity,
): CompletionFeedbackSpec = when (intensity) {
    CompletionCelebrationIntensity.Restrained -> CompletionFeedbackSpec(
        pressedScale = 0.99f,
        pressedOffset = 2.dp,
        checkStartScale = 0.82f,
        burstRayCount = 0,
        burstDurationMillis = 180,
    )

    CompletionCelebrationIntensity.Strong -> CompletionFeedbackSpec(
        pressedScale = 0.965f,
        pressedOffset = 4.dp,
        checkStartScale = 0.45f,
        burstRayCount = 8,
        burstDurationMillis = 420,
    )
}

@Composable
internal fun CompletionButton(
    actionLabel: String,
    completedLabel: String,
    completed: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    testTag: String,
    modifier: Modifier = Modifier,
    // The enclosing view's identity; a screen only names colors when it borrows another view's.
    colors: CompletionButtonColors = LocalViewColors.current.completionButtonColors(),
    enabled: Boolean = true,
    pending: Boolean = false,
    celebrationSignal: Int = 0,
    intensity: CompletionCelebrationIntensity = CompletionCelebrationIntensity.Strong,
    motionEnabled: Boolean = platformAnimationsEnabled(),
    accessibilityLabel: String? = null,
) {
    val feedback = completionFeedbackSpec(intensity)
    val canClick = enabled && !pending && !completed
    val state = stringResource(
        when {
            pending -> R.string.core_completion_saving
            completed -> R.string.core_completion_completed
            else -> R.string.core_completion_not_completed
        },
    )
    val checkScale = remember { Animatable(1f) }
    val burstProgress = remember { Animatable(1f) }
    var burstVisible by remember { mutableStateOf(false) }
    var consumedCelebrationSignal by remember { mutableIntStateOf(celebrationSignal) }

    LaunchedEffect(celebrationSignal, completed, motionEnabled) {
        burstVisible = false
        checkScale.snapTo(1f)
        burstProgress.snapTo(1f)
        if (celebrationSignal == consumedCelebrationSignal) return@LaunchedEffect
        consumedCelebrationSignal = celebrationSignal
        if (!completed || !motionEnabled) {
            burstVisible = false
            checkScale.snapTo(1f)
            burstProgress.snapTo(1f)
            return@LaunchedEffect
        }

        checkScale.snapTo(feedback.checkStartScale)
        burstProgress.snapTo(0f)
        burstVisible = feedback.burstRayCount > 0
        coroutineScope {
            launch {
                checkScale.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = 0.48f,
                        stiffness = 520f,
                    ),
                )
            }
            launch {
                burstProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(feedback.burstDurationMillis),
                )
            }
        }
        burstVisible = false
    }

    MomentumButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = MomentumButtonColors(
            container = colors.actionContainer,
            content = colors.actionContent,
            ledge = colors.depth,
            disabledContainer = if (completed) colors.completedContainer else colors.disabledContainer,
            disabledContent = if (completed) colors.completedContent else colors.disabledContent,
        ),
        enabled = canClick,
        raised = !completed,
        pressFeedback = MomentumPressFeedback(feedback.pressedScale, feedback.pressedOffset),
        motionEnabled = motionEnabled,
        testTag = testTag,
        faceModifier = Modifier.semantics {
            role = Role.Button
            accessibilityLabel?.let { contentDescription = it }
            stateDescription = state
        },
    ) {
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (completed) Icons.Rounded.Check else icon,
                contentDescription = null,
                modifier = Modifier
                    .size(28.dp)
                    .graphicsLayer {
                        val scale = if (completed && motionEnabled) checkScale.value else 1f
                        scaleX = scale
                        scaleY = scale
                    }
                    .then(if (completed) Modifier.testTag("${testTag}_check") else Modifier),
            )
            if (burstVisible && motionEnabled) {
                CompletionBurst(
                    progress = { burstProgress.value },
                    colors = colors,
                    rayCount = feedback.burstRayCount,
                    modifier = Modifier.matchParentSize().testTag("${testTag}_celebration"),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        MomentumButtonLabel(if (completed) completedLabel else actionLabel)
    }
}

/**
 * For saves that can repeat, such as append-only logs or forms that reset: acknowledges a
 * successful save with the same check and celebration, then returns to the action so the next
 * entry can be logged. Increment [celebrationSignal] only after a save succeeds.
 */
@Composable
internal fun MomentaryCompletionButton(
    actionLabel: String,
    completedLabel: String,
    onClick: () -> Unit,
    icon: ImageVector,
    testTag: String,
    celebrationSignal: Int,
    modifier: Modifier = Modifier,
    colors: CompletionButtonColors = LocalViewColors.current.completionButtonColors(),
    enabled: Boolean = true,
    intensity: CompletionCelebrationIntensity = CompletionCelebrationIntensity.Strong,
    motionEnabled: Boolean = platformAnimationsEnabled(),
) {
    var acknowledging by remember { mutableStateOf(false) }
    var seenSignal by remember { mutableIntStateOf(celebrationSignal) }
    // Advances together with `acknowledging` so the inner button sees completion and signal in one frame.
    var acknowledgedSignal by remember { mutableIntStateOf(0) }

    LaunchedEffect(celebrationSignal) {
        if (celebrationSignal == seenSignal) return@LaunchedEffect
        seenSignal = celebrationSignal
        acknowledging = true
        acknowledgedSignal += 1
        try {
            delay(MomentaryAcknowledgeMillis)
        } finally {
            acknowledging = false
        }
    }

    CompletionButton(
        actionLabel = actionLabel,
        completedLabel = completedLabel,
        completed = acknowledging,
        onClick = onClick,
        icon = icon,
        colors = colors,
        testTag = testTag,
        modifier = modifier,
        enabled = enabled,
        celebrationSignal = acknowledgedSignal,
        intensity = intensity,
        motionEnabled = motionEnabled,
    )
}

private const val MomentaryAcknowledgeMillis = 1400L

@Composable
private fun CompletionBurst(
    progress: () -> Float,
    colors: CompletionButtonColors,
    rayCount: Int,
    modifier: Modifier,
) {
    val burstColors = remember(colors) {
        listOf(colors.burstPrimary, colors.burstSecondary, colors.burstTertiary)
    }
    Canvas(modifier.clearAndSetSemantics {}) {
        val current = progress()
        drawBurst(
            progress = current,
            colors = burstColors,
            rayCount = rayCount,
            innerRadius = size.minDimension * (0.40f + current * 0.15f),
            outerRadius = size.minDimension * (0.55f + current * 0.25f),
            strokeWidth = 3.dp.toPx(),
        )
    }
}
