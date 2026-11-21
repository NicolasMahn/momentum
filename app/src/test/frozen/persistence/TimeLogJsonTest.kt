package com.example.workouttracker.persistence.json

import com.example.workouttracker.domain.compatibility.ReadingTimeId
import com.example.workouttracker.domain.time.TimeLog
import com.example.workouttracker.domain.measurement.withWeight
import com.example.workouttracker.domain.AppState
import org.junit.Assert.assertEquals
import org.junit.Test
import com.example.workouttracker.domain.questions.withEating
import com.example.workouttracker.domain.time.withReading
import com.example.workouttracker.domain.activity.withActivity

class TimeLogJsonTest {
    @Test
    fun durationSurvivesStateRoundTripAndLegacyMarkRemainsUnknown() {
        val state = AppState(timeLogs = listOf(
            TimeLog(ReadingTimeId, "2026-09-16", 100L, 15),
            TimeLog(ReadingTimeId, "2026-09-17", 200L),
        )).withWeight().withEating().withReading().withActivity()

        val restored = AppStateJsonCodec.decodeVersion30(AppStateJsonCodec.encodeVersion30(state))

        assertEquals(state.timeLogs.toSet(), restored.timeLogs.toSet())
        assertEquals(null, restored.timeLogs.first { it.dateKey == "2026-09-17" }.minutes)
    }
}
