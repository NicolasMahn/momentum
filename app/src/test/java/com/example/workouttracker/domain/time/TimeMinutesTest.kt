package com.example.workouttracker.domain.time

import com.example.workouttracker.domain.time.TimeLog
import com.example.workouttracker.domain.AppState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate
import com.example.workouttracker.domain.compatibility.ReadingTimeId
import com.example.workouttracker.domain.time.addTimeMinutes

class TimeMinutesTest {
    /** Manual entries and timed sessions add minutes through the same transition. */
    private fun AppState.adding(dateKey: String, minutes: Int, timestampMillis: Long) =
        copy(timeLogs = addTimeMinutes(timeLogs, ReadingTimeId, dateKey, minutes, timestampMillis))

    @Test
    fun partialSessionsAccumulateWithoutGivingUnknownLegacyReadingCredit() {
        val monday = LocalDate.parse("2026-09-14")
        val old = AppState(timeLogs = listOf(TimeLog(ReadingTimeId, "2026-09-16", 10L)))
        assertEquals(0, minutesInWeek(old.timeLogs, ReadingTimeId, monday))

        val fifteen = old.adding("2026-09-16", 15, 20L)
        val fortyFive = fifteen.adding("2026-09-19", 45, 30L)
        assertEquals(60, minutesInWeek(fortyFive.timeLogs, ReadingTimeId, monday))
        assertEquals(15, fortyFive.timeLogs.filter { it.viewId == ReadingTimeId }.first { it.dateKey == "2026-09-16" }.minutes)

        val more = fortyFive.adding("2026-09-16", 15, 40L)
        assertEquals(75, minutesInWeek(more.timeLogs, ReadingTimeId, monday))
        assertEquals(0, minutesInWeek(more.timeLogs, ReadingTimeId, monday.plusWeeks(1)))
    }

    @Test
    fun impossibleDurationsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            AppState().adding("2026-09-16", 0, 10L)
        }
        val nearlyFull = AppState().adding("2026-09-16", 1440, 10L)
        assertThrows(IllegalArgumentException::class.java) {
            nearlyFull.adding("2026-09-16", 1, 11L)
        }
    }
}
