package de.nicolasmahn.momentum.domain.mo

import de.nicolasmahn.momentum.domain.ActivityTrackerRef
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.TrackerRef
import de.nicolasmahn.momentum.domain.activity.ActivityEdit
import de.nicolasmahn.momentum.domain.activity.ActivityEntry
import de.nicolasmahn.momentum.domain.activity.ActivityTokenStep
import de.nicolasmahn.momentum.domain.activity.ActivityTokenSteps
import de.nicolasmahn.momentum.domain.activity.DefaultActivityWeek
import de.nicolasmahn.momentum.domain.activity.applyingActivityEdits
import de.nicolasmahn.momentum.domain.activity.routine.ExerciseLog
import de.nicolasmahn.momentum.domain.activity.routine.LoggedSet
import de.nicolasmahn.momentum.domain.activity.routine.WorkoutSession
import de.nicolasmahn.momentum.domain.activity.withActivity
import de.nicolasmahn.momentum.domain.checklist.WhenAway
import de.nicolasmahn.momentum.domain.compatibility.CareChecklistId
import de.nicolasmahn.momentum.domain.compatibility.EatingQuestionsId
import de.nicolasmahn.momentum.domain.compatibility.ReadingTimeId
import de.nicolasmahn.momentum.domain.compatibility.SupplementsChecklistId
import de.nicolasmahn.momentum.domain.compatibility.WeightMeasurementId
import de.nicolasmahn.momentum.domain.compatibility.MoNeed
import de.nicolasmahn.momentum.domain.holiday.DayRange
import de.nicolasmahn.momentum.domain.holiday.HolidaySchedule
import de.nicolasmahn.momentum.domain.mo.compatibility.InheritedMoEvaluation
import de.nicolasmahn.momentum.domain.mo.compatibility.withInheritedTrackerConfigurations
import de.nicolasmahn.momentum.domain.settings.DashboardFeature
import de.nicolasmahn.momentum.domain.settings.FeatureVisibilitySettings
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import de.nicolasmahn.momentum.domain.checklist.RewardPeriod
import de.nicolasmahn.momentum.domain.activity.firstActiveDay

/**
 * Activity in the walk, in Mo's ledger and in his mood reads its days a week, its holiday rule
 * with its count and its bank from its configuration, by the rule of each day. With what is
 * stored for it every day is judged as the code judged it: five days a week, two free days, the
 * holiday table, three tokens. A saved change counts from the day it is saved: it never re-judges
 * an earlier day and moves no recorded fact. Days are counted from a Monday.
 */
class ActivitySettingsTest {
    private val monday = LocalDate.parse("2026-10-05")
    private fun on(day: Int) = monday.plusDays(day.toLong())

    /** A state active on each of [days], an entry or a workout with a set. */
    private fun active(vararg days: Int) = AppState(
        activityEntries = days.filter { it % 2 == 0 }.map { ActivityEntry("e$it", "bouldering", "Bouldering", on(it).toString(), it.toLong()) },
        sessions = days.filter { it % 2 == 1 }.map { WorkoutSession("s$it", "gym", "A", on(it).toString(), it.toLong(), listOf(ExerciseLog("bench_press", "Bench Press", listOf(LoggedSet(20.0, 8.0))))) },
    ).withActivity()
    private fun AppState.saved(day: Int, vararg edits: ActivityEdit) = applyingActivityEdits(edits.toList(), on(day))
    private fun week(days: Int) = ActivityEdit.SetWeek(DefaultActivityWeek.withDays(DayOfWeek.entries.take(days).toSet()))
    private fun away(vararg days: Int) = HolidaySchedule(days.map { DayRange(on(it), on(it)) })
    private fun HolidaySchedule.sick(vararg days: Int) = days.fold(this) { schedule, day -> schedule.withSickDays(DayRange(on(day), on(day))) }
    private val activityRef = ActivityTrackerRef
    private val onlyActivity = FeatureVisibilitySettings(
        disabledFeatures = setOf(DashboardFeature.Substances, DashboardFeature.Statistics),
        hiddenChecklists = setOf(CareChecklistId, SupplementsChecklistId),
        hiddenMeasurements = setOf(WeightMeasurementId),
        hiddenQuestions = setOf(EatingQuestionsId),
        hiddenTime = setOf(ReadingTimeId),
    )
    private fun MoStreak.mark(day: Int) = history.single { it.date == on(day) }
    private fun AppState.statuses(today: Int, holidays: HolidaySchedule = HolidaySchedule()): String =
        MoLedger.of(this, onlyActivity, on(today), holidays).history(activityRef).let { history ->
            (0 until today).joinToString("") { day ->
                when (history[on(day)]) {
                    MoStatus.Kept -> "K"
                    MoStatus.Free -> "F"
                    MoStatus.Missed -> "M"
                    null -> "-"
                }
            }
        }

    /**
     * Leaving the streak closes the period Activity has been in since its first record, an entry
     * or a session, and keeps the bank's size and steps; joining the next day reopens it, a later
     * day adds one, and joining while it takes part changes nothing. With no record yet, leaving
     * leaves no period.
     */
    @Test fun leavingClosesThePeriodFromTheFirstRecordAndJoiningReopensOrAddsOne() {
        val records = active(0, 2, 4)
        val left = records.saved(5, ActivityEdit.LeaveRewards)
        assertEquals(on(0), records.firstActiveDay)
        assertEquals(listOf(RewardPeriod(on(0), on(4))), left.activity!!.rewards.periods)
        assertEquals(records.activity!!.rewards.copy(periods = left.activity!!.rewards.periods), left.activity!!.rewards)
        assertEquals(listOf(RewardPeriod(on(0))), left.saved(5, ActivityEdit.JoinRewards).activity!!.rewards.periods)
        assertEquals(listOf(RewardPeriod(on(0), on(4)), RewardPeriod(on(8))), left.saved(8, ActivityEdit.JoinRewards).activity!!.rewards.periods)
        assertEquals(records.activity, records.saved(5, ActivityEdit.JoinRewards).activity)
        assertEquals(emptyList<RewardPeriod>(), AppState().withActivity().saved(5, ActivityEdit.LeaveRewards).activity!!.rewards.periods)
    }

    /** Legacy defaults are materialized at the fixture boundary; orphan activity records stay stored and unjudged. */
    @Test fun aRecoveredActivityProfilePreservesLegacyHistoryAndLeavesOrphansStored() {
        val configured = active(0, 2, 4, 8, 9, 11, 15, 17, 18, 22, 23)
        val orphaned = configured.copy(activity = null)
        val recovered = orphaned.withInheritedTrackerConfigurations()
        assertEquals(configured.activityEntries, orphaned.activityEntries)
        assertEquals(configured.sessions, orphaned.sessions)
        assertEquals(orphaned.activityEntries, recovered.activityEntries)
        assertEquals(orphaned.sessions, recovered.sessions)
        val orphanMood = moMood(orphaned, onlyActivity, on(29))
        assertEquals(0, orphanMood.dueToday)
        assertTrue(activityRef !in orphanMood.trackerOrder)
        assertTrue(activityRef !in orphanMood.streak.standings)
        assertTrue(orphaned.activityEntries.isNotEmpty() || orphaned.sessions.isNotEmpty())
        listOf(HolidaySchedule(), away(3, 9, 10, 20), HolidaySchedule().sick(9, 16, 17), away(8, 9, 10, 11).sick(9, 13)).forEach { holidays ->
            (0..29).forEach { today ->
                assertEquals("day $today", habitStreak(recovered, onlyActivity, on(today), holidays), habitStreak(configured, onlyActivity, on(today), holidays))
                val legacy = InheritedMoEvaluation(orphaned, onlyActivity, on(today), holidays)
                val stored = InheritedMoEvaluation(recovered, onlyActivity, on(today), holidays)
                assertEquals("day $today", stored.streak, legacy.streak)
                assertEquals("day $today", stored.mood, legacy.mood)
            }
        }
    }

    /**
     * The default is what the code held: five active days earn a token into a bank of three, two
     * free days a week, the third empty day a gap; a holiday week asks the table, a day less per
     * sick day; and the walk, the ledger and the mood all read the same week.
     */
    @Test fun theSeededRulesAreFiveDaysAWeekTwoFreeDaysTheHolidayTableAndThreeTokens() {
        val state = active(0, 1, 2, 3, 4, 7, 8, 9, 10, 14, 15, 16, 17)
        val afterOne = habitStreak(state, onlyActivity, on(7))
        assertEquals(MoDayRecord(on(5), MoDayMark.Perfect, restedTrackers = setOf(activityRef)), afterOne.mark(5))
        assertEquals(MoDayRecord(on(6), MoDayMark.Perfect, restedTrackers = setOf(activityRef)), afterOne.mark(6))
        assertEquals(2, afterOne.standings.getValue(activityRef).passes)
        assertEquals(3, afterOne.standings.getValue(activityRef).passCapacity)
        assertEquals(listOf(activityRef), afterOne.earnedTrackersYesterday)
        assertEquals(2, afterOne.freeDaysLeftByTracker.getValue(activityRef))

        val afterTwo = habitStreak(state, onlyActivity, on(14))
        assertEquals(MoDayRecord(on(13), MoDayMark.Protected, coveredTrackers = setOf(activityRef)), afterTwo.mark(13))
        assertEquals(1, afterTwo.standings.getValue(activityRef).passes)
        // Monday's record already counts toward the next token.
        assertEquals(MoTokenProgress(1, 5), afterTwo.standings.getValue(activityRef).nextToken!!.copy(week = emptyList()))

        assertEquals("KKKKKFF" + "KKKKFFM", state.statuses(14))
        assertEquals("KKKKKFF" + "KKKKFF-", state.statuses(14, HolidaySchedule().sick(13)))
        assertEquals("KKKKKFF" + "KKKKFFF", state.statuses(14, away(7, 8, 9)))
        assertEquals(MoWeekProgress(4, 5), moMood(state, onlyActivity, on(12)).trackerWeekProgress[activityRef])
        assertEquals(MoWeekProgress(4, 4), moMood(state, onlyActivity, on(12), away(7, 8, 9)).trackerWeekProgress[activityRef])
        assertEquals(MoWeekProgress(3, 3), moMood(state, onlyActivity, on(12), away(7, 8, 9).sick(8)).trackerWeekProgress[activityRef])
        assertEquals(5, InheritedMoEvaluation(state, onlyActivity, on(7)).mood.activityDaysForATokenYesterday)
    }

    /**
     * Fewer days a week saved on a Wednesday: a new span starts, so Wednesday to Sunday are judged
     * by its free days with none used, while Monday and Tuesday keep what they were dealt. The
     * week earns by the days its Sunday has, and the week before is judged as it was.
     */
    @Test fun fewerDaysSavedMidweekStartANewSpanAndJudgeNoEarlierDay() {
        val records = active(0, 1, 2, 3, 4, 7)
        val changed = records.saved(9, week(1))

        // Up to the Sunday before, nothing differs at all: the week a change is saved in earns by its Sunday's days from its Monday on.
        (0..6).forEach { day -> assertEquals("day $day", habitStreak(records, onlyActivity, on(day)), habitStreak(changed, onlyActivity, on(day))) }
        val beforeChange = habitStreak(records, onlyActivity, on(8))
        val afterChange = habitStreak(changed, onlyActivity, on(8))
        val beforeLegacy = InheritedMoEvaluation(records, onlyActivity, on(8)).streak
        val afterLegacy = InheritedMoEvaluation(changed, onlyActivity, on(8)).streak
        assertEquals(afterChange.standings.getValue(activityRef).nextToken, afterLegacy.nextToken.getValue(MoNeed.Activity))
        assertEquals(outcomeText(beforeLegacy.copy(nextToken = emptyMap())), outcomeText(afterLegacy.copy(nextToken = emptyMap())))
        assertEquals("KKKKKFF" + "KFFMMMM", records.statuses(14))
        assertEquals("KKKKKFF" + "KFFFFFF", changed.statuses(14))
        val streak = habitStreak(changed, onlyActivity, on(14))
        (8..13).forEach { day -> assertEquals("day $day", MoDayMark.Perfect, streak.mark(day).mark) }
        assertTrue((8..13).all { activityRef in streak.mark(it).restedTrackers })
        // One active day earns the week's token, where five were asked before.
        assertEquals(3, streak.standings.getValue(activityRef).passes)
        assertEquals(listOf(activityRef), streak.earnedTrackersYesterday)
        assertEquals(1, InheritedMoEvaluation(changed, onlyActivity, on(14)).mood.activityDaysForATokenYesterday)
        assertEquals(MoWeekProgress(1, 1), moMood(changed, onlyActivity, on(10)).trackerWeekProgress[activityRef])
        // Wednesday rested on one of the six, so five are left for the rest of the span.
        assertEquals(5, habitStreak(changed, onlyActivity, on(10)).freeDaysLeftByTracker.getValue(activityRef))
        assertEquals(MoTokenProgress(0, 1), habitStreak(changed, onlyActivity, on(14)).standings.getValue(activityRef).nextToken!!.copy(week = emptyList()))
    }

    /**
     * More days a week saved on a Wednesday after two free days: the new span has one free day,
     * whole, so Wednesday rests and Thursday is a gap. The week asks six, which the days do not
     * reach, so it earns nothing; the week before stays judged by five.
     */
    @Test fun moreDaysSavedMidweekStartANewSpanWithItsFreeDaysWhole() {
        val records = active(0, 1, 2, 3, 4, 11, 12, 13)
        val changed = records.saved(9, week(6))

        assertEquals("KKKKKFF" + "FFFMKKK", changed.statuses(14))
        assertEquals("KKKKKFF" + "FFMMKKK", records.statuses(14))
        val streak = habitStreak(changed, onlyActivity, on(14))
        assertEquals(MoDayMark.Protected, streak.mark(10).mark)
        assertEquals(setOf(activityRef), streak.mark(10).coveredTrackers)
        assertEquals(1, streak.standings.getValue(activityRef).passes)
        assertEquals(emptyList<TrackerRef>(), streak.earnedTrackersYesterday)
        assertEquals(habitStreak(records, onlyActivity, on(6)), habitStreak(changed, onlyActivity, on(6)))
        assertEquals(records.statuses(8), changed.statuses(8))
    }

    /**
     * Still due on holiday, saved in the middle of a holiday week, keeps the span: the free day
     * already used stays used, and from the change on the week's free days are the rule's two. Not
     * due takes the away days out of the week; the records on them earn nothing, and the week's
     * token needs the days a week its Sunday has among the judged days.
     */
    @Test fun aChangedHolidayAnswerKeepsTheSpanAndNotDueTakesAwayDaysOutOfTheWeek() {
        val holidays = away(7, 8, 9, 10)
        val records = active(0, 1, 2, 3, 4, 7, 11, 13)
        // Four days away ask four and leave three free, which Tuesday to Thursday use; Saturday is a gap.
        assertEquals("KKKKKFF" + "KFFFKMK", records.statuses(14, holidays))

        val due = records.saved(9, ActivityEdit.SetWhenAway(WhenAway.Due))
        // Five asked from Wednesday on with two free days, one of them used on Tuesday: Wednesday
        // rests, and every empty day after it is a gap.
        assertEquals("KKKKKFF" + "KFFMKMK", due.statuses(14, holidays))
        assertEquals(MoDayMark.Protected, habitStreak(due, onlyActivity, on(14), holidays).mark(10).mark)
        assertEquals(MoDayMark.Protected, habitStreak(due, onlyActivity, on(14), holidays).mark(12).mark)

        val excused = records.saved(9, ActivityEdit.SetWhenAway(WhenAway.Excused))
        // Wednesday and Thursday are not the week's days; Friday to Sunday ask five less four, one, which Friday keeps.
        assertEquals("KKKKKFF" + "KF--KFK", excused.statuses(14, holidays))
        val streak = habitStreak(excused, onlyActivity, on(14), holidays)
        assertEquals(MoDayMark.NothingDue, streak.mark(9).mark)
        assertEquals(MoDayMark.NothingDue, streak.mark(10).mark)
        assertEquals(MoWeekProgress(3, 3), moMood(excused, onlyActivity, on(13), holidays).trackerWeekProgress[activityRef])
        // Three healthy, due days with a record do not reach the five the Sunday asks, so no token.
        assertEquals(emptyList<TrackerRef>(), streak.earnedTrackersYesterday)
        assertEquals(2, streak.standings.getValue(activityRef).passes)
        // The week's squares say the same: an away day not due rests, with a record on it or
        // without, and the count toward the token leaves it out.
        val loggedAway = active(0, 1, 2, 3, 4, 7, 9, 11, 13).saved(9, ActivityEdit.SetWhenAway(WhenAway.Excused))
        val progress = habitStreak(loggedAway, onlyActivity, on(13), holidays).standings.getValue(activityRef).nextToken!!
        assertEquals(
            listOf(MoWeekDay.Kept, MoWeekDay.Rested, MoWeekDay.Rested, MoWeekDay.Rested, MoWeekDay.Kept, MoWeekDay.Rested, MoWeekDay.Kept),
            progress.week,
        )
        assertEquals(MoTokenProgress(3, 5, progress.week), progress)
        // A week spent entirely away asks nothing and earns nothing, however active it was.
        val allAway = active(7, 8, 9, 10, 11, 12, 13).saved(7, ActivityEdit.SetWhenAway(WhenAway.Excused))
        val wholeWeek = HolidaySchedule(listOf(DayRange(on(7), on(13))))
        assertEquals("-------" + "-------", allAway.statuses(14, wholeWeek))
        assertEquals(emptyList<TrackerRef>(), habitStreak(allAway, onlyActivity, on(14), wholeWeek).earnedTrackersYesterday)
        assertEquals(MoWeekProgress(0, 0), moMood(allAway, onlyActivity, on(13), wholeWeek).trackerWeekProgress[activityRef])
    }

    /** A holiday week asking two earns no token for two: earning reads the days a week, not what the holidays lowered. */
    @Test fun aHolidayWeekAskingTwoEarnsNoTokenForTwo() {
        val state = active(0, 1, 2, 3, 4, 7, 8)
        val wholeWeek = HolidaySchedule(listOf(DayRange(on(7), on(13))))
        val streak = habitStreak(state, onlyActivity, on(14), wholeWeek)
        assertEquals("KKKKKFF" + "KKFFFFF", state.statuses(14, wholeWeek))
        assertEquals(MoWeekProgress(2, 2), moMood(state, onlyActivity, on(13), wholeWeek).trackerWeekProgress[activityRef])
        assertEquals(emptyList<TrackerRef>(), streak.earnedTrackersYesterday)
        assertEquals(2, streak.standings.getValue(activityRef).passes)
        assertTrue(MoEvent.WeekGoalReached(activityRef) in moEvents(moMood(state, onlyActivity, on(8), wholeWeek)))
    }

    /** A smaller bank holds no more than it allows from the day it is saved, and a larger one fills from then on. */
    @Test fun aChangedBankCountsFromTheDayItIsSavedAndResetsNoSpan() {
        val records = active(0, 1, 2, 3, 4, 7, 8, 9, 10, 11, 14, 15, 16, 17, 18)
        val smaller = records.saved(16, ActivityEdit.SetBankSize(1))
        // Two weeks earned a token each into a bank of three, which the third week only fills.
        assertEquals(3, habitStreak(records, onlyActivity, on(21)).standings.getValue(activityRef).passes)
        assertEquals(3, habitStreak(records, onlyActivity, on(15)).standings.getValue(activityRef).passes)
        assertEquals(1, habitStreak(smaller, onlyActivity, on(16)).standings.getValue(activityRef).passes)
        assertEquals(1, habitStreak(smaller, onlyActivity, on(21)).standings.getValue(activityRef).passCapacity)
        assertEquals(habitStreak(records, onlyActivity, on(15)), habitStreak(smaller, onlyActivity, on(15)))
        assertEquals(records.statuses(21), smaller.statuses(21))
    }

    /** Seven days a week has no free day and reaches its goal on the seventh, which Mo marks as a week goal. */
    @Test fun sevenDaysAWeekReachesItsGoalOnTheSeventhDay() {
        val every = active(0, 1, 2, 3, 4, 5, 6).saved(0, week(7))
        assertEquals("KKKKKKK", every.statuses(7))
        assertEquals(0, habitStreak(every, onlyActivity, on(3)).freeDaysLeftByTracker.getValue(activityRef))
        val sunday = moMood(every, onlyActivity, on(6))
        assertEquals(MoWeekProgress(7, 7), sunday.trackerWeekProgress[activityRef])
        assertTrue(MoEvent.WeekGoalReached(activityRef) in moEvents(sunday))
        assertEquals(2, habitStreak(every, onlyActivity, on(7)).standings.getValue(activityRef).passes)
    }

    /**
     * One step of one token at the week's days, which is what every installation starts with: a
     * week that reaches the days earns one token and no more however many days it keeps, a week
     * below them earns none, and the next token is the days themselves, before and after they are
     * reached. The steps a week earns by generalize this, so it is pinned before they do.
     */
    @Test fun oneStepOfOneTokenAtTheDaysJudgesAndShowsEveryWeekAsItDid() {
        // Week one keeps all seven days, week two exactly five, week three four.
        val state = active(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 14, 15, 16, 17)
        assertEquals(listOf(activityRef), habitStreak(state, onlyActivity, on(7)).earnedTrackersYesterday)
        assertEquals(2, habitStreak(state, onlyActivity, on(7)).standings.getValue(activityRef).passes)
        assertEquals(listOf(activityRef), habitStreak(state, onlyActivity, on(14)).earnedTrackersYesterday)
        assertEquals(3, habitStreak(state, onlyActivity, on(14)).standings.getValue(activityRef).passes)
        assertEquals(emptyList<TrackerRef>(), habitStreak(state, onlyActivity, on(21)).earnedTrackersYesterday)
        assertEquals(2, habitStreak(state, onlyActivity, on(21)).standings.getValue(activityRef).passes)
        fun nextToken(day: Int) = habitStreak(state, onlyActivity, on(day)).standings.getValue(activityRef).nextToken!!.copy(week = emptyList())
        assertEquals(MoTokenProgress(4, 5), nextToken(3))
        assertEquals(MoTokenProgress(5, 5), nextToken(5))
        assertEquals(MoTokenProgress(5, 5), nextToken(6))
        assertEquals(MoTokenProgress(4, 5), nextToken(18))
    }

    /**
     * With steps at the days and one day more, a week of five active days earns one token and a
     * week of six earns two, into a bank that holds them; the next token after five is six, and
     * once six are reached it is six still. Mo's sentence names the week's days, not a step.
     */
    @Test fun withStepsAtTheDaysAndOneDayMoreAWeekOfSixEarnsTwo() {
        val state = active(0, 1, 2, 3, 4, 7, 8, 9, 10, 11, 12)
            .saved(0, ActivityEdit.SetBankSize(5), ActivityEdit.SetTokenSteps(ActivityTokenSteps(listOf(ActivityTokenStep(0, 1), ActivityTokenStep(1, 1)))))
        assertEquals(2, habitStreak(state, onlyActivity, on(7)).standings.getValue(activityRef).passes)
        assertEquals(listOf(activityRef), habitStreak(state, onlyActivity, on(7)).earnedTrackersYesterday)
        assertEquals(4, habitStreak(state, onlyActivity, on(14)).standings.getValue(activityRef).passes)
        assertEquals(listOf(activityRef), habitStreak(state, onlyActivity, on(14)).earnedTrackersYesterday)
        fun nextToken(day: Int) = habitStreak(state, onlyActivity, on(day)).standings.getValue(activityRef).nextToken!!.copy(week = emptyList())
        assertEquals(MoTokenProgress(4, 5), nextToken(10))
        assertEquals(MoTokenProgress(5, 6), nextToken(11))
        assertEquals(MoTokenProgress(6, 6), nextToken(12))
        assertEquals(MoTokenProgress(6, 6), nextToken(13))
        assertEquals(5, InheritedMoEvaluation(state, onlyActivity, on(14)).mood.activityDaysForATokenYesterday)
        // Which days are chosen changes no judgment: the same records under other weekdays earn the same.
        val otherDays = state.saved(0, ActivityEdit.SetWeek(DefaultActivityWeek.withDays(setOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY))))
        (0..14).forEach { day -> assertEquals("day $day", habitStreak(state, onlyActivity, on(day)), habitStreak(otherDays, onlyActivity, on(day))) }
    }

    /**
     * The days not chosen decide the allowance, two with Monday to Friday; which dates rest is the
     * walk's to say, dealt in order to the empty days. With Monday and Tuesday empty and records
     * Wednesday to Sunday, Monday and Tuesday are the rested days and the week is kept.
     */
    @Test fun theRestDaysAreDealtInOrderToTheEmptyDays() {
        val state = active(0, 1, 2, 3, 4, 5, 6, 9, 10, 11, 12, 13)
        val streak = habitStreak(state, onlyActivity, on(14))
        assertEquals(listOf(true, true, false, false, false, false, false), (7..13).map { activityRef in streak.mark(it).restedTrackers })
        assertTrue((7..13).all { streak.mark(it).mark == MoDayMark.Perfect })
        assertEquals(listOf(activityRef), streak.earnedTrackersYesterday)
        assertEquals("KKKKKKK" + "FFKKKKK", state.statuses(14))
    }

    /** The configured holiday allowance applies from the first observed day, including a partial week. */
    @Test fun theLedgerAppliesTheConfiguredHolidayAllowanceFromItsFirstDay() {
        val fromThursday = active(3, 7)
        val holidays = HolidaySchedule(listOf(DayRange(on(0), on(13))))
        assertEquals("---KFFF" + "KFFFFFM", fromThursday.statuses(14, holidays))
        assertEquals("---KFFM" + "KFFMMMM", fromThursday.statuses(14))
    }
}
