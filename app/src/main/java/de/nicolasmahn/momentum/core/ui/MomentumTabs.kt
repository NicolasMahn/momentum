package de.nicolasmahn.momentum.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import de.nicolasmahn.momentum.core.locale.currentLocale

private val TabMinHeight = 48.dp
private val LabelPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)
private val DividerThickness = 1.dp
private val IndicatorThickness = 3.dp
private val IndicatorInset = 12.dp
private const val LabelFadeMillis = 150

/** Material's disabled content alpha, so a step ahead fades like any control that waits. */
private const val DisabledLabelAlpha = 0.38f

/**
 * The app's one tab surface: labels over a hairline, the chosen one in the view's color and
 * underlined in its accent. Tabs switch what a page shows; they are not actions, so they have no
 * card, outline or ledge.
 *
 * Every label keeps one line. When they all fit, each tab is as wide as its label and the room
 * left is shared equally, so "Erinnerungen" beside "Streak" never breaks. When they cannot all
 * fit, as a numbered setup's steps on a narrow phone, in German or a large font,
 * each tab keeps its label's width and the row scrolls sideways, bringing the chosen tab into
 * sight; the row keeps one line's height, so the page under it never loses its room.
 *
 * Every label keeps one weight whether chosen or not, so choosing a tab can never re-wrap a label
 * and change the row's height; the underline slides in the draw phase for the same reason.
 *
 * [testTag] names each tab's touch target, which is also the element TalkBack reads.
 *
 * A tab [enabled] says no to is shown faded and cannot be chosen, which is how a setup lists the
 * steps still ahead: in sight, so the length of the way is known, but not before their turn.
 */
@Composable
internal fun <T> MomentumTabs(
    tabs: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    testTag: ((T) -> String)? = null,
    enabled: (T) -> Boolean = { true },
    motionEnabled: Boolean = platformAnimationsEnabled(),
) {
    val selectedIndex = tabs.indexOf(selected)
    require(selectedIndex >= 0) { "The selected tab $selected is not one of $tabs" }

    val viewColors = LocalViewColors.current
    val divider = MaterialTheme.colorScheme.outlineVariant
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    val faded = quiet.copy(alpha = DisabledLabelAlpha)
    // Hyphenated in the language the screen speaks, so a long German word breaks where German does.
    val labelStyle = MaterialTheme.typography.titleSmall.copy(
        hyphens = Hyphens.Auto,
        localeList = LocaleList(currentLocale().toLanguageTag()),
    )
    // The underline's position in tabs, fractional while it slides.
    val indicator = remember { Animatable(selectedIndex.toFloat()) }
    LaunchedEffect(selectedIndex, motionEnabled) {
        val target = selectedIndex.toFloat()
        if (motionEnabled) indicator.animateTo(target, IndicatorSpring) else indicator.snapTo(target)
    }
    // Written by the layout and read by the underline, which is drawn after it in the same frame.
    val bounds = remember { TabBounds() }
    val scroll = rememberScrollState()

    val measurer = rememberTextMeasurer()
    val labels = tabs.map { label(it) }
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val viewport = constraints.maxWidth
        // Only a row whose labels cannot all fit scrolls; one that fits stays a plain row.
        // Each edge rounds on its own, as the padding modifier rounds it.
        val padding = with(LocalDensity.current) {
            LabelPadding.calculateLeftPadding(LayoutDirection.Ltr).roundToPx() + LabelPadding.calculateRightPadding(LayoutDirection.Ltr).roundToPx()
        }
        val fits = labels.sumOf { measurer.measure(it, labelStyle, maxLines = 1, softWrap = false).size.width + padding } <= viewport
        // A scrolling row centers the chosen tab, again whenever its labels change or it starts to
        // scroll again, so an offset left from other widths never hides the tab.
        LaunchedEffect(selectedIndex, viewport, scroll.maxValue, fits, labels) {
            if (fits) return@LaunchedEffect
            val (start, width) = bounds.at(selectedIndex.toFloat())
            val target = (start + width / 2 - viewport / 2f).toInt().coerceIn(0, scroll.maxValue)
            if (motionEnabled) scroll.animateScrollTo(target) else scroll.scrollTo(target)
        }
        Layout(
            modifier = Modifier
                .then(if (fits) Modifier else Modifier.horizontalScroll(scroll))
                .selectableGroup()
                .drawWithContent {
                    drawContent()
                    val hairline = DividerThickness.toPx()
                    drawRect(
                        color = divider,
                        topLeft = Offset(0f, size.height - hairline),
                        size = Size(size.width, hairline),
                    )
                    val (left, tabWidth) = bounds.at(indicator.value)
                    val inset = IndicatorInset.toPx().coerceAtMost(tabWidth / 4)
                    val thickness = IndicatorThickness.toPx()
                    val start = left + inset
                    val width = tabWidth - 2 * inset
                    drawRoundRect(
                        color = viewColors.accent,
                        topLeft = Offset(
                            x = if (layoutDirection == LayoutDirection.Ltr) start else size.width - start - width,
                            y = size.height - thickness,
                        ),
                        size = Size(width, thickness),
                        cornerRadius = CornerRadius(thickness / 2),
                    )
                },
            content = {
                tabs.forEachIndexed { index, tab ->
                    val isSelected = index == selectedIndex
                    val isEnabled = enabled(tab)
                    val labelColor by animateColorAsState(
                        targetValue = when {
                            isSelected -> viewColors.label
                            isEnabled -> quiet
                            else -> faded
                        },
                        animationSpec = if (motionEnabled) tween(LabelFadeMillis) else snap(),
                        label = "tab label color",
                    )
                    Box(
                        modifier = Modifier
                            .heightIn(min = TabMinHeight)
                            .selectable(selected = isSelected, enabled = isEnabled, role = Role.Tab, onClick = { onSelect(tab) })
                            .then(if (testTag != null) Modifier.testTag(testTag(tab)) else Modifier)
                            .padding(LabelPadding),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = labels[index],
                            color = labelColor,
                            style = labelStyle,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            },
        ) { measurables, _ ->
            val count = measurables.size
            val natural = measurables.map { it.maxIntrinsicWidth(Constraints.Infinity) }
            // Whether the labels fit is decided once, above, so the widths and the scroll always agree.
            val widths = if (fits) {
                val extra = (viewport - natural.sum()) / count
                natural.mapIndexed { index, width -> width + extra + if (index == count - 1) (viewport - natural.sum()) % count else 0 }
            } else {
                natural
            }
            val total = widths.sum()
            val height = measurables.mapIndexed { index, tab -> tab.minIntrinsicHeight(widths[index]) }.max()
                .coerceAtLeast(TabMinHeight.roundToPx())
            val placeables = measurables.mapIndexed { index, tab -> tab.measure(Constraints.fixed(widths[index], height)) }
            bounds.widths = widths
            layout(total, height) {
                var x = 0
                placeables.forEach { tab ->
                    tab.placeRelative(x, 0)
                    x += tab.width
                }
            }
        }
    }
}

/** Where each tab sits, from the start edge, so the underline can follow tabs of unequal width. */
private class TabBounds {
    var widths: List<Int> = emptyList()

    /** The start and width at [position], a tab index that is fractional while the underline slides. */
    fun at(position: Float): Pair<Float, Float> {
        if (widths.isEmpty()) return 0f to 0f
        val starts = widths.runningFold(0) { start, width -> start + width }
        val from = position.toInt().coerceIn(0, maxOf(0, widths.size - 2))
        val to = minOf(from + 1, widths.size - 1)
        val fraction = position - from
        return lerp(starts[from].toFloat(), starts[to].toFloat(), fraction) to
            lerp(widths[from].toFloat(), widths[to].toFloat(), fraction)
    }
}

private val IndicatorSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessMediumLow,
)
