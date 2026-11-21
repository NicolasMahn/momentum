package com.example.workouttracker.feature.logactivity

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.workouttracker.R
import com.example.workouttracker.core.text.resolve
import com.example.workouttracker.core.ui.LocalDayCovers
import com.example.workouttracker.core.ui.RecentDayCalendar
import com.example.workouttracker.core.ui.RecentDayCalendarStyle
import com.example.workouttracker.core.ui.blankDayStyle
import com.example.workouttracker.domain.activity.ActivityConfiguration
import com.example.workouttracker.domain.ActivityTrackerRef

/** Low enough to stay clear of the date, tall enough that a deep hue beside a bright one still reads. */
private val ActivityBandHeight = 6.dp

@Composable
internal fun ActivityHistory(
    grid: ActivityHistoryGrid,
    configuration: ActivityConfiguration,
    selectedDateKey: String?,
    todayKey: String,
    onDayClick: (ActivityHistoryDay) -> Unit,
) {
    val covers = LocalDayCovers.current[ActivityTrackerRef].orEmpty()
    // The descriptions are built outside composition, only when accessibility or a test asks.
    val resources = LocalResources.current
    val markers = remember(grid, resources) {
        grid.days
            .flatMap { it.activities }
            .distinctBy { it.key }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label.resolve(resources) })
    }

    RecentDayCalendar(
        days = grid.days,
        dateKey = ActivityHistoryDay::dateKey,
        selectedDateKey = selectedDateKey,
        todayKey = todayKey,
        onDayClick = onDayClick,
        // A day is filled with its first activity's colour, the reward a done day earns; any others
        // ride as a band along the foot, so the date stays on the ink made for its ground.
        dayStyle = { day ->
            day.activities.firstOrNull()?.let { first ->
                val look = configuration.lookOf(first.mark)
                RecentDayCalendarStyle(containerColor = look.containerColor, contentColor = look.contentColor)
            } ?: blankDayStyle()
        },
        statusDescription = { day ->
            if (day.activities.isEmpty()) {
                resources.getString(R.string.activity_calendar_none)
            } else {
                day.activities.joinToString { it.label.resolve(resources) }
            }
        },
        // A day is a selection now, never a door to a dialog, so every cell offers the same act.
        actionDescription = { resources.getString(R.string.activity_calendar_select) },
        testTagPrefix = "activity_history",
        cover = { day -> covers[day.dateKey] },
        background = { day ->
            if (day.activities.size > 1) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(ActivityBandHeight),
                ) {
                    day.activities.forEach { activity ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(configuration.lookOf(activity.mark).containerColor),
                        )
                    }
                }
            }
        },
        footer = {
            if (markers.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .testTag("activity_history_key"),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    markers.forEach { marker ->
                        val look = configuration.lookOf(marker.mark)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                modifier = Modifier.size(10.dp),
                                shape = CircleShape,
                                color = look.containerColor,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface),
                            ) {}
                            Text(
                                text = marker.label.resolve(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
    )
}
