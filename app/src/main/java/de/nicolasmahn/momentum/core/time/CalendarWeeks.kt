package de.nicolasmahn.momentum.core.time

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.TemporalAdjusters

internal const val CalendarWeekCount = 4

/**
 * The calendar's columns, Monday first. That is simply ISO order, which is what [DayOfWeek] uses;
 * the decision recorded here is that the device locale is never asked: this is one person's
 * calendar, and a layout that stays put matters more than following the locale.
 */
internal val CalendarWeekdays: List<DayOfWeek> = DayOfWeek.entries

/**
 * The date keys of a recent-day calendar: whole weeks, Monday to Sunday, ending with the week
 * that holds [todayKey]. Always `7 × weekCount` keys, so a day keeps its column and a new row
 * starts each Monday. The days after today are included to hold the grid's shape; ask
 * [isFutureDateKey] before treating a key as a day that has happened.
 *
 * Date keys are calendar dates, so this is [LocalDate] arithmetic with no time zone in it: a
 * daylight-saving change can neither skip nor repeat a day.
 */
internal fun calendarWeekDateKeys(
    todayKey: String,
    weekCount: Int = CalendarWeekCount,
): List<String> {
    require(weekCount > 0) { "Calendar week count must be greater than zero." }
    val today = try {
        LocalDate.parse(todayKey)
    } catch (error: DateTimeParseException) {
        throw IllegalArgumentException("Today must use a valid YYYY-MM-DD date.", error)
    }
    val firstMonday = today
        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        .minusWeeks(weekCount - 1L)
    return List(7 * weekCount) { index -> firstMonday.plusDays(index.toLong()).toString() }
}

/**
 * A recent-day calendar's window: the grid's [dateKeys] and which of them have happened.
 *
 * The days after today only hold the grid's shape, so a record dated ahead must never show there.
 * Every feature model gets that rule from [groupVisible] instead of repeating the filter.
 */
internal class CalendarWeeksWindow(
    val dateKeys: List<String>,
    val visibleDateKeys: Set<String>,
) {
    fun <T> groupVisible(records: Iterable<T>, dateKey: (T) -> String): Map<String, List<T>> =
        records
            .asSequence()
            .filter { dateKey(it) in visibleDateKeys }
            .groupBy(dateKey)
}

internal fun calendarWeeksWindow(
    todayKey: String,
    weekCount: Int = CalendarWeekCount,
): CalendarWeeksWindow {
    val dateKeys = calendarWeekDateKeys(todayKey, weekCount)
    return CalendarWeeksWindow(
        dateKeys = dateKeys,
        visibleDateKeys = dateKeys.filterNot { isFutureDateKey(it, todayKey) }.toSet(),
    )
}

/** ISO date keys sort as their dates do, so "after today" needs no parsing. */
internal fun isFutureDateKey(dateKey: String, todayKey: String): Boolean = dateKey > todayKey
