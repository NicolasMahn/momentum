package de.nicolasmahn.momentum.domain.time


import de.nicolasmahn.momentum.core.time.parseDateKey
import de.nicolasmahn.momentum.core.time.requireValidDateKey
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.holiday.HolidaySchedule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * One day's minutes for one time view, as the tracker state stores them: the day, when the log was
 * last written, and the minutes the day holds, added up from every session and everything typed.
 *
 * A view has one log per date. Sessions are not kept: adding minutes adds to the day's log, a day
 * cannot be lowered, and removing takes the whole day. A log of a view no configuration knows is
 * kept as it is and read by nothing.
 */
data class TimeLog(
    val viewId: String,
    val dateKey: String,
    val timestampMillis: Long,
    /**
     * Recorded minutes within [MinutesInADay]. Null preserves a completion mark without inventing
     * a duration; it contributes no minutes to factual totals.
     */
    val minutes: Int? = null,
)

/** What one day can hold: a minute to every minute it has. */
val MinutesInADay = 1..1440

/** Stored order: newest date and write first, then view ID. */
val TimeLogOrder: Comparator<TimeLog> =
    compareByDescending<TimeLog> { it.dateKey }.thenByDescending { it.timestampMillis }.thenBy { it.viewId }

/**
 * What a stored state's logs must be. Each names its view, has a valid date and a time of writing
 * of zero or more; a view has one log per date; and recorded minutes are [MinutesInADay].
 * A completion mark without duration contributes no minutes to factual totals.
 */
internal fun requireStorableTimeLogs(logs: List<TimeLog>) {
    logs.forEach { log ->
        require(log.viewId.isNotBlank()) { "A log names its view." }
        requireValidDateKey(log.dateKey, subject = "The date of a log of ${log.viewId}")
        require(log.timestampMillis >= 0) { "A log of ${log.viewId} was written at a time before the epoch." }
        when (val minutes = log.minutes) {
            null -> Unit
            else -> require(minutes in MinutesInADay) { "A day holds ${MinutesInADay.first} to ${MinutesInADay.last} minutes, not $minutes." }
        }
    }
    require(logs.distinctBy { it.viewId to it.dateKey }.size == logs.size) { "A time view has two logs for one date." }
}

/**
 * [logs] with [minutes] added to what the view [viewId] holds for [dateKey], written at
 * [timestampMillis]: one log for that view and date holding the day's new total, every other log
 * as it was. A mark without minutes becomes the minutes added to it and nothing more. Throws for
 * minutes a day cannot hold, alone or with what it has already.
 */
fun addTimeMinutes(logs: List<TimeLog>, viewId: String, dateKey: String, minutes: Int, timestampMillis: Long): List<TimeLog> {
    requireValidDateKey(dateKey)
    require(minutes in MinutesInADay) { "Minutes must be between ${MinutesInADay.first} and ${MinutesInADay.last}." }
    require(timestampMillis >= 0) { "Timestamp must not be negative." }
    val total = minutesOn(logs, viewId, dateKey) + minutes
    require(total <= MinutesInADay.last) { "A day's total cannot exceed a day." }
    return (removeTimeDay(logs, viewId, dateKey) + TimeLog(viewId, dateKey, timestampMillis, total)).sortedWith(TimeLogOrder)
}

/** [logs] without what the view [viewId] holds for [dateKey]; every other log stays as it was. */
fun removeTimeDay(logs: List<TimeLog>, viewId: String, dateKey: String): List<TimeLog> =
    logs.filterNot { it.viewId == viewId && it.dateKey == dateKey }

/** The minutes the view [viewId] holds for [dateKey]: none on a day without a log, and none for a mark without minutes. */
fun minutesOn(logs: List<TimeLog>, viewId: String, dateKey: String): Int =
    logs.filter { it.viewId == viewId && it.dateKey == dateKey }.sumOf { it.minutes ?: 0 }

/**
 * The minutes the view [viewId] holds in the week starting on [monday]. Only recorded minutes
 * count toward a goal: a mark without them cannot imply a duration.
 */
fun minutesInWeek(logs: List<TimeLog>, viewId: String, monday: LocalDate): Int {
    require(monday.dayOfWeek == DayOfWeek.MONDAY)
    val sunday = monday.plusDays(6)
    return logs.sumOf { log ->
        val date = if (log.viewId == viewId) parseDateKey(log.dateKey) else null
        if (date != null && date >= monday && date <= sunday) log.minutes ?: 0 else 0
    }
}

/**
 * The minutes the week of [day] still asks of [view]: what that week asks
 * ([TimeConfiguration.weekAsks]), its goal less for each sick day and each away day the view is
 * not due on, as the streak asks less, less what its logs hold for that week. A reminder's words
 * and whether it speaks both come here, so they cannot disagree.
 */
fun AppState.minutesToGo(view: TimeConfiguration, day: LocalDate, daysOff: HolidaySchedule): Int {
    val monday = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    return (view.weekAsks(monday, daysOff) - minutesInWeek(timeLogs, view.id, monday)).coerceAtLeast(0)
}

/** The log the view [viewId] holds for [dateKey], or null on a day without one. */
fun AppState.timeLog(viewId: String, dateKey: String): TimeLog? = timeLogs.firstOrNull { it.viewId == viewId && it.dateKey == dateKey }
