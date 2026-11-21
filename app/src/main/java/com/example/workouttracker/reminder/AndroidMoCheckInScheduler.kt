package com.example.workouttracker.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.workouttracker.domain.reminder.MoCheckInSettings
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

internal const val MoCheckInRequestCode = 8_601
internal const val MoCheckInNudgeKey = "mo_check_in_nudge"

internal class AndroidMoCheckInScheduler(
    private val alarmGateway: MoCheckInAlarmGateway,
    private val now: () -> ZonedDateTime = { ZonedDateTime.now(ZoneId.systemDefault()) },
) : MoCheckInScheduler {
    constructor(context: Context) : this(AndroidMoCheckInAlarmGateway(context))

    override fun replace(
        settings: MoCheckInSettings,
        firedNudge: LocalDateTime?,
    ): Boolean = runCatching {
        if (!settings.enabled) {
            alarmGateway.cancel()
            return@runCatching
        }
        if (!alarmGateway.canScheduleExact()) return false

        val currentTime = now()
        val nudge = nextMoNudge(
            now = currentTime,
            firedNudge = firedNudge,
        )
        alarmGateway.setExact(nudge.atZone(currentTime.zone).toInstant().toEpochMilli(), nudge)
    }.isSuccess
}

internal interface MoCheckInAlarmGateway {
    fun canScheduleExact(): Boolean

    fun cancel()

    /** [nudge] travels with the alarm, so a late delivery still knows which moment it speaks for. */
    fun setExact(triggerAtMillis: Long, nudge: LocalDateTime)
}

private class AndroidMoCheckInAlarmGateway(
    context: Context,
) : MoCheckInAlarmGateway {
    private val applicationContext = context.applicationContext
    private val alarmManager = applicationContext.getSystemService(AlarmManager::class.java)

    override fun canScheduleExact(): Boolean = canScheduleExactReminders(applicationContext)

    override fun cancel() {
        alarmManager.cancel(reminderPendingIntent())
    }

    override fun setExact(triggerAtMillis: Long, nudge: LocalDateTime) {
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            reminderPendingIntent(nudge),
        )
    }

    private fun reminderPendingIntent(nudge: LocalDateTime? = null): PendingIntent =
        PendingIntent.getBroadcast(
            applicationContext,
            MoCheckInRequestCode,
            Intent(applicationContext, MoCheckInReceiver::class.java).apply {
                nudge?.let { putExtra(MoCheckInNudgeKey, it.toString()) }
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
