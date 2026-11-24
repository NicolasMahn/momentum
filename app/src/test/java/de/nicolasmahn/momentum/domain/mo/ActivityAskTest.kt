package de.nicolasmahn.momentum.domain.mo

import de.nicolasmahn.momentum.domain.ActivityTrackerRef
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.TrackerRef
import de.nicolasmahn.momentum.domain.activity.ActivityEdit
import de.nicolasmahn.momentum.domain.activity.WeekPlan
import de.nicolasmahn.momentum.domain.activity.applyingActivityEdits
import de.nicolasmahn.momentum.domain.activity.withActivity
import de.nicolasmahn.momentum.domain.compatibility.MoNeed
import de.nicolasmahn.momentum.domain.trackerRef
import de.nicolasmahn.momentum.domain.reminder.Bedtime
import de.nicolasmahn.momentum.domain.reminder.ReminderSettings
import de.nicolasmahn.momentum.domain.reminder.ReminderTime
import de.nicolasmahn.momentum.domain.reminder.WorkoutReminderSettings
import de.nicolasmahn.momentum.feature.mo.acts.moScene
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Activity is asked for on the days its week plans, at the workout reminder, until the week is
 * done, and on a day it leaves out once the planned days left cannot finish the week. A free day still protects the streak, but it no longer hides a planned workout from his
 * anger: on 2026-10-14, a Wednesday of a Monday-to-Friday plan, Mo waved happily at 20:44 with the
 * workout still not done.
 */
class ActivityAskTest {
    private val monday = LocalDate.parse("2026-10-12")
    private fun on(day: Int) = monday.plusDays(day.toLong())
    private val activityOnly = only(MoNeed.Activity)
    private val workoutAtSix = ReminderSettings(workout = WorkoutReminderSettings(enabled = true, fixedTime = ReminderTime(18, 0)))

    /** Up at eight: getting ready for bed from 23:00. */
    private val bedtime = Bedtime(LocalTime.of(8, 0), fixedBedtime = null)

    /** Activity's seeded Monday-to-Friday week, active on each of [days]. */
    private fun active(vararg days: Int): AppState = days.fold(AppState().withActivity()) { state, day -> state.keeping(MoNeed.Activity, on(day)) }

    private fun AppState.planning(vararg days: java.time.DayOfWeek) = applyingActivityEdits(listOf(ActivityEdit.SetWeek(WeekPlan.of(*days))), on(0))

    private fun overdue(state: AppState, day: Int, at: LocalTime, reminders: ReminderSettings = workoutAtSix): List<TrackerRef> =
        moOverdueTrackers(moMood(state, activityOnly, on(day)), reminders, at)

    @Test fun aPlannedDayAFreeDayCoversIsOverdueAnHourAfterTheWorkoutReminder() {
        val state = active(0, 1)
        val wednesday = moMood(state, activityOnly, on(2))

        assertEquals(MoStatus.Free, MoLedger.of(state, activityOnly, on(2)).history(ActivityTrackerRef)[on(2)])
        assertEquals(listOf(ActivityTrackerRef), wednesday.freeTrackersToday)
        assertEquals(emptyList<TrackerRef>(), wednesday.missingTrackersToday)
        assertEquals(emptyList<TrackerRef>(), overdue(state, 2, LocalTime.of(18, 59)))
        assertEquals(listOf(ActivityTrackerRef), overdue(state, 2, LocalTime.of(19, 1)))
        val asked = moAskedTrackers(wednesday, workoutAtSix, LocalTime.of(19, 1))
        assertEquals(MoOccasion.Angry, moFeeling(wednesday, asked, on(2).atTime(19, 1), bedtime).occasion)
    }

    @Test fun withTheWorkoutReminderOffAPlannedDayIsAskedAtTheCheckIn() {
        val off = workoutAtSix.copy(workout = workoutAtSix.workout.copy(enabled = false))

        assertEquals(emptyList<TrackerRef>(), overdue(active(0, 1), 2, LocalTime.of(20, 59), off))
        assertEquals(listOf(ActivityTrackerRef), overdue(active(0, 1), 2, LocalTime.of(21, 0), off))
    }

    @Test fun aRestDayThePlannedDaysLeftCanFinishTheWeekIsNeverAskedFor() {
        // Monday, Wednesday and Friday: with Wednesday done, Friday alone finishes the week.
        val state = active(0, 2).planning(MONDAY, WEDNESDAY, FRIDAY)

        assertEquals(MoStatus.Free, MoLedger.of(state, activityOnly, on(3)).history(ActivityTrackerRef)[on(3)])
        assertEquals(emptyList<TrackerRef>(), overdue(state, 3, LocalTime.of(23, 0)))
        assertEquals(emptyMap<TrackerRef, List<Int>>(), moAskedTrackers(moMood(state, activityOnly, on(3)), workoutAtSix, LocalTime.of(23, 0)).waited)
    }

    // Wednesday, planned, was rested on, so Saturday or Sunday must be trained: Saturday is asked
    // at the check-in, since the workout reminder comes on planned days only.
    @Test fun aRestDayIsAskedForOnceAPlannedDayTookItsRest() {
        val state = active(0, 1, 3, 4)

        assertEquals(MoStatus.Free, MoLedger.of(state, activityOnly, on(5)).history(ActivityTrackerRef)[on(5)])
        assertEquals(emptyList<TrackerRef>(), overdue(state, 5, LocalTime.of(19, 1)))
        assertEquals(listOf(ActivityTrackerRef), overdue(state, 5, LocalTime.of(23, 0)))
    }

    @Test fun aPlannedDayIsNotAskedForOnceTheWeekIsDone() {
        val mondayWednesdayFriday = arrayOf(MONDAY, WEDNESDAY, FRIDAY)

        assertEquals(emptyList<TrackerRef>(), overdue(active(0, 1, 2).planning(*mondayWednesdayFriday), 4, LocalTime.of(19, 1)))
        assertEquals(listOf(ActivityTrackerRef), overdue(active(0, 1).planning(*mondayWednesdayFriday), 4, LocalTime.of(19, 1)))
    }

    /**
     * Whether Wednesday is asked for changes nothing a reward reads: with the same records, a plan
     * that rests on Wednesday deals the same free day, the same streak and the same tokens.
     */
    @Test fun askingForAPlannedDayLeavesTheStreakAndItsRewardsAsTheyAre() {
        val planned = active(0, 1)
        val resting = planned.planning(MONDAY, TUESDAY, THURSDAY, FRIDAY, SATURDAY)

        assertEquals(listOf(ActivityTrackerRef), overdue(planned, 2, LocalTime.of(19, 1)))
        assertEquals(emptyList<TrackerRef>(), overdue(resting, 2, LocalTime.of(19, 1)))
        assertEquals(MoLedger.of(planned, activityOnly, on(2)).history(ActivityTrackerRef), MoLedger.of(resting, activityOnly, on(2)).history(ActivityTrackerRef))
        val plannedMood = moMood(planned, activityOnly, on(2))
        val restingMood = moMood(resting, activityOnly, on(2))
        assertEquals(plannedMood.streak, restingMood.streak)
        assertEquals(plannedMood.freeTrackersToday, restingMood.freeTrackersToday)
        assertEquals(plannedMood.trackerTokenRequirements, restingMood.trackerTokenRequirements)
        assertNotEquals(plannedMood.workoutRemindedTrackers, restingMood.workoutRemindedTrackers)
    }

    /** A late save of another task at 20:44 leaves the workout open, and his day angry. */
    @Test fun saveOfOneTaskLeavesHimAngryWhileAnotherIsOverdue() {
        val visible = only(MoNeed.Activity, MoNeed.Weight)
        val before = active(0, 1).keeping(MoNeed.Weight, on(0)).keeping(MoNeed.Weight, on(1))
        val after = before.keeping(MoNeed.Weight, on(2))
        val now = on(2).atTime(20, 44)
        val mood = moMood(after, visible, on(2))
        val asked = moAskedTrackers(mood, workoutAtSix, now.toLocalTime())

        assertEquals(listOf(MoNeed.Weight.view.trackerRef), mood.keptTrackersToday)
        assertEquals(listOf(ActivityTrackerRef), asked.overdueBy.keys.toList())
        assertEquals(MoOccasion.Angry, moFeeling(mood, asked, now, bedtime).occasion)
        assertEquals(MoOccasion.Angry, moScene(mood, asked, now, bedtime)?.act?.occasion)
    }
}
