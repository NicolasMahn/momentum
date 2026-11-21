package com.example.workouttracker.domain.mo

import com.example.workouttracker.domain.compatibility.free

import com.example.workouttracker.domain.compatibility.mood

import com.example.workouttracker.domain.compatibility.MoNeed

import com.example.workouttracker.core.time.HabitDayStart
import com.example.workouttracker.domain.CheckpointPart
import com.example.workouttracker.domain.DayPart
import com.example.workouttracker.domain.TrackerRef
import com.example.workouttracker.domain.trackerRef
import com.example.workouttracker.domain.compatibility.LegacyCareSession
import com.example.workouttracker.domain.checklist.CheckpointTime
import com.example.workouttracker.domain.checklist.Checkpoint
import com.example.workouttracker.domain.checklist.DayCheckpointId
import com.example.workouttracker.domain.checklist.on
import com.example.workouttracker.domain.reminder.Bedtime
import com.example.workouttracker.domain.compatibility.EatingReminderPolicy
import com.example.workouttracker.domain.reminder.MoCheckInSettings
import com.example.workouttracker.domain.reminder.ReminderSettings
import com.example.workouttracker.domain.reminder.ReminderTime
import com.example.workouttracker.domain.compatibility.WakeRelativeReminderPolicy
import com.example.workouttracker.domain.reminder.WorkoutReminderSettings
import com.example.workouttracker.domain.compatibility.fallbackTime
import com.example.workouttracker.domain.reminder.toLocalTime
import com.example.workouttracker.domain.care.testCareChecklist
import com.example.workouttracker.domain.supplement.testSupplementsChecklist
import com.example.workouttracker.domain.compatibility.defaultWeightCheckpoint
import com.example.workouttracker.domain.compatibility.defaultEatingCheckpoint
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class MoObligationsComparisonTest {
    @Test
    fun everyTaskKeepsItsOldDueTimeAcrossReminderModesAndRelativeTimeBoundaries() {
        val wakeTimes = listOf("00:00", "03:55", "04:00", "06:30", "08:00", "23:44", "23:50", "23:59")
        val checkInTimes = listOf("00:30", "03:59", "04:00", "19:30")
        for (flags in 0 until 256) {
            for (wake in wakeTimes) {
                for (checkIn in checkInTimes) {
                    for (workout in listOf("03:45", "18:00")) {
                        val reminders = reminders(flags, wake, checkIn, workout)
                        for (weeklyReading in listOf(false, true)) {
                            val mood = mood(flags, weeklyReading)
                            val expected = trackerRefs.associateWith { ref ->
                                mood.trackerStillOpen(ref).map { part -> MoObligation(part, oldDueTime(part, needFor(ref), mood, reminders)) }
                            }
                            assertEquals("flags=$flags wake=$wake checkIn=$checkIn workout=$workout weekly=$weeklyReading", expected, mood.trackerObligations(reminders))
                        }
                    }
                }
            }
        }
    }

    @Test
    fun askedAndOverdueKeepTheirOldMinuteThresholdsAndHabitDayBoundaries() {
        for (flags in listOf(0, 255, 1, 16, 32, 64)) {
            for (wake in listOf("03:55", "08:00", "23:50")) {
                val reminders = reminders(flags, wake, checkIn = "20:00", workout = "18:00")
                for (weeklyReading in listOf(false, true)) {
                    for (careParts in listOf(LegacyCareSession.entries, listOf(LegacyCareSession.MORNING), listOf(LegacyCareSession.EVENING))) {
                        for (freeTasks in listOf(emptyList(), listOf(MoNeed.Activity, MoNeed.Reading))) {
                            val freeRefs = freeTasks.map { it.view.trackerRef }
                            val careRef = MoNeed.Care.view.trackerRef
                            val mood = mood(flags, weeklyReading).copy(
                                missingTrackersToday = trackerRefs - freeRefs.toSet(),
                                freeTrackersToday = freeRefs,
                                trackerOpenParts = mapOf(careRef to careParts.map { CheckpointPart(it.storageId) }),
                            )
                            val dueTimes = trackerRefs.flatMap { ref ->
                                mood.trackerStillOpen(ref).map { oldDueTime(it, needFor(ref), mood, reminders) }
                            }
                            val times = (listOf("03:59:59", "04:00", "04:00:59").map(LocalTime::parse) +
                                dueTimes.flatMap { due ->
                                    listOf(0L, 15L, 60L).flatMap { minutes ->
                                        val threshold = due.plusMinutes(minutes)
                                        listOf(threshold.minusSeconds(1), threshold, threshold.plusSeconds(59))
                                    }
                                }).distinct()
                            for (now in times) {
                                val expected = oldAsked(mood, reminders, now)
                                val context = "flags=$flags wake=$wake weekly=$weeklyReading care=$careParts free=$freeTasks now=$now"
                                assertEquals(context, expected, moAskedTrackers(mood, reminders, now))
                                assertEquals(context, oldOverdue(mood, reminders, now), moOverdueTrackers(mood, reminders, now))
                                assertEquals(context, expected.overdueBy.isNotEmpty(), moIsAngry(mood, reminders, now))
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun checklistTimesStillComeFromTheStoredCheckpointRatherThanTheReminderSwitch() {
        val reminders = reminders(flags = 255, wake = "23:50", checkIn = "19:30", workout = "18:00")
        for (at in listOf(CheckpointTime.Fixed(LocalTime.of(12, 30)), CheckpointTime.AfterWake(15), CheckpointTime.BeforeSleep(15))) {
            for (remind in listOf(false, true)) {
                val checklist = testSupplementsChecklist(remind).let { configuration ->
                    configuration.copy(checkpoints = configuration.checkpoints.map { it.copy(at = at) })
                }
                val supplementsRef = MoNeed.Supplements.view.trackerRef
                val mood = mood(flags = 255, weeklyReading = true).copy(trackerCheckpoints = mapOf(supplementsRef to checklist.checkpoints))
                val part = mood.trackerStillOpen(supplementsRef).single()
                assertEquals(
                    listOf(MoObligation(part, oldDueTime(part, MoNeed.Supplements, mood, reminders))),
                    mood.trackerObligations(reminders).getValue(supplementsRef),
                )
            }
        }
        val supplementsRef = MoNeed.Supplements.view.trackerRef
        val withoutChecklist = mood(flags = 255, weeklyReading = true).copy(trackerCheckpoints = emptyMap())
        assertEquals(LocalTime.of(19, 30), withoutChecklist.trackerObligations(reminders).getValue(supplementsRef).single().since)
    }

    /** Whether the reminder of [bit] is on: Weight 0, the workout 1, Supplements 2, Reading 3, Care 4 and 5, Eating 6, the check-in 7. */
    private fun Int.switches(bit: Int) = this and (1 shl bit) != 0

    private fun reminders(flags: Int, wake: String, checkIn: String, workout: String): ReminderSettings {
        fun time(text: String) = LocalTime.parse(text).let { ReminderTime(it.hour, it.minute) }
        return ReminderSettings(
            workout = WorkoutReminderSettings(flags.switches(1), fixedTime = time(workout)),
            moCheckIn = MoCheckInSettings(flags.switches(7), fixedTime = time(checkIn)),
            fallbackAlarmTime = time(wake),
        )
    }

    private fun mood(flags: Int, weeklyReading: Boolean) = MoMood(
        state = MoState.Steady,
        missingTrackersToday = trackerRefs,
        freeTrackersToday = emptyList(),
        keptTrackersToday = emptyList(),
        latestTrackerToday = null,
        dueToday = MoNeed.entries.size,
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
            MoNeed.Supplements.view.trackerRef to testSupplementsChecklist(flags.switches(2)).checkpoints,
            MoNeed.Care.view.trackerRef to testCareChecklist(remindMorning = flags.switches(4), remindEvening = flags.switches(5)).checkpoints,
            // Weight's, Eating's and Reading's switches are their checkpoints', as the app stores them.
            MoNeed.Weight.view.trackerRef to listOf(defaultWeightCheckpoint(flags.switches(0))),
            MoNeed.Eating.view.trackerRef to listOf(defaultEatingCheckpoint(flags.switches(6))),
            // This legacy fixture stores the 15-minute wake-relative reminder; current defaults
            // use a fixed 18:00 checkpoint and would change the historical policy under test.
            MoNeed.Reading.view.trackerRef to listOf(Checkpoint(DayCheckpointId, CheckpointTime.AfterWake(15), remind = flags.switches(3))),
        ),
        trackerOpenParts = mapOf(MoNeed.Care.view.trackerRef to listOf(CheckpointPart("morning"), CheckpointPart("evening"))),
        trackerWeeklyRemainders = if (weeklyReading) mapOf(MoNeed.Reading.view.trackerRef to 1) else emptyMap(),
    )

    // Frozen selection from MoOverdue.kt before Block 6. Keep it independent of the new obligation adapter.
    private fun oldDueTime(part: DayPart, need: MoNeed, mood: MoMood, reminders: ReminderSettings): LocalTime {
        val checkIn = reminders.moCheckIn.fixedTime.toLocalTime()
        // Weekly Reading's old fallback stays at 22:00; its reminder switch only controls daily Reading.
        if (need == MoNeed.Reading && need.view.trackerRef in mood.trackerWeeklyRemainders) return LocalTime.of(22, 0)
        if (need == MoNeed.Care) {
            val checkpointId = (part as? CheckpointPart)?.checkpointId ?: error("Care is asked for one checkpoint at a time")
            val checkpoint = mood.trackerCheckpoints.getValue(need.view.trackerRef).single { it.id == checkpointId }
            return if (checkpoint.remind) checkpoint.at.on(Bedtime(reminders.fallbackAlarmTime.toLocalTime(), fixedBedtime = null)) else checkIn
        }
        if (need == MoNeed.Supplements) {
            mood.trackerCheckpoints[need.view.trackerRef]?.singleOrNull()?.let { checkpoint ->
                return if (checkpoint.remind) checkpoint.at.on(Bedtime(reminders.fallbackAlarmTime.toLocalTime(), fixedBedtime = null)) else checkIn
            }
        }
        val due = oldReminderTime(need, reminders, mood)
        return due ?: checkIn
    }

    private fun oldReminderTime(need: MoNeed, reminders: ReminderSettings, mood: MoMood): LocalTime? {
        fun afterWaking(policy: WakeRelativeReminderPolicy, enabled: Boolean) =
            policy.fallbackTime(reminders.fallbackAlarmTime).takeIf { enabled }
        // These three switches were the reminder settings' then and are their checkpoints' now.
        fun switchedOn(need: MoNeed) = mood.trackerCheckpoints[need.view.trackerRef]?.singleOrNull()?.remind == true
        return when (need) {
            MoNeed.Activity -> reminders.workout.run { fixedTime.toLocalTime().takeIf { enabled } }
            MoNeed.Reading -> afterWaking(WakeRelativeReminderPolicy.Reading, switchedOn(MoNeed.Reading))
            MoNeed.Care -> error("Care has a separate due time for each session")
            MoNeed.Supplements -> null
            MoNeed.Eating -> afterWaking(EatingReminderPolicy, switchedOn(MoNeed.Eating))
            MoNeed.Weight -> afterWaking(WakeRelativeReminderPolicy.Weight, switchedOn(MoNeed.Weight))
        }
    }

    private fun oldWaited(mood: MoMood, reminders: ReminderSettings, now: LocalTime): Map<TrackerRef, List<Int>> {
        val minute = oldHabitDayMinute(now.truncatedTo(ChronoUnit.MINUTES))
        return (mood.missingTrackersToday + mood.freeTrackersToday).associateWith { ref ->
            mood.trackerStillOpen(ref).map { minute - oldHabitDayMinute(oldDueTime(it, needFor(ref), mood, reminders)) }.filter { it >= 0 }
        }
    }

    private fun oldAsked(mood: MoMood, reminders: ReminderSettings, now: LocalTime): TrackerAsked {
        val waited = oldWaited(mood, reminders, now)
        return TrackerAsked(
            waited = waited.mapValues { (_, parts) -> parts.filter { it >= 15 } }.filterValues { it.isNotEmpty() },
            overdueBy = mood.missingTrackersToday.mapNotNull { ref ->
                waited.getValue(ref).maxOrNull()?.takeIf { it >= 60 }?.let { ref to it }
            }.toMap(),
        )
    }

    private fun oldOverdue(mood: MoMood, reminders: ReminderSettings, now: LocalTime): List<TrackerRef> {
        val waited = oldWaited(mood, reminders, now)
        return mood.missingTrackersToday.filter { ref -> waited.getValue(ref).any { it >= 60 } }
    }

    private val trackerRefs: List<TrackerRef> = MoNeed.entries.map { it.view.trackerRef }
    private val needsByTracker = MoNeed.entries.associateBy { it.view.trackerRef }
    private fun needFor(ref: TrackerRef): MoNeed = needsByTracker.getValue(ref)

    private fun oldHabitDayMinute(time: LocalTime): Int =
        Math.floorMod(time.toSecondOfDay() / 60 - HabitDayStart.toSecondOfDay() / 60, 24 * 60)
}
