package com.example.workouttracker.domain.mo

import com.example.workouttracker.domain.compatibility.obligations

import com.example.workouttracker.domain.compatibility.missed

import com.example.workouttracker.domain.compatibility.kept

import com.example.workouttracker.domain.compatibility.history

import com.example.workouttracker.domain.compatibility.mood

import com.example.workouttracker.domain.compatibility.MoNeed

import com.example.workouttracker.domain.settings.showingOnly
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.CheckpointPart
import com.example.workouttracker.domain.trackerRef
import com.example.workouttracker.domain.compatibility.CareChecklistId
import com.example.workouttracker.domain.compatibility.LegacyCareSession
import com.example.workouttracker.domain.compatibility.careChecklist
import com.example.workouttracker.domain.care.careState
import com.example.workouttracker.domain.checklist.ChecklistEdit
import com.example.workouttracker.domain.checklist.CheckpointTime
import com.example.workouttracker.domain.checklist.applyEdit
import com.example.workouttracker.domain.checklist.checklist
import com.example.workouttracker.domain.checklist.tickAllDue
import com.example.workouttracker.domain.checklist.tickAllDueAt
import com.example.workouttracker.domain.reminder.ReminderSettings
import com.example.workouttracker.domain.compatibility.SupplementsChecklistId
import com.example.workouttracker.domain.supplement.supplementsState
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import com.example.workouttracker.domain.holiday.HolidaySchedule

/**
 * Supplements and Care edited past the shape they were built with: every checkpoint they are given
 * is judged, reminded of and waited for like their own, and a day they ask nothing is not judged.
 */
class FeatureChecklistEditsTest {
    private val monday = LocalDate.parse("2026-10-05")
    private fun day(n: Long) = monday.plusDays(n)
    private fun only(need: MoNeed) = showingOnly(need)
    private val careRef = MoNeed.Care.view.trackerRef
    private val supplementsRef = MoNeed.Supplements.view.trackerRef

    /** Every day from [from] to [until] ticked in full, but [missed]. */
    private fun AppState.keptEveryDay(id: String, from: Long, until: Long, vararg missed: Long): AppState =
        (from..until).filterNot { it in missed }.fold(this) { state, n -> tickAllDue(state, state.checklist(id)!!, day(n).toString(), n + 100, HolidaySchedule()) }

    @Test fun twoMissedCheckpointsOfSupplementsAreTwoGaps() {
        val withEvening = supplementsState().applyEdit(
            SupplementsChecklistId,
            ChecklistEdit.AddCheckpoint("cpt_eve", "Evening", CheckpointTime.Fixed(LocalTime.of(20, 0)), remind = false),
            monday,
        ).keptEveryDay(SupplementsChecklistId, 0, 20, 12)

        val streak = habitStreak(withEvening, only(MoNeed.Supplements), day(13))
        assertEquals(MoDayMark.Protected, streak.history.single { it.date == day(12) }.mark)
        // A pass to start and one for the seventh kept day, both spent on the day's two gaps.
        assertEquals(0, streak.standings.getValue(supplementsRef).passes)
    }

    @Test fun aDayCareAsksNothingIsNotJudged() {
        val kept = careState().keptEveryDay(CareChecklistId, 0, 9)
        // A checklist keeps an item; what stays is due on Saturdays only, so Thursday and Friday ask nothing.
        val emptied = listOf("toothbrushing", "hair_foam")
            .fold(kept) { state, item -> state.applyEdit(CareChecklistId, ChecklistEdit.RemoveItem(item), day(10)) }

        val streak = habitStreak(emptied, only(MoNeed.Care), day(12))
        assertEquals(listOf(MoDayMark.NothingDue, MoDayMark.NothingDue), streak.history.filter { it.date >= day(10) && it.date < day(12) }.map { it.mark })
        assertFalse(careRef in moMood(emptied, only(MoNeed.Care), day(11)).missingTrackersToday)
    }

    @Test fun supplementsLeavingRewardsAreNotJudgedAfterAndKeepTheirBankOutOfSight() {
        val left = supplementsState().keptEveryDay(SupplementsChecklistId, 0, 20, 12)
            .applyEdit(SupplementsChecklistId, ChecklistEdit.LeaveRewards, day(10))

        val streak = habitStreak(left, only(MoNeed.Supplements), day(14))
        assertEquals(MoDayMark.NothingDue, streak.history.single { it.date == day(12) }.mark)
        assertEquals(null, streak.standings[supplementsRef])
        assertEquals(
            habitStreak(supplementsState().keptEveryDay(SupplementsChecklistId, 0, 20, 12), only(MoNeed.Supplements), day(14))
                .history.filter { it.date < day(10) },
            streak.history.filter { it.date < day(10) },
        )
    }

    /** Mo waits for a checkpoint Care was given at its own time, once Care's sessions are done. */
    @Test fun moWaitsForACheckpointGivenToCare() {
        val noon = CheckpointTime.Fixed(LocalTime.NOON)
        val withNoon = careState()
            .applyEdit(CareChecklistId, ChecklistEdit.AddCheckpoint("cpt_noon", "Noon", noon, remind = true), day(0))
        val sessionsDone = LegacyCareSession.entries.fold(withNoon) { state, session ->
            tickAllDueAt(state, state.careChecklist!!, day(0).toString(), session.storageId, 1L, HolidaySchedule())
        }

        val mood = moMood(sessionsDone, only(MoNeed.Care), day(0))
        assertEquals(listOf(CheckpointPart("cpt_noon")), mood.trackerStillOpen(careRef))
        assertEquals(listOf(MoObligation(CheckpointPart("cpt_noon"), LocalTime.NOON)), mood.trackerObligations(ReminderSettings()).getValue(careRef))
    }

    /** Supplements given a second checkpoint is waited for at each one's own time, not at the check-in. */
    @Test fun eachCheckpointOfSupplementsIsWaitedForAtItsTime() {
        // Kept the days before, so Mo already counts on it.
        val twoTimes = supplementsState(remind = true).keptEveryDay(SupplementsChecklistId, -3, -1)
            .applyEdit(SupplementsChecklistId, ChecklistEdit.AddCheckpoint("cpt_eve", "Evening", CheckpointTime.Fixed(LocalTime.of(20, 0)), remind = true), day(0))

        val mood = moMood(twoTimes, only(MoNeed.Supplements), day(0))
        assertEquals(
            listOf(MoObligation(CheckpointPart("midday"), LocalTime.of(12, 30)), MoObligation(CheckpointPart("cpt_eve"), LocalTime.of(20, 0))),
            mood.trackerObligations(ReminderSettings()).getValue(supplementsRef),
        )
    }
}
