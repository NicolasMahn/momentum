package de.nicolasmahn.momentum.domain.mo

import de.nicolasmahn.momentum.domain.compatibility.withWeightWeekRule
import de.nicolasmahn.momentum.domain.measurement.MeasurementValuePolicy

import de.nicolasmahn.momentum.domain.LoggedViewKind
import de.nicolasmahn.momentum.domain.LoggedViewRef
import de.nicolasmahn.momentum.domain.TrackerRef
import de.nicolasmahn.momentum.domain.trackerRef
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.checklist.WhenAway
import de.nicolasmahn.momentum.domain.compatibility.CareChecklistId
import de.nicolasmahn.momentum.domain.compatibility.EatingQuestionsId
import de.nicolasmahn.momentum.domain.compatibility.ReadingTimeId
import de.nicolasmahn.momentum.domain.compatibility.SupplementsChecklistId
import de.nicolasmahn.momentum.domain.compatibility.WeightMeasurementId
import de.nicolasmahn.momentum.domain.holiday.DayRange
import de.nicolasmahn.momentum.domain.holiday.HolidaySchedule
import de.nicolasmahn.momentum.domain.measurement.MeasurementEdit
import de.nicolasmahn.momentum.domain.measurement.MeasurementRecord
import de.nicolasmahn.momentum.domain.measurement.applyingMeasurementEdits
import de.nicolasmahn.momentum.domain.measurement.asMeasurementLogs
import de.nicolasmahn.momentum.domain.measurement.recordsOf
import de.nicolasmahn.momentum.domain.measurement.withWeight
import de.nicolasmahn.momentum.domain.mo.compatibility.InheritedMoEvaluation
import de.nicolasmahn.momentum.domain.mo.compatibility.withInheritedTrackerConfigurations
import de.nicolasmahn.momentum.domain.settings.DashboardFeature
import de.nicolasmahn.momentum.domain.settings.FeatureVisibilitySettings
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a saved change to Weight's Streak settings does: it counts from the day it is saved, never
 * re-judges an earlier day or week, and moves no recorded fact. Days are counted from a Monday.
 */
class WeightSettingsTest {
    private val monday = LocalDate.parse("2026-10-05")
    private fun on(day: Int) = monday.plusDays(day.toLong())
    private fun weighed(days: Iterable<Int>) = AppState(measurementLogs = days.map { MeasurementRecord(on(it).toString(), it.toLong(), 80.0 - it * 0.1) }.asMeasurementLogs(WeightMeasurementId)).withWeight()
    private fun AppState.saved(day: Int, vararg edits: MeasurementEdit) = applyingMeasurementEdits(WeightMeasurementId, edits.toList(), on(day))
    private fun away(vararg days: Int) = HolidaySchedule(days.map { DayRange(on(it), on(it)) })
    private val weightRef = LoggedViewRef(LoggedViewKind.Measurement, WeightMeasurementId).trackerRef
    private val onlyWeight = FeatureVisibilitySettings(
        disabledFeatures = setOf(DashboardFeature.Activity, DashboardFeature.Substances, DashboardFeature.Statistics),
        hiddenChecklists = setOf(CareChecklistId, SupplementsChecklistId),
        hiddenQuestions = setOf(EatingQuestionsId),
        hiddenTime = setOf(ReadingTimeId),
    )
    private fun AppState.weekRule(day: Int, maxPasses: Int = 7, days: Int = 6) = withWeightWeekRule(on(day), maxPasses, days)

    /** A recovered historical profile keeps its measurements; current evaluation ignores an unconfigured orphan. */
    @Test fun aRecoveredWeightProfilePreservesLegacyHistoryAndLeavesOrphansStored() {
        val configured = weighed(0..20)
        val orphaned = configured.copy(measurements = emptyList())
        val recovered = orphaned.withInheritedTrackerConfigurations()
        assertEquals(configured.measurementLogs, orphaned.measurementLogs)
        assertEquals(orphaned.measurementLogs, recovered.measurementLogs)
        val orphanMood = moMood(orphaned, onlyWeight, on(21))
        assertEquals(0, orphanMood.dueToday)
        assertTrue(weightRef !in orphanMood.trackerOrder)
        assertTrue(weightRef !in orphanMood.streak.standings)

        (0..21).forEach { today ->
            val legacy = InheritedMoEvaluation(orphaned, onlyWeight, on(today))
            val stored = InheritedMoEvaluation(configured, onlyWeight, on(today))
            val recoveredReport = InheritedMoEvaluation(recovered, onlyWeight, on(today))
            assertEquals("day $today", stored.streak, legacy.streak)
            assertEquals("day $today", stored.mood, legacy.mood)
            assertEquals("day $today", stored.streak, recoveredReport.streak)
            assertEquals("day $today", stored.mood, recoveredReport.mood)
        }
    }

    /**
     * Five weigh-ins in each of two weeks earn nothing while a week takes six. Five days saved on
     * the second week's Wednesday: that week closes by the number of its Sunday and earns, and
     * the first week, closed under six, is not judged again.
     */
    @Test fun aChangedNumberOfDaysCountsFromTheWeekItIsSavedIn() {
        val records = weighed((0..4) + (7..11))
        val changed = records.weekRule(9, days = 5)

        val before = habitStreak(records, onlyWeight, on(14))
        val after = habitStreak(changed, onlyWeight, on(14))
        assertEquals(emptyList<TrackerRef>(), before.earnedTrackersYesterday)
        assertEquals(0, before.standings.getValue(weightRef).passes)
        assertEquals(listOf(weightRef), after.earnedTrackersYesterday)
        assertEquals(1, after.standings.getValue(weightRef).passes)
        // Every day is marked as it was: the number moves what a week earns, not what a day asks.
        assertEquals(before.history, after.history)
        assertEquals(before.days, after.days)

        // On the day before the change, and on the Monday after the first week closed, nothing differs at all.
        listOf(7, 8).forEach { day -> assertEquals("day $day", habitStreak(records, onlyWeight, on(day)), habitStreak(changed, onlyWeight, on(day))) }
        // From the day it is saved the token page shows the new goal for the week it is in.
        assertEquals(MoTokenProgress(3, 6), habitStreak(records, onlyWeight, on(9)).standings.getValue(weightRef).nextToken!!.copy(week = emptyList()))
        assertEquals(MoTokenProgress(3, 5), habitStreak(changed, onlyWeight, on(9)).standings.getValue(weightRef).nextToken!!.copy(week = emptyList()))
    }

    /** A week closes by the number of its Sunday: saved on that Sunday it still counts, saved the Monday after it does not reach back. */
    @Test fun aWeekEarnsByTheNumberOfItsSunday() {
        val records = weighed(0..3)
        assertEquals(emptyList<TrackerRef>(), habitStreak(records, onlyWeight, on(7)).earnedTrackersYesterday)
        assertEquals(listOf(weightRef), habitStreak(records.weekRule(6, days = 4), onlyWeight, on(7)).earnedTrackersYesterday)
        assertEquals(emptyList<TrackerRef>(), habitStreak(records.weekRule(7, days = 4), onlyWeight, on(7)).earnedTrackersYesterday)

        // More days asked from Sunday on: six weigh-ins no longer earn in the week it is saved in.
        val six = weighed(0..5)
        assertEquals(listOf(weightRef), habitStreak(six, onlyWeight, on(7)).earnedTrackersYesterday)
        assertEquals(emptyList<TrackerRef>(), habitStreak(six.weekRule(6, days = 7), onlyWeight, on(7)).earnedTrackersYesterday)
    }

    /**
     * Three full weeks fill the bank to four. A bank of two saved on the fourth week's Thursday
     * holds two from that day, today included, and what was earned or spent before is as it was.
     */
    @Test fun aSmallerBankHoldsNoMoreThanItAllowsFromTheDayItIsSaved() {
        val records = weighed(0..27)
        val smaller = records.weekRule(24, maxPasses = 2)

        assertEquals(4, habitStreak(records, onlyWeight, on(23)).standings.getValue(weightRef).passes)
        assertEquals(habitStreak(records, onlyWeight, on(23)), habitStreak(smaller, onlyWeight, on(23)))
        val thursday = habitStreak(smaller, onlyWeight, on(24))
        assertEquals(2, thursday.standings.getValue(weightRef).passes)
        assertEquals(2, thursday.standings.getValue(weightRef).passCapacity)
        // The fourth week earns its token into a bank that is already full.
        assertEquals(2, habitStreak(smaller, onlyWeight, on(28)).standings.getValue(weightRef).passes)
        assertEquals(emptyList<TrackerRef>(), habitStreak(smaller, onlyWeight, on(28)).earnedTrackersYesterday)
        assertEquals(5, habitStreak(records, onlyWeight, on(28)).standings.getValue(weightRef).passes)
        assertEquals(habitStreak(records, onlyWeight, on(28)).history, habitStreak(smaller, onlyWeight, on(28)).history)

        // A bigger bank has room from its day on and hands nothing out for the weeks before.
        val bigger = weighed(0 until 70).weekRule(69, maxPasses = 9)
        assertEquals(7, habitStreak(weighed(0 until 70), onlyWeight, on(70)).standings.getValue(weightRef).passes)
        assertEquals(8, habitStreak(bigger, onlyWeight, on(70)).standings.getValue(weightRef).passes)
        assertEquals(9, habitStreak(bigger, onlyWeight, on(70)).standings.getValue(weightRef).passCapacity)
    }

    /** The number of days is what a week earns by, never what it asks: the week still counts toward seven, and the week-goal moment still needs seven. */
    @Test fun theDailyWeekTargetDoesNotFollowTheRewardEarningThreshold() {
        val fourDays = weighed(0..3).weekRule(0, days = 4)
        val thursday = moMood(fourDays, onlyWeight, on(3))
        assertEquals(MoWeekProgress(4, 7), thursday.trackerWeekProgress[weightRef])
        assertFalse(MoEvent.WeekGoalReached(weightRef) in moEvents(thursday))
        assertEquals(4, thursday.streak.standings.getValue(weightRef).nextToken!!.goal)
        // Friday without a weigh-in is still a day Weight was asked for.
        assertEquals(listOf(weightRef), moMood(fourDays, onlyWeight, on(4)).missingTrackersToday)
        assertEquals(MoDayMark.Protected, habitStreak(fourDays, onlyWeight, on(5)).history.single { it.date == on(4) }.mark)

        val sunday = moMood(weighed(0..6).weekRule(0, days = 4), onlyWeight, on(6))
        assertEquals(MoWeekProgress(7, 7), sunday.trackerWeekProgress[weightRef])
        assertFalse(MoEvent.WeekGoalReached(weightRef) in moEvents(sunday))
        // Past its goal the ribbon stays full instead of counting on.
        assertEquals(MoTokenProgress(4, 4), sunday.streak.standings.getValue(weightRef).nextToken!!.copy(week = emptyList()))
    }



    /**
     * Still due saved on the second week's Monday. That week's away Wednesday with a weigh-in is a
     * kept day that earns, and its away Thursday without one is a gap; the first week's away
     * Wednesday, judged while Weight was excused, stays as it was.
     */
    @Test fun stillDueOnHolidayMakesAnAwayDayAnOrdinaryDayFromTheDayItIsSaved() {
        val records = weighed(listOf(0, 1, 3, 4, 5, 6) + listOf(7, 8, 9, 11, 12, 13))
        val holidays = away(2, 9, 10)
        val due = records.saved(7, MeasurementEdit.SetWhenAway(WhenAway.Due))

        val excused = habitStreak(records, onlyWeight, on(14), holidays)
        val stillDue = habitStreak(due, onlyWeight, on(14), holidays)
        fun MoStreak.mark(day: Int) = history.single { it.date == on(day) }
        // Excused, the second week has five kept days and a Thursday without a gap.
        assertEquals(MoDayMark.NothingDue, excused.mark(10).mark)
        assertEquals(emptyList<TrackerRef>(), excused.earnedTrackersYesterday)
        assertEquals(2, excused.standings.getValue(weightRef).passes)
        // Still due, Wednesday's weigh-in is the sixth kept day and Thursday takes a token.
        assertEquals(MoDayRecord(on(10), MoDayMark.Protected, coveredTrackers = setOf(weightRef)), stillDue.mark(10))
        assertEquals(listOf(weightRef), stillDue.earnedTrackersYesterday)
        assertEquals(2, stillDue.standings.getValue(weightRef).passes)
        // No earlier away day is judged again: the first week reads the same to the day.
        assertEquals(excused.history.filter { it.date < on(7) }, stillDue.history.filter { it.date < on(7) })
        assertEquals(habitStreak(records, onlyWeight, on(6), holidays), habitStreak(due, onlyWeight, on(6), holidays))

        // Mo's ledger follows: the away days of the second week are judged, the first week's is left out.
        val ledger = MoLedger.of(due, onlyWeight, on(14), holidays).history(weightRef)
        assertNull(ledger[on(2)])
        assertEquals(MoStatus.Kept, ledger[on(9)])
        assertEquals(MoStatus.Missed, ledger[on(10)])
        assertNull(MoLedger.of(records, onlyWeight, on(14), holidays).history(weightRef)[on(9)])

        // The week's ribbon and today's question follow too.
        val friday = habitStreak(due, onlyWeight, on(11), holidays).standings.getValue(weightRef).nextToken!!
        assertEquals(listOf(MoWeekDay.Kept, MoWeekDay.Kept, MoWeekDay.Kept, MoWeekDay.Covered, MoWeekDay.Kept), friday.week.take(5))
        assertEquals(listOf(MoWeekDay.Kept, MoWeekDay.Kept, MoWeekDay.Rested, MoWeekDay.Rested, MoWeekDay.Kept), habitStreak(records, onlyWeight, on(11), holidays).standings.getValue(weightRef).nextToken!!.week.take(5))
        assertEquals(listOf(weightRef), moMood(due, onlyWeight, on(10), holidays).missingTrackersToday)
        assertEquals(0, moMood(records, onlyWeight, on(10), holidays).dueToday)

        // Taken back a week later, the days in between keep the rule they were judged by.
        val excusedAgain = due.saved(14, MeasurementEdit.SetWhenAway(WhenAway.Excused))
        assertEquals(stillDue.history.dropLast(1), habitStreak(excusedAgain, onlyWeight, on(14), holidays).history.dropLast(1))
    }

    /** What Mo says after a token names the number the week was closed with, also once today's number is another. */
    @Test fun theMoodCarriesTheNumberOfTheClosingSunday() {
        val closedWithFour = weighed(0..3).weekRule(2, days = 4)
        val changedOnMonday = closedWithFour.weekRule(7, days = 2)
        val mood = moMood(changedOnMonday, onlyWeight, on(7))
        assertEquals(listOf(weightRef), mood.streak.earnedTrackersYesterday)
        assertEquals(4, mood.tokenEarningBasisByTracker[weightRef])
        assertEquals(2, changedOnMonday.measurements.single { it.id == WeightMeasurementId }.weekRewards!!.keptDaysInWeek.on(on(7)))
        assertEquals(2, mood.streak.standings.getValue(weightRef).nextToken!!.goal)
        assertEquals(6, moMood(weighed(0..5), onlyWeight, on(7)).tokenEarningBasisByTracker[weightRef])
    }

    /** No setting here is a record: the weigh-ins are the same whatever is saved. */
    @Test fun noSettingChangesARecord() {
        val records = weighed((0..20).filter { it % 4 != 0 })
        val changed = records.weekRule(3, maxPasses = 1, days = 1).saved(3, MeasurementEdit.SetWhenAway(WhenAway.Due), MeasurementEdit.Rename("Scale"))
            .weekRule(10, maxPasses = 9, days = 7).saved(10, MeasurementEdit.SetWhenAway(WhenAway.Excused))
        assertEquals(records.recordsOf(WeightMeasurementId), changed.recordsOf(WeightMeasurementId))
        assertEquals(records.copy(measurements = changed.measurements), changed)
        // The rewards really moved, so the comparison is not between equals.
        assertTrue(habitStreak(records, onlyWeight, on(21), away(5, 12)) != habitStreak(changed, onlyWeight, on(21), away(5, 12)))
    }
}
