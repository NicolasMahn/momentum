package com.example.workouttracker.domain.mo

import com.example.workouttracker.domain.compatibility.free

import com.example.workouttracker.domain.compatibility.kept

import com.example.workouttracker.domain.compatibility.weekProgress

import com.example.workouttracker.domain.compatibility.history

import com.example.workouttracker.domain.compatibility.needs

import com.example.workouttracker.domain.compatibility.mood

import com.example.workouttracker.domain.compatibility.MoNeed

import com.example.workouttracker.domain.mo.compatibility.withInheritedTrackerConfigurations
import com.example.workouttracker.domain.mo.isFrozen
import com.example.workouttracker.domain.trackerRef
import com.example.workouttracker.domain.ActivityTrackerRef
import com.example.workouttracker.domain.measurement.recordsOf

import com.example.workouttracker.domain.compatibility.ReadingTimeId
import com.example.workouttracker.domain.time.TimeLog
import com.example.workouttracker.domain.settings.showingOnly
import com.example.workouttracker.R
import com.example.workouttracker.core.text.uiText
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.activity.ActivityEntry
import com.example.workouttracker.domain.holiday.DayRange
import com.example.workouttracker.domain.holiday.HolidaySchedule
import com.example.workouttracker.domain.measurement.asMeasurementLogs
import com.example.workouttracker.domain.measurement.MeasurementRecord
import com.example.workouttracker.feature.mo.line
import com.example.workouttracker.domain.reminder.Bedtime
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** A sick day asks nothing of any view: it spends nothing, earns nothing, and leaves the streak as it was. */
class SickDaysTest {
    private val monday = LocalDate.parse("2026-09-28")
    private val activityRef = ActivityTrackerRef
    private val weightRef = MoNeed.Weight.view.trackerRef
    private fun visible(vararg needs: MoNeed) = showingOnly(*needs)
    private fun on(day: Int) = monday.plusDays(day.toLong())
    private fun weight(day: Int) = MeasurementRecord(on(day).toString(), day.toLong(), 75.0)
    private fun activity(day: Int) = ActivityEntry("a$day", "run", "Run", on(day).toString(), day.toLong())
    private fun sick(from: Int, to: Int = from) = HolidaySchedule().withSickDays(DayRange(on(from), on(to)))

    /**
     * Three days without a weigh-in cost the pass, the freeze and then the streak. Declared sick
     * afterwards, they cost nothing, and the streak carries on from where it was.
     */
    @Test fun sickDaysSpendNoPassOrFreezeAndTheStreakCarriesOn() {
        val state = AppState(measurementLogs = listOf(weight(0), weight(4)).asMeasurementLogs("weight"))
            .withInheritedTrackerConfigurations()
        val today = on(5)

        val healthy = habitStreak(state, visible(MoNeed.Weight), today)
        assertEquals(listOf(MoDayMark.Perfect, MoDayMark.Protected, MoDayMark.Frozen, MoDayMark.Missed, MoDayMark.Perfect), healthy.week.take(5))
        assertEquals(1, healthy.days)

        val sick = habitStreak(state, visible(MoNeed.Weight), today, sick(1, 3))
        assertEquals(listOf(MoDayMark.Perfect, MoDayMark.Sick, MoDayMark.Sick, MoDayMark.Sick, MoDayMark.Perfect), sick.week.take(5))
        assertEquals(2, sick.days)
        assertEquals(1, sick.standings.getValue(weightRef).passes)
        assertEquals(1, sick.freezes)
        // Being sick changes no record.
        assertEquals(2, state.recordsOf("weight").size)
    }

    /** A sick day asks nothing even when everything was done: the streak does not grow on it either. */
    @Test fun aSickDayDoesNotGrowTheStreakWhateverWasDone() {
        val state = AppState(measurementLogs = (0..3).map(::weight).asMeasurementLogs("weight"))
            .withInheritedTrackerConfigurations()
        val streak = habitStreak(state, visible(MoNeed.Weight), on(4), sick(1))
        assertEquals(listOf(MoDayMark.Perfect, MoDayMark.Sick, MoDayMark.Perfect, MoDayMark.Perfect), streak.week.take(4))
        assertEquals(3, streak.days)
    }

    /**
     * Activity asks five days a week. Sick on Tuesday and Wednesday, the week's two free days stay
     * for the weekend, so an empty Saturday and Sunday cost nothing.
     */
    @Test fun aWeeksFreeDaysStayForTheDaysNotSick() {
        val state = AppState(activityEntries = listOf(activity(0), activity(3), activity(4)))
            .withInheritedTrackerConfigurations()
        val nextMonday = on(7)

        val healthy = habitStreak(state, visible(MoNeed.Activity), nextMonday)
        assertEquals(0, healthy.freezes)

        val sick = habitStreak(state, visible(MoNeed.Activity), nextMonday, sick(1, 2))
        assertEquals(1, sick.freezes)
        assertEquals(1, sick.standings.getValue(activityRef).passes)
        assertEquals(
            listOf(setOf(activityRef), setOf(activityRef)),
            sick.history.filter { it.date >= on(5) && it.date < nextMonday }.map { it.restedTrackers },
        )
    }

    /** Reading's sixty minutes shrink by a seventh for each sick day, and a sick Sunday leaves the week unjudged. */
    @Test fun readingAsksLessInASickWeekAndASickSundayJudgesNothing() {
        val state = AppState(timeLogs = listOf(TimeLog(ReadingTimeId, on(3).toString(), 1L, 35)))
            .withInheritedTrackerConfigurations()
        val nextMonday = on(7)

        val healthy = habitStreak(state, visible(MoNeed.Reading), nextMonday)
        assertEquals(MoDayMark.Frozen, healthy.history.single { it.date == on(6) }.mark)

        val sickThreeDays = habitStreak(state, visible(MoNeed.Reading), nextMonday, sick(0, 2))
        assertEquals(MoDayMark.Perfect, sickThreeDays.history.single { it.date == on(6) }.mark)

        val sickSunday = habitStreak(
            AppState(timeLogs = listOf(TimeLog(ReadingTimeId, on(3).toString(), 1L, 5))).withInheritedTrackerConfigurations(),
            visible(MoNeed.Reading),
            nextMonday,
            sick(6),
        )
        assertEquals(MoDayMark.Sick, sickSunday.history.single { it.date == on(6) }.mark)
        assertEquals(1, sickSunday.freezes)
    }

    /** Sick today, nothing is missing, so Mo neither asks nor fumes, and his check-in has nothing to say. */
    @Test fun moAsksNothingOnASickDay() {
        val state = AppState(measurementLogs = listOf(weight(0)).asMeasurementLogs("weight"))
            .withInheritedTrackerConfigurations()
        val today = on(1)
        assertEquals(listOf(weightRef), moMood(state, visible(MoNeed.Weight), today).missingTrackersToday)

        val mood = moMood(state, visible(MoNeed.Weight), today, sick(1))
        assertEquals(emptyList<com.example.workouttracker.domain.TrackerRef>(), mood.missingTrackersToday)
        assertEquals(0, mood.dueToday)
        assertEquals(MoDayMark.Sick, mood.streak.week[1])
    }

    /** A freeze spent yesterday seals Mo in ice until something is logged, but a sick today asks not even that. */
    @Test fun aSickDayAsksNoThawEither() {
        val state = AppState(measurementLogs = listOf(weight(0)).asMeasurementLogs("weight"))
            .withInheritedTrackerConfigurations()
        val today = on(3)
        val frozen = moMood(state, visible(MoNeed.Weight), today)
        assertEquals(listOf(weightRef), frozen.streak.frozenTrackersYesterday)
        assertEquals(true, frozen.isFrozen)

        val sick = moMood(state, visible(MoNeed.Weight), today, sick(3))
        assertEquals(false, sick.isFrozen)
        assertEquals(uiText(R.string.mo_line_sick), sick.line())
        // His act follows the same answer, or one made for the ice would play round a Mo with no block.
        val noon = today.atTime(12, 0)
        val bedtime = Bedtime(LocalTime.of(7, 0), fixedBedtime = null)
        assertEquals(MoOccasion.Frozen, moOccasion(frozen, TrackerAsked(), noon, bedtime))
        assertNotEquals(MoOccasion.Frozen, moOccasion(sick, TrackerAsked(), noon, bedtime))
    }

    /**
     * Sick on Tuesday and Wednesday, Activity's week asks three days, as the walk judges it, so
     * Friday's third activity reaches the week's goal. The days actually recorded stay three.
     */
    @Test fun activitysWeekAsksADayLessForEachSickDay() {
        val state = AppState(activityEntries = listOf(activity(0), activity(3), activity(4)))
            .withInheritedTrackerConfigurations()
        val friday = on(4)
        assertEquals(MoWeekProgress(3, 5), moMood(state, visible(MoNeed.Activity), friday).trackerWeekProgress[activityRef])

        val sick = moMood(state, visible(MoNeed.Activity), friday, sick(1, 2))
        assertEquals(MoWeekProgress(3, 3), sick.trackerWeekProgress[activityRef])
        assertEquals(true, MoEvent.WeekGoalReached(activityRef) in moEvents(sick))
    }

    /**
     * Activity recorded on a sick day is still recorded, but it keeps nothing: with Tuesday and
     * Wednesday sick and activity on Monday, Tuesday and Friday, two healthy days of three are kept,
     * so the week is not complete, as the streak will judge it.
     */
    @Test fun activityRecordedWhileSickKeepsNoDayOfTheWeek() {
        val state = AppState(activityEntries = listOf(activity(0), activity(1), activity(4)))
            .withInheritedTrackerConfigurations()
        val mood = moMood(state, visible(MoNeed.Activity), on(4), sick(1, 2))
        assertEquals(MoWeekProgress(2, 3), mood.trackerWeekProgress[activityRef])
        assertEquals(false, MoEvent.WeekGoalReached(activityRef) in moEvents(mood))
        assertEquals(3, com.example.workouttracker.domain.activity.activityDaysForWeek(state.activityEntries, state.sessions, monday))
    }

    /** A weigh-in recorded while sick earns nothing toward Weight's week, because nothing was asked. */
    @Test fun aRecordMadeWhileSickEarnsNothing() {
        val state = AppState(measurementLogs = (0..3).map(::weight).asMeasurementLogs("weight"))
            .withInheritedTrackerConfigurations()
        val progress = habitStreak(state, visible(MoNeed.Weight), on(3), sick(1)).standings.getValue(weightRef).nextToken!!
        assertEquals(3, progress.done)
        assertEquals(MoWeekDay.Sick, progress.week[1])
    }
}
