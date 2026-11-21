package com.example.workouttracker.domain.mo

import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.checklist.AboutDay
import com.example.workouttracker.domain.checklist.Dated
import com.example.workouttracker.domain.holiday.HolidaySchedule
import com.example.workouttracker.domain.measurement.MeasurementBank
import com.example.workouttracker.domain.measurement.MeasurementLog
import com.example.workouttracker.domain.measurement.MeasurementRewards
import com.example.workouttracker.domain.measurement.testMadeMeasurement
import com.example.workouttracker.domain.questions.QuestionEntry
import com.example.workouttracker.domain.questions.testEatingQuestions
import com.example.workouttracker.domain.ref
import com.example.workouttracker.domain.settings.FeatureVisibilitySettings
import com.example.workouttracker.domain.time.TimeLog
import com.example.workouttracker.domain.time.TimeRewards
import com.example.workouttracker.domain.time.testMadeTime
import com.example.workouttracker.domain.trackerRef
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfiguredRewardPolicyTest {
    private val monday = LocalDate.parse("2026-10-05")
    private val visibility = FeatureVisibilitySettings()

    @Test fun anArbitraryMeasurementCanEarnByWeeklyRecordedDaysFromItsFirstRecord() {
        val configuration = testMadeMeasurement(id = "weekly_measurement").copy(
            rewards = MeasurementBank.Week(MeasurementRewards(maxPasses = 3, keptDaysInWeek = 6)),
        )
        val ref = configuration.ref.trackerRef
        val state = AppState(
            measurements = listOf(configuration),
            measurementLogs = (0L..5L).map { MeasurementLog(configuration.id, monday.plusDays(it).toString(), it, 80.0) },
        )
        val sunday = habitStreak(state, visibility, monday.plusDays(6))
        assertEquals(monday, sunday.history.first().date)
        assertEquals(1, sunday.standings.getValue(ref).passes)
        assertEquals(MoTokenProgress(6, 6, List(6) { MoWeekDay.Kept } + MoWeekDay.Open), sunday.standings.getValue(ref).nextToken)
        // A six-day earning goal does not excuse Sunday's missing daily measurement.
        assertTrue(sunday.standings.getValue(ref).openToday)
        val nextMonday = habitStreak(state, visibility, monday.plusDays(7))
        assertEquals(listOf(ref), nextMonday.earnedTrackersYesterday)
        assertEquals(setOf(ref), nextMonday.history.single { it.date == monday.plusDays(6) }.coveredTrackers)
        val observation = trackerRewards(MoRecordIndex(state, HolidaySchedule()), visibility, HolidaySchedule(), monday.plusDays(6))
            .observations.single { it.ref == ref }
        assertEquals(7, observation.expectedInWeek)
        // Mo watches what counts toward the streak, so he asks for Sunday's measurement too.
        assertEquals(listOf(ref), moMood(state, visibility, monday.plusDays(6)).missingTrackersToday)
    }

    @Test fun aWholeHistoryTimerStartsAtItsFirstRecordAndJudgesItsMissingAmount() {
        val timer = testMadeTime(id = "practice_timer", rewards = TimeRewards(3))
        val ref = timer.ref.trackerRef
        val state = AppState(time = listOf(timer), timeLogs = listOf(TimeLog(timer.id, monday.toString(), 1, 15)))
        val sunday = habitStreak(state, visibility, monday.plusDays(6))
        assertEquals(monday, sunday.history.first().date)
        assertTrue(sunday.standings.getValue(ref).openToday)
        assertEquals(MoTokenProgress(15, 60), sunday.standings.getValue(ref).nextToken)
        val nextMonday = habitStreak(state, visibility, monday.plusDays(7))
        assertEquals(MoDayMark.Frozen, nextMonday.history.single { it.date == monday.plusDays(6) }.mark)
        assertEquals(setOf(ref), nextMonday.history.single { it.date == monday.plusDays(6) }.coveredTrackers)
        assertEquals(state.timeLogs, listOf(TimeLog(timer.id, monday.toString(), 1, 15)))
    }

    @Test fun aWholeHistoryConfiguredQuestionSourceStartsAtItsFirstAnswer() {
        val questions = testEatingQuestions().copy(id = "check_in", label = "Check-in", about = Dated.always(AboutDay.Today))
        val ref = questions.ref.trackerRef
        val state = AppState(
            questions = listOf(questions),
            questionEntries = listOf(QuestionEntry("answer", questions.id, monday.toString(), 1, mapOf("quality" to "good"))),
        )
        val nextDay = habitStreak(state, visibility, monday.plusDays(1))
        assertEquals(monday, nextDay.history.first().date)
        assertEquals(MoDayMark.Perfect, nextDay.history.first().mark)
        assertTrue(nextDay.standings.getValue(ref).openToday)
    }
}
