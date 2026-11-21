package com.example.workouttracker.domain.mo

import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.ActivityTrackerRef
import com.example.workouttracker.domain.TrackerRef
import com.example.workouttracker.domain.TrackerTypeId
import com.example.workouttracker.domain.activity.ActivityEntry
import com.example.workouttracker.domain.checklist.ChecklistDay
import com.example.workouttracker.domain.measurement.MeasurementLog
import com.example.workouttracker.domain.questions.QuestionEntry
import com.example.workouttracker.domain.settings.FeatureVisibilitySettings
import com.example.workouttracker.domain.time.TimeLog
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnconfiguredTrackerFactsTest {
    @Test fun orphanFactsDoNotRecreateRetiredTrackers() {
        val day = LocalDate.parse("2026-10-12")
        val state = AppState(
            measurementLogs = listOf(MeasurementLog("weight", day.toString(), 1, 80.0)),
            questionEntries = listOf(QuestionEntry("entry", "eating", day.toString(), 1, mapOf("quality" to "good"))),
            timeLogs = listOf(TimeLog("reading", day.toString(), 1, 30)),
            checklistDays = listOf(
                ChecklistDay("care", day.toString(), 1, setOf("morning/toothbrushing")),
                ChecklistDay("supplements", day.toString(), 1, setOf("midday/creatine")),
            ),
            activityEntries = listOf(ActivityEntry("entry", "run", "Running", day.toString(), 1)),
        )
        val evaluation = MoEvaluation(state, FeatureVisibilitySettings(), day.plusDays(1))
        val mood = evaluation.moodWithTrackerChoices(emptySet())
        assertEquals(0, mood.dueToday)
        assertTrue(mood.streak.standings.isEmpty())
        val orphanRefs = setOf(
            ActivityTrackerRef,
            TrackerRef(TrackerTypeId("measurement"), "weight"),
            TrackerRef(TrackerTypeId("questions"), "eating"),
            TrackerRef(TrackerTypeId("time"), "reading"),
            TrackerRef(TrackerTypeId("checklist"), "care"),
            TrackerRef(TrackerTypeId("checklist"), "supplements"),
        )
        assertTrue(mood.trackerContent.keys.none { it in orphanRefs })
        assertTrue(mood.missingTrackersToday.isEmpty())
        assertTrue(evaluation.trackerFreeDays().isEmpty())
        // Ignoring an unconfigured tracker must not erase the facts a future recovery can read.
        assertEquals(1, state.measurementLogs.size)
        assertEquals(1, state.questionEntries.size)
        assertEquals(1, state.timeLogs.size)
        assertEquals(2, state.checklistDays.size)
        assertEquals(1, state.activityEntries.size)
    }
}
