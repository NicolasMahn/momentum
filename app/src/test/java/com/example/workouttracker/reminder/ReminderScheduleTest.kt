package com.example.workouttracker.reminder

import com.example.workouttracker.domain.checklist.CheckpointTime
import com.example.workouttracker.domain.reminder.ReminderTime
import com.example.workouttracker.domain.activity.DefaultActivityWeek
import com.example.workouttracker.domain.reminder.WorkoutReminderSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** When reminders ring: a checkpoint after waking, by the phone's alarm or the fallback, a fixed time, and the workout reminder on its days. */
class ReminderScheduleTest {
    private val berlin = ZoneId.of("Europe/Berlin")
    private val fallbackAlarmTime = ReminderTime(8, 0)

    @Test
    fun futureSystemAlarmSchedulesFiveMinutesAfterIt() {
        val now = time("2026-09-16T06:30:00+02:00[Europe/Berlin]")
        val alarm = time("2026-09-16T07:00:00+02:00[Europe/Berlin]")

        val result = afterWake(
            minutes = 5,
            now = now,
            fallbackAlarmTime = fallbackAlarmTime,
            nextSystemAlarmMillis = alarm.toInstant().toEpochMilli(),
        )

        assertEquals(
            time("2026-09-16T07:05:00+02:00[Europe/Berlin]").toInstant(),
            result,
        )
    }

    @Test
    fun movingThePhoneAlarmRecomputesTheMorningReminderFromTheNewTime() {
        val now = time("2026-09-16T03:00:00+02:00[Europe/Berlin]")
        val originalAlarm = time("2026-09-16T10:00:00+02:00[Europe/Berlin]")
        val movedAlarm = time("2026-09-16T11:00:00+02:00[Europe/Berlin]")

        val originalReminder = afterWake(
            minutes = 5,
            now = now,
            fallbackAlarmTime = fallbackAlarmTime,
            nextSystemAlarmMillis = originalAlarm.toInstant().toEpochMilli(),
        )
        val movedReminder = afterWake(
            minutes = 5,
            now = now,
            fallbackAlarmTime = fallbackAlarmTime,
            nextSystemAlarmMillis = movedAlarm.toInstant().toEpochMilli(),
        )

        assertEquals(
            time("2026-09-16T10:05:00+02:00[Europe/Berlin]").toInstant(),
            originalReminder,
        )
        assertEquals(
            time("2026-09-16T11:05:00+02:00[Europe/Berlin]").toInstant(),
            movedReminder,
        )
    }

    @Test
    fun missingOrStaleSystemAlarmUsesFiveMinutesAfterTheFallbackAlarm() {
        val beforeEight = time("2026-09-16T06:30:00+02:00[Europe/Berlin]")
        val staleAlarm = time("2026-09-16T06:00:00+02:00[Europe/Berlin]")
        val afterEight = time("2026-09-16T08:30:00+02:00[Europe/Berlin]")
        assertEquals(
            time("2026-09-16T08:05:00+02:00[Europe/Berlin]").toInstant(),
            afterWake(
            minutes = 5,
                now = beforeEight,
                fallbackAlarmTime = fallbackAlarmTime,
                nextSystemAlarmMillis = staleAlarm.toInstant().toEpochMilli(),
            ),
        )
        assertEquals(
            time("2026-09-17T08:05:00+02:00[Europe/Berlin]").toInstant(),
            afterWake(5, afterEight, fallbackAlarmTime, null),
        )
    }

    @Test
    fun alarmSeveralDaysAwayDoesNotSkipDailyFallbacks() {
        val now = time("2026-09-16T09:00:00+02:00[Europe/Berlin]")
        val distantAlarm = time("2026-09-19T07:00:00+02:00[Europe/Berlin]")

        val result = afterWake(
            minutes = 5,
            now = now,
            fallbackAlarmTime = fallbackAlarmTime,
            nextSystemAlarmMillis = distantAlarm.toInstant().toEpochMilli(),
        )

        assertEquals(
            time("2026-09-17T08:05:00+02:00[Europe/Berlin]").toInstant(),
            result,
        )
    }

    @Test
    fun sameDayAlarmRemainsValidAfterTodaysFallbackHasPassed() {
        val now = time("2026-09-16T09:00:00+02:00[Europe/Berlin]")
        val alarm = time("2026-09-16T10:00:00+02:00[Europe/Berlin]")
        val alarmMillis = alarm.toInstant().toEpochMilli()

        assertEquals(
            time("2026-09-16T10:05:00+02:00[Europe/Berlin]").toInstant(),
            afterWake(5, now, fallbackAlarmTime, alarmMillis),
        )
        assertEquals(
            time("2026-09-16T10:15:00+02:00[Europe/Berlin]").toInstant(),
            afterWake(15, now, fallbackAlarmTime, alarmMillis),
        )
    }

    @Test
    fun laterDayAlarmDoesNotReplaceTheNearestFallbackForAnyWakePolicy() {
        val now = time("2026-09-16T09:00:00+02:00[Europe/Berlin]")
        val distantAlarm = time("2026-09-19T07:00:00+02:00[Europe/Berlin]")
        val alarmMillis = distantAlarm.toInstant().toEpochMilli()

        assertEquals(
            time("2026-09-17T08:05:00+02:00[Europe/Berlin]").toInstant(),
            afterWake(5, now, fallbackAlarmTime, alarmMillis),
        )
        assertEquals(
            time("2026-09-17T08:15:00+02:00[Europe/Berlin]").toInstant(),
            afterWake(15, now, fallbackAlarmTime, alarmMillis),
        )
    }

    @Test
    fun nextDayAlarmReplacesNextDayFallback() {
        val now = time("2026-09-16T09:00:00+02:00[Europe/Berlin]")
        val tomorrowAlarm = time("2026-09-17T07:00:00+02:00[Europe/Berlin]")

        val result = afterWake(
            minutes = 5,
            now = now,
            fallbackAlarmTime = fallbackAlarmTime,
            nextSystemAlarmMillis = tomorrowAlarm.toInstant().toEpochMilli(),
        )

        assertEquals(
            time("2026-09-17T07:05:00+02:00[Europe/Berlin]").toInstant(),
            result,
        )
    }

    @Test
    fun customFallbackAlarmAndFollowingDayBoundaryAreHonored() {
        val now = time("2026-09-16T06:30:00+02:00[Europe/Berlin]")

        val result = afterWake(
            minutes = 5,
            now = now,
            fallbackAlarmTime = ReminderTime(9, 15),
            nextSystemAlarmMillis = null,
            earliestLocalDate = now.toLocalDate().plusDays(1),
        )

        assertEquals(
            time("2026-09-17T09:20:00+02:00[Europe/Berlin]").toInstant(),
            result,
        )
    }

    @Test
    fun weightFallbackOffsetRemainsLocalAcrossDaylightSavingChange() {
        val now = ZonedDateTime.of(2026, 3, 28, 9, 0, 0, 0, berlin)

        val result = afterWake(
            minutes = 5,
            now = now,
            fallbackAlarmTime = fallbackAlarmTime,
            nextSystemAlarmMillis = null,
        ).atZone(berlin)

        assertEquals(2026, result.year)
        assertEquals(3, result.monthValue)
        assertEquals(29, result.dayOfMonth)
        assertEquals(8, result.hour)
        assertEquals(5, result.minute)
    }

    private val Lunch = com.example.workouttracker.domain.checklist.CheckpointTime.Fixed(java.time.LocalTime.of(12, 30))

    @Test
    fun supplementsScheduleAtLunchAndRollOverAfterLunch() {
        for ((current, expected) in listOf(
            "2026-09-16T06:30" to "2026-09-16T12:30",
            "2026-09-16T12:30" to "2026-09-17T12:30",
            "2026-09-16T13:00" to "2026-09-17T12:30",
            "2026-03-28T13:00" to "2026-03-29T12:30",
        )) {
            val now = java.time.LocalDateTime.parse(current).atZone(berlin)
            val target = java.time.LocalDateTime.parse(expected).atZone(berlin)
            assertEquals(target.toInstant(), nextCheckpointReminderInstant(now, Lunch, ReminderTime(7, 0), fixedBedtime = null, nextSystemAlarmMillis = null))
        }
    }

    @Test
    fun supplementsEarliestDateForcesTomorrowLunch() {
        val now = time("2026-09-16T09:00:00+02:00[Europe/Berlin]")
        assertEquals(
            time("2026-09-17T12:30:00+02:00[Europe/Berlin]").toInstant(),
            nextCheckpointReminderInstant(now, Lunch, ReminderTime(7, 0), fixedBedtime = null, nextSystemAlarmMillis = null, earliestLocalDate = now.toLocalDate().plusDays(1)),
        )
    }

    @Test
    fun readingSchedulesFifteenMinutesAfterTheNextAlarm() {
        val now = time("2026-09-16T06:30:00+02:00[Europe/Berlin]")
        val alarm = time("2026-09-16T07:00:00+02:00[Europe/Berlin]")

        assertEquals(
            time("2026-09-16T07:15:00+02:00[Europe/Berlin]").toInstant(),
            afterWake(
            minutes = 15,
                now = now,
                fallbackAlarmTime = fallbackAlarmTime,
                nextSystemAlarmMillis = alarm.toInstant().toEpochMilli(),
            ),
        )
    }

    @Test
    fun readingUsesTheEightFifteenFallbackWhenThereIsNoUsableAlarm() {
        val beforeFallback = time("2026-09-16T06:30:00+02:00[Europe/Berlin]")
        val afterFallback = time("2026-09-16T09:00:00+02:00[Europe/Berlin]")

        assertEquals(
            time("2026-09-16T08:15:00+02:00[Europe/Berlin]").toInstant(),
            afterWake(15, beforeFallback, fallbackAlarmTime, null),
        )
        assertEquals(
            time("2026-09-17T08:15:00+02:00[Europe/Berlin]").toInstant(),
            afterWake(15, afterFallback, fallbackAlarmTime, null),
        )
    }

    @Test
    fun readingEarliestDateForcesTomorrowAndStillUsesTomorrowAlarm() {
        val now = time("2026-09-16T09:00:00+02:00[Europe/Berlin]")
        val tomorrowAlarm = time("2026-09-17T07:00:00+02:00[Europe/Berlin]")

        assertEquals(
            time("2026-09-17T07:15:00+02:00[Europe/Berlin]").toInstant(),
            afterWake(
            minutes = 15,
                now = now,
                fallbackAlarmTime = fallbackAlarmTime,
                nextSystemAlarmMillis = tomorrowAlarm.toInstant().toEpochMilli(),
                earliestLocalDate = now.toLocalDate().plusDays(1),
            ),
        )
    }

    @Test
    fun readingFallbackRemainsEightFifteenAcrossDaylightSavingChange() {
        val now = ZonedDateTime.of(2026, 3, 28, 9, 0, 0, 0, berlin)

        val result = afterWake(
            minutes = 15,
            now = now,
            fallbackAlarmTime = fallbackAlarmTime,
            nextSystemAlarmMillis = null,
        ).atZone(berlin)

        assertEquals(2026, result.year)
        assertEquals(3, result.monthValue)
        assertEquals(29, result.dayOfMonth)
        assertEquals(8, result.hour)
        assertEquals(15, result.minute)
    }

    @Test
    fun readingAlarmOffsetRemainsFifteenRealMinutesAcrossSpringForward() {
        val now = time("2026-03-29T01:30:00+01:00[Europe/Berlin]")
        val alarm = time("2026-03-29T01:45:00+01:00[Europe/Berlin]")

        val result = afterWake(
            minutes = 15,
            now = now,
            fallbackAlarmTime = fallbackAlarmTime,
            nextSystemAlarmMillis = alarm.toInstant().toEpochMilli(),
        ).atZone(berlin)

        assertEquals(time("2026-03-29T03:00:00+02:00[Europe/Berlin]"), result)
    }

    @Test
    fun nonexistentFallbackAlarmAdvancesThroughTheSpringGapBeforeApplyingTheOffset() {
        val now = time("2026-03-28T09:00:00+01:00[Europe/Berlin]")

        val result = afterWake(
            minutes = 15,
            now = now,
            fallbackAlarmTime = ReminderTime(2, 30),
            nextSystemAlarmMillis = null,
        ).atZone(berlin)

        assertEquals(time("2026-03-29T03:45:00+02:00[Europe/Berlin]"), result)
    }

    @Test
    fun workoutUsesItsFixedTimeAndRollsToTheNextDayAfterItPasses() {
        val settings = WorkoutReminderSettings(
            enabled = true,
            fixedTime = ReminderTime(18, 0),
        )

        assertEquals(
            time("2026-09-16T18:00:00+02:00[Europe/Berlin]").toInstant(),
            nextWorkoutReminderInstant(
                now = time("2026-09-16T17:30:00+02:00[Europe/Berlin]"),
                settings = settings,
                days = DefaultActivityWeek.chosen,
                ),
        )
        assertEquals(
            time("2026-09-17T18:00:00+02:00[Europe/Berlin]").toInstant(),
            nextWorkoutReminderInstant(
                now = time("2026-09-16T18:01:00+02:00[Europe/Berlin]"),
                settings = settings,
                days = DefaultActivityWeek.chosen,
                ),
        )
    }

    /** Its days decide which day: from a Wednesday, a Monday-only reminder comes next Monday, and from the earliest habit date on. */
    @Test
    fun workoutComesOnlyOnItsDays() {
        val settings = WorkoutReminderSettings(enabled = true, fixedTime = ReminderTime(18, 0))
        val mondays = setOf(DayOfWeek.MONDAY)
        assertEquals(
            time("2026-09-21T18:00:00+02:00[Europe/Berlin]").toInstant(),
            nextWorkoutReminderInstant(now = time("2026-09-16T17:30:00+02:00[Europe/Berlin]"), settings = settings, days = mondays),
        )
        assertEquals(
            time("2026-09-28T18:00:00+02:00[Europe/Berlin]").toInstant(),
            nextWorkoutReminderInstant(now = time("2026-09-16T17:30:00+02:00[Europe/Berlin]"), settings = settings, days = mondays, earliestLocalDate = LocalDate.parse("2026-09-22")),
        )
        assertEquals(
            time("2026-09-19T18:00:00+02:00[Europe/Berlin]").toInstant(),
            nextWorkoutReminderInstant(now = time("2026-09-19T17:30:00+02:00[Europe/Berlin]"), settings = settings, days = DayOfWeek.entries.toSet()),
        )
        assertTrue(runCatching { nextWorkoutReminderInstant(now = time("2026-09-19T17:30:00+02:00[Europe/Berlin]"), settings = settings, days = emptySet()) }.isFailure)
    }

    @Test
    fun workoutFixedTimeRemainsLocalAcrossDaylightSavingChange() {
        val now = ZonedDateTime.of(2026, 3, 28, 19, 0, 0, 0, berlin)

        val result = nextWorkoutReminderInstant(
            now = now,
            // The clock gap is the point, so the reminder comes every day, the Sunday it falls on too.
            settings = WorkoutReminderSettings(enabled = true, fixedTime = ReminderTime(18, 0)),
                days = DayOfWeek.entries.toSet(),
        ).atZone(berlin)

        assertEquals(2026, result.year)
        assertEquals(3, result.monthValue)
        assertEquals(29, result.dayOfMonth)
        assertEquals(18, result.hour)
        assertEquals(0, result.minute)
    }

    @Test
    fun repeatedAutumnHourUsesTheStillFutureLaterOffset() {
        val now = time("2026-10-25T02:15:00+01:00[Europe/Berlin]")

        val result = nextWorkoutReminderInstant(
            now = now,
            settings = WorkoutReminderSettings(enabled = true, fixedTime = ReminderTime(2, 30)),
            // The clock gap is the point, so the reminder comes every day, the Sunday it falls on too.
            days = DayOfWeek.entries.toSet(),
        ).atZone(berlin)

        assertEquals(time("2026-10-25T02:30:00+01:00[Europe/Berlin]"), result)
    }

    @Test
    fun nonexistentSpringTimeAdvancesByTheClockGapOnTheSameDay() {
        val now = time("2026-03-29T01:30:00+01:00[Europe/Berlin]")

        val result = nextWorkoutReminderInstant(
            now = now,
            settings = WorkoutReminderSettings(enabled = true, fixedTime = ReminderTime(2, 30)),
            // The clock gap is the point, so the reminder comes every day, the Sunday it falls on too.
            days = DayOfWeek.entries.toSet(),
        ).atZone(berlin)

        assertEquals(time("2026-03-29T03:30:00+02:00[Europe/Berlin]"), result)
    }

    @Test
    fun earliestWorkoutDateStillForcesTheFollowingDay() {
        val now = time("2026-09-16T17:30:00+02:00[Europe/Berlin]")

        val result = nextWorkoutReminderInstant(
            now = now,
            settings = WorkoutReminderSettings(enabled = true, fixedTime = ReminderTime(18, 0)),
            days = DefaultActivityWeek.chosen,
            earliestLocalDate = now.toLocalDate().plusDays(1),
        )

        assertEquals(time("2026-09-17T18:00:00+02:00[Europe/Berlin]").toInstant(), result)
    }

    private fun time(value: String): ZonedDateTime = ZonedDateTime.parse(value)

    /** When a checkpoint [minutes] after waking rings next: Weight's five, and fifteen as Eating's and Reading's were. */
    private fun afterWake(
        minutes: Int,
        now: ZonedDateTime,
        fallbackAlarmTime: ReminderTime,
        nextSystemAlarmMillis: Long?,
        earliestLocalDate: LocalDate = now.toLocalDate(),
    ) = nextCheckpointReminderInstant(now, CheckpointTime.AfterWake(minutes), fallbackAlarmTime, fixedBedtime = null, nextSystemAlarmMillis, earliestLocalDate)
}
