package com.example.workouttracker.domain.mo

import com.example.workouttracker.domain.LoggedViewKind
import com.example.workouttracker.domain.LoggedViewRef
import com.example.workouttracker.domain.TrackerRef
import com.example.workouttracker.domain.trackerRef
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.checklist.WhenAway
import com.example.workouttracker.domain.compatibility.CareChecklistId
import com.example.workouttracker.domain.compatibility.EatingQuestionsId
import com.example.workouttracker.domain.holiday.DayRange
import com.example.workouttracker.domain.holiday.HolidaySchedule
import com.example.workouttracker.domain.compatibility.ReadingTimeId
import com.example.workouttracker.domain.compatibility.SupplementsChecklistId
import com.example.workouttracker.domain.compatibility.WeightMeasurementId
import com.example.workouttracker.domain.mo.compatibility.InheritedMoEvaluation
import com.example.workouttracker.domain.mo.compatibility.withInheritedTrackerConfigurations
import com.example.workouttracker.domain.settings.DashboardFeature
import com.example.workouttracker.domain.settings.FeatureVisibilitySettings
import com.example.workouttracker.domain.time.TimeEdit
import com.example.workouttracker.domain.time.TimeLog
import com.example.workouttracker.domain.time.applyingTimeEdits
import com.example.workouttracker.domain.time.TokenCover
import com.example.workouttracker.domain.time.minutesInWeek
import com.example.workouttracker.domain.activity.withActivity
import com.example.workouttracker.domain.time.withReading
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reading in the walk reads its goal, the gap that is a quarter of it, its bank's size and its
 * holiday rule from its configuration, by the week in question. With what is stored for it every
 * week is judged as the code judged it: sixty minutes, gaps of fifteen, three tokens, two a week,
 * two gaps covered, still due on holiday. A saved change counts from the day it is saved: it never
 * re-judges an earlier week and moves no recorded fact. Days are counted from a Monday.
 */
class ReadingSettingsTest {
    private val monday = LocalDate.parse("2026-10-05")
    private fun on(day: Int) = monday.plusDays(day.toLong())

    /** Minutes read on each of [days], as day to minutes. */
    private fun read(vararg days: Pair<Int, Int>) =
        AppState(timeLogs = days.map { (day, minutes) -> TimeLog(ReadingTimeId, on(day).toString(), day.toLong(), minutes) }).withReading().withActivity()
    private fun AppState.saved(day: Int, vararg edits: TimeEdit) = applyingTimeEdits(ReadingTimeId, edits.toList(), on(day))
    private fun away(vararg days: Int) = HolidaySchedule(days.map { DayRange(on(it), on(it)) })
    private fun HolidaySchedule.sick(vararg days: Int) = days.fold(this) { schedule, day -> schedule.withSickDays(DayRange(on(day), on(day))) }
    private val readingRef = LoggedViewRef(LoggedViewKind.Time, ReadingTimeId).trackerRef
    private val onlyReading = FeatureVisibilitySettings(
        disabledFeatures = setOf(DashboardFeature.Activity, DashboardFeature.Substances, DashboardFeature.Statistics),
        hiddenChecklists = setOf(CareChecklistId, SupplementsChecklistId),
        hiddenMeasurements = setOf(WeightMeasurementId),
        hiddenQuestions = setOf(EatingQuestionsId),
    )
    private fun AppState.readingConfig() = time.single { it.id == ReadingTimeId }
    private fun MoStreak.mark(day: Int) = history.single { it.date == on(day) }
    private val notDue = TimeEdit.SetWhenAway(WhenAway.Excused)

    /** A recovered historical profile keeps its logs; current evaluation ignores an unconfigured orphan. */
    @Test fun aRecoveredReadingProfilePreservesLegacyHistoryAndLeavesOrphansStored() {
        val configured = read(2 to 120, 8 to 30, 15 to 29, 22 to 45)
        val orphaned = configured.copy(time = emptyList())
        val recovered = orphaned.withInheritedTrackerConfigurations()
        assertEquals(configured.timeLogs, orphaned.timeLogs)
        assertEquals(orphaned.timeLogs, recovered.timeLogs)
        val orphanMood = moMood(orphaned, onlyReading, on(29))
        assertEquals(0, orphanMood.dueToday)
        assertTrue(readingRef !in orphanMood.trackerOrder)
        assertTrue(readingRef !in orphanMood.streak.standings)
        listOf(HolidaySchedule(), away(3, 9, 10, 20), HolidaySchedule().sick(9, 16, 17), away(8, 9).sick(9, 13)).forEach { holidays ->
            (0..29).forEach { today ->
                val legacy = InheritedMoEvaluation(orphaned, onlyReading, on(today), holidays)
                val stored = InheritedMoEvaluation(configured, onlyReading, on(today), holidays)
                val recoveredReport = InheritedMoEvaluation(recovered, onlyReading, on(today), holidays)
                assertEquals("day $today", stored.streak, legacy.streak)
                assertEquals("day $today", stored.mood, legacy.mood)
                assertEquals("day $today", stored.streak, recoveredReport.streak)
                assertEquals("day $today", stored.mood, recoveredReport.mood)
            }
        }
    }

    /**
     * The default is what the code held. Two hours in the first week earn two tokens, the most a
     * week earns, into a bank of three. Thirty minutes in the second are two gaps of fifteen, the
     * most tokens cover. Twenty-nine in the third are three gaps, more than tokens cover, so the
     * freeze pays.
     */
    @Test fun theSeededRulesAreSixtyMinutesGapsOfFifteenThreeTokensTwoAWeekAndTwoGapsCovered() {
        val state = read(2 to 120, 8 to 30, 15 to 29)
        val reading = state.readingConfig()
        assertEquals(60, reading.goalOfWeek(monday))
        assertEquals(60, reading.weekAsks(monday, HolidaySchedule()))

        val afterOne = habitStreak(state, onlyReading, on(7))
        assertEquals(MoDayRecord(on(6), MoDayMark.Perfect), afterOne.mark(6))
        assertEquals(3, afterOne.standings.getValue(readingRef).passes)
        assertEquals(3, afterOne.standings.getValue(readingRef).passCapacity)
        assertEquals(listOf(readingRef), afterOne.earnedTrackersYesterday)
        // The second week's thirty minutes are on the way to its first token.
        assertEquals(MoTokenProgress(30, 60), afterOne.standings.getValue(readingRef).nextToken!!)

        val afterTwo = habitStreak(state, onlyReading, on(14))
        assertEquals(MoDayRecord(on(13), MoDayMark.Protected, coveredTrackers = setOf(readingRef)), afterTwo.mark(13))
        assertEquals(1, afterTwo.standings.getValue(readingRef).passes)
        assertEquals(1, afterTwo.freezes)

        val afterThree = habitStreak(state, onlyReading, on(21))
        assertEquals(MoDayRecord(on(20), MoDayMark.Frozen, coveredTrackers = setOf(readingRef)), afterThree.mark(20))
        assertEquals(1, afterThree.standings.getValue(readingRef).passes)
        assertEquals(0, afterThree.freezes)

        // A week on the way to its second token says so, and a week past it stays at two goals.
        assertEquals(MoTokenProgress(0, 60), habitStreak(read(0 to 60), onlyReading, on(7)).standings.getValue(readingRef).nextToken!!)
        assertEquals(MoTokenProgress(75, 120), habitStreak(read(0 to 75), onlyReading, on(3)).standings.getValue(readingRef).nextToken!!)
        assertEquals(MoTokenProgress(120, 120), habitStreak(read(0 to 200), onlyReading, on(3)).standings.getValue(readingRef).nextToken!!)

        // Mo's mood says the same week: what is left of sixty, and the gap a token covers.
        val wednesday = moMood(state, onlyReading, on(9))
        assertEquals(30, minutesInWeek(state.timeLogs, ReadingTimeId, on(7)))
        assertEquals(30, wednesday.trackerWeeklyRemainders[readingRef])
        assertEquals(15, TokenCover.QuarterOfGoal.minutesOf(state.readingConfig().goalOfWeek(on(7))))
        assertEquals(MoWeekProgress(0, 1), wednesday.trackerWeekProgress[readingRef])
        assertEquals(60, moMood(state, onlyReading, on(7)).tokenEarningBasisByTracker[readingRef])
    }

    /**
     * A token covers what its week's Sunday says. The second week is twenty minutes short: two
     * quarters of an hour, which costs two of the bank's tokens; set on that week's Tuesday to cover
     * twenty minutes, one token covers the week and one stays in the bank. The first week is judged
     * as it was, and the minutes recorded are what they were.
     */
    @Test fun aTokenCoversWhatItIsSetToFromTheWeekItIsSavedIn() {
        val records = read(1 to 60, 8 to 40)
        val quarter = habitStreak(records, onlyReading, on(14))
        val twenty = habitStreak(records.saved(8, TimeEdit.SetTokenCover(TokenCover.Minutes(20))), onlyReading, on(14))

        val protectedWeek = MoDayRecord(on(13), MoDayMark.Protected, coveredTrackers = setOf(readingRef))
        assertEquals(protectedWeek, quarter.mark(13))
        assertEquals(protectedWeek, twenty.mark(13))
        assertEquals(0, quarter.standings.getValue(readingRef).passes)
        assertEquals(1, twenty.standings.getValue(readingRef).passes)
        assertEquals(quarter.mark(6), twenty.mark(6))
        assertEquals(40, minutesInWeek(records.timeLogs, ReadingTimeId, on(7)))
    }

    /**
     * Ninety minutes a week, saved on the second week's Wednesday. That week is judged by ninety,
     * the goal its Sunday has: an hour leaves thirty missing, two gaps of twenty-three, and earns
     * nothing. The week before was judged by sixty and stays so.
     */
    @Test fun aChangedGoalJudgesTheWeekItIsSavedInAndNoEarlierWeek() {
        val records = read(1 to 60, 8 to 60, 15 to 100)
        val changed = records.saved(9, TimeEdit.SetGoal(90))

        // Up to the Sunday before, nothing differs at all: the week a goal is saved in is its week from its Monday on.
        (0..6).forEach { day -> assertEquals("day $day", habitStreak(records, onlyReading, on(day)), habitStreak(changed, onlyReading, on(day))) }
        (0..6).forEach { day -> assertEquals("day $day", moMood(records, onlyReading, on(day)), moMood(changed, onlyReading, on(day))) }
        // On the day it is saved the bank is what it was, and the token page shows the new goal.
        assertEquals(2, habitStreak(changed, onlyReading, on(9)).standings.getValue(readingRef).passes)
        assertEquals(MoTokenProgress(60, 120), habitStreak(records, onlyReading, on(9)).standings.getValue(readingRef).nextToken!!)
        assertEquals(MoTokenProgress(60, 90), habitStreak(changed, onlyReading, on(9)).standings.getValue(readingRef).nextToken!!)

        val before = habitStreak(records, onlyReading, on(14))
        val after = habitStreak(changed, onlyReading, on(14))
        assertEquals(MoDayRecord(on(13), MoDayMark.Perfect), before.mark(13))
        assertEquals(3, before.standings.getValue(readingRef).passes)
        assertEquals(MoDayRecord(on(13), MoDayMark.Protected, coveredTrackers = setOf(readingRef)), after.mark(13))
        assertEquals(0, after.standings.getValue(readingRef).passes)
        assertEquals(emptyList<TrackerRef>(), after.earnedTrackersYesterday)
        // The first week reads the same to the day.
        assertEquals(before.history.filter { it.date < on(7) }, after.history.filter { it.date < on(7) })
        assertEquals(MoDayRecord(on(6), MoDayMark.Perfect), after.mark(6))

        // The third week's hundred minutes reach ninety once.
        val third = habitStreak(changed, onlyReading, on(21))
        assertEquals(MoDayRecord(on(20), MoDayMark.Perfect), third.mark(20))
        assertEquals(1, third.standings.getValue(readingRef).passes)
        assertEquals(listOf(readingRef), third.earnedTrackersYesterday)

        // Mo's ledger and his mood follow the same goal.
        val ledger = MoLedger.of(changed, onlyReading, on(21)).history(readingRef)
        assertEquals(listOf(MoStatus.Kept, MoStatus.Missed, MoStatus.Kept), listOf(on(6), on(13), on(20)).map { ledger[it] })
        assertEquals(listOf(MoStatus.Kept, MoStatus.Kept, MoStatus.Kept), listOf(on(6), on(13), on(20)).map { MoLedger.of(records, onlyReading, on(21)).history(readingRef)[it] })
        val thursday = moMood(changed, onlyReading, on(10))
        assertEquals(30, thursday.trackerWeeklyRemainders[readingRef])
        assertEquals(23, TokenCover.QuarterOfGoal.minutesOf(changed.readingConfig().goalOfWeek(on(7))))
        assertEquals(0, moMood(records, onlyReading, on(10)).trackerWeeklyRemainders[readingRef])
    }

    /** A goal saved on a Sunday is that week's, and one saved the Monday after is the next week's. */
    @Test fun aGoalSavedOnSundayJudgesThatSunday() {
        val records = read(1 to 45, 8 to 45)
        val onSunday = records.saved(6, TimeEdit.SetGoal(45))
        val onMonday = records.saved(7, TimeEdit.SetGoal(45))
        assertEquals(MoDayMark.Perfect, habitStreak(onSunday, onlyReading, on(7)).mark(6).mark)
        assertEquals(MoDayMark.Protected, habitStreak(onMonday, onlyReading, on(7)).mark(6).mark)
        assertEquals(60, records.readingConfig().goalOfWeek(on(7)))
        assertEquals(45, onMonday.readingConfig().goalOfWeek(on(7)))
        assertEquals(45, onSunday.readingConfig().goalOfWeek(on(7)))
        assertEquals(60, InheritedMoEvaluation(records, onlyReading, on(7)).mood.readingGoalForATokenYesterday)
        assertEquals(60, InheritedMoEvaluation(onMonday, onlyReading, on(7)).mood.readingGoalForATokenYesterday)
        assertEquals(45, InheritedMoEvaluation(onSunday, onlyReading, on(7)).mood.readingGoalForATokenYesterday)
        assertEquals(MoDayMark.Perfect, habitStreak(onMonday, onlyReading, on(14)).mark(13).mark)
    }

    /**
     * Two hours a week fill the bank to three. A bank of one saved on the second week's Wednesday
     * holds one from that day, today included, and what was earned before is as it was.
     */
    @Test fun aSmallerBankHoldsNoMoreThanItAllowsFromTheDayItIsSaved() {
        val records = read(1 to 120, 8 to 120, 15 to 120)
        val smaller = records.saved(9, TimeEdit.SetBankSize(1))

        assertEquals(3, habitStreak(records, onlyReading, on(8)).standings.getValue(readingRef).passes)
        assertEquals(habitStreak(records, onlyReading, on(8)), habitStreak(smaller, onlyReading, on(8)))
        val wednesday = habitStreak(smaller, onlyReading, on(9))
        assertEquals(1, wednesday.standings.getValue(readingRef).passes)
        assertEquals(1, wednesday.standings.getValue(readingRef).passCapacity)
        // The second week's two hours earn into a bank that is already full.
        assertEquals(1, habitStreak(smaller, onlyReading, on(14)).standings.getValue(readingRef).passes)
        assertEquals(emptyList<TrackerRef>(), habitStreak(smaller, onlyReading, on(14)).earnedTrackersYesterday)
        assertEquals(habitStreak(records, onlyReading, on(14)).history, habitStreak(smaller, onlyReading, on(14)).history)

        // A bigger bank has room from its day on and hands nothing out for the week before.
        val bigger = records.saved(9, TimeEdit.SetBankSize(5))
        assertEquals(3, habitStreak(bigger, onlyReading, on(9)).standings.getValue(readingRef).passes)
        assertEquals(5, habitStreak(bigger, onlyReading, on(14)).standings.getValue(readingRef).passes)
        assertEquals(5, habitStreak(bigger, onlyReading, on(14)).standings.getValue(readingRef).passCapacity)
        assertEquals(listOf(readingRef), habitStreak(bigger, onlyReading, on(14)).earnedTrackersYesterday)
    }

    /**
     * Not due saved on the second week's Monday, with Wednesday to Friday away in both weeks and
     * thirty-five minutes read in each. The first week was judged while Reading was still due and
     * stays short of sixty. The second asks a seventh less for each away day, thirty-five, and is
     * kept.
     */
    @Test fun notDueOnHolidayLowersAWeekByASeventhForEachAwayDayFromTheDayItIsSaved() {
        val records = read(1 to 35, 8 to 35)
        val holidays = away(2, 3, 4, 9, 10, 11)
        val excused = records.saved(7, notDue)
        assertEquals(60, excused.readingConfig().weekAsks(on(0), holidays))
        assertEquals(35, excused.readingConfig().weekAsks(on(7), holidays))
        assertEquals(60, records.readingConfig().weekAsks(on(7), holidays))

        val stillDue = habitStreak(records, onlyReading, on(14), holidays)
        val lowered = habitStreak(excused, onlyReading, on(14), holidays)
        // Still due, as ever: each week is twenty-five minutes short, two gaps, more than its one token.
        assertEquals(MoDayRecord(on(6), MoDayMark.Frozen, coveredTrackers = setOf(readingRef)), stillDue.mark(6))
        assertEquals(MoDayRecord(on(13), MoDayMark.Missed, missedTrackers = listOf(readingRef)), stillDue.mark(13))
        // Not due from the second Monday: that week asks thirty-five and has them.
        assertEquals(MoDayRecord(on(6), MoDayMark.Frozen, coveredTrackers = setOf(readingRef)), lowered.mark(6))
        assertEquals(MoDayRecord(on(13), MoDayMark.Perfect), lowered.mark(13))
        // A lowered week earns by the whole goal all the same: thirty-five minutes are no token.
        assertEquals(1, lowered.standings.getValue(readingRef).passes)
        assertEquals(emptyList<TrackerRef>(), lowered.earnedTrackersYesterday)
        // No earlier week is judged again: the first reads the same to the day.
        (0..7).forEach { day -> assertEquals("day $day", habitStreak(records, onlyReading, on(day), holidays), habitStreak(excused, onlyReading, on(day), holidays)) }

        // Mo's ledger follows, and so does what his mood says is left of the week.
        val ledger = MoLedger.of(excused, onlyReading, on(14), holidays).history(readingRef)
        assertEquals(MoStatus.Missed, ledger[on(6)])
        assertEquals(MoStatus.Kept, ledger[on(13)])
        assertEquals(MoStatus.Missed, MoLedger.of(records, onlyReading, on(14), holidays).history(readingRef)[on(13)])
        assertEquals(25, moMood(records, onlyReading, on(12), holidays).trackerWeeklyRemainders[readingRef])
        assertEquals(0, moMood(excused, onlyReading, on(12), holidays).trackerWeeklyRemainders[readingRef])
        assertEquals(MoWeekProgress(1, 1), moMood(excused, onlyReading, on(12), holidays).trackerWeekProgress[readingRef])
        // The gap stays a quarter of the goal, whatever the week asks.
        assertEquals(15, TokenCover.QuarterOfGoal.minutesOf(excused.readingConfig().goalOfWeek(on(7))))

        // Taken back a week later, the weeks in between keep the rule they were judged by.
        val dueAgain = excused.saved(14, TimeEdit.SetWhenAway(WhenAway.Due))
        assertEquals(lowered.history.dropLast(1), habitStreak(dueAgain, onlyReading, on(14), holidays).history.dropLast(1))
    }

    /** Each away day is read by its own rule: saved on Thursday, Wednesday was still due and only Thursday and Friday lower the week. */
    @Test fun anAwayDayBeforeTheRuleWasSavedIsStillDue() {
        val records = read(1 to 35, 8 to 35)
        val holidays = away(9, 10, 11)
        val excused = records.saved(10, notDue)
        // Five of seven days ask: sixty less two sevenths, rounded up, is forty-three.
        assertEquals(43, excused.readingConfig().weekAsks(on(7), holidays))
        val streak = habitStreak(excused, onlyReading, on(14), holidays)
        // Eight minutes short is one gap, which the one token covers.
        assertEquals(MoDayRecord(on(13), MoDayMark.Protected, coveredTrackers = setOf(readingRef)), streak.mark(13))
        assertEquals(0, streak.standings.getValue(readingRef).passes)
    }

    /** A day that is sick and away counts once, and the amount is rounded up once at the end. */
    @Test fun aDayThatIsSickAndAwayCountsOnce() {
        val excused = read(1 to 60, 8 to 43).saved(0, notDue)
        // Wednesday sick and away, Thursday away: two days off, not three.
        val holidays = away(9, 10).sick(9)
        assertEquals(43, excused.readingConfig().weekAsks(on(7), holidays))
        assertEquals(MoDayRecord(on(13), MoDayMark.Perfect), habitStreak(excused, onlyReading, on(14), holidays).mark(13))
        assertEquals(MoDayRecord(on(13), MoDayMark.Protected, coveredTrackers = setOf(readingRef)), habitStreak(read(1 to 60, 8 to 42).saved(0, notDue), onlyReading, on(14), holidays).mark(13))
        // One sick day and one away day ask what two sick days ask: rounded up once, not once each.
        assertEquals(43, excused.readingConfig().weekAsks(on(7), away(10).sick(12)))
        assertEquals(43, excused.readingConfig().weekAsks(on(7), HolidaySchedule().sick(10, 12)))
    }

    /**
     * A week whose Sunday is an away day Reading is not due on is not judged, as for a sick Sunday:
     * nothing is asked on it and nothing spent. Its minutes still earn, as they do on a sick
     * Sunday. Still due, the same Sunday judges the week.
     */
    @Test fun aWeekWhoseSundayIsAwayAndNotDueIsNotJudgedAndStillEarns() {
        val records = read(1 to 60, 8 to 5)
        val holidays = away(13)
        val excused = records.saved(7, notDue)

        val stillDue = habitStreak(records, onlyReading, on(14), holidays)
        assertEquals(MoDayRecord(on(13), MoDayMark.Frozen, coveredTrackers = setOf(readingRef)), stillDue.mark(13))
        val notJudged = habitStreak(excused, onlyReading, on(14), holidays)
        assertEquals(MoDayRecord(on(13), MoDayMark.NothingDue), notJudged.mark(13))
        assertEquals(2, notJudged.standings.getValue(readingRef).passes)
        assertEquals(1, notJudged.freezes)
        assertNull(MoLedger.of(excused, onlyReading, on(14), holidays).history(readingRef)[on(13)])
        assertEquals(MoStatus.Missed, MoLedger.of(records, onlyReading, on(14), holidays).history(readingRef)[on(13)])
        // On that Sunday itself nothing is open, where still due the week would be.
        assertEquals(listOf(readingRef), moMood(records, onlyReading, on(13), holidays).missingTrackersToday)
        assertEquals(0, moMood(excused, onlyReading, on(13), holidays).dueToday)

        // Two hours read in such a week earn their two tokens although the week is not judged.
        val earning = read(1 to 5, 8 to 120).saved(7, notDue)
        val earned = habitStreak(earning, onlyReading, on(14), holidays)
        assertEquals(MoDayMark.NothingDue, earned.mark(13).mark)
        assertEquals(listOf(readingRef), earned.earnedTrackersYesterday)
        // The first week took the freeze and left the starting token; the second adds two.
        assertEquals(3, earned.standings.getValue(readingRef).passes)
        // A sick Sunday does the same, as it always has.
        assertEquals(listOf(readingRef), habitStreak(read(1 to 5, 8 to 120), onlyReading, on(14), HolidaySchedule().sick(13)).earnedTrackersYesterday)
    }



    /** What Mo says after a token names the goal it was earned by, also once today's goal is another. */
    @Test fun theMoodCarriesTheGoalTheTokenWasEarnedBy() {
        val earnedByThirty = read(1 to 30).saved(0, TimeEdit.SetGoal(30))
        val changedToday = earnedByThirty.saved(7, TimeEdit.SetGoal(90))
        val mood = moMood(changedToday, onlyReading, on(7))
        assertEquals(listOf(readingRef), mood.streak.earnedTrackersYesterday)
        assertEquals(30, mood.tokenEarningBasisByTracker[readingRef])
        assertEquals(90, changedToday.readingConfig().goalOfWeek(on(7)))
        assertEquals(MoTokenProgress(0, 90), mood.streak.standings.getValue(readingRef).nextToken!!)
        // This week's gap is a quarter of this week's goal.
        assertEquals(23, TokenCover.QuarterOfGoal.minutesOf(changedToday.readingConfig().goalOfWeek(on(7))))
        assertEquals(60, moMood(read(1 to 60), onlyReading, on(7)).tokenEarningBasisByTracker[readingRef])
    }

    /** No setting here is a record: the logs are the same whatever is saved. */
    @Test fun noSettingChangesARecord() {
        val records = read(1 to 20, 4 to 15, 9 to 70, 16 to 5, 20 to 40)
        val changed = records.saved(3, TimeEdit.SetGoal(30), TimeEdit.SetBankSize(1), notDue, TimeEdit.Rename("Books"))
            .saved(10, TimeEdit.SetGoal(200), TimeEdit.SetBankSize(9), TimeEdit.SetWhenAway(WhenAway.Due))
        assertEquals(records.timeLogs.filter { it.viewId == ReadingTimeId }, changed.timeLogs.filter { it.viewId == ReadingTimeId })
        assertEquals(records.copy(time = changed.time), changed)
        // The rewards really moved, so the comparison is not between equals.
        assertTrue(habitStreak(records, onlyReading, on(21), away(5, 12)) != habitStreak(changed, onlyReading, on(21), away(5, 12)))
    }
}
