package com.example.workouttracker.domain.mo

import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.TrackerRef
import com.example.workouttracker.domain.activity.ActivityEntry
import com.example.workouttracker.domain.activity.testActivityConfiguration
import com.example.workouttracker.domain.checklist.AboutDay
import com.example.workouttracker.domain.checklist.ChecklistConfiguration
import com.example.workouttracker.domain.checklist.ChecklistDay
import com.example.workouttracker.domain.checklist.ChecklistEdit
import com.example.workouttracker.domain.checklist.ChecklistItem
import com.example.workouttracker.domain.checklist.ChecklistRewards
import com.example.workouttracker.domain.checklist.Checkpoint
import com.example.workouttracker.domain.checklist.CheckpointTime
import com.example.workouttracker.domain.checklist.Dated
import com.example.workouttracker.domain.checklist.RewardPeriod
import com.example.workouttracker.domain.checklist.ViewHue
import com.example.workouttracker.domain.checklist.ViewIcon
import com.example.workouttracker.domain.checklist.recordJudgedOn
import com.example.workouttracker.domain.checklist.applyEdit
import com.example.workouttracker.domain.holiday.DayRange
import com.example.workouttracker.domain.holiday.HolidaySchedule
import com.example.workouttracker.domain.measurement.MeasurementBank
import com.example.workouttracker.domain.measurement.MeasurementLog
import com.example.workouttracker.domain.measurement.MeasurementRewards
import com.example.workouttracker.domain.measurement.testMadeMeasurement
import com.example.workouttracker.domain.questions.QuestionEntry
import com.example.workouttracker.domain.questions.QuestionsEdit
import com.example.workouttracker.domain.questions.applyingQuestionsEdits
import com.example.workouttracker.domain.questions.testMadeQuestions
import com.example.workouttracker.domain.settings.FeatureVisibilitySettings
import com.example.workouttracker.domain.substance.SubstanceNoneDay
import com.example.workouttracker.domain.substance.SubstanceSettings
import com.example.workouttracker.domain.time.applyingTimeEdits
import com.example.workouttracker.domain.time.TimeEdit
import com.example.workouttracker.domain.time.TimeLog
import com.example.workouttracker.domain.time.TimeRewards
import com.example.workouttracker.domain.time.testMadeTime
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IndependentMoPolicyTest {
    private val monday = LocalDate.parse("2026-10-19")
    private val today = LocalDate.parse("2026-10-25")
    private val visibility = FeatureVisibilitySettings()
    private val bank = ChecklistRewards(3, 7)
    private val mo = TrackerMoPolicy(enabled = true)
    private val checklist = ChecklistConfiguration(
        "my-checklist", "Routine", ViewIcon.Checklist, ViewHue.Sky,
        checkpoints = listOf(Checkpoint("day", CheckpointTime.Fixed(LocalTime.of(9, 0)), remind = false)),
        items = listOf(ChecklistItem("task", "Task")), rewards = bank, mo = mo,
    )

    private fun observations(state: AppState): Map<TrackerRef, TrackerObservation> =
        MoEvaluation(state, visibility, today).observations.filter { it.participatesInMood }.associateBy { it.ref }

    @Test fun endingEveryKindsBankPeriodChangesNoMoObservationOrFactualBadge() {
        val measurement = testMadeMeasurement("my-measurement").copy(rewards = MeasurementBank.Standard(bank), mo = mo)
        val questions = testMadeQuestions("my-questions", madeOn = monday).copy(rewards = bank, mo = mo)
        val timer = testMadeTime("my-timer", rewards = TimeRewards(3)).copy(mo = mo)
        val activity = testActivityConfiguration()
        val substances = SubstanceSettings(rewards = bank.copy(periods = listOf(RewardPeriod(monday))), mo = mo)
        val recorded = AppState(
            checklists = listOf(checklist), checklistDays = listOf(ChecklistDay(checklist.id, today.toString(), 1, setOf("day/task"))),
            measurements = listOf(measurement), measurementLogs = listOf(MeasurementLog(measurement.id, today.toString(), 2, 42.0)),
            questions = listOf(questions), questionEntries = listOf(QuestionEntry("entry", questions.id, today.toString(), 3, mapOf("qn_rest" to "a1"))),
            time = listOf(timer), timeLogs = listOf(TimeLog(timer.id, monday.toString(), 4, 60)),
            activity = activity, activityEntries = listOf(ActivityEntry("activity-entry", "unknown", "Walk", today.toString(), 5)),
            substances = substances, substanceNoneDays = listOf(SubstanceNoneDay(today.toString(), 6)),
        )
        val left = recorded.copy(
            checklists = listOf(checklist.copy(rewards = bank.copy(periods = emptyList()))),
            measurements = listOf(measurement.copy(rewards = MeasurementBank.Standard(bank.copy(periods = emptyList())))),
            questions = listOf(questions.copy(rewards = bank.copy(periods = emptyList()))),
            time = listOf(timer.copy(rewards = timer.rewards!!.copy(periods = emptyList()))),
            activity = activity.copy(rewards = activity.rewards.copy(periods = emptyList())),
            substances = substances.copy(rewards = substances.rewards!!.copy(periods = emptyList())),
        )
        val before = observations(recorded)
        val after = observations(left)
        assertEquals(6, before.size)
        assertEquals(before.keys, after.keys)
        before.forEach { (ref, observation) ->
            val changed = after.getValue(ref)
            assertEquals(ref.toString(), observation.recordedAt, changed.recordedAt)
            assertEquals(ref.toString(), observation.statuses(today, HolidaySchedule()), changed.statuses(today, HolidaySchedule()))
            assertEquals(ref.toString(), observation.expectedInWeek, changed.expectedInWeek)
            assertEquals(ref.toString(), observation.countsOn(today), changed.countsOn(today))
        }
        val beforeMood = MoEvaluation(recorded, visibility, today).moodWithTrackerChoices(emptySet())
        val afterMood = MoEvaluation(left, visibility, today).moodWithTrackerChoices(emptySet())
        assertEquals(beforeMood.state, afterMood.state)
        assertEquals(beforeMood.keptTrackersToday, afterMood.keptTrackersToday)
        assertEquals(beforeMood.missingTrackersToday, afterMood.missingTrackersToday)
        assertEquals(beforeMood.trackerWeekProgress, afterMood.trackerWeekProgress)
        assertEquals(beforeMood.trackerProgress, afterMood.trackerProgress)
        assertTrue(afterMood.streak.standings.isEmpty())
    }

    @Test fun leavingRewardsTodayStillLetsMoJudgeAndShowTodaysActualChecklistRecord() {
        val state = AppState(checklists = listOf(checklist), checklistDays = listOf(
            ChecklistDay(checklist.id, monday.toString(), 1, setOf("day/task")),
            ChecklistDay(checklist.id, today.toString(), 2, setOf("day/task")),
        ))
        val left = state.applyEdit(checklist.id, ChecklistEdit.LeaveRewards, today)
        val before = MoEvaluation(state, visibility, today).moodWithTrackerChoices(emptySet())
        val after = MoEvaluation(left, visibility, today).moodWithTrackerChoices(emptySet())
        assertEquals(before.keptTrackersToday, after.keptTrackersToday)
        assertEquals(before.trackerProgress, after.trackerProgress)
        assertEquals(before.latestTrackerToday, after.latestTrackerToday)
        assertFalse(after.trackerProgress.isEmpty())
        assertTrue(after.streak.standings.isEmpty())
        assertEquals(state.checklistDays, left.checklistDays)
    }

    @Test fun changingAboutPreservesChecklistFactsWithNoBankWhetherMoIsEnabledOrNot() {
        val recordedDay = today.minusDays(2)
        listOf(true, false).forEach { enrolled ->
            val configured = checklist.copy(rewards = null, mo = TrackerMoPolicy(enrolled))
            val state = AppState(checklists = listOf(configured), checklistDays = listOf(ChecklistDay(configured.id, recordedDay.toString(), 1, setOf("day/task"))))
            val changed = state.applyEdit(configured.id, ChecklistEdit.SetAbout(AboutDay.Yesterday), today)
            val updated = changed.checklists.single()
            assertEquals(recordedDay, updated.recordJudgedOn(recordedDay))
            assertEquals(listOf(Dated.Revision(AboutDay.Today), Dated.Revision(AboutDay.Yesterday, today)), updated.about.revisions)
            assertEquals(state.checklistDays, changed.checklistDays)
            val observation = MoEvaluation(changed, visibility, today).observations.single { it.ref.id == configured.id }
            assertEquals(setOf(recordedDay), observation.recordedAt.keys)
        }
    }

    @Test fun changingAboutPreservesQuestionFactsWithNoBankWhetherMoIsEnabledOrNot() {
        val recordedDay = today.minusDays(2)
        listOf(true, false).forEach { enrolled ->
            val configured = testMadeQuestions("my-questions", madeOn = monday).copy(mo = TrackerMoPolicy(enrolled))
            val state = AppState(questions = listOf(configured), questionEntries = listOf(QuestionEntry("entry", configured.id, recordedDay.toString(), 1, mapOf("qn_rest" to "a1"))))
            val changed = state.applyingQuestionsEdits(configured.id, listOf(QuestionsEdit.SetAbout(AboutDay.Yesterday)), today)
            val updated = changed.questions.single()
            assertEquals(recordedDay, updated.recordJudgedOn(recordedDay))
            assertEquals(listOf(Dated.Revision(AboutDay.Today), Dated.Revision(AboutDay.Yesterday, today)), updated.about.revisions)
            assertEquals(state.questionEntries, changed.questionEntries)
            val observation = MoEvaluation(changed, visibility, today).observations.single { it.ref.id == configured.id }
            assertEquals(setOf(recordedDay), observation.recordedAt.keys)
        }
    }

    @Test fun aWeeklyMeasurementEarningGoalCannotChangeMosDailyWeekTarget() {
        val measurement = testMadeMeasurement("any-id").copy(rewards = MeasurementBank.Week(MeasurementRewards(3, 4)), mo = mo)
        val state = AppState(measurements = listOf(measurement), measurementLogs = listOf(MeasurementLog(measurement.id, monday.toString(), 1, 42.0)))
        assertEquals(7, observations(state).values.single().expectedInWeek)
        val withoutBank = state.copy(measurements = listOf(measurement.copy(rewards = null)))
        assertEquals(7, observations(withoutBank).values.single().expectedInWeek)
    }

    @Test fun anExcusedWeeklyMeasurementRecordIsRestedInTheTokenRibbonButStaysRecorded() {
        val measurement = testMadeMeasurement("my-measurement").copy(
            rewards = MeasurementBank.Week(MeasurementRewards(3, 6)),
            whenAway = Dated.always(com.example.workouttracker.domain.checklist.WhenAway.Excused),
        )
        val logs = (0L..2L).map { MeasurementLog(measurement.id, monday.plusDays(it).toString(), it, 42.0) }
        val state = AppState(measurements = listOf(measurement), measurementLogs = logs)
        val away = HolidaySchedule(listOf(DayRange(monday.plusDays(2), monday.plusDays(2))))
        val standing = habitStreak(state, visibility, monday.plusDays(3), away).standings.values.single()
        assertEquals(listOf(MoWeekDay.Kept, MoWeekDay.Kept, MoWeekDay.Rested), standing.nextToken!!.week.take(3))
        assertEquals(2, standing.nextToken!!.done)
        assertEquals(logs, state.measurementLogs)
    }
    @Test fun leavingAnImplicitTimerHistoryCannotRewriteItsFirstPartialWeek() {
        val timer = testMadeTime("any-timer", rewards = TimeRewards(5)).copy(mo = mo)
        val state = AppState(time = listOf(timer), timeLogs = listOf(
            TimeLog(timer.id, monday.plusDays(2).toString(), 1, 60),
            TimeLog(timer.id, monday.plusDays(9).toString(), 2, 60),
        ))
        val leaveDay = monday.plusDays(16)
        val left = state.applyingTimeEdits(timer.id, listOf(TimeEdit.LeaveRewards), leaveDay)
        val before = habitStreak(state, visibility, leaveDay.plusDays(1))
        val after = habitStreak(left, visibility, leaveDay.plusDays(1))
        assertEquals(before.history.filter { it.date < leaveDay }, after.history.filter { it.date < leaveDay })
        assertEquals(MoDayMark.Perfect, after.history.single { it.date == monday.plusDays(6) }.mark)
        assertEquals(monday, left.time.single().rewards!!.periods!!.single().from)
        assertEquals(state.timeLogs, left.timeLogs)
    }

}
