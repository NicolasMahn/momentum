package de.nicolasmahn.momentum.domain.mo

import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.TrackerRef
import de.nicolasmahn.momentum.domain.TrackerTypeId
import de.nicolasmahn.momentum.domain.checklist.AboutDay
import de.nicolasmahn.momentum.domain.checklist.ChecklistConfiguration
import de.nicolasmahn.momentum.domain.checklist.ChecklistDay
import de.nicolasmahn.momentum.domain.checklist.ChecklistEdit
import de.nicolasmahn.momentum.domain.checklist.ChecklistItem
import de.nicolasmahn.momentum.domain.checklist.ChecklistRewards
import de.nicolasmahn.momentum.domain.checklist.Checkpoint
import de.nicolasmahn.momentum.domain.checklist.CheckpointTime
import de.nicolasmahn.momentum.domain.checklist.Dated
import de.nicolasmahn.momentum.domain.checklist.RewardPeriod
import de.nicolasmahn.momentum.domain.checklist.ViewHue
import de.nicolasmahn.momentum.domain.checklist.ViewIcon
import de.nicolasmahn.momentum.domain.checklist.applyingEdits
import de.nicolasmahn.momentum.domain.checklist.checklistRecordedDates
import de.nicolasmahn.momentum.domain.firstJudgmentDay
import de.nicolasmahn.momentum.domain.checklist.applyEdit
import de.nicolasmahn.momentum.domain.holiday.HolidaySchedule
import de.nicolasmahn.momentum.domain.judgmentDayFor
import de.nicolasmahn.momentum.domain.questions.QuestionEntry
import de.nicolasmahn.momentum.domain.questions.QuestionsEdit
import de.nicolasmahn.momentum.domain.questions.applyingQuestionsEdits
import de.nicolasmahn.momentum.domain.questions.firstDueDayOf
import de.nicolasmahn.momentum.domain.questions.testMadeQuestions
import de.nicolasmahn.momentum.domain.ref
import de.nicolasmahn.momentum.domain.settings.FeatureVisibilitySettings
import de.nicolasmahn.momentum.domain.trackerRef
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JudgmentHistoryMaterializationTest {
    private val firstRecord = LocalDate.parse("2026-10-19")
    private val leaveDay = LocalDate.parse("2026-10-25")
    private val visibility = FeatureVisibilitySettings()
    private val rewards = ChecklistRewards(3, 7)

    private fun began(state: AppState, ref: TrackerRef, today: LocalDate): LocalDate? =
        trackerRewards(MoRecordIndex(state, HolidaySchedule()), visibility, HolidaySchedule(), today)
            .entries.single { it.ref == ref }.participant.began

    @Test fun leavingAnImplicitLookBackChecklistPreservesItsFirstJudgmentAndPriorOutcomes() {
        val checklist = ChecklistConfiguration(
            id = "night-routine", label = "Night routine", icon = ViewIcon.Checklist, hue = ViewHue.Sky,
            checkpoints = listOf(Checkpoint("check", CheckpointTime.Fixed(LocalTime.of(9, 0)), remind = false)),
            items = listOf(ChecklistItem("task", "Task")),
            about = Dated.always(AboutDay.Yesterday), rewards = rewards, mo = TrackerMoPolicy(true),
        )
        val records = (0L..5L).map { ChecklistDay(checklist.id, firstRecord.plusDays(it).toString(), it, setOf("check/task")) }
        val state = AppState(checklists = listOf(checklist), checklistDays = records)
        val ref = TrackerRef(TrackerTypeId("checklist"), checklist.id)
        val firstJudgment = firstRecord.plusDays(1)
        val before = habitStreak(state, visibility, leaveDay)
        val left = state.applyEdit(checklist.id, ChecklistEdit.LeaveRewards, leaveDay)
        val after = habitStreak(left, visibility, leaveDay)

        assertEquals(firstJudgment, checklist.judgmentDayFor(firstRecord))
        assertEquals(firstJudgment, began(state, ref, leaveDay))
        assertEquals(listOf(RewardPeriod(firstJudgment, leaveDay.minusDays(1))), left.checklists.single().rewards!!.periods)
        assertEquals(firstJudgment, began(left, ref, leaveDay))
        assertEquals(before.history.filter { it.date < leaveDay }, after.history.filter { it.date < leaveDay })
        assertEquals(MoDayMark.NothingDue, after.history.single { it.date == firstRecord }.mark)
        assertTrue(after.history.filter { it.date < firstJudgment }.all { ref !in it.coveredTrackers })
        assertEquals(habitStreak(state, visibility, leaveDay.minusDays(1)), habitStreak(left, visibility, leaveDay.minusDays(1)))
        assertEquals(records, left.checklistDays)
        assertEquals(MoLedger.of(state, visibility, leaveDay).history(ref), MoLedger.of(left, visibility, leaveDay).history(ref))
    }

    @Test fun leavingQuestionsAfterAnOwnershipSwitchCannotDropAnAlreadyJudgedRecord() {
        val questions = testMadeQuestions("evening-reflection", madeOn = firstRecord).copy(rewards = rewards, mo = TrackerMoPolicy(true))
        val entry = QuestionEntry("answer", questions.id, firstRecord.toString(), 1, mapOf("qn_rest" to "a1"))
        val recorded = AppState(questions = listOf(questions), questionEntries = listOf(entry))
        val switched = recorded.applyingQuestionsEdits(questions.id, listOf(QuestionsEdit.SetAbout(AboutDay.Yesterday)), firstRecord.plusDays(1))
        val ref = questions.ref.trackerRef
        val leave = LocalDate.parse("2026-10-23")
        val before = habitStreak(switched, visibility, leave)
        val left = switched.applyingQuestionsEdits(questions.id, listOf(QuestionsEdit.LeaveRewards), leave)
        val after = habitStreak(left, visibility, leave)

        assertEquals(firstRecord, switched.questions.single().judgmentDayFor(firstRecord))
        assertEquals(firstRecord, switched.firstDueDayOf(switched.questions.single()))
        assertEquals(firstRecord, began(switched, ref, leave))
        assertEquals(listOf(RewardPeriod(firstRecord, leave.minusDays(1))), left.questions.single().rewards!!.periods)
        assertEquals(firstRecord, began(left, ref, leave))
        assertEquals(MoDayMark.Perfect, after.history.single { it.date == firstRecord }.mark)
        assertEquals(MoDayMark.NothingDue, after.history.single { it.date == firstRecord.plusDays(1) }.mark)
        assertEquals(before.history.filter { it.date < leave }, after.history.filter { it.date < leave })
        assertEquals(habitStreak(switched, visibility, leave.minusDays(1)), habitStreak(left, visibility, leave.minusDays(1)))
        assertEquals(listOf(entry), left.questionEntries)
        assertEquals(setOf(firstRecord), MoEvaluation(left, visibility, leave).observations.single { it.ref == ref }.recordedAt.keys)
    }

    @Test fun aRecordNoDueDayJudgesCannotMoveTheStartAheadOfTheFirstActuallyJudgedRecord() {
        val switchDay = firstRecord.plusDays(1)
        val questions = testMadeQuestions("morning-notes", madeOn = firstRecord).copy(
            rewards = rewards,
            about = Dated(listOf(Dated.Revision(AboutDay.Yesterday), Dated.Revision(AboutDay.Today, switchDay))),
            mo = TrackerMoPolicy(true),
        )
        val skipped = QuestionEntry("skipped", questions.id, firstRecord.toString(), 1, mapOf("qn_rest" to "a1"))
        val judged = QuestionEntry("judged", questions.id, switchDay.toString(), 2, mapOf("qn_rest" to "a1"))
        val state = AppState(questions = listOf(questions), questionEntries = listOf(judged, skipped))
        val left = state.applyingQuestionsEdits(questions.id, listOf(QuestionsEdit.LeaveRewards), leaveDay)

        assertNull(questions.judgmentDayFor(firstRecord))
        assertEquals(switchDay, questions.judgmentDayFor(switchDay))
        assertEquals(switchDay, state.firstDueDayOf(questions))
        assertEquals(switchDay, began(state, questions.ref.trackerRef, leaveDay))
        assertEquals(switchDay, left.questions.single().rewards!!.periods!!.single().from)
        assertEquals(state.questionEntries, left.questionEntries)
    }
    private fun switchingChecklist() = ChecklistConfiguration(
        id = "morning-routine", label = "Routine", icon = ViewIcon.Checklist, hue = ViewHue.Sky,
        checkpoints = listOf(Checkpoint("check", CheckpointTime.Fixed(LocalTime.of(9, 0)), remind = false)),
        items = listOf(ChecklistItem("task", "Task")), rewards = rewards, mo = TrackerMoPolicy(true),
        about = Dated(listOf(Dated.Revision(AboutDay.Yesterday), Dated.Revision(AboutDay.Today, firstRecord.plusDays(1)))),
    )

    @Test fun anUnjudgedFirstChecklistRecordCannotHideLaterJudgedHistory() {
        val checklist = switchingChecklist()
        val ref = TrackerRef(TrackerTypeId("checklist"), checklist.id)
        val judgedDay = firstRecord.plusDays(1)
        val records = listOf(judgedDay, firstRecord).mapIndexed { index, day ->
            ChecklistDay(checklist.id, day.toString(), index.toLong(), setOf("check/task"))
        }
        val state = AppState(checklists = listOf(checklist), checklistDays = records)
        val dates = state.checklistRecordedDates(checklist.id)
        assertNull(checklist.judgmentDayFor(firstRecord))
        assertEquals(judgedDay, checklist.firstJudgmentDay(dates))
        assertEquals(judgedDay, began(state, ref, leaveDay))
        assertEquals(1, habitStreak(state, visibility, judgedDay.plusDays(1)).standings.getValue(ref).nextToken!!.done)
        assertEquals(MoStatus.Kept, MoLedger.of(state, visibility, leaveDay).history(ref)[judgedDay])

        val left = state.applyEdit(checklist.id, ChecklistEdit.LeaveRewards, leaveDay)
        assertEquals(listOf(RewardPeriod(judgedDay, leaveDay.minusDays(1))), left.checklists.single().rewards!!.periods)
        assertEquals(judgedDay, began(left, ref, leaveDay))
        assertEquals(
            habitStreak(state, visibility, leaveDay).history.filter { it.date < leaveDay },
            habitStreak(left, visibility, leaveDay).history.filter { it.date < leaveDay },
        )
        assertEquals(habitStreak(state, visibility, leaveDay.minusDays(1)), habitStreak(left, visibility, leaveDay.minusDays(1)))
        assertEquals(left.checklists.single(), checklist.applyingEdits(listOf(ChecklistEdit.LeaveRewards), leaveDay, dates))
        assertEquals(left, state.applyingEdits(checklist.id, listOf(ChecklistEdit.LeaveRewards), leaveDay))
        assertEquals(records, left.checklistDays)
    }

    @Test fun anUnjudgedAssumedStartDoesNotHideLaterExplicitlyAssumedTasks() {
        val checklist = switchingChecklist().copy(items = listOf(
            ChecklistItem("task", "Task", from = firstRecord, exceptionsOnly = Dated.always(true)),
        ))
        val state = AppState(checklists = listOf(checklist))
        val ref = TrackerRef(TrackerTypeId("checklist"), checklist.id)
        val judgedDay = firstRecord.plusDays(1)
        assertNull(checklist.judgmentDayFor(firstRecord))
        assertEquals(judgedDay, checklist.firstJudgmentDay(emptySet()))
        assertEquals(judgedDay, began(state, ref, leaveDay))
        val left = state.applyEdit(checklist.id, ChecklistEdit.LeaveRewards, leaveDay)
        assertEquals(listOf(RewardPeriod(judgedDay, leaveDay.minusDays(1))), left.checklists.single().rewards!!.periods)
        assertEquals(habitStreak(state, visibility, leaveDay.minusDays(1)), habitStreak(left, visibility, leaveDay.minusDays(1)))
        assertTrue(left.checklistDays.isEmpty())
        assertTrue(MoEvaluation(left, visibility, leaveDay).observations.single { it.ref == ref }.recordedAt.isEmpty())
    }

}
