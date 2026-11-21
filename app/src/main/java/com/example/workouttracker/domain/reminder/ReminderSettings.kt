package com.example.workouttracker.domain.reminder

import com.example.workouttracker.domain.checklist.CheckpointTime
import com.example.workouttracker.domain.checklist.Timing
import java.time.LocalTime

val DefaultFallbackAlarmTime = ReminderTime(hour = 8, minute = 0)

/**
 * What the reminders share and what is not a view's: the workout reminder, Mo's check-in, and the
 * night every reminder that follows waking or sleep is read against. Every other view's reminder
 * is its checkpoint's, stored with its configuration.
 */
data class ReminderSettings(
    val workout: WorkoutReminderSettings = WorkoutReminderSettings(),
    val moCheckIn: MoCheckInSettings = MoCheckInSettings(),
    val fallbackAlarmTime: ReminderTime = DefaultFallbackAlarmTime,
    /** When sleep starts, or null for eight hours before waking. */
    val fixedBedtime: ReminderTime? = null,
)

/** The night that ends at [wake], asleep by the bedtime these settings keep. */
fun ReminderSettings.nightEndingAt(wake: LocalTime): Bedtime = Bedtime(wake, fixedBedtime?.toLocalTime())

/**
 * The night of a day without a phone alarm. Mo and the settings read relative times against it,
 * because the phone's real alarm is not part of the settings.
 */
val ReminderSettings.usualNight: Bedtime
    get() = nightEndingAt(fallbackAlarmTime.toLocalTime())

/** The times obligations are read against, so Mo and every reminder agree on what is owed. */
val ReminderSettings.timing: Timing
    get() = Timing(night = usualNight, checkIn = moCheckIn.fixedTime.toLocalTime())

/**
 * The workout reminder's switch and time. The days it comes on are Activity's week, the chosen
 * days of its stored configuration, which every scheduler is handed beside these. On those days
 * Mo asks for Activity at [fixedTime] while the reminder is on, and at the check-in time while it
 * is off; a day the week rests is asked for only once no free day is left to cover it.
 */
data class WorkoutReminderSettings(
    val enabled: Boolean = false,
    val fixedTime: ReminderTime = ReminderTime(hour = 18, minute = 0),
)

/**
 * On from the start, unlike the feature reminders. Regular nudges speak only about overdue tasks.
 * The stored name and time remain compatible with existing settings and backup archives.
 */
data class MoCheckInSettings(
    val enabled: Boolean = true,
    /** Due-time fallback for tasks without an enabled reminder; never an extra notification slot. */
    val fixedTime: ReminderTime = ReminderTime(hour = 20, minute = 0),
)

data class ReminderTime(
    val hour: Int,
    val minute: Int,
) {
    init {
        require(hour in 0..23) { "Reminder hour must be between 0 and 23" }
        require(minute in 0..59) { "Reminder minute must be between 0 and 59" }
    }
}
