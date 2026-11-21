package com.example.workouttracker.app

import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.LoggedViewKind
import com.example.workouttracker.domain.LoggedViewRef
import com.example.workouttracker.domain.holiday.DayRange
import com.example.workouttracker.domain.holiday.HolidaySchedule
import com.example.workouttracker.domain.time.TimeLog
import com.example.workouttracker.domain.time.testMadeTime
import com.example.workouttracker.domain.trackerRef
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/** A timer's Home bar fills by what its week asks after sick days, as the timer's own screen counts it. */
class HomeTimePictureTest {
    private val monday = LocalDate.parse("2026-10-12")
    private val wednesday = monday.plusDays(2)
    private val timer = testMadeTime(goal = 45)

    private fun fill(minutes: Int, daysOff: HolidaySchedule): Float {
        val state = AppState(time = listOf(timer), timeLogs = listOf(TimeLog(timer.id, wednesday.toString(), 1, minutes)))
        val picture = homeDayPictures(state, wednesday, daysOff)[LoggedViewRef(LoggedViewKind.Time, timer.id).trackerRef]
        return (picture as HomeDayPicture.Segments).fills.single()
    }

    @Test fun sickDaysLowerTheWeekTheBarFillsTo() {
        // 45 a week less two sevenths is 33 when rounded up, as the timer's screen says.
        val sick = HolidaySchedule(sickDays = listOf(DayRange(monday, monday.plusDays(1))))
        assertEquals(10f / 33, fill(10, sick), 1e-6f)
        assertEquals(1f, fill(33, sick), 0f)
        assertEquals(33f / 45, fill(33, HolidaySchedule()), 1e-6f)
    }
}
