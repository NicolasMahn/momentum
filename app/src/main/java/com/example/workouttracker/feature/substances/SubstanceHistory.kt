package com.example.workouttracker.feature.substances

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.example.workouttracker.R
import com.example.workouttracker.core.text.UiText
import com.example.workouttracker.core.text.joinedBy
import com.example.workouttracker.core.text.resolve
import com.example.workouttracker.core.text.uiText
import com.example.workouttracker.core.ui.CountUpText
import com.example.workouttracker.core.ui.MomentumTileGrid
import com.example.workouttracker.core.ui.NoValueText
import com.example.workouttracker.core.ui.RecentDayCalendar
import com.example.workouttracker.core.ui.LocalDayCovers
import com.example.workouttracker.core.ui.blankDayStyle
import com.example.workouttracker.domain.SubstancesTrackerRef
import com.example.workouttracker.core.ui.ViewIdentity
import kotlin.math.roundToInt

@Composable
internal fun SubstanceSummary(window: SubstanceHistoryWindow) {
    // No container: these tiles are the legend for the calendar right below, and a tonal slab
    // above the calendar's own ground would read as a separate, unrelated block. Three to a row,
    // as the three always were; a fourth tracked substance starts a row below.
    MomentumTileGrid(
        items = window.statuses,
        columns = SummaryColumns,
        modifier = Modifier.testTag("substance_summary"),
    ) { status, tileModifier ->
        SubstanceStatTile(
            presentation = window.substances.presentation(status.substance),
            label = status.substance.label().resolve(),
            stat = status.stat(),
            modifier = tileModifier,
        )
    }
}

/**
 * One substance's state as a plain stat: the number and what it counts, nothing else. The disc
 * carries the same accent as that substance's calendar dots, so the tile is still the legend,
 * and the whole tile is one node for TalkBack.
 */
@Composable
private fun SubstanceStatTile(
    presentation: ViewIdentity,
    label: String,
    stat: SubstanceStat,
    modifier: Modifier = Modifier,
) {
    val description = stat.description.resolve()
    Column(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Surface(
            modifier = Modifier.size(MarkerSize),
            shape = CircleShape,
            color = presentation.colors.accent,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = presentation.icon,
                    contentDescription = null,
                    modifier = Modifier.size(MarkerIconSize),
                    tint = presentation.colors.onAccent,
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = presentation.colors.label,
            maxLines = 1,
        )
        // Each count starts at the same digit count as its target, so the number never changes
        // width while it travels.
        CountUpText(
            target = stat.count?.toDouble(),
            format = { value, _ -> value.roundToInt().toString() },
            placeholder = stat.word?.resolve() ?: NoValueText,
            restartFrom = { target -> countUpStart(target.roundToInt()).toDouble() },
            durationMillis = CountUpMillis,
            style = MaterialTheme.typography.headlineMedium,
            color = if (stat.count == null && stat.word == null) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                presentation.colors.label
            },
        )
        Text(
            text = stat.unit.resolve(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

private fun countUpStart(target: Int): Int {
    var floor = 1
    while (floor * 10 <= target) floor *= 10
    return if (floor == 1) 0 else floor
}

private const val SummaryColumns = 3
private val MarkerSize = 34.dp

/** A day that said "none": one quiet dash where the substances' dots would be, in no reward color. */
private val NoneMarkWidth = 10.dp
private val NoneMarkHeight = 3.dp
private val SubstanceDotSize = 6.dp
private val MarkerIconSize = 20.dp
private const val CountUpMillis = 420

@Composable
internal fun SubstanceHistory(
    window: SubstanceHistoryWindow,
    selectedDateKey: String,
    todayKey: String,
    onDayClick: (SubstanceHistoryDay) -> Unit,
) {
    val resources = LocalResources.current
    val covers = LocalDayCovers.current[SubstancesTrackerRef].orEmpty()
    RecentDayCalendar(
        days = window.days,
        dateKey = SubstanceHistoryDay::dateKey,
        selectedDateKey = selectedDateKey,
        todayKey = todayKey,
        onDayClick = onDayClick,
        // Every day keeps the neutral ground: a use is recorded, never celebrated, so it is a small
        // dot per substance and never a bright cell to collect.
        dayStyle = { blankDayStyle() },
        statusDescription = { day -> window.statusDescription(day).resolve(resources) },
        actionDescription = { day ->
            resources.getString(
                if (day.logs.isEmpty() && !day.none) R.string.substance_action_select else R.string.substance_action_open,
            )
        },
        testTagPrefix = "substance_history",
        cover = { day -> covers[day.dateKey] },
        decoration = { day, ink ->
            if (day.none) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .width(NoneMarkWidth)
                        .height(NoneMarkHeight)
                        .background(ink, CircleShape),
                )
            }
            val substances = window.substancesOn(day)
            if (substances.isNotEmpty()) {
                Row(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    substances.forEach { substance ->
                        Box(
                            modifier = Modifier
                                .size(SubstanceDotSize)
                                .background(window.substances.presentation(substance).colors.accent, CircleShape),
                        )
                    }
                }
            }
        },
    )
}

private fun SubstanceHistoryWindow.statusDescription(day: SubstanceHistoryDay): UiText = if (day.none) {
    uiText(R.string.substance_none_day)
} else if (day.logs.isEmpty()) {
    uiText(R.string.substance_history_none)
} else {
    val counts = day.logs.groupingBy { it.substanceId }.eachCount()
    substancesOn(day).map { substance ->
        uiText(R.string.substance_history_kind_count, substance.label(), counts.getValue(substance.id))
    }.joinedBy(", ")
}
