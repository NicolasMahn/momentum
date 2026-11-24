package de.nicolasmahn.momentum.domain.holiday

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HolidayScheduleTest {
    @Test
    fun overlappingDatesCountOnceAndEachYearCountsItsOwnDays() {
        val schedule = HolidaySchedule()
            .withHoliday(DayRange(LocalDate.parse("2026-12-28"), LocalDate.parse("2027-01-03")))
            .withHoliday(DayRange(LocalDate.parse("2027-01-02"), LocalDate.parse("2027-01-08")))

        assertEquals(1, schedule.holidays.size)
        assertEquals(4, schedule.holidayDaysIn(2026))
        assertEquals(8, schedule.holidayDaysIn(2027))
        assertTrue(schedule.isHoliday(LocalDate.parse("2027-01-08")))
        assertFalse(schedule.isHoliday(LocalDate.parse("2027-01-09")))
        assertEquals(7, schedule.holidayDaysInWeek(LocalDate.parse("2026-12-28")))
        assertEquals(5, schedule.holidayDaysInWeek(LocalDate.parse("2027-01-04")))
    }

    /** Sick days are their own list: they never become holidays, and a day both is sick. */
    @Test
    fun sickDaysAreKeptApartFromHolidaysAndWinOnADayThatIsBoth() {
        val day = LocalDate.parse("2026-10-07")
        val schedule = HolidaySchedule()
            .withHoliday(DayRange(day, day.plusDays(2)))
            .withSickDays(DayRange(day.minusDays(1), day))
            .withSickDays(DayRange(day.plusDays(1), day.plusDays(1)))

        assertEquals(listOf(DayRange(day.minusDays(1), day.plusDays(1))), schedule.sickDays)
        assertEquals(3, schedule.sickDaysIn(2026))
        assertEquals(3, schedule.holidayDaysIn(2026))
        assertEquals(DayOff.Sick, schedule.dayOff(day))
        assertEquals(DayOff.Away, schedule.dayOff(day.plusDays(2)))
        assertEquals(null, schedule.dayOff(day.plusDays(3)))
        // Three holiday days are the week's, a sick one among them; the sick days are counted apart.
        assertEquals(3, schedule.holidayDaysInWeek(LocalDate.parse("2026-10-05")))
        assertEquals(3, schedule.sickDaysInWeek(LocalDate.parse("2026-10-05")))
        assertEquals(HolidaySchedule().withHoliday(DayRange(day, day.plusDays(2))), schedule.withoutSickDays(schedule.sickDays.single()))
    }

    @Test
    fun aWeeklyAmountAsksASeventhLessForEachSickDayRoundedUp() {
        val monday = LocalDate.parse("2026-10-05")
        assertEquals(60, HolidaySchedule().weekAmountWithoutDaysOff(60, monday))
        val threeSick = HolidaySchedule().withSickDays(DayRange(monday, monday.plusDays(2)))
        assertEquals(35, threeSick.weekAmountWithoutDaysOff(60, monday))
        val wholeWeek = HolidaySchedule().withSickDays(DayRange(monday, monday.plusDays(6)))
        assertEquals(0, wholeWeek.weekAmountWithoutDaysOff(60, monday))
        // A sick day of the week before asks nothing less of this one.
        assertEquals(60, HolidaySchedule().withSickDays(DayRange(monday.minusDays(1), monday.minusDays(1))).weekAmountWithoutDaysOff(60, monday))
    }
}
