package de.nicolasmahn.momentum.feature.settings

import de.nicolasmahn.momentum.domain.holiday.DayOff
import de.nicolasmahn.momentum.domain.holiday.DayRange
import de.nicolasmahn.momentum.domain.holiday.HolidaySchedule
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DaysOffSettingsTest {
    private fun day(text: String) = LocalDate.parse(text)

    @Test
    fun aFirstTapIsAOneDayRangeAndALaterTapExtendsIt() {
        val first = DayRangeDraft().tap(day("2026-10-12"))
        assertEquals(DayRange(day("2026-10-12"), day("2026-10-12")), first.range)

        val extended = first.tap(day("2026-10-18"))
        assertEquals(DayRange(day("2026-10-12"), day("2026-10-18")), extended.range)

        // A finished range starts over, and so does a tap before the first day.
        assertEquals(day("2026-10-20"), extended.tap(day("2026-10-20")).range?.start)
        assertEquals(
            DayRange(day("2026-10-05"), day("2026-10-05")),
            first.tap(day("2026-10-05")).range,
        )
        assertNull(DayRangeDraft().range)
    }

    /** A holiday is planned from today on; being sick is declared up to today, never ahead. */
    @Test
    fun holidaysLookAheadAndSickDaysLookBack() {
        val today = day("2026-10-05")
        assertTrue(DayOff.Away.canChoose(today, today))
        assertTrue(DayOff.Away.canChoose(today.plusDays(30), today))
        assertFalse(DayOff.Away.canChoose(today.minusDays(1), today))
        assertTrue(DayOff.Sick.canChoose(today, today))
        assertTrue(DayOff.Sick.canChoose(today.minusDays(30), today))
        assertFalse(DayOff.Sick.canChoose(today.plusDays(1), today))
    }

    /**
     * Holidays are planned, so the page looks ahead from this year. Sick days are declared
     * afterwards and re-judge their days, so every one stays listed and removable, newest first,
     * even after the year turns.
     */
    @Test
    fun holidaysShowWhatIsAheadAndSickDaysEverythingBehind() {
        val today = day("2027-01-01")
        assertEquals(listOf(2027), shownYears(DayOff.Away, HolidaySchedule(), today))

        val lastYearsHoliday = DayRange(day("2026-07-01"), day("2026-07-05"))
        val schedule = HolidaySchedule()
            .withHoliday(lastYearsHoliday)
            .withHoliday(DayRange(day("2027-12-28"), day("2028-01-03")))
            .withSickDays(DayRange(day("2025-03-02"), day("2025-03-03")))
            .withSickDays(DayRange(day("2026-12-30"), day("2027-01-01")))
        assertEquals(listOf(2027, 2028), shownYears(DayOff.Away, schedule, today))
        assertEquals(listOf(DayRange(day("2027-12-28"), day("2028-01-03"))), shownRanges(DayOff.Away, schedule, today))

        assertEquals(listOf(2027, 2026, 2025), shownYears(DayOff.Sick, schedule, today))
        assertEquals(schedule.sickDays.reversed(), shownRanges(DayOff.Sick, schedule, today))
        assertEquals(2, schedule.daysIn(DayOff.Sick, 2026))
        assertEquals(1, schedule.daysIn(DayOff.Sick, 2027))
    }

    /** A holiday under way keeps the days already lived; one that has not started, and sick days, go whole. */
    @Test
    fun removingAHolidayUnderWayCutsItToEndYesterday() {
        val today = day("2026-11-21")
        val trip = DayRange(day("2026-11-18"), day("2026-11-24"))
        val ahead = DayRange(day("2026-11-21"), day("2026-11-24"))
        assertEquals(listOf(DayRange(day("2026-11-18"), day("2026-11-20"))), HolidaySchedule(holidays = listOf(trip)).removing(DayOff.Away, trip, today).holidays)
        assertEquals(emptyList<DayRange>(), HolidaySchedule(holidays = listOf(ahead)).removing(DayOff.Away, ahead, today).holidays)
        assertEquals(emptyList<DayRange>(), HolidaySchedule(sickDays = listOf(trip)).removing(DayOff.Sick, trip, today).sickDays)
    }
}
