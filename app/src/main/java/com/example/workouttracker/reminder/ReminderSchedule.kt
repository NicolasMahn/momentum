package com.example.workouttracker.reminder

import com.example.workouttracker.core.time.habitDate
import com.example.workouttracker.domain.reminder.ReminderTime
import com.example.workouttracker.domain.reminder.Bedtime
import com.example.workouttracker.domain.reminder.isWakeTimeNear
import com.example.workouttracker.domain.reminder.toLocalTime
import com.example.workouttracker.domain.reminder.WorkoutReminderSettings
import com.example.workouttracker.domain.checklist.CheckpointTime
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZonedDateTime

/** The workout reminder's fixed time on the next of [days], Activity's week, on or after [earliestLocalDate]. */
internal fun nextWorkoutReminderInstant(
    now: ZonedDateTime,
    settings: WorkoutReminderSettings,
    days: Set<DayOfWeek>,
    earliestLocalDate: LocalDate = now.toLocalDate(),
): Instant {
    require(days.isNotEmpty()) { "The workout reminder comes on at least one day." }
    var earliest = earliestLocalDate
    while (true) {
        val candidate = nextDailyOccurrence(now = now, time = settings.fixedTime, earliestLocalDate = earliest)
        if (candidate.dayOfWeek in days) return candidate.toInstant()
        earliest = candidate.toLocalDate().plusDays(1)
    }
}

internal fun nextDailyOccurrence(
    now: ZonedDateTime,
    time: ReminderTime,
    earliestLocalDate: LocalDate,
): ZonedDateTime {
    var date = maxOf(now.toLocalDate(), earliestLocalDate)
    while (true) {
        localOccurrences(date, time, now)
            .sortedBy(ZonedDateTime::toInstant)
            .firstOrNull { it.isAfter(now) }
            ?.let { return it }
        date = date.plusDays(1)
    }
}

private fun localOccurrences(
    date: LocalDate,
    time: ReminderTime,
    zoneReference: ZonedDateTime,
): List<ZonedDateTime> {
    val localDateTime = LocalDateTime.of(
        date,
        LocalTime.of(time.hour, time.minute),
    )
    val validOffsets = zoneReference.zone.rules.getValidOffsets(localDateTime)
    return if (validOffsets.isEmpty()) {
        // Java advances a nonexistent spring-forward wall time by the gap length.
        listOf(localDateTime.atZone(zoneReference.zone))
    } else {
        validOffsets.map { offset ->
            ZonedDateTime.ofLocal(localDateTime, zoneReference.zone, offset)
        }
    }
}

/**
 * The next time a checklist checkpoint reminds: a fixed time every day, or a time that follows the
 * phone's wake alarm by the same rules as Weight's and evening Care's reminders.
 */
internal fun nextCheckpointReminderInstant(
    now: ZonedDateTime,
    at: CheckpointTime,
    fallbackAlarmTime: ReminderTime,
    fixedBedtime: ReminderTime?,
    nextSystemAlarmMillis: Long?,
    earliestLocalDate: LocalDate = now.toLocalDate(),
): Instant = when (at) {
    is CheckpointTime.Fixed -> nextDailyOccurrence(now, ReminderTime(at.time.hour, at.time.minute), earliestLocalDate).toInstant()
    is CheckpointTime.AfterWake ->
        nextAfterWakeCheckpointInstant(now, fallbackAlarmTime, at.minutes.toLong(), nextSystemAlarmMillis, earliestLocalDate)
    is CheckpointTime.BeforeSleep -> nextBeforeSleepReminderInstant(
        now, fallbackAlarmTime, fixedBedtime, nextSystemAlarmMillis, earliestLocalDate,
    ) { bedtime -> bedtime.beforeSleep(Duration.ofMinutes(at.minutes.toLong())) }
}

/**
 * [minutes] after the phone's next wake alarm, or after the fallback alarm, as Weight's reminder is
 * scheduled, except that a checkpoint never runs past its wake day: an offset that would reach
 * midnight reminds at the day's last minute, the day a checkpoint's time is read for.
 */
private fun nextAfterWakeCheckpointInstant(
    now: ZonedDateTime,
    fallbackAlarmTime: ReminderTime,
    minutes: Long,
    nextSystemAlarmMillis: Long?,
    earliestLocalDate: LocalDate,
): Instant {
    fun afterAlarm(alarm: ZonedDateTime): ZonedDateTime = alarm.plusMinutes(minutes)
        .takeIf { it.toLocalDate() == alarm.toLocalDate() }
        ?: alarm.toLocalDate().atTime(LastMinuteOfWakeDay).atZone(alarm.zone)

    val fallback = generateSequence(maxOf(now.toLocalDate(), earliestLocalDate).minusDays(1)) { it.plusDays(1) }
        .firstNotNullOf { alarmDate ->
            localOccurrences(alarmDate, fallbackAlarmTime, now)
                .map(::afterAlarm)
                .filter { it.isAfter(now) && !it.toLocalDate().isBefore(earliestLocalDate) }
                .minByOrNull(ZonedDateTime::toInstant)
        }
    val alarmCandidate = nextSystemAlarmMillis
        ?.let { Instant.ofEpochMilli(it).atZone(now.zone) }
        ?.takeIf { it.toInstant().isAfter(now.toInstant()) }
        ?.let(::afterAlarm)
        // A late alarm's clamped reminder may already have passed; then the next one decides.
        ?.takeIf { it.isAfter(now) && !it.toLocalDate().isBefore(earliestLocalDate) && !it.toLocalDate().isAfter(fallback.toLocalDate()) }
    // The phone's next alarm decides whenever it belongs to the nearest eligible day.
    return (alarmCandidate ?: fallback).toInstant()
}

private val LastMinuteOfWakeDay: LocalTime = LocalTime.of(23, 59)

/**
 * A reminder at a time before sleep, which [evening] reads from the night's [Bedtime], asleep by
 * [fixedBedtime] when one is set. The phone's alarm decides only the evening right before it; every
 * other evening follows the fallback wake time, and a later alarm never suppresses a nearer evening.
 */
internal fun nextBeforeSleepReminderInstant(
    now: ZonedDateTime,
    fallbackAlarmTime: ReminderTime,
    fixedBedtime: ReminderTime?,
    nextSystemAlarmMillis: Long?,
    earliestLocalDate: LocalDate,
    evening: (Bedtime) -> LocalTime,
): Instant {
    fun nightEndingAt(wake: LocalTime) = Bedtime(wake, fixedBedtime?.toLocalTime())
    val fallback = nextDailyOccurrence(
        now = now,
        time = evening(nightEndingAt(fallbackAlarmTime.toLocalTime())).toReminderTime(),
        earliestLocalDate = earliestLocalDate,
    )
    val beforeAlarm = nextSystemAlarmMillis
        ?.let { Instant.ofEpochMilli(it).atZone(now.zone) }
        ?.takeIf { it.toLocalTime().isWakeTimeNear(fallbackAlarmTime.toLocalTime()) }
        // A night's evening is always the day before it ends, even for an alarm just before midnight.
        ?.let { alarm -> alarm.toLocalDate().minusDays(1).atTime(evening(nightEndingAt(alarm.toLocalTime()))).atZone(now.zone) }
        ?.takeIf { reminder ->
            reminder.isAfter(now) &&
                !reminder.toLocalDate().isBefore(earliestLocalDate) &&
                !reminder.toLocalDate().isAfter(fallback.toLocalDate())
        }
    return (beforeAlarm ?: fallback).toInstant()
}

private fun LocalTime.toReminderTime() = ReminderTime(hour = hour, minute = minute)

/**
 * Coordinators advance to an earliest habit date after handling a reminder. The underlying
 * schedules use civil dates; before 04:00 (including repeated DST hours), a civil occurrence can
 * still belong to the handled habit day. Keep searching without shifting the wake alarm itself.
 */
internal fun nextReminderOnOrAfterHabitDate(
    now: ZonedDateTime,
    earliestHabitDate: LocalDate?,
    nextOccurrence: (ZonedDateTime) -> Instant?,
): Instant? {
    var searchFrom = now
    while (true) {
        val candidate = nextOccurrence(searchFrom) ?: return null
        if (earliestHabitDate == null || habitDate(candidate.atZone(now.zone)) >= earliestHabitDate) {
            return candidate
        }
        check(candidate.isAfter(searchFrom.toInstant())) { "Reminder search must advance." }
        searchFrom = candidate.atZone(now.zone)
    }
}
