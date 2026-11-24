package de.nicolasmahn.momentum.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import de.nicolasmahn.momentum.domain.reminder.WorkoutReminderSettings
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

internal const val WorkoutReminderRequestCode = 8_101

internal class AndroidWorkoutReminderScheduler(
    private val alarmGateway: WorkoutReminderAlarmGateway,
    private val now: () -> ZonedDateTime = { ZonedDateTime.now(ZoneId.systemDefault()) },
) : WorkoutReminderScheduler {
    constructor(context: Context) : this(AndroidWorkoutReminderAlarmGateway(context))

    override fun replace(
        settings: WorkoutReminderSettings,
        days: Set<DayOfWeek>,
        earliestLocalDate: LocalDate?,
    ): Boolean = runCatching {
        if (!settings.enabled) {
            alarmGateway.cancel()
            return@runCatching
        }
        if (!alarmGateway.canScheduleExact()) return false

        val currentTime = now()
        val instant = nextReminderOnOrAfterHabitDate(currentTime, earliestLocalDate) { searchFrom ->
            nextWorkoutReminderInstant(
                now = searchFrom,
                settings = settings,
                days = days,
                earliestLocalDate = earliestLocalDate ?: currentTime.toLocalDate(),
            )
        } ?: return false
        alarmGateway.setExact(instant.toEpochMilli())
    }.isSuccess
}

internal interface WorkoutReminderAlarmGateway {
    fun canScheduleExact(): Boolean

    fun cancel()

    fun setExact(triggerAtMillis: Long)
}

private class AndroidWorkoutReminderAlarmGateway(
    context: Context,
) : WorkoutReminderAlarmGateway {
    private val applicationContext = context.applicationContext
    private val alarmManager = applicationContext.getSystemService(AlarmManager::class.java)

    override fun canScheduleExact(): Boolean = canScheduleExactReminders(applicationContext)

    override fun cancel() {
        alarmManager.cancel(reminderPendingIntent())
    }

    override fun setExact(triggerAtMillis: Long) {
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            reminderPendingIntent(),
        )
    }

    private fun reminderPendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        applicationContext,
        WorkoutReminderRequestCode,
        Intent(applicationContext, WorkoutReminderReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
