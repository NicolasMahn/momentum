package com.example.workouttracker.domain.mo

import com.example.workouttracker.domain.compatibility.free

import com.example.workouttracker.domain.compatibility.kept

import com.example.workouttracker.domain.compatibility.weekProgress

import com.example.workouttracker.domain.compatibility.history

import com.example.workouttracker.domain.compatibility.needs

import com.example.workouttracker.domain.compatibility.mood

import com.example.workouttracker.domain.compatibility.MoNeed

import com.example.workouttracker.domain.mo.compatibility.withInheritedTrackerConfigurations
import com.example.workouttracker.domain.trackerRef
import com.example.workouttracker.domain.mo.compatibility.inheritedHabitStreak
import com.example.workouttracker.domain.mo.compatibility.inheritedMoMood
import com.example.workouttracker.domain.mo.compatibility.InheritedDayReport

import com.example.workouttracker.domain.measurement.recordsOf

import com.example.workouttracker.domain.questions.eatingEntry
import com.example.workouttracker.domain.compatibility.ReadingTimeId
import com.example.workouttracker.domain.time.TimeLog
import com.example.workouttracker.domain.settings.showingOnly
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.activity.ActivityEntry
import com.example.workouttracker.domain.compatibility.LegacyCareAction
import com.example.workouttracker.domain.care.CareDone
import com.example.workouttracker.domain.care.careState
import com.example.workouttracker.domain.compatibility.LegacyCareSession
import com.example.workouttracker.domain.holiday.DayRange
import com.example.workouttracker.domain.holiday.HolidaySchedule
import com.example.workouttracker.domain.measurement.asMeasurementLogs
import com.example.workouttracker.domain.settings.FeatureVisibilitySettings
import com.example.workouttracker.domain.supplement.AllSupplementIds
import com.example.workouttracker.domain.supplement.supplementDay
import com.example.workouttracker.domain.supplement.withSupplements
import com.example.workouttracker.domain.measurement.MeasurementRecord
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import com.example.workouttracker.domain.activity.withActivity
import com.example.workouttracker.domain.measurement.withWeight
import com.example.workouttracker.domain.questions.withEating
import com.example.workouttracker.domain.time.withReading
import org.junit.Test
import com.example.workouttracker.domain.activity.ActivityEdit
import com.example.workouttracker.domain.activity.applyingActivityEdits
import com.example.workouttracker.domain.checklist.RewardPeriod
import com.example.workouttracker.domain.time.TimeEdit
import com.example.workouttracker.domain.time.applyingTimeEdits
import com.example.workouttracker.domain.compatibility.readingRules

class HabitRewardsTest {
    private val monday = LocalDate.parse("2026-09-28")
    private fun visible(vararg needs: MoNeed) = showingOnly(*needs)
    private fun weight(day: Int) = MeasurementRecord(monday.plusDays(day.toLong()).toString(), day.toLong(), 75.0)
    private fun activity(day: Int) = ActivityEntry("a$day", "run", "Run", monday.plusDays(day.toLong()).toString(), day.toLong())

    /** A task not taken up yet is not due, but its bank still shows how far the first token is. */
    @Test fun everyBankShowsItsNextTokenBeforeItsTaskIsTakenUp() {
        val streak = inheritedHabitStreak(AppState(), FeatureVisibilitySettings(), monday)
        assertEquals(MoDayMark.NothingDue, streak.week.first())
        assertEquals(MoNeed.entries.toSet(), streak.nextToken.keys)
        assertEquals(MoTokenProgress(0, 7), streak.nextToken[MoNeed.Supplements])
        assertEquals(MoTokenProgress(0, 60), streak.nextToken[MoNeed.Reading])
        assertEquals(0, streak.nextToken.getValue(MoNeed.Activity).done)
        assertEquals(MoWeekDay.Open, streak.nextToken.getValue(MoNeed.Weight).week.first())
    }

    @Test fun oneMissedWeighInSpendsOnlyItsPassAndHoldsTheStreak() {
        val state = AppState(measurementLogs = listOf(weight(0), weight(2)).asMeasurementLogs("weight"))
        val streak = inheritedHabitStreak(state, visible(MoNeed.Weight), monday.plusDays(2))
        assertEquals(2, streak.days) // Monday + Wednesday; Tuesday held the number at one.
        assertEquals(0, streak.passes[MoNeed.Weight])
        assertEquals(1, streak.freezes)
    }

    @Test fun twoMissingCareSessionsUseOneFreezeWithoutWastingTheCarePass() {
        val completeMonday = LegacyCareSession.entries.flatMap { session ->
            listOf(LegacyCareAction.TOOTHBRUSHING, LegacyCareAction.HAIR_FOAM).map { action -> CareDone(monday.toString(), session, action, 1L) }
        }
        val state = careState(*completeMonday.toTypedArray())
        val streak = inheritedHabitStreak(state, visible(MoNeed.Care), monday.plusDays(2))
        assertEquals(1, streak.days)
        assertEquals(0, streak.freezes)
        assertEquals(1, streak.passes[MoNeed.Care])
        assertEquals(listOf(MoNeed.Care), streak.frozenYesterday)
    }

    @Test fun readingIsJudgedOnceAtWeekCloseAndTwoPassesCoverThirtyMinutes() {
        val thirty = AppState(timeLogs = listOf(TimeLog(ReadingTimeId, monday.toString(), 1L, 30)))
        val onePass = inheritedHabitStreak(thirty, visible(MoNeed.Reading), monday.plusDays(7))
        assertEquals(0, onePass.freezes)
        assertEquals(1, onePass.passes[MoNeed.Reading])
        val sixty = AppState(timeLogs = listOf(TimeLog(ReadingTimeId, monday.toString(), 1L, 60)))
        val earned = inheritedHabitStreak(sixty, visible(MoNeed.Reading), monday.plusDays(7))
        assertEquals(2, earned.passes[MoNeed.Reading])
    }

    @Test fun bankedReadingPassesCoverThirtyMinutesWithoutAddingFakeMinutes() {
        val state = AppState(timeLogs = listOf(
            TimeLog(ReadingTimeId, monday.toString(), 1L, 60),
            TimeLog(ReadingTimeId, monday.plusDays(7).toString(), 2L, 30),
        ))
        val streak = inheritedHabitStreak(state, visible(MoNeed.Reading), monday.plusDays(14))
        assertEquals(0, streak.passes[MoNeed.Reading])
        assertEquals(1, streak.freezes)
        assertEquals(1, streak.days)
        assertEquals(30, com.example.workouttracker.domain.time.minutesInWeek(state.timeLogs, ReadingTimeId, monday.plusDays(7)))
    }

    @Test fun choosingAWeightPassQuietsTodayWithoutInventingARecord() {
        val state = AppState(measurementLogs = listOf(weight(0)).asMeasurementLogs("weight"))
        val today = monday.plusDays(1)
        val mood = inheritedMoMood(state, visible(MoNeed.Weight), today, chosenPasses = setOf(MoNeed.Weight))
        assertEquals(emptyList<MoNeed>(), mood.missingToday)
        assertEquals(setOf(MoNeed.Weight), mood.chosenToday)
        assertEquals(1, mood.streak.passes[MoNeed.Weight])
        assertEquals(1, mood.streak.days)
    }

    /** Weight is excused on holiday: a weigh-in made anyway counts for nothing at the day's close, so it counts for nothing today either. */
    @Test fun aWeighInOnAHolidayCountsForNothingTodayAsAtItsClose() {
        val holiday = HolidaySchedule(listOf(DayRange(monday.plusDays(2), monday.plusDays(2))))
        val state = AppState(measurementLogs = listOf(weight(0), weight(1), weight(2)).asMeasurementLogs("weight"))
        val wednesday = inheritedHabitStreak(state, visible(MoNeed.Weight), monday.plusDays(2), holiday)
        val thursday = inheritedHabitStreak(state, visible(MoNeed.Weight), monday.plusDays(3), holiday)

        assertEquals(MoTokenProgress(2, 6, listOf(MoWeekDay.Kept, MoWeekDay.Kept, MoWeekDay.Rested, MoWeekDay.Upcoming, MoWeekDay.Upcoming, MoWeekDay.Upcoming, MoWeekDay.Upcoming)), wednesday.nextToken[MoNeed.Weight])
        assertEquals(2, thursday.nextToken.getValue(MoNeed.Weight).done)
        assertEquals(MoWeekDay.Rested, thursday.nextToken.getValue(MoNeed.Weight).week[2])
    }

    @Test fun weeklyRestGrowsStreakButHolidayTwoActivitiesEarnNoSavedPass() {
        val state = AppState(activityEntries = listOf(activity(0), activity(3)))
        val holiday = HolidaySchedule(listOf(DayRange(monday, monday.plusDays(6))))
        val streak = inheritedHabitStreak(state, visible(MoNeed.Activity), monday.plusDays(7), holiday)
        assertEquals(7, streak.days)
        assertEquals(1, streak.passes[MoNeed.Activity])
        assertEquals(1, streak.freezes)
    }



    @Test fun daysBeforeTheRulesStartedEarnNoFreeMondays() {
        // Weighed once, then nothing for two weeks. The old rules would have kept the next Monday free.
        val state = AppState(measurementLogs = listOf(weight(0)).asMeasurementLogs("weight"))
        val start = monday.plusDays(14)
        val streak = inheritedHabitStreak(state, visible(MoNeed.Weight), start, rewardStart = start)
        val marks = streak.history.associate { it.date to it.mark }

        assertEquals(MoDayMark.Protected, marks[monday.plusDays(1)]) // The one starting token.
        assertEquals(MoDayMark.Frozen, marks[monday.plusDays(2)]) // The one starting freeze.
        assertEquals(MoDayMark.Missed, marks[monday.plusDays(7)])
        assertEquals(0, streak.days)
    }

    @Test fun weeklyHeadersReflectCompletedReadingAndTheHolidayActivityTargetBeforeSunday() {
        val saturday = monday.plusDays(5)
        val state = AppState(
            timeLogs = listOf(TimeLog(ReadingTimeId, saturday.toString(), 1L, 60)),
            activityEntries = listOf(activity(0), activity(2)),
        )
        val holiday = HolidaySchedule(listOf(DayRange(monday, monday.plusDays(2))))
        val mood = inheritedMoMood(state, visible(MoNeed.Reading, MoNeed.Activity), saturday, holiday)
        assertEquals(MoWeekProgress(1, 1), mood.weekProgress[MoNeed.Reading])
        assertEquals(MoWeekProgress(2, 4), mood.weekProgress[MoNeed.Activity])
        assertEquals(MoStatus.Kept, MoLedger.of(state.withInheritedTrackerConfigurations(), visible(MoNeed.Activity), saturday, holiday).history(MoNeed.Activity)[monday])
    }

    @Test fun aWeekWhoseFirstRecordIsMidweekStillHasSevenDays() {
        val saturday = monday.plusDays(5)
        val state = AppState(measurementLogs = (3..5).map(::weight).asMeasurementLogs("weight"))
        val streak = inheritedHabitStreak(state, visible(MoNeed.Weight), saturday)
        assertEquals(7, streak.week.size)
        assertEquals(MoDayMark.NothingDue, streak.week[0])
        assertEquals(MoDayMark.Perfect, streak.week[5])
    }

    @Test fun theMorningAfterAWeekCloseKnowsWhatItEarned() {
        val weighedSixDays = AppState(measurementLogs = (0..5).map(::weight).asMeasurementLogs("weight"))
        val nextMonday = monday.plusDays(7)
        val streak = inheritedHabitStreak(weighedSixDays, visible(MoNeed.Weight), nextMonday)
        assertEquals(listOf(MoNeed.Weight), streak.earnedYesterday)
        // A day later the token is simply in the bank; it is no longer news.
        assertEquals(emptyList<MoNeed>(), inheritedHabitStreak(weighedSixDays, visible(MoNeed.Weight), nextMonday.plusDays(1)).earnedYesterday)
        val mood = inheritedMoMood(weighedSixDays, visible(MoNeed.Weight), nextMonday)
        assertEquals(true, MoEvent.TokenEarned(MoNeed.Weight.view.trackerRef) in moEvents(moMood(weighedSixDays.withInheritedTrackerConfigurations(), visible(MoNeed.Weight), nextMonday)))
    }

    @Test fun theHistoryKnowsWhoseTokenCoveredADayAndNeverCallsItDone() {
        val state = AppState(measurementLogs = listOf(weight(0), weight(2)).asMeasurementLogs("weight"))
        val streak = inheritedHabitStreak(state, visible(MoNeed.Weight), monday.plusDays(3))
        val byDate = streak.history.associateBy { it.date }

        assertEquals(MoDayMark.Perfect, byDate.getValue(monday).mark)
        assertEquals(InheritedDayReport(monday.plusDays(1), MoDayMark.Protected, covered = setOf(MoNeed.Weight)), byDate.getValue(monday.plusDays(1)))
        assertEquals(monday.plusDays(3), streak.history.last().date)
        // A covered day is no weigh-in: the records are untouched.
        assertEquals(2, state.recordsOf("weight").size)
    }

    @Test fun theNextTokenCountsTodayOnceKeptAndAWeeklyGoalStartsOverOnMonday() {
        val taken = AllSupplementIds
        val state = AppState(
            measurementLogs = (0..3).map(::weight).asMeasurementLogs("weight"),
        ).withSupplements(*(0..3).map { supplementDay(monday.plusDays(it.toLong()).toString(), it.toLong(), taken) }.toTypedArray())
        val thursday = inheritedHabitStreak(state, visible(MoNeed.Supplements, MoNeed.Weight), monday.plusDays(3))
        assertEquals(MoTokenProgress(4, 7), thursday.nextToken[MoNeed.Supplements])
        val kept = MoWeekDay.Kept
        val upcoming = MoWeekDay.Upcoming
        assertEquals(
            MoTokenProgress(4, 6, listOf(kept, kept, kept, kept, upcoming, upcoming, upcoming)),
            thursday.nextToken[MoNeed.Weight],
        )

        val nextMonday = inheritedHabitStreak(state, visible(MoNeed.Supplements, MoNeed.Weight), monday.plusDays(7))
        assertEquals(0, nextMonday.nextToken.getValue(MoNeed.Weight).done)
        assertEquals(MoWeekDay.Open, nextMonday.nextToken.getValue(MoNeed.Weight).week.first())
    }

    /**
     * An implicit timer history judges its first partial week by recorded minutes; Activity's
     * week counts its recorded days. Closing the timer's implicit period materializes its first
     * Monday so leaving and rejoining cannot rewrite that Sunday's outcome.
     */
    @Test fun readingAndActivityFirstLoggedOnAWednesdayJudgeAndEarnThatSunday() {
        val wednesday = monday.plusDays(2)
        val state = AppState(
            timeLogs = listOf(TimeLog(ReadingTimeId, wednesday.toString(), 1L, 60)),
            activityEntries = (2..6).map(::activity),
        )
        val streak = inheritedHabitStreak(state, visible(MoNeed.Reading, MoNeed.Activity), monday.plusDays(7))
        assertEquals(MoDayMark.Perfect, streak.history.single { it.date == monday.plusDays(6) }.mark)
        assertEquals(setOf(MoNeed.Activity, MoNeed.Reading), streak.earnedYesterday.toSet())
        assertEquals(2, streak.passes[MoNeed.Reading])
        assertEquals(2, streak.passes[MoNeed.Activity])
        assertEquals(5, streak.days)

        // Leaving the next month and rejoining the day after leaves that first week exactly as it was.
        val leaveDay = monday.plusDays(37)
        val back = state.withReading().withActivity()
            .applyingTimeEdits("reading", listOf(TimeEdit.LeaveRewards), leaveDay).applyingTimeEdits("reading", listOf(TimeEdit.JoinRewards), leaveDay.plusDays(1))
            .applyingActivityEdits(listOf(ActivityEdit.LeaveRewards), leaveDay).applyingActivityEdits(listOf(ActivityEdit.JoinRewards), leaveDay.plusDays(1))
        assertEquals(listOf(RewardPeriod(monday, leaveDay.minusDays(1)), RewardPeriod(leaveDay.plusDays(1))), back.readingRules.rewards?.periods)
        val rejoined = inheritedHabitStreak(back, visible(MoNeed.Reading, MoNeed.Activity), monday.plusDays(7))
        assertEquals(streak.history, rejoined.history)
        assertEquals(streak.passes, rejoined.passes)
        assertEquals(streak.earnedYesterday, rejoined.earnedYesterday)
    }

    /** The six default configurations, none with periods, judge exactly as the fallbacks judge a state that holds none: the walk, the ledger and the mood. */
    @Test fun theSixDefaultConfigurationsWithoutPeriodsJudgeAsTheFallbacksDo() {
        val bare = generatedHistory(31, LocalDate.parse("2026-08-03"), monday.plusDays(20), adherence = 0.8)
        val configured = bare.withWeight().withEating().withReading().withActivity()
        val everything = FeatureVisibilitySettings()
        (0..21).map { monday.plusDays(it.toLong()) }.forEach { today ->
            assertEquals("$today", inheritedHabitStreak(bare, everything, today), inheritedHabitStreak(configured, everything, today))
            assertEquals("$today", inheritedMoMood(bare, everything, today).outcome(), inheritedMoMood(configured, everything, today).outcome())
            MoNeed.entries.forEach { need ->
                assertEquals("$today $need", MoLedger.of(bare, everything, today).history(need), MoLedger.of(configured, everything, today).history(need))
            }
        }
    }

    @Test fun aPlannedRestDayIsInTheHistoryAsRest() {
        val state = AppState(activityEntries = listOf(activity(0), activity(2)))
        val streak = inheritedHabitStreak(state, visible(MoNeed.Activity), monday.plusDays(3))

        assertEquals(true, streak.history.single { it.date == monday.plusDays(1) }.rested)
        assertEquals(MoDayMark.Perfect, streak.history.single { it.date == monday.plusDays(1) }.mark)
    }
}
