package com.example.workouttracker.feature.checklist

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.dp
import com.example.workouttracker.R
import com.example.workouttracker.core.ui.DayCover
import com.example.workouttracker.core.ui.RecentDayCalendar
import com.example.workouttracker.core.ui.blankDayStyle
import com.example.workouttracker.core.ui.hasSeal
import com.example.workouttracker.core.ui.RecentDayCalendarStyle
import com.example.workouttracker.domain.checklist.Checkpoint
import com.example.workouttracker.domain.checklist.ChecklistItem
import com.example.workouttracker.domain.checklist.tickOf
import com.example.workouttracker.core.ui.ViewIdentity

@Composable
internal fun ChecklistHistory(
    days: List<ChecklistHistoryDay>,
    identity: ViewIdentity,
    selectedDateKey: String?,
    todayKey: String,
    testTagPrefix: String,
    onDayClick: (ChecklistHistoryDay) -> Unit,
    covers: Map<String, DayCover> = emptyMap(),
) {
    val presentation = identity
    val resources = LocalResources.current
    RecentDayCalendar(
        days = days,
        dateKey = ChecklistHistoryDay::dateKey,
        selectedDateKey = selectedDateKey,
        todayKey = todayKey,
        onDayClick = onDayClick,
        dayStyle = { day ->
            // A covered day was never complete, so it gives its partial ground to the token's seal;
            // the bar keeps saying what was done.
            if (covers[day.dateKey]?.hasSeal == true) {
                blankDayStyle()
            } else {
                when (day.state) {
                    ChecklistDayState.EMPTY -> blankDayStyle()
                    ChecklistDayState.PARTIAL -> RecentDayCalendarStyle(presentation.colors.container, presentation.colors.onContainer)
                    ChecklistDayState.COMPLETE -> RecentDayCalendarStyle(presentation.colors.accent, presentation.colors.onAccent)
                }
            }
        },
        statusDescription = { day ->
            // Facts first: a tick or a miss is said as recorded, whatever the rule; an item that
            // logs only misses with neither is said to be assumed done, never done.
            fun spoken(checkpoint: Checkpoint, items: List<ChecklistItem>) = items.joinToString { item ->
                val tick = tickOf(checkpoint.id, item.id)
                val status = when {
                    tick in day.record.ticks -> R.string.checklist_day_done
                    tick in day.record.misses -> R.string.checklist_day_missed
                    tick in day.exceptionsOnly -> R.string.checklist_day_assumed
                    else -> R.string.checklist_day_not_done
                }
                resources.getString(status, item.label)
            }
            // With several checkpoints an item can appear twice, so each part names its checkpoint.
            // Something recorded on a day it was not due, or for an item since removed, is said too.
            val several = day.listed.size > 1
            day.listed.joinToString(if (several) "; " else ", ") { (checkpoint, items) ->
                val label = checkpoint.label?.takeIf { several }
                if (label == null) spoken(checkpoint, items) else resources.getString(R.string.checklist_day_checkpoint, label, spoken(checkpoint, items))
            }
        },
        // Tapping a day only selects it; the form above is where it is edited or removed.
        actionDescription = { resources.getString(R.string.checklist_day_select) },
        testTagPrefix = "${testTagPrefix}_history",
        cover = { day -> covers[day.dateKey] },
        decoration = { day, ink ->
            // A week item's mark stands on its own, so a day with only one ticked shows it too, as
            // does a day kept only by items that log only misses. A complete day needs no bar.
            if (day.showsMarks && day.state != ChecklistDayState.COMPLETE) {
                ChecklistDayBar(day.marks.values, ink, Modifier.align(Alignment.BottomCenter))
            }
        },
    )
}

/**
 * How much of the day was done, as one bar that reads the same for three items or twenty: ticked
 * items fill it in [ink], assumed ones follow in a quiet fill, so an assumption never reads as a
 * tick, and the rest is the empty outline.
 */
@Composable
private fun ChecklistDayBar(marks: Collection<CalendarMark>, ink: Color, modifier: Modifier = Modifier) {
    val total = marks.size.coerceAtLeast(1).toFloat()
    val ticked = marks.count { it == CalendarMark.Ticked } / total
    val assumed = marks.count { it == CalendarMark.Assumed } / total
    Canvas(
        modifier
            .padding(bottom = BarLift)
            .fillMaxWidth(BarWidthShare)
            .height(BarHeight)
            .clip(CircleShape),
    ) {
        drawRect(ink, size = Size(size.width * ticked, size.height))
        drawRect(ink.copy(alpha = AssumedAlpha), topLeft = Offset(size.width * ticked, 0f), size = Size(size.width * assumed, size.height))
        val outline = BarOutline.toPx()
        drawRoundRect(
            ink,
            topLeft = Offset(outline / 2f, outline / 2f),
            size = Size(size.width - outline, size.height - outline),
            cornerRadius = CornerRadius(size.height / 2f),
            style = Stroke(outline),
        )
    }
}

/** Narrow enough to sit inside a seal's hexagon, wide enough that a fifth of the day still shows. */
private const val BarWidthShare = 0.6f
private val BarHeight = 4.dp
private val BarLift = 3.dp
private val BarOutline = 1.dp
private const val AssumedAlpha = 0.4f
