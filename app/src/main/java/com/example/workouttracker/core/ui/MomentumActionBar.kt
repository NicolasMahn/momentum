package com.example.workouttracker.core.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** A sentence the bar can say, and the tag a test finds it by. */
internal class ActionBarLine(val text: String, val testTag: String)

/**
 * The bar pinned under a page's scrolling content: its actions in reach on every part, risen above
 * the keyboard, on a lifted surface with a shadow over the content it sits on. It is one button
 * high, and its actions share its whole width; [leading] holds a rare way out such as Delete.
 *
 * It says nothing until there is something to say, and then says it over the content, so it
 * never takes room for a sentence. An [error] stands there while it lasts. What the page still
 * waits for ([waitingFor]) is said only when someone presses an action that is not ready yet
 * ([MomentumBarAction]), and goes again after a moment or once the page is ready. The button
 * itself already looks not ready; the sentence answers the press with why.
 *
 * The surface, shadow and sentence are drawn past the bar's bounds, up over the content and down
 * under the navigation bar, so none of them ever moves anything.
 */
@Composable
internal fun MomentumActionBar(
    modifier: Modifier = Modifier,
    error: ActionBarLine? = null,
    waitingFor: ActionBarLine? = null,
    leading: @Composable RowScope.() -> Unit = {},
    actions: @Composable RowScope.() -> Unit,
) {
    var presses by remember { mutableIntStateOf(0) }
    var explaining by remember { mutableStateOf(false) }
    LaunchedEffect(presses) {
        if (presses == 0) return@LaunchedEffect
        explaining = true
        delay(MomentumActionBarDefaults.ExplainMillis)
        explaining = false
    }
    val said = error ?: waitingFor?.takeIf { explaining }
    val surface = MaterialTheme.colorScheme.surfaceContainer
    val edge = MaterialTheme.colorScheme.outlineVariant
    val shadow = MaterialTheme.colorScheme.scrim
    val below = WindowInsets.navigationBars.getBottom(LocalDensity.current).toFloat()
    CompositionLocalProvider(LocalExplainWaiting provides { presses++ }) {
        Layout(
            contents = listOf(
                { BarSaying(said, isError = error != null) },
                {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        leading()
                        actions()
                    }
                },
            ),
            modifier = modifier
                .fillMaxWidth()
                .drawBehind {
                    val fall = MomentumActionBarDefaults.ShadowHeight.toPx()
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to shadow.copy(alpha = 0f),
                            1f to shadow.copy(alpha = MomentumActionBarDefaults.ShadowAlpha),
                            startY = -fall,
                            endY = 0f,
                        ),
                        topLeft = Offset(0f, -fall),
                        size = Size(size.width, fall),
                    )
                    drawRect(color = surface, size = Size(size.width, size.height + below))
                    drawRect(color = edge, size = Size(size.width, 1.dp.toPx()))
                }
                // The page already stands above the navigation bar, so only the keyboard's rise beyond it counts.
                .windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars))
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) { (sayingPart, rowPart), constraints ->
            val row = rowPart.single().measure(constraints.copy(minHeight = 0))
            val saying = sayingPart.singleOrNull()?.measure(constraints.copy(minWidth = 0, minHeight = 0))
            layout(row.width, row.height) {
                row.placeRelative(0, 0)
                // Over the content, clear of the shadow: drawn there, it takes no room in the page.
                if (saying != null) {
                    val lift = saying.height + MomentumActionBarDefaults.ShadowHeight.roundToPx() + 8.dp.roundToPx()
                    saying.placeRelative(0, -lift)
                }
            }
        }
    }
}

/**
 * One of the bar's actions, sharing its width with the others. One that is not [ready] wears the
 * not-ready face and reads as disabled, yet takes a press: the press asks the bar to say what the
 * page still waits for instead of acting.
 */
@Composable
internal fun RowScope.MomentumBarAction(
    text: String,
    onClick: () -> Unit,
    ready: Boolean,
    testTag: String,
) {
    val explain = LocalExplainWaiting.current
    val colors = MomentumButtonDefaults.colors(MomentumButtonVariant.Primary)
    MomentumButton(
        onClick = if (ready) onClick else explain,
        modifier = Modifier.weight(1f).fillMaxHeight(),
        colors = if (ready) colors else colors.copy(
            container = colors.disabledContainer,
            content = colors.disabledContent,
            ledge = MaterialTheme.colorScheme.outlineVariant,
        ),
        testTag = testTag,
        faceModifier = Modifier.semantics { if (!ready) disabled() },
    ) {
        MomentumButtonLabel(text)
    }
}

/** The bar's sentence, faded in and out in place; while it says nothing it has no node at all. */
@Composable
private fun BarSaying(line: ActionBarLine?, isError: Boolean) {
    var last by remember { mutableStateOf(line) }
    if (line != null) last = line
    val shown by animateFloatAsState(if (line != null) 1f else 0f, label = "bar saying")
    val words = last ?: return
    if (line == null && shown == 0f) return
    Text(
        text = words.text,
        modifier = Modifier
            .graphicsLayer { alpha = shown }
            .then(if (line != null) Modifier.testTag(words.testTag).semantics { liveRegion = LiveRegionMode.Polite } else Modifier)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
    )
}

/**
 * Says what the page still waits for, as a press on an action that is not ready does. For an
 * action that did its work yet leaves the page open, such as Save with typed text not yet added:
 * without it the page would stay in silence and look broken.
 */
@Composable
internal fun rememberBarExplanation(): () -> Unit = LocalExplainWaiting.current

private val LocalExplainWaiting = staticCompositionLocalOf<() -> Unit> { {} }

internal object MomentumActionBarDefaults {
    val ShadowHeight = 14.dp
    const val ShadowAlpha = 0.32f
    const val ExplainMillis = 4_000L
}
