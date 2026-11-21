package com.example.workouttracker.domain.mo

import com.example.workouttracker.domain.compatibility.free

import com.example.workouttracker.domain.compatibility.kept

import com.example.workouttracker.domain.compatibility.needs

import com.example.workouttracker.domain.compatibility.mood

import com.example.workouttracker.domain.compatibility.MoNeed

import com.example.workouttracker.domain.CheckpointPart
import com.example.workouttracker.domain.TrackerRef
import com.example.workouttracker.domain.trackerRef
import com.example.workouttracker.domain.reminder.MoCheckInSettings
import com.example.workouttracker.domain.reminder.ReminderSettings
import com.example.workouttracker.domain.reminder.ReminderTime
import com.example.workouttracker.domain.reminder.WorkoutReminderSettings
import com.example.workouttracker.domain.care.testCareChecklist
import com.example.workouttracker.domain.supplement.testSupplementsChecklist
import com.example.workouttracker.domain.checklist.Checkpoint
import com.example.workouttracker.domain.checklist.CheckpointTime
import com.example.workouttracker.domain.compatibility.defaultWeightCheckpoint
import com.example.workouttracker.domain.compatibility.defaultEatingCheckpoint
import com.example.workouttracker.domain.compatibility.defaultReadingCheckpoint
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class MoOverdueTest {
    /**
     * Wake-relative reminders follow 08:00, the workout is asked for at 18:00, Mo checks in at
     * 20:00. Each feature reminder is set on the checkpoint configuration in the mood below.
     */
    private val allReminders = ReminderSettings(
        workout = WorkoutReminderSettings(enabled = true),
    )

    @Test
    fun heActsATaskOutAQuarterHourAfterItsReminderAndIsAngryAnHourAfter() {
        val mood = mood(missing = listOf(MoNeed.Activity))
        fun at(time: String) = moAsked(mood, allReminders, LocalTime.parse(time))
        fun waited(minutes: Int) = mapOf(MoNeed.Activity.view.trackerRef to listOf(minutes))

        assertEquals(TrackerAsked(), at("18:14:59"))
        assertEquals(TrackerAsked(waited(15)), at("18:15"))
        assertEquals(TrackerAsked(waited(59)), at("18:59:59"))
        assertEquals(TrackerAsked(waited(60), mapOf(MoNeed.Activity.view.trackerRef to 60)), at("19:00"))
        assertEquals(TrackerAsked(waited(359), mapOf(MoNeed.Activity.view.trackerRef to 359)), at("23:59"))
    }

    @Test
    fun eachOpenCarePartIsAskedForByItsOwnReminder() {
        val morningDone = mood(missing = listOf(MoNeed.Care)).copy(trackerOpenParts = mapOf(MoNeed.Care.view.trackerRef to listOf(CheckpointPart("evening"))))
        val neitherDone = mood(missing = listOf(MoNeed.Care))
        fun parts(mood: MoMood, time: String) = moAsked(mood, allReminders, LocalTime.parse(time)).waited[MoNeed.Care.view.trackerRef]?.size

        // Evening Care is asked for at 23:45 and acted out from midnight.
        assertEquals(null, parts(morningDone, "15:00"))
        assertEquals(null, parts(morningDone, "23:59"))
        assertEquals(1, parts(morningDone, "00:00"))
        assertEquals(1, parts(neitherDone, "15:00"))
        assertEquals(2, parts(neitherDone, "00:00"))
    }

    @Test
    fun remindersThatFollowTheAlarmAreLateAfterTheirOffsetFromTheFallbackWakeTime() {
        val mood = mood(
            missing = listOf(MoNeed.Reading, MoNeed.Supplements, MoNeed.Eating, MoNeed.Weight),
        )
        val reminders = allReminders.copy(fallbackAlarmTime = ReminderTime(6, 30))

        val expected = mapOf(
            "07:34" to emptyList(),
            "07:35" to listOf(MoNeed.Weight),
            "07:44" to listOf(MoNeed.Weight),
            "07:45" to listOf(MoNeed.Reading, MoNeed.Eating, MoNeed.Weight),
            "13:29" to listOf(MoNeed.Reading, MoNeed.Eating, MoNeed.Weight),
            "13:30" to listOf(MoNeed.Reading, MoNeed.Supplements, MoNeed.Eating, MoNeed.Weight),
        )

        assertEquals(expected.mapValues { (_, needs) -> needs.map { it.view.trackerRef } }, expected.keys.associateWith { overdueAt(it, mood, reminders) })
    }

    @Test
    fun aLateStartFindsEverythingWhoseTimeHasPassedAndNothingStillToCome() {
        val mood = mood(missing = MoNeed.entries)

        assertEquals(
            MoNeed.entries.filterNot { it == MoNeed.Activity }.map { it.view.trackerRef },
            overdueAt("15:00", mood, allReminders),
        )
        assertEquals(MoNeed.entries.map { it.view.trackerRef }, overdueAt("21:00", mood, allReminders))
    }

    @Test
    fun aTaskWithoutAnEnabledReminderFallsToTheCheckInTimeEvenWhileTheCheckInIsOff() {
        val mood = mood(missing = MoNeed.entries, builtInsRemind = false, checklistRemind = false)
        val reminders = ReminderSettings(
            // Their times are set but switched off, so they must not count.
            workout = WorkoutReminderSettings(enabled = false, fixedTime = ReminderTime(7, 0)),
            moCheckIn = MoCheckInSettings(enabled = false, fixedTime = ReminderTime(19, 30)),
        )

        assertEquals(emptyList<TrackerRef>(), overdueAt("20:29", mood, reminders))
        assertEquals(MoNeed.entries.map { it.view.trackerRef }, overdueAt("20:30", mood, reminders))
    }

    @Test
    fun openCareSessionsUseTheirOwnReminderOrTheCheckIn() {
        fun lateFrom(morning: Boolean, evening: Boolean): String {
            val care = testCareChecklist(morning, evening)
            val mood = mood(missing = listOf(MoNeed.Care)).copy(
                trackerCheckpoints = mapOf(MoNeed.Care.view.trackerRef to care.checkpoints),
            )
            return listOf("09:05", "21:00", "23:59").first { time ->
                moIsAngry(mood, ReminderSettings(), LocalTime.parse(time))
            }
        }

        assertEquals("09:05", lateFrom(morning = true, evening = true))
        assertEquals("09:05", lateFrom(morning = true, evening = false))
        assertEquals("21:00", lateFrom(morning = false, evening = true))
    }

    @Test
    fun completedMorningCareWaitsForEveningAndStaysOverdueAfterMidnight() {
        val mood = mood(missing = listOf(MoNeed.Care)).copy(
            trackerOpenParts = mapOf(MoNeed.Care.view.trackerRef to listOf(CheckpointPart("evening"))),
        )
        assertEquals(emptyList<TrackerRef>(), overdueAt("09:05", mood, allReminders))
        // Evening Care is asked for at 23:45 before an 08:00 alarm.
        assertEquals(emptyList<TrackerRef>(), overdueAt("23:59", mood, allReminders))
        assertEquals(emptyList<TrackerRef>(), overdueAt("00:44:59", mood, allReminders))
        assertEquals(listOf(MoNeed.Care.view.trackerRef), overdueAt("00:45", mood, allReminders))
        assertEquals(listOf(MoNeed.Care.view.trackerRef), overdueAt("03:59", mood, allReminders))
        assertEquals(emptyList<TrackerRef>(), overdueAt("04:00", mood, allReminders))
    }

    @Test
    fun aTaskAFreeDayWouldCoverIsNeverOverdueAndNeitherIsAKeptOne() {
        val mood = mood(free = listOf(MoNeed.Reading, MoNeed.Activity), kept = listOf(MoNeed.Care))

        assertEquals(emptyList<TrackerRef>(), overdueAt("23:59", mood, allReminders))
        assertEquals(false, moIsAngry(mood, allReminders, LocalTime.parse("23:59")))
    }

    @Test
    fun heIsAngryExactlyWhileSomethingIsOverdue() {
        val mood = mood(missing = listOf(MoNeed.Weight), free = listOf(MoNeed.Reading))

        assertEquals(false, moIsAngry(mood, allReminders, LocalTime.parse("09:04")))
        assertEquals(true, moIsAngry(mood, allReminders, LocalTime.parse("09:05")))
        assertEquals(false, moIsAngry(mood(), allReminders, LocalTime.parse("23:59")))
    }

    @Test
    fun aWakeTimeJustBeforeMidnightDoesNotMakeTheWholeDayLate() {
        val mood = mood(missing = listOf(MoNeed.Reading))
        val reminders = allReminders.copy(fallbackAlarmTime = ReminderTime(23, 50))

        assertEquals(false, moIsAngry(mood, reminders, LocalTime.parse("12:00")))
        assertEquals(false, moIsAngry(mood, reminders, LocalTime.parse("23:59")))
    }

    /**
     * Weight's, Eating's and Reading's due times with each switch on and off, before and after the
     * reward start: the first two at their fixed offset after waking while the switch is on and at
     * the check-in while it is off; Reading at wake+15 or the check-in before the start, and at
     * 22:00 under the weekly rules whatever the switch says. Each switch is its view's checkpoint
     * now, and these times did not move with it.
     */
    @Test
    fun weightEatingAndReadingAreDueAtTheirReminderWhileItIsOnAndAtTheCheckInOtherwise() {
        val waking = ReminderSettings(fallbackAlarmTime = ReminderTime(6, 30), moCheckIn = MoCheckInSettings(fixedTime = ReminderTime(20, 0)))
        fun due(need: MoNeed, remind: Boolean, weekly: Boolean) =
            mood(missing = listOf(need), builtInsRemind = remind, weeklyReading = weekly).trackerObligations(waking).getValue(need.view.trackerRef).single().since

        listOf(false, true).forEach { weekly ->
            assertEquals(LocalTime.of(6, 35), due(MoNeed.Weight, remind = true, weekly))
            assertEquals(LocalTime.of(6, 45), due(MoNeed.Eating, remind = true, weekly))
            assertEquals(if (weekly) LocalTime.of(22, 0) else LocalTime.of(6, 45), due(MoNeed.Reading, remind = true, weekly))
            assertEquals(LocalTime.of(20, 0), due(MoNeed.Weight, remind = false, weekly))
            assertEquals(LocalTime.of(20, 0), due(MoNeed.Eating, remind = false, weekly))
            assertEquals(if (weekly) LocalTime.of(22, 0) else LocalTime.of(20, 0), due(MoNeed.Reading, remind = false, weekly))
        }
    }

    /**
     * Weight and Eating are due at their checkpoint wherever it was moved, while it reminds, and
     * at the check-in while it does not or where the view is gone. Reading keeps its two rules:
     * before the reward start a quarter of an hour after waking while it reminds, whatever time its
     * checkpoint has, and 22:00 under the weekly rules.
     */
    @Test
    fun weightAndEatingAreDueAtTheirCheckpointWhereverItIsAndReadingByItsOwnRules() {
        val waking = ReminderSettings(fallbackAlarmTime = ReminderTime(6, 30), moCheckIn = MoCheckInSettings(fixedTime = ReminderTime(20, 0)))
        val nine = CheckpointTime.Fixed(LocalTime.of(21, 0))
        val beforeSleep = CheckpointTime.BeforeSleep(30)
        fun due(need: MoNeed, checkpoint: Checkpoint?, weekly: Boolean = true) =
            mood(missing = listOf(need), weeklyReading = weekly).copy(
                trackerCheckpoints = mapOf(need.view.trackerRef to listOfNotNull(checkpoint)),
            ).trackerObligations(waking).getValue(need.view.trackerRef).single().since

        assertEquals(LocalTime.of(21, 0), due(MoNeed.Weight, defaultWeightCheckpoint(remind = true).copy(at = nine)))
        // Eight hours before the 06:30 alarm is 22:30, and half an hour before that.
        assertEquals(LocalTime.of(22, 0), due(MoNeed.Eating, defaultEatingCheckpoint(remind = true).copy(at = beforeSleep)))
        assertEquals(LocalTime.of(20, 0), due(MoNeed.Weight, defaultWeightCheckpoint(remind = false).copy(at = nine)))
        assertEquals(LocalTime.of(20, 0), due(MoNeed.Eating, null))
        assertEquals(LocalTime.of(6, 45), due(MoNeed.Reading, defaultReadingCheckpoint(remind = true).copy(at = nine), weekly = false))
        assertEquals(LocalTime.of(20, 0), due(MoNeed.Reading, defaultReadingCheckpoint(remind = false), weekly = false))
        assertEquals(LocalTime.of(22, 0), due(MoNeed.Reading, defaultReadingCheckpoint(remind = true).copy(at = nine)))
    }

    private fun overdueAt(time: String, mood: MoMood, reminders: ReminderSettings): List<TrackerRef> =
        moOverdueTrackers(mood, reminders, LocalTime.parse(time))

    private fun moAsked(mood: MoMood, reminders: ReminderSettings, now: LocalTime) =
        moAskedTrackers(mood, reminders, now)

    private fun moIsAngry(mood: MoMood, reminders: ReminderSettings, now: LocalTime) =
        com.example.workouttracker.domain.mo.moIsAngry(mood, reminders, now)

    /**
     * The one place this file builds a mood, so a new field is a one-line fix. Weight's, Eating's
     * and Reading's checkpoints remind as [builtInsRemind] says.
     */
    private fun mood(
        missing: List<MoNeed> = emptyList(),
        free: List<MoNeed> = emptyList(),
        kept: List<MoNeed> = emptyList(),
        builtInsRemind: Boolean = true,
        checklistRemind: Boolean = true,
        weeklyReading: Boolean = false,
    ) = MoMood(
        state = MoState.Steady,
        missingTrackersToday = missing.map { it.view.trackerRef },
        freeTrackersToday = free.map { it.view.trackerRef },
        keptTrackersToday = kept.map { it.view.trackerRef },
        latestTrackerToday = kept.lastOrNull()?.view?.trackerRef,
        dueToday = missing.size + free.size + kept.size,
        daysCold = 0,
        streak = MoStreak(
            days = 0,
            freezes = 0,
            freezeCapacity = 3,
            week = listOf(MoDayMark.Open) + List(6) { MoDayMark.Upcoming },
            weekCanEarnFreeze = true,
            frozenTrackersYesterday = emptyList(),
        ),
        trackerCheckpoints = mapOf(
            MoNeed.Supplements.view.trackerRef to testSupplementsChecklist(remind = checklistRemind).checkpoints,
            MoNeed.Care.view.trackerRef to testCareChecklist(remindMorning = checklistRemind, remindEvening = checklistRemind).checkpoints,
            MoNeed.Weight.view.trackerRef to listOf(defaultWeightCheckpoint(builtInsRemind)),
            MoNeed.Eating.view.trackerRef to listOf(defaultEatingCheckpoint(builtInsRemind)),
            MoNeed.Reading.view.trackerRef to listOf(defaultReadingCheckpoint(builtInsRemind)),
        ),
        trackerOpenParts = mapOf(MoNeed.Care.view.trackerRef to listOf(CheckpointPart("morning"), CheckpointPart("evening"))),
        trackerWeeklyRemainders = if (weeklyReading) mapOf(MoNeed.Reading.view.trackerRef to 1) else emptyMap(),
    )
}
