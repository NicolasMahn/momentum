package de.nicolasmahn.momentum.domain.compatibility

import de.nicolasmahn.momentum.domain.reminder.ReminderTime
import de.nicolasmahn.momentum.domain.reminder.afterWake
import de.nicolasmahn.momentum.domain.reminder.toLocalTime
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
