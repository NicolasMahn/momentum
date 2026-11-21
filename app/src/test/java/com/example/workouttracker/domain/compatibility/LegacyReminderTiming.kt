package com.example.workouttracker.domain.compatibility

import com.example.workouttracker.domain.reminder.ReminderTime
import com.example.workouttracker.domain.reminder.afterWake
import com.example.workouttracker.domain.reminder.toLocalTime
import java.time.LocalTime

/** Fixed offsets used by the historical reminder comparison tests. */
internal enum class WakeRelativeReminderPolicy(val minutesAfterAlarm: Long) {
    Weight(5L),
    Reading(15L),
}

internal val MorningCareReminderPolicy = WakeRelativeReminderPolicy.Weight
internal val EatingReminderPolicy = WakeRelativeReminderPolicy.Reading

internal fun WakeRelativeReminderPolicy.fallbackTime(fallbackAlarmTime: ReminderTime): LocalTime =
    afterWake(fallbackAlarmTime.toLocalTime(), minutesAfterAlarm)
