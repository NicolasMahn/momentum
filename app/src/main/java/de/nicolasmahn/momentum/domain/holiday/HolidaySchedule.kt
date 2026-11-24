package de.nicolasmahn.momentum.domain.holiday

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Inclusive dates. A day here is a habit day, which ends at 04:00 local time. */
data class DayRange(val start: LocalDate, val end: LocalDate) {
    init {
        require(!end.isBefore(start))
    }

    val days: Int get() = ChronoUnit.DAYS.between(start, end).toInt() + 1

    fun contains(day: LocalDate): Boolean = day >= start && day <= end
}

/** A day that is not an ordinary one. Sick wins over away, because a sick day asks nothing at all. */
enum class DayOff { Away, Sick }

/**
 * The days that are not ordinary: [holidays] planned ahead, and [sickDays] declared, usually
 * afterwards. What a holiday means is each view's own `whenAway`; a sick day asks nothing of any
 * view. Neither ever changes a record.
 */
data class HolidaySchedule(
    val holidays: List<DayRange> = emptyList(),
    val sickDays: List<DayRange> = emptyList(),
) {
    init {
        require(holidays.isOrderedAndSeparate()) { "Holiday ranges must be ordered and non-overlapping" }
        require(sickDays.isOrderedAndSeparate()) { "Sick ranges must be ordered and non-overlapping" }
    }

    fun isHoliday(day: LocalDate): Boolean = holidays.any { it.contains(day) }

    fun isSick(day: LocalDate): Boolean = sickDays.any { it.contains(day) }

    fun dayOff(day: LocalDate): DayOff? = when {
        isSick(day) -> DayOff.Sick
        isHoliday(day) -> DayOff.Away
        else -> null
    }

    /** The holiday days of the week starting on [monday], a holiday day that is also sick among them. */
    fun holidayDaysInWeek(monday: LocalDate): Int {
        require(monday.dayOfWeek == DayOfWeek.MONDAY)
        return (0L..6L).count { isHoliday(monday.plusDays(it)) }
    }

    /**
     * A weekly [amount] for the days of the week starting on [monday] that ask for it: a seventh
     * less for each day that is sick or that a view is [alsoOff] on, such as an away day it is not
     * due on. A day that is both counts once, and the amount is rounded up once at the end.
     */
    fun weekAmountWithoutDaysOff(amount: Int, monday: LocalDate, alsoOff: (LocalDate) -> Boolean = { false }): Int {
        require(monday.dayOfWeek == DayOfWeek.MONDAY)
        val daysOff = (0L..6L).count { monday.plusDays(it).let { day -> isSick(day) || alsoOff(day) } }
        return (amount * (7 - daysOff) + 6) / 7
    }

    /** The sick days of the week starting on [monday]. */
    fun sickDaysInWeek(monday: LocalDate): Int {
        require(monday.dayOfWeek == DayOfWeek.MONDAY)
        return (0L..6L).count { isSick(monday.plusDays(it)) }
    }

    fun holidayDaysIn(year: Int): Int = holidays.daysIn(year)

    fun sickDaysIn(year: Int): Int = sickDays.daysIn(year)

    fun withHoliday(range: DayRange): HolidaySchedule = copy(holidays = holidays.merging(range))

    fun withoutHoliday(range: DayRange): HolidaySchedule = copy(holidays = holidays - range)

    fun withSickDays(range: DayRange): HolidaySchedule = copy(sickDays = sickDays.merging(range))

    fun withoutSickDays(range: DayRange): HolidaySchedule = copy(sickDays = sickDays - range)
}

private fun List<DayRange>.isOrderedAndSeparate(): Boolean = zipWithNext().all { (before, after) -> before.end < after.start }

/** Adds [range], joining it with every range it overlaps or touches, so the list stays ordered and separate. */
private fun List<DayRange>.merging(range: DayRange): List<DayRange> {
    val merged = mutableListOf<DayRange>()
    (this + range).sortedBy { it.start }.forEach { next ->
        val previous = merged.lastOrNull()
        if (previous != null && !previous.end.plusDays(1).isBefore(next.start)) {
            merged[merged.lastIndex] = DayRange(previous.start, maxOf(previous.end, next.end))
        } else {
            merged += next
        }
    }
    return merged
}

private fun List<DayRange>.daysIn(year: Int): Int {
    val first = LocalDate.of(year, 1, 1)
    val last = LocalDate.of(year, 12, 31)
    return sumOf { range ->
        val overlapStart = maxOf(first, range.start)
        val overlapEnd = minOf(last, range.end)
        if (overlapEnd < overlapStart) 0 else DayRange(overlapStart, overlapEnd).days
    }
}
