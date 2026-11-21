package com.example.workouttracker.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.workouttracker.R
import com.example.workouttracker.core.text.resolve
import com.example.workouttracker.feature.tracker.TrackerCreation

/**
 * What "Track something new" opens: one card for each kind, and choosing one opens that kind's
 * setup through [onChoose]. The page holds nothing, so it has nothing to save and leaving it asks
 * nothing; the setup behind a card is what guards what was typed.
 *
 * The cards are one height, so a kind whose line wraps on this phone, in this language or at this
 * font size does not stand out among the others.
 */
@Composable
internal fun NewViewPage(onChoose: (SettingsPage) -> Unit, creations: List<TrackerCreation>) {
    EqualHeightColumn(spacing = 12.dp) {
        creations.forEach { creation ->
            SettingsLinkRow(
                icon = creation.icon,
                title = creation.title.resolve(),
                summary = creation.description.resolve(),
                onClick = { onChoose(SettingsPage.NewTracker(creation.typeId, creation.variant)) },
                testTag = creation.testTag,
                fillsHeight = true,
            )
        }
    }
}

/**
 * A column that makes each child as tall as the tallest of them at the width it has. A child
 * decides itself where its content sits in a height larger than it needs.
 */
@Composable
internal fun EqualHeightColumn(spacing: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = measurables.maxOfOrNull { it.maxIntrinsicHeight(width) } ?: 0
        val placeables = measurables.map { it.measure(Constraints.fixed(width, height)) }
        val gap = spacing.roundToPx()
        layout(width, placeables.size * height + (placeables.size - 1).coerceAtLeast(0) * gap) {
            placeables.forEachIndexed { index, placeable -> placeable.placeRelative(0, index * (height + gap)) }
        }
    }
}
