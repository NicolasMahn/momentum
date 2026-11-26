package de.nicolasmahn.momentum.reminder

import android.app.AlarmManager
import android.content.Context
import de.nicolasmahn.momentum.domain.reminder.Bedtime
import de.nicolasmahn.momentum.domain.reminder.ReminderSettings
import de.nicolasmahn.momentum.domain.reminder.nextWakeTime
import de.nicolasmahn.momentum.domain.reminder.nightEndingAt
import java.time.ZonedDateTime

/**
 * The night ahead, from the phone's next alarm or else the fallback wake time, asleep by the
 * bedtime the settings keep. The app, the widget and a notification's face all ask here, so they
 * agree on when Mo goes to bed.
 */
internal fun Context.currentBedtime(
    reminders: ReminderSettings,
    now: ZonedDateTime = ZonedDateTime.now(),
): Bedtime {
    val upcoming = getSystemService(AlarmManager::class.java)?.nextAlarmClock?.triggerTime
    return reminders.nightEndingAt(
        nextWakeTime(
            now = now,
            nextSystemAlarmMillis = upcoming,
            fallbackAlarmTime = reminders.fallbackAlarmTime,
            lastRungAlarmMillis = noteSystemAlarm(upcoming, now.toInstant().toEpochMilli()),
        ),
    )
}

/**
 * Android forgets an alarm the moment it rings, so the last upcoming alarm seen is kept and becomes
 * "the alarm that rang" once its time has passed. Returns that alarm. It is runtime state, not user
 * data: losing it only lets Mo lie in until the fallback time once, so it stays out of the backup.
 */
private fun Context.noteSystemAlarm(upcoming: Long?, nowMillis: Long): Long? {
    val seen = getSharedPreferences("bedtime_runtime", Context.MODE_PRIVATE)
    val stored = seen.getLong(UPCOMING, NONE)
    var rung = seen.getLong(RUNG, NONE)
    if (stored != NONE && stored <= nowMillis) rung = stored
    seen.edit().putLong(UPCOMING, upcoming ?: NONE).putLong(RUNG, rung).apply()
    return rung.takeIf { it != NONE }
}

private const val UPCOMING = "upcoming"
private const val RUNG = "rung"
private const val NONE = -1L
