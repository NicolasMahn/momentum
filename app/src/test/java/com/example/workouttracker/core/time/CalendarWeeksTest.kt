package com.example.workouttracker.core.time

import java.time.DayOfWeek
import java.time.LocalDate
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarWeeksTest {
    @Test
    fun todayOnAMonday_startsANewRowWithSixDaysStillToCome() {
        val keys = calendarWeekDateKeys("2026-09-14")

        assertEquals(28, keys.size)
        assertEquals("2026-08-24", keys.first())
        assertEquals("2026-09-20", keys.last())
        assertEquals("2026-09-14", keys[21])
        assertEquals(6, keys.count { isFutureDateKey(it, "2026-09-14") })
    }

    @Test
    fun todayOnASunday_hasNoFutureDays() {
        val keys = calendarWeekDateKeys("2026-09-20")

        assertEquals("2026-08-24", keys.first())
        assertEquals("2026-09-20", keys.last())
        assertTrue(keys.none { isFutureDateKey(it, "2026-09-20") })
    }

    @Test
    fun todayMidWeek_sitsInItsOwnColumnOfTheLastRow() {
        val keys = calendarWeekDateKeys("2026-09-17")

        assertEquals("2026-08-24", keys.first())
        assertEquals("2026-09-17", keys[21 + 3])
        assertEquals(listOf("2026-09-18", "2026-09-19", "2026-09-20"), keys.takeLast(3))
    }

    @Test
    fun everyDayOfOneWeek_sharesTheSameWindow() {
        val monday = LocalDate.parse("2026-09-14")
        val windows = List(7) { calendarWeekDateKeys(monday.plusDays(it.toLong()).toString()) }

        assertEquals(1, windows.distinct().size)
    }

    @Test
    fun everyRow_runsMondayToSundayWithoutGaps() {
        val keys = calendarWeekDateKeys("2026-09-17", weekCount = 6)

        assertEquals(42, keys.size)
        keys.map(LocalDate::parse).chunked(7).forEach { week ->
            assertEquals(DayOfWeek.entries, week.map(LocalDate::getDayOfWeek))
        }
        keys.map(LocalDate::parse).zipWithNext { day, next ->
            assertEquals(day.plusDays(1), next)
        }
    }

    @Test
    fun windowCrossesMonthAndYearBoundaries() {
        // 2027-01-01 is a Friday; its week began in the old year.
        val keys = calendarWeekDateKeys("2027-01-01")

        assertEquals("2026-12-07", keys.first())
        assertEquals(listOf("2026-12-31", "2027-01-01", "2027-01-02", "2027-01-03"), keys.takeLast(4))
    }

    @Test
    fun leapDayIsOneOrdinaryCell() {
        val keys = calendarWeekDateKeys("2028-03-01")

        assertEquals(listOf("2028-02-28", "2028-02-29", "2028-03-01"), keys.subList(21, 24))
        assertEquals(28, keys.distinct().size)
    }

    @Test
    fun daylightSavingChanges_neitherSkipNorRepeatADay() {
        val original = TimeZone.getDefault()
        try {
            // Berlin loses an hour on 2026-03-29 and gains one on 2026-10-25.
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"))
            listOf("2026-03-29", "2026-04-01", "2026-10-25", "2026-10-28").forEach { todayKey ->
                val dates = calendarWeekDateKeys(todayKey).map(LocalDate::parse)

                assertEquals(28, dates.distinct().size)
                dates.zipWithNext { day, next -> assertEquals(day.plusDays(1), next) }
                assertEquals(DayOfWeek.MONDAY, dates.first().dayOfWeek)
            }
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun oneWeek_isJustTheCurrentWeek() {
        assertEquals(
            listOf(
                "2026-09-14", "2026-09-15", "2026-09-16", "2026-09-17",
                "2026-09-18", "2026-09-19", "2026-09-20",
            ),
            calendarWeekDateKeys("2026-09-19", weekCount = 1),
        )
    }

    @Test
    fun invalidInput_isRejected() {
        listOf("", "2026-9-1", "2026-02-30", "not-a-date").forEach { todayKey ->
            assertThrows(IllegalArgumentException::class.java) { calendarWeekDateKeys(todayKey) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            calendarWeekDateKeys("2026-09-19", weekCount = 0)
        }
    }

    @Test
    fun futureMeansStrictlyAfterToday() {
        assertFalse(isFutureDateKey("2026-09-18", "2026-09-19"))
        assertFalse(isFutureDateKey("2026-09-19", "2026-09-19"))
        assertTrue(isFutureDateKey("2026-09-20", "2026-09-19"))
        assertTrue(isFutureDateKey("2027-01-01", "2026-12-31"))
    }
}
