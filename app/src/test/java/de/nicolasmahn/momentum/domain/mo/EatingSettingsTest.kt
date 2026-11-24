package de.nicolasmahn.momentum.domain.mo

import de.nicolasmahn.momentum.domain.LoggedViewKind
import de.nicolasmahn.momentum.domain.LoggedViewRef
import de.nicolasmahn.momentum.domain.TrackerRef
import de.nicolasmahn.momentum.domain.trackerRef
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.checklist.WhenAway
import de.nicolasmahn.momentum.domain.compatibility.CareChecklistId
import de.nicolasmahn.momentum.domain.holiday.DayRange
import de.nicolasmahn.momentum.domain.holiday.HolidaySchedule
import de.nicolasmahn.momentum.domain.compatibility.EatingQuestionsId
import de.nicolasmahn.momentum.domain.compatibility.ReadingTimeId
import de.nicolasmahn.momentum.domain.compatibility.SupplementsChecklistId
import de.nicolasmahn.momentum.domain.compatibility.WeightMeasurementId
import de.nicolasmahn.momentum.domain.mo.compatibility.InheritedMoEvaluation
import de.nicolasmahn.momentum.domain.mo.compatibility.withInheritedTrackerConfigurations
import de.nicolasmahn.momentum.domain.settings.DashboardFeature
import de.nicolasmahn.momentum.domain.settings.FeatureVisibilitySettings
import de.nicolasmahn.momentum.domain.questions.QuestionsEdit
import de.nicolasmahn.momentum.domain.questions.applyingQuestionsEdits
import de.nicolasmahn.momentum.domain.questions.withEating
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import de.nicolasmahn.momentum.domain.questions.testEatingQuestions
import de.nicolasmahn.momentum.domain.reminder.ReminderSettings
import de.nicolasmahn.momentum.domain.reminder.ReminderTime
import java.time.LocalTime
import org.junit.Test

/**
 * Eating in the walk reads its bank's two numbers and its holiday rule from its configuration, by
 * the day in question. With what is stored for it every day is judged as the code judged it, and a
 * saved change counts from the day it is saved: it never re-judges an earlier day and moves no
 * recorded fact. Days are counted from a Monday, and a day is kept by the reflection about the day
 * before.
 */
class EatingSettingsTest {
    private val monday = LocalDate.parse("2026-10-05")
    private fun on(day: Int) = monday.plusDays(day.toLong())

    /** Reflections that keep each of [days]: one about the day before each, written in time. */
    private fun kept(days: Iterable<Int>) = AppState(questionEntries = days.map { reflectionAbout(on(it - 1)) }).withEating()
    private fun AppState.saved(day: Int, vararg edits: QuestionsEdit) = applyingQuestionsEdits(EatingQuestionsId, edits.toList(), on(day))
    private fun away(vararg days: Int) = HolidaySchedule(days.map { DayRange(on(it), on(it)) })
    private val eatingRef = LoggedViewRef(LoggedViewKind.Questions, EatingQuestionsId).trackerRef
    private val onlyEating = FeatureVisibilitySettings(
        disabledFeatures = setOf(DashboardFeature.Activity, DashboardFeature.Substances, DashboardFeature.Statistics),
        hiddenChecklists = setOf(CareChecklistId, SupplementsChecklistId),
        hiddenMeasurements = setOf(WeightMeasurementId),
        hiddenTime = setOf(ReadingTimeId),
    )
    private fun rules(maxPasses: Int = 3, days: Int = 7) = QuestionsEdit.SetRewardRules(maxPasses, days)

    /** A recovered historical profile keeps its reflections; current evaluation ignores an unconfigured orphan. */
    @Test fun aRecoveredEatingProfilePreservesLegacyHistoryAndLeavesOrphansStored() {
        val configured = kept((0..12).filter { it != 2 && it != 9 })
        val orphaned = configured.copy(questions = emptyList())
        val recovered = orphaned.withInheritedTrackerConfigurations()
        assertEquals(configured.questionEntries, orphaned.questionEntries)
        assertEquals(orphaned.questionEntries, recovered.questionEntries)
        val orphanMood = moMood(orphaned, onlyEating, on(14))
        assertEquals(0, orphanMood.dueToday)
        assertTrue(eatingRef !in orphanMood.trackerOrder)
        assertTrue(eatingRef !in orphanMood.streak.standings)
        listOf(HolidaySchedule(), away(2, 8), HolidaySchedule().withSickDays(DayRange(on(9), on(9)))).forEach { holidays ->
            (0..14).forEach { today ->
                val legacy = InheritedMoEvaluation(orphaned, onlyEating, on(today), holidays)
                val stored = InheritedMoEvaluation(configured, onlyEating, on(today), holidays)
                val recoveredReport = InheritedMoEvaluation(recovered, onlyEating, on(today), holidays)
                assertEquals("day $today", stored.streak, legacy.streak)
                assertEquals("day $today", stored.mood, legacy.mood)
                assertEquals("day $today", stored.streak, recoveredReport.streak)
                assertEquals("day $today", stored.mood, recoveredReport.mood)
            }
        }
        // The default is what the code held: three tokens, one for seven kept days, still due away.
        val streak = habitStreak(configured, onlyEating, on(3), away(2))
        assertEquals(3, streak.standings.getValue(eatingRef).passCapacity)
        assertEquals(7, streak.standings.getValue(eatingRef).nextToken!!.goal)
        assertEquals(MoDayRecord(on(2), MoDayMark.Protected, coveredTrackers = setOf(eatingRef)), streak.history.single { it.date == on(2) })
    }

    /**
     * Eating is due at its checkpoint, fifteen minutes after waking, while its reminder is on, and
     * at the check-in's time while it is off.
     */
    @Test fun mosDueTimeForEatingFollowsItsCheckpointOrTheCheckIn() {
        val records = kept(listOf(1))
        val wakingAtHalfPastSix = ReminderSettings(fallbackAlarmTime = ReminderTime(6, 30))
        val reminded = moMood(records.withEating(testEatingQuestions().let { it.copy(checkpoint = it.checkpoint.copy(remind = true)) }), onlyEating, on(2))
        assertEquals(listOf(eatingRef), reminded.missingTrackersToday)
        assertEquals(listOf(LocalTime.of(6, 45)), reminded.trackerObligations(wakingAtHalfPastSix).getValue(eatingRef).map { it.since })
        assertEquals(emptyList<TrackerRef>(), moOverdueTrackers(reminded, wakingAtHalfPastSix, LocalTime.of(7, 44)))
        assertEquals(listOf(eatingRef), moOverdueTrackers(reminded, wakingAtHalfPastSix, LocalTime.of(7, 45)))

        val unreminded = moMood(records, onlyEating, on(2))
        assertEquals(listOf(LocalTime.of(20, 0)), unreminded.trackerObligations(wakingAtHalfPastSix).getValue(eatingRef).map { it.since })
        assertEquals(emptyList<TrackerRef>(), moOverdueTrackers(unreminded, wakingAtHalfPastSix, LocalTime.of(20, 59)))
        assertEquals(listOf(eatingRef), moOverdueTrackers(unreminded, wakingAtHalfPastSix, LocalTime.of(21, 0)))
        val orphaned = records.copy(questions = emptyList())
        val ignoredOrphan = moMood(orphaned, onlyEating, on(2))
        assertEquals(0, ignoredOrphan.dueToday)
        assertTrue(eatingRef !in ignoredOrphan.trackerOrder)
        assertTrue(eatingRef !in ignoredOrphan.streak.standings)
        assertEquals(records.questionEntries, orphaned.questionEntries)
    }

    /**
     * A token for every three kept days, saved on the second week's Wednesday. The seven days
     * before it earned one token under seven and are not counted again; the two kept days carried
     * into Wednesday make three with it, so Wednesday earns the next.
     */
    @Test fun aChangedNumberOfKeptDaysCountsFromTheDayItIsSaved() {
        val records = kept(0..13)
        val changed = records.saved(9, rules(days = 3))

        // Up to the day before, nothing differs at all.
        (0..8).forEach { day -> assertEquals("day $day", habitStreak(records, onlyEating, on(day)), habitStreak(changed, onlyEating, on(day))) }
        // On the day it is saved the bank is what it was, and the token page shows the new goal.
        assertEquals(2, habitStreak(changed, onlyEating, on(9)).standings.getValue(eatingRef).passes)
        assertEquals(MoTokenProgress(3, 7), habitStreak(records, onlyEating, on(9)).standings.getValue(eatingRef).nextToken!!)
        assertEquals(MoTokenProgress(3, 3), habitStreak(changed, onlyEating, on(9)).standings.getValue(eatingRef).nextToken!!)

        val before = habitStreak(records, onlyEating, on(10))
        val after = habitStreak(changed, onlyEating, on(10))
        assertEquals(emptyList<TrackerRef>(), before.earnedTrackersYesterday)
        assertEquals(2, before.standings.getValue(eatingRef).passes)
        assertEquals(listOf(eatingRef), after.earnedTrackersYesterday)
        assertEquals(3, after.standings.getValue(eatingRef).passes)
        // Every day is marked as it was: the number moves what kept days earn, not what a day asks.
        assertEquals(habitStreak(records, onlyEating, on(14)).history, habitStreak(changed, onlyEating, on(14)).history)
        assertEquals(before.days, after.days)
    }

    /**
     * Two weeks fill the bank to three. A bank of one saved on the third week's Wednesday holds one
     * from that day, today included, and what was earned before is as it was.
     */
    @Test fun aSmallerBankHoldsNoMoreThanItAllowsFromTheDayItIsSaved() {
        val records = kept(0..27)
        val smaller = records.saved(16, rules(maxPasses = 1))

        assertEquals(3, habitStreak(records, onlyEating, on(15)).standings.getValue(eatingRef).passes)
        assertEquals(habitStreak(records, onlyEating, on(15)), habitStreak(smaller, onlyEating, on(15)))
        val wednesday = habitStreak(smaller, onlyEating, on(16))
        assertEquals(1, wednesday.standings.getValue(eatingRef).passes)
        assertEquals(1, wednesday.standings.getValue(eatingRef).passCapacity)
        // The third week's seven days earn their token into a bank that is already full.
        assertEquals(1, habitStreak(smaller, onlyEating, on(21)).standings.getValue(eatingRef).passes)
        assertEquals(emptyList<TrackerRef>(), habitStreak(smaller, onlyEating, on(21)).earnedTrackersYesterday)
        assertEquals(habitStreak(records, onlyEating, on(21)).history, habitStreak(smaller, onlyEating, on(21)).history)

        // A bigger bank has room from its day on and hands nothing out for the weeks before.
        val bigger = records.saved(20, rules(maxPasses = 5))
        assertEquals(3, habitStreak(records, onlyEating, on(21)).standings.getValue(eatingRef).passes)
        assertEquals(4, habitStreak(bigger, onlyEating, on(21)).standings.getValue(eatingRef).passes)
        assertEquals(5, habitStreak(bigger, onlyEating, on(21)).standings.getValue(eatingRef).passCapacity)
        assertEquals(listOf(eatingRef), habitStreak(bigger, onlyEating, on(21)).earnedTrackersYesterday)
    }

    /**
     * Not due saved on the second week's Monday. That week's away Wednesday without a reflection
     * is no gap and stays in the walk, as an excused day of Weight does; the first week's away
     * Wednesday, judged while Eating was still due, stays the gap it was.
     */
    @Test fun notDueOnHolidayExcusesTheConfiguredRecordDayFromTheRevisionDay() {
        val records = kept(0..13)
        val holidays = away(2, 9)
        val excused = records.saved(7, QuestionsEdit.SetWhenAway(WhenAway.Excused))
        val before = habitStreak(records, onlyEating, on(14), holidays)
        val after = habitStreak(excused, onlyEating, on(14), holidays)
        assertEquals(before.history.filter { it.date < on(7) }, after.history.filter { it.date < on(7) })
        // Due day 10 asks for record day 9, which the dated policy now excuses.
        assertEquals(MoDayMark.NothingDue, after.history.single { it.date == on(10) }.mark)
        assertEquals(MoDayMark.Perfect, before.history.single { it.date == on(10) }.mark)
        assertEquals(records.questionEntries, excused.questionEntries)
    }

    /**
     * The rule read is the one of the day the reflection is due on, and the away day excused is
     * that day too, never the day it is about. A reflection written for an excused day is a record
     * and no kept day, as a weigh-in on an excused day is.
     */
    @Test fun theHolidayRuleIsReadForTheRecordDayOfALookBackTracker() {
        val records = kept(0..6).saved(0, QuestionsEdit.SetWhenAway(WhenAway.Excused))
        val holidays = away(2)
        val streak = habitStreak(records, onlyEating, on(7), holidays)
        // Day 2 asks about healthy day 1; day 3 asks about away day 2.
        assertEquals(MoDayMark.Perfect, streak.history.single { it.date == on(2) }.mark)
        assertEquals(MoDayMark.NothingDue, streak.history.single { it.date == on(3) }.mark)
        val history = MoLedger.of(records, onlyEating, on(7), holidays).history(eatingRef)
        assertEquals(MoStatus.Kept, history[on(2)])
        assertNull(history[on(3)])
        assertEquals(7, records.questionEntries.size)
    }



    /** What Mo says after a token names the number it was earned by, also once today's number is another. */
    @Test fun theMoodCarriesTheNumberTheTokenWasEarnedBy() {
        val earnedByThree = kept(0..2).saved(0, rules(days = 3))
        val changedToday = earnedByThree.saved(3, rules(days = 5))
        val mood = moMood(changedToday, onlyEating, on(3))
        assertEquals(listOf(eatingRef), mood.streak.earnedTrackersYesterday)
        assertEquals(3, mood.tokenEarningBasisByTracker[eatingRef])
        assertEquals(5, changedToday.questions.single { it.id == EatingQuestionsId }.rewards!!.everyKeptDays.on(on(3)))
        assertEquals(5, mood.streak.standings.getValue(eatingRef).nextToken!!.goal)
        assertEquals(7, moMood(kept(0..6), onlyEating, on(7)).tokenEarningBasisByTracker[eatingRef])
    }

    /** The week still counts toward seven: the number of days is what earns a token, never what a week asks. */
    @Test fun theWeeksSevenDoesNotFollowTheNumberOfDays() {
        val thursday = moMood(kept(0..3).saved(0, rules(days = 2)), onlyEating, on(3))
        assertEquals(MoWeekProgress(4, 7), thursday.trackerWeekProgress[eatingRef])
        assertEquals(2, thursday.streak.standings.getValue(eatingRef).nextToken!!.goal)
    }

    /** No setting here is a record: the reflections are the same whatever is saved. */
    @Test fun noSettingChangesARecord() {
        val records = kept((0..20).filter { it % 4 != 0 })
        val changed = records.saved(3, rules(maxPasses = 1, days = 1), QuestionsEdit.SetWhenAway(WhenAway.Excused), QuestionsEdit.Rename("Food"))
            .saved(10, rules(maxPasses = 9, days = 7), QuestionsEdit.SetWhenAway(WhenAway.Due))
        assertEquals(records.questionEntries.filter { it.viewId == EatingQuestionsId }, changed.questionEntries.filter { it.viewId == EatingQuestionsId })
        assertEquals(records.copy(questions = changed.questions), changed)
        // The rewards really moved, so the comparison is not between equals.
        assertTrue(habitStreak(records, onlyEating, on(21), away(5, 12)) != habitStreak(changed, onlyEating, on(21), away(5, 12)))
    }
}
