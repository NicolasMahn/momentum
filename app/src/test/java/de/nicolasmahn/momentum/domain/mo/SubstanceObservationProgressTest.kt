package de.nicolasmahn.momentum.domain.mo

import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.DayProgress
import de.nicolasmahn.momentum.domain.SubstancesTrackerRef
import de.nicolasmahn.momentum.domain.checklist.ChecklistRewards
import de.nicolasmahn.momentum.domain.checklist.Dated
import de.nicolasmahn.momentum.domain.checklist.RewardPeriod
import de.nicolasmahn.momentum.domain.holiday.HolidaySchedule
import de.nicolasmahn.momentum.domain.settings.FeatureVisibilitySettings
import de.nicolasmahn.momentum.domain.substance.SubstanceGoal
import de.nicolasmahn.momentum.domain.substance.SubstanceKind
import de.nicolasmahn.momentum.domain.substance.SubstanceLog
import de.nicolasmahn.momentum.domain.substance.SubstanceNoneDay
import de.nicolasmahn.momentum.domain.substance.SubstanceSettings
import de.nicolasmahn.momentum.feature.substances.substanceHistoryWindow
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubstanceObservationProgressTest {
    private val today = LocalDate.parse("2026-10-25")

    /** Substances is about yesterday: today judges this day's record. */
    private val asked = today.minusDays(1)
    private val dayBefore = asked.minusDays(1)
    private val ref = SubstancesTrackerRef
    private val visibility = FeatureVisibilitySettings()
    private val kind = SubstanceKind.CANNABIS
    private val settings = SubstanceSettings(
        tracked = setOf(kind.id), stayingAway = Dated.always(setOf(kind.id)),
        rewards = ChecklistRewards(3, 2, periods = listOf(RewardPeriod(dayBefore))),
        mo = TrackerMoPolicy(true),
    )
    private val use = SubstanceLog("use", kind.id, asked.toString(), 2, 1.5, kind.unit)
    private val none = SubstanceNoneDay(asked.toString(), 2)

    private fun state(goal: SubstanceGoal, exceptionsOnly: Boolean = true) = AppState(
        substances = settings.copy(goal = Dated.always(goal), exceptionsOnly = Dated.always(exceptionsOnly)),
        substanceNoneDays = listOf(SubstanceNoneDay(dayBefore.toString(), 1)),
    )

    private fun evaluate(state: AppState) = MoEvaluation(state, visibility, today)
    private fun facts(state: AppState) = substanceHistoryWindow(
        state.substanceLogs, today.toString(), state.substances, state.substanceNoneDays,
    ).let { it.days to it.statuses }

    @Test fun aLoggingUseIsAnActualCompletedPart() {
        val recorded = state(SubstanceGoal.Logging).copy(substanceLogs = listOf(use))
        val before = facts(recorded)
        val evaluation = evaluate(recorded)
        assertEquals(DayProgress(1, 1), evaluation.todayProgress().getValue(ref))
        assertTrue(ref in evaluation.moodWithTrackerChoices(emptySet()).keptTrackersToday)
        assertEquals(before, facts(recorded))
        assertEquals(listOf(use), recorded.substanceLogs)
        assertTrue(recorded.substanceNoneDays.none { it.dateKey == asked.toString() })
    }

    @Test fun anExplicitLoggingNoneIsAnActualCompletedPart() {
        val recorded = state(SubstanceGoal.Logging).let { it.copy(substanceNoneDays = it.substanceNoneDays + none) }
        val evaluation = evaluate(recorded)
        assertEquals(DayProgress(1, 1), evaluation.todayProgress().getValue(ref))
        assertTrue(ref in evaluation.moodWithTrackerChoices(emptySet()).keptTrackersToday)
        assertTrue(facts(recorded).first.single { it.dateKey == asked.toString() }.none)
        assertEquals(none, recorded.substanceNoneDays.last())
    }

    @Test fun aForbiddenUseIsRecordedButNeverACompletedOrKeptPart() {
        val recorded = state(SubstanceGoal.StayingAway).copy(substanceLogs = listOf(use))
        val before = facts(recorded)
        val evaluation = evaluate(recorded)
        val mood = evaluation.moodWithTrackerChoices(emptySet())
        assertEquals(DayProgress(0, 1), evaluation.todayProgress().getValue(ref))
        assertFalse(evaluation.todayProgress().getValue(ref).isComplete)
        assertTrue(ref in mood.missingTrackersToday)
        assertFalse(ref in mood.keptTrackersToday)
        val target = evaluation.observations.single { it.ref == ref }.target as ObservationTarget.Daily
        assertFalse(target.keptOn(today))
        assertEquals(MoStatus.Missed, MoLedger.of(recorded, visibility, today, HolidaySchedule()).history(ref)[today])
        assertEquals(before, facts(recorded))
        assertEquals(listOf(use), facts(recorded).first.single { it.dateKey == asked.toString() }.logs)
        assertTrue(recorded.substanceNoneDays.none { it.dateKey == asked.toString() })
    }

    @Test fun anExceptionOnlyDayWithNoRecordIsKeptByAssumptionAndInventsNoNoneRecord() {
        val emptyToday = state(SubstanceGoal.StayingAway)
        val before = facts(emptyToday)
        val evaluation = evaluate(emptyToday)
        val progress = evaluation.todayProgress().getValue(ref)
        assertEquals(DayProgress(1, 1, assumed = true), progress)
        assertTrue(ref in evaluation.moodWithTrackerChoices(emptySet()).keptTrackersToday)
        assertNull(evaluation.moodWithTrackerChoices(emptySet()).latestTrackerToday)
        assertTrue(emptyToday.substanceLogs.isEmpty())
        assertEquals(listOf(SubstanceNoneDay(dayBefore.toString(), 1)), emptyToday.substanceNoneDays)
        val actual = facts(emptyToday).first.single { it.dateKey == asked.toString() }
        assertTrue(actual.logs.isEmpty())
        assertFalse(actual.none)
        assertEquals(before, facts(emptyToday))
    }

    @Test fun explicitNoneWhileStayingAwayIsBothFactualAndKeptWithoutAssumption() {
        val recorded = state(SubstanceGoal.StayingAway).let { it.copy(substanceNoneDays = it.substanceNoneDays + none) }
        val before = facts(recorded)
        val evaluation = evaluate(recorded)
        assertEquals(DayProgress(1, 1, assumed = false), evaluation.todayProgress().getValue(ref))
        assertTrue(ref in evaluation.moodWithTrackerChoices(emptySet()).keptTrackersToday)
        assertTrue(facts(recorded).first.single { it.dateKey == asked.toString() }.none)
        assertEquals(before, facts(recorded))
        assertEquals(none, recorded.substanceNoneDays.last())
    }

    @Test fun requiringALogLeavesAnUnrecordedStayingAwayDayOpen() {
        val emptyToday = state(SubstanceGoal.StayingAway, exceptionsOnly = false)
        val evaluation = evaluate(emptyToday)
        assertEquals(DayProgress(0, 1), evaluation.todayProgress().getValue(ref))
        assertTrue(ref in evaluation.moodWithTrackerChoices(emptySet()).missingTrackersToday)
        assertFalse(ref in evaluation.moodWithTrackerChoices(emptySet()).keptTrackersToday)
        assertTrue(emptyToday.substanceNoneDays.none { it.dateKey == asked.toString() })
    }

    /** The walk judges a bank from the day it joined, records or none, so an unstarted day is open for Mo too. */
    @Test fun anUnstartedLoggingTrackerInTheStreakIsOpenWithTheFlagOff() {
        val unstarted = AppState(substances = settings.copy(mo = TrackerMoPolicy(false)))
        val evaluation = evaluate(unstarted)
        assertEquals(DayProgress(0, 1), evaluation.todayProgress()[ref])
        assertTrue(ref in evaluation.moodWithTrackerChoices(emptySet()).missingTrackersToday)
        assertFalse(ref in evaluation.moodWithTrackerChoices(emptySet()).keptTrackersToday)
        // Outside the streak nothing is asked, and nothing is shown.
        assertNull(evaluate(AppState(substances = settings.copy(mo = TrackerMoPolicy(false), rewards = null))).todayProgress()[ref])
        assertTrue(unstarted.substanceLogs.isEmpty())
        assertTrue(unstarted.substanceNoneDays.isEmpty())
    }

    @Test fun goalsAndProtectionNeverChangeTheFactualStatisticsOfARecordedUse() {
        val away = state(SubstanceGoal.StayingAway).copy(substanceLogs = listOf(use))
        val logging = away.copy(substances = away.substances.copy(goal = Dated.always(SubstanceGoal.Logging)))
        val before = facts(away)
        assertEquals(before, facts(logging))
        val reserved = evaluate(away).moodWithTrackerChoices(setOf(ref))
        assertTrue(ref in reserved.chosenTrackersToday)
        assertFalse(ref in reserved.keptTrackersToday)
        assertEquals(before, facts(away))
        assertEquals(away.substanceLogs, logging.substanceLogs)
        assertEquals(away.substanceNoneDays, logging.substanceNoneDays)
        assertEquals(listOf(use), away.substanceLogs)
        assertTrue(away.substanceNoneDays.none { it.dateKey == asked.toString() })
    }
}
