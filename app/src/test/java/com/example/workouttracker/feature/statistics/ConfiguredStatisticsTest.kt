package com.example.workouttracker.feature.statistics

import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.checklist.AboutDay
import com.example.workouttracker.domain.checklist.ChecklistDay
import com.example.workouttracker.domain.checklist.Dated
import com.example.workouttracker.domain.measurement.MeasurementLog
import com.example.workouttracker.domain.measurement.testMadeMeasurement
import com.example.workouttracker.domain.questions.QuestionEntry
import com.example.workouttracker.domain.questions.testMadeQuestions
import com.example.workouttracker.domain.supplement.testSupplementsChecklist
import com.example.workouttracker.domain.time.TimeLog
import com.example.workouttracker.domain.time.testMadeTime
import com.example.workouttracker.domain.settings.FeatureVisibilitySettings
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfiguredStatisticsTest {
    private val today = "2026-10-15"

    @Test fun everyVisibleConfigurationContributesFactsWithoutJoiningRewards() {
        val number = testMadeMeasurement(label = "Morning number", unit = "bpm")
        val answers = testMadeQuestions(label = "Rest", about = AboutDay.Yesterday)
        val timer = testMadeTime(label = "Practice")
        val checklist = testSupplementsChecklist().copy(label = "My items")
        val state = AppState(
            checklists = listOf(checklist),
            measurements = listOf(number),
            questions = listOf(answers),
            time = listOf(timer),
            checklistDays = listOf(ChecklistDay(checklist.id, today, 0, emptySet(), misses = setOf("midday/creatine"))),
            measurementLogs = listOf(MeasurementLog(number.id, today, 0, 65.0)),
            questionEntries = listOf(QuestionEntry("entry", answers.id, "2026-10-14", 0, emptyMap())),
            timeLogs = listOf(TimeLog(timer.id, today, 0, minutes = null)),
        )
        val summary = statisticsSummary(state, today, StatisticsRange.FourWeeks)
        assertEquals(listOf("My items", "Morning number", "Rest", "Practice"), summary.consistency.map { it.label })
        assertEquals(
            listOf(0 to 0, 1 to 1, 1 to 1, 0 to 0),
            summary.consistency.map { row -> row.weeks.last().let { it.kept to it.counted } },
        )
        assertEquals(number, summary.measurements.single().configuration)
        assertEquals(65.0, summary.measurements.single().latestValue!!, 0.0)
        val hidden = statisticsSummary(state, today, StatisticsRange.FourWeeks, FeatureVisibilitySettings(hiddenMeasurements = setOf(number.id)))
        assertTrue(hidden.measurements.isEmpty())
        assertFalse(hidden.consistency.any { it.ref.id == number.id })
    }

    @Test fun switchingToYesterdayDoesNotCountOneChecklistRecordTwice() {
        val changeDay = LocalDate.parse("2026-10-16")
        val checklist = testSupplementsChecklist().copy(
            id = "chk_k2p9",
            about = Dated.always(AboutDay.Today).changedFrom(changeDay, AboutDay.Yesterday),
        )
        val state = AppState(checklists = listOf(checklist), checklistDays = listOf(
            ChecklistDay(checklist.id, "2026-10-15", 0, setOf("midday/creatine")),
        ))
        val summary = statisticsSummary(state, changeDay.toString(), StatisticsRange.FourWeeks)
        assertEquals(1, summary.consistency.single().weeks.last().counted)
    }

    @Test fun switchingToYesterdayDoesNotCountOneQuestionEntryTwice() {
        val changeDay = LocalDate.parse("2026-10-16")
        val questions = testMadeQuestions().copy(
            about = Dated.always(AboutDay.Today).changedFrom(changeDay, AboutDay.Yesterday),
        )
        val state = AppState(questions = listOf(questions), questionEntries = listOf(
            QuestionEntry("entry", questions.id, "2026-10-15", 0, emptyMap()),
        ))
        val summary = statisticsSummary(state, changeDay.toString(), StatisticsRange.FourWeeks)
        assertEquals(1, summary.consistency.single().weeks.last().kept)
    }

    @Test fun anUnreadableHistoricalMeasurementDateIsPreservedWithoutBreakingStatistics() {
        val measurement = testMadeMeasurement()
        val state = AppState(measurements = listOf(measurement), measurementLogs = listOf(
            MeasurementLog(measurement.id, "not-a-date", 0, 9.0),
            MeasurementLog(measurement.id, today, 0, 10.0),
        ))
        val summary = statisticsSummary(state, today, StatisticsRange.FourWeeks)
        assertEquals(1, summary.consistency.single().weeks.last().kept)
        assertEquals(2, state.measurementLogs.size)
    }

    @Test fun recordsWithoutAConfigurationDoNotCreatePhantomTrackers() {
        val summary = statisticsSummary(AppState(measurementLogs = listOf(MeasurementLog("removed", today, 0, 10.0))), today, StatisticsRange.FourWeeks)
        assertTrue(summary.consistency.isEmpty())
        assertTrue(summary.measurements.isEmpty())
    }
}
