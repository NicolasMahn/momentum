package com.example.workouttracker.app

import com.example.workouttracker.domain.ActivityTrackerRef
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.LoggedViewKind
import com.example.workouttracker.domain.LoggedViewRef
import com.example.workouttracker.domain.SubstancesTrackerRef
import com.example.workouttracker.domain.TrackerRef
import com.example.workouttracker.domain.TrackerTypeId
import com.example.workouttracker.domain.checklist.isSchedule
import com.example.workouttracker.domain.checklist.recordDayFor
import com.example.workouttracker.domain.holiday.HolidaySchedule
import com.example.workouttracker.domain.mo.MoRecordIndex
import com.example.workouttracker.domain.substance.isSubstancesLoggedFor
import com.example.workouttracker.domain.substance.substanceAskedDay
import com.example.workouttracker.domain.trackerRef
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * What a Home row draws of its view's day, in the one slot every row has. It shows what was
 * recorded, never a judgment: a measurement's line has no direction that is good or bad.
 */
internal sealed interface HomeDayPicture {
    data object Empty : HomeDayPicture

    /**
     * One segment per part of the day, each filled from 0 to 1. [grouped] parts are a daily plan's
     * checkpoints, each filled by its items, and stand further apart than a checklist's items.
     */
    data class Segments(val fills: List<Float>, val grouped: Boolean = false) : HomeDayPicture {
        init {
            require(fills.isNotEmpty() && fills.all { it in 0f..1f }) { "Segments fill from 0 to 1, were $fills." }
        }
    }

    /** The current week, Monday to Sunday, as the calendar shows it: which days hold a record, and where today is. */
    data class Week(val days: List<WeekDay>) : HomeDayPicture {
        init {
            require(days.size == 7) { "A week has seven days, was ${days.size}." }
        }
    }

    /** A measurement's latest values, oldest first. */
    data class Trend(val values: List<Double>) : HomeDayPicture
}

/** A day of [HomeDayPicture.Week]. */
internal enum class WeekDay {
    /** Recorded. */
    Recorded,

    /** Over, with nothing recorded. */
    Empty,

    /** The day the view asks about, today or for Substances yesterday, with nothing recorded yet. */
    Today,

    /** Still to come. */
    Ahead,
}

/** How many of a measurement's latest values its line shows. */
private const val TrendValues = 14

/**
 * Each view's picture of [today]. Records are read through the same index the streak reads, so a
 * checklist's segments fill exactly as its items count as kept.
 */
internal fun homeDayPictures(state: AppState, today: LocalDate, daysOff: HolidaySchedule): Map<TrackerRef, HomeDayPicture> = buildMap {
    val records = MoRecordIndex(state, daysOff)
    val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    fun week(asked: LocalDate = today, recorded: (String) -> Boolean) = HomeDayPicture.Week(
        (0L until 7L).map { monday.plusDays(it) }.map { day ->
            when {
                recorded(day.toString()) -> WeekDay.Recorded
                day == asked -> WeekDay.Today
                day > today -> WeekDay.Ahead
                else -> WeekDay.Empty
            }
        },
    )
    state.checklists.forEach { checklist ->
        // The record the row's mark reads: yesterday's for a checklist about yesterday.
        val day = checklist.recordDayFor(today)
        val checkpoints = records.progress(checklist, day).checkpoints.filter { it.due.isNotEmpty() }
        val picture = when {
            checkpoints.isEmpty() -> HomeDayPicture.Empty
            checklist.isSchedule(day) -> HomeDayPicture.Segments(
                checkpoints.map { checkpoint -> (checkpoint.due.size - checkpoint.unmet.size).toFloat() / checkpoint.due.size },
                grouped = true,
            )
            else -> HomeDayPicture.Segments(checkpoints.flatMap { checkpoint -> checkpoint.due.map { if (it in checkpoint.unmet) 0f else 1f } })
        }
        put(TrackerRef(TrackerTypeId("checklist"), checklist.id), picture)
    }
    state.time.forEach { view ->
        // The week's minutes against what the week asks after days off, as the view's own screen
        // counts them; a week off throughout asks nothing, and so reads as full.
        val week = (0L until 7L).map { monday.plusDays(it).toString() }.toSet()
        val minutes = state.timeLogs.filter { it.viewId == view.id && it.dateKey in week }.sumOf { it.minutes ?: 0 }
        val asks = view.weekAsks(monday, daysOff)
        val fill = if (asks == 0) 1f else (minutes.toFloat() / asks).coerceIn(0f, 1f)
        put(LoggedViewRef(LoggedViewKind.Time, view.id).trackerRef, HomeDayPicture.Segments(listOf(fill)))
    }
    state.measurements.forEach { view ->
        val logs = state.measurementLogs.filter { it.measurementId == view.id }
        val values = logs.sortedBy { it.dateKey }.takeLast(TrendValues).map { it.value }
        // A line needs two points; until then the week shows which days were measured.
        put(LoggedViewRef(LoggedViewKind.Measurement, view.id).trackerRef, if (values.size < 2) week { day -> logs.any { it.dateKey == day } } else HomeDayPicture.Trend(values))
    }
    state.questions.forEach { view ->
        put(LoggedViewRef(LoggedViewKind.Questions, view.id).trackerRef, week { day -> state.questionEntries.any { it.viewId == view.id && it.dateKey == day } })
    }
    if (state.activity != null) {
        put(ActivityTrackerRef, week { day -> state.activityEntries.any { it.dateKey == day } || state.sessions.any { it.dateKey == day } })
    }
    // Substances asks about yesterday, so that is the day outlined; on a Monday it is last week's.
    put(SubstancesTrackerRef, week(asked = substanceAskedDay(today)) { day -> state.isSubstancesLoggedFor(day) })
}
