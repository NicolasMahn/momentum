package com.example.workouttracker.feature.time

import com.example.workouttracker.domain.time.TimeLog
import androidx.compose.runtime.Composable
import com.example.workouttracker.TestReadingTime
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.DayChangeResult
import com.example.workouttracker.domain.holiday.HolidaySchedule
import com.example.workouttracker.feature.time.TimeDayChange
import com.example.workouttracker.feature.time.TimeScreen
import com.example.workouttracker.feature.time.TimeSessionNote

/** A configured timer over records held by an interface test. */
@Composable
internal fun TimeScreenFixture(
    records: List<TimeLog>,
    onAddMinutes: (String, Int) -> Boolean,
    onRemoveDate: (String) -> Boolean,
    todayKey: String,
    daysOff: HolidaySchedule = HolidaySchedule(),
    onStartSession: () -> Unit = {},
    sessionNote: TimeSessionNote? = null,
) {
    val state = AppState(time = listOf(TestReadingTime), timeLogs = records)
    TimeScreen(
        state = state,
        view = TestReadingTime,
        words = TestReadingTime.words(),
        onDayChange = { change ->
            val stored = when (change) {
                is TimeDayChange.Add -> onAddMinutes(change.dateKey, change.minutes)
                is TimeDayChange.Remove -> onRemoveDate(change.dateKey)
            }
            // The screen reads only whether it was stored; the records it shows next are the test's.
            if (stored) DayChangeResult.Stored(before = state, after = state) else DayChangeResult.Failed
        },
        todayKey = todayKey,
        daysOff = daysOff,
        onStartSession = onStartSession,
        sessionNote = sessionNote,
        testTagPrefix = TestReadingTime.testTagPrefix,
    )
}
