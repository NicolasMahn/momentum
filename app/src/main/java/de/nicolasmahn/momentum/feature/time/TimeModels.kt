package de.nicolasmahn.momentum.feature.time

import de.nicolasmahn.momentum.core.time.calendarWeeksWindow
import de.nicolasmahn.momentum.domain.time.MinutesInADay
import de.nicolasmahn.momentum.domain.time.TimeLog

/** One day of a time view's calendar: filled once the day has a log, whatever it holds. */
internal data class TimeHistoryDay(
    val dateKey: String,
    val isLogged: Boolean,
)

internal data class TimeHistoryWindow(
    val days: List<TimeHistoryDay>,
)

/** The recent weeks of one view over its own [logs], as its calendar shows them. */
internal fun timeHistoryWindow(
    logs: List<TimeLog>,
    todayKey: String,
): TimeHistoryWindow {
    val weeks = calendarWeeksWindow(todayKey)
    val loggedDates = weeks.groupVisible(logs, TimeLog::dateKey).keys

    return TimeHistoryWindow(days = weeks.dateKeys.map { dateKey -> TimeHistoryDay(dateKey = dateKey, isLogged = dateKey in loggedDates) })
}

/** Why typed minutes cannot be added to a day. */
internal sealed interface MinutesError {
    /** Not a number of minutes a day can hold, one to [most]. */
    data class OutOfRange(val most: Int) : MinutesError

    /** More than the day has left beside what it holds already. */
    data object OverADay : MinutesError
}

/** What is wrong with [minutes] typed for a day that holds [alreadyRecorded] of them, or null where they can be added. */
internal fun minutesError(minutes: Int?, alreadyRecorded: Int): MinutesError? = when {
    minutes == null || minutes !in MinutesInADay -> MinutesError.OutOfRange(MinutesInADay.last)
    alreadyRecorded + minutes > MinutesInADay.last -> MinutesError.OverADay
    else -> null
}
