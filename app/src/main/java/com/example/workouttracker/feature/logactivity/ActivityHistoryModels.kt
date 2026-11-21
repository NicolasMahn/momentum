package com.example.workouttracker.feature.logactivity

import com.example.workouttracker.R
import com.example.workouttracker.core.text.UiText
import com.example.workouttracker.core.text.joinedBy
import com.example.workouttracker.core.text.pluralText
import com.example.workouttracker.core.text.uiText
import com.example.workouttracker.core.time.CalendarWeekCount
import com.example.workouttracker.core.time.calendarWeeksWindow
import com.example.workouttracker.core.time.currentDateKey
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.activity.ActivityEntry
import com.example.workouttracker.domain.activity.activityRules
import com.example.workouttracker.domain.activity.routine.WorkoutSession

/**
 * What a timeline row stands for. A row is the unit of action now, so it carries the record it
 * would edit or remove instead of an id another lookup has to resolve. A workout carries the names
 * of its exercises as its activity's routine calls them now, falling back on the name each log keeps.
 */
sealed interface ActivityTimelineSource {
    data class Logged(val entry: ActivityEntry) : ActivityTimelineSource
    data class Workout(val session: WorkoutSession, val exerciseLabels: Map<String, String>) : ActivityTimelineSource
}

data class ActivityTimelineItem(
    val id: String,
    val title: UiText,
    val detail: UiText?,
    val timestampMillis: Long,
    val mark: ActivityMark,
    val source: ActivityTimelineSource,
)

data class ActivityHistoryMarker(
    val key: String,
    val mark: ActivityMark,
    val timestampMillis: Long,
    val label: UiText,
)

/** [activities] is newest first, which is also the order the calendar cell draws its dots in. */
data class ActivityHistoryDay(
    val dateKey: String,
    val activities: List<ActivityHistoryMarker>,
)

data class ActivityHistoryGrid(
    val days: List<ActivityHistoryDay>,
)

/** The day's records as rows, each named by the configuration as it is now: a renamed activity or exercise shows its new name on old days. */
fun activityTimelineForDate(
    state: AppState,
    dateKey: String,
): List<ActivityTimelineItem> = buildList {
    val rules = state.activityRules
    state.activityEntries
        .filter { it.dateKey == dateKey }
        .mapTo(this) { entry ->
            ActivityTimelineItem(
                id = "activity:${entry.id}",
                title = rules.labelOf(entry.mark, entry.name.trim()),
                detail = entry.durationMinutes?.let { uiText(R.string.activity_duration_minutes, it) },
                timestampMillis = entry.timestampMillis,
                mark = entry.mark,
                source = ActivityTimelineSource.Logged(entry),
            )
        }
    state.sessions
        .filter { it.dateKey == dateKey }
        .mapTo(this) { session ->
            ActivityTimelineItem(
                id = "workout:${session.id}",
                title = rules.labelOf(session.mark),
                detail = buildList {
                    session.durationMinutes?.let { add(uiText(R.string.activity_duration_minutes, it)) }
                    session.totalLoggedSets.takeIf { it > 0 }?.let { count ->
                        add(pluralText(R.plurals.activity_sets, count))
                    }
                }.takeIf { it.isNotEmpty() }?.joinedBy(" · "),
                timestampMillis = session.timestampMillis,
                mark = session.mark,
                source = ActivityTimelineSource.Workout(
                    session,
                    session.exerciseLogs.associate { it.exerciseId to (rules.activity(session.activityId)?.routine?.exerciseLabel(it.exerciseId, it.exerciseName) ?: it.exerciseName) },
                ),
            )
        }
}.sortedByDescending { it.timestampMillis }

internal val ActivityEntry.mark: ActivityMark get() = ActivityMark.Listed(activityId)

internal val WorkoutSession.mark: ActivityMark get() = ActivityMark.RoutineDay(activityId, day)

fun activityHistoryGrid(
    state: AppState,
    todayKey: String = currentDateKey(),
    weekCount: Int = CalendarWeekCount,
): ActivityHistoryGrid {
    val rules = state.activityRules
    val weeks = calendarWeeksWindow(todayKey, weekCount)
    val markersByDate = mutableMapOf<String, MutableList<ActivityHistoryMarker>>()

    state.sessions
        .filter { it.dateKey in weeks.visibleDateKeys }
        .forEach { session ->
            markersByDate.getOrPut(session.dateKey, ::mutableListOf) += ActivityHistoryMarker(
                key = "routine:${session.day}",
                mark = session.mark,
                timestampMillis = session.timestampMillis,
                label = rules.labelOf(session.mark),
            )
        }

    state.activityEntries
        .filter { it.dateKey in weeks.visibleDateKeys && it.name.isNotBlank() }
        .forEach { entry ->
            markersByDate.getOrPut(entry.dateKey, ::mutableListOf) += ActivityHistoryMarker(
                key = "activity:${entry.activityId}",
                mark = entry.mark,
                timestampMillis = entry.timestampMillis,
                label = rules.labelOf(entry.mark, entry.name.trim()),
            )
        }

    return ActivityHistoryGrid(
        days = weeks.dateKeys.map { dateKey ->
            ActivityHistoryDay(
                dateKey = dateKey,
                activities = markersByDate[dateKey]
                    .orEmpty()
                    .sortedWith(
                        compareByDescending<ActivityHistoryMarker> { it.timestampMillis }
                            .thenBy { it.key },
                    )
                    .distinctBy { it.key },
            )
        },
    )
}
