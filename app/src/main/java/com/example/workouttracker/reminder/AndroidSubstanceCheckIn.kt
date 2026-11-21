package com.example.workouttracker.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.workouttracker.R
import com.example.workouttracker.app.appPendingIntent
import com.example.workouttracker.app.substancesDestinationPendingIntent
import com.example.workouttracker.core.locale.withAppLanguage
import com.example.workouttracker.core.text.UiText
import com.example.workouttracker.core.text.resolve
import com.example.workouttracker.core.text.uiText
import com.example.workouttracker.domain.checklist.CheckpointTime
import com.example.workouttracker.domain.reminder.ReminderTime
import com.example.workouttracker.domain.settings.DashboardFeature
import com.example.workouttracker.feature.substances.viewLabel
import com.example.workouttracker.persistence.PreferencesReminderSettingsRepository
import com.example.workouttracker.persistence.PreferencesTrackerRepository
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class SubstanceCheckInReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        context.substanceCheckInCoordinator().onReminderDue()
    }
}

/**
 * "None yesterday" on the check-in. It records "none" for the day the check-in asked about, and never
 * over a use: a day that holds one stays as it is, and the notification says so, its tap opening
 * the day. A receiver may not open a screen itself (Android 12 blocks the trampoline), and a tap
 * on the notification is the way that always works.
 */
class SubstanceNoneTodayReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!isSubstanceNoneTodayAction(intent.action)) return
        context.featureNotificationActionGuard().run(DashboardFeature.Substances) {
            val canceller = FeatureNotificationCanceller(context.applicationContext)
            val dateKey = reminderTargetDateKey(intent.getStringExtra(ExtraReminderTargetDateKey))
            if (dateKey == null) {
                canceller.cancel(DashboardFeature.Substances)
                return@run
            }
            when (SubstanceNoneTodayCoordinator(PreferencesTrackerRepository(context.applicationContext)).record(dateKey)) {
                SubstanceNoneTodayResult.Saved -> {
                    context.announceSavedRecord()
                    canceller.cancel(DashboardFeature.Substances)
                }
                SubstanceNoneTodayResult.HoldsUse -> SubstanceCheckInNotification(context.applicationContext).showHoldsRecord(dateKey)
                SubstanceNoneTodayResult.SaveFailed,
                SubstanceNoneTodayResult.NotOver,
                -> SubstanceCheckInNotification(context.applicationContext).showActionFailure(dateKey)
            }
        }
    }
}

internal fun isSubstanceNoneTodayAction(action: String?): Boolean = action == SubstanceCheckInNotification.ActionNoneToday

internal fun Context.substanceCheckInCoordinator() = SubstanceCheckInCoordinator(
    trackerRepository = PreferencesTrackerRepository(applicationContext),
    featureVisibility = featureVisibilitySource(),
    gateway = AndroidSubstanceCheckInGateway(applicationContext),
    notifier = SubstanceCheckInNotification(applicationContext),
    reminderSettings = PreferencesReminderSettingsRepository(applicationContext)::load,
    quietedByChoice = quietedByChoice(applicationContext),
)

internal class AndroidSubstanceCheckInGateway(
    context: Context,
    private val now: () -> ZonedDateTime = { ZonedDateTime.now(ZoneId.systemDefault()) },
) : SubstanceCheckInGateway {
    private val applicationContext = context.applicationContext
    private val alarmManager = applicationContext.getSystemService(AlarmManager::class.java)

    override fun canScheduleExact(): Boolean = canScheduleExactReminders(applicationContext)

    override fun schedule(at: CheckpointTime, earliestDate: LocalDate?, fallbackAlarmTime: ReminderTime, fixedBedtime: ReminderTime?): Boolean = runCatching {
        val current = now()
        val nextSystemAlarm = alarmManager.nextAlarmClock?.triggerTime
        val instant = nextReminderOnOrAfterHabitDate(current, earliestDate) { searchFrom ->
            nextCheckpointReminderInstant(searchFrom, at, fallbackAlarmTime, fixedBedtime, nextSystemAlarm, earliestDate ?: current.toLocalDate())
        } ?: return false
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, instant.toEpochMilli(), alarm())
    }.isSuccess

    override fun cancel() {
        alarmManager.cancel(alarm())
        NotificationManagerCompat.from(applicationContext).cancel(SubstanceCheckInNotification.NotificationId)
    }

    private fun alarm(): PendingIntent = PendingIntent.getBroadcast(
        applicationContext,
        SubstanceCheckInRequestCode,
        Intent(applicationContext, SubstanceCheckInReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

internal const val SubstanceCheckInRequestCode = 8_901

/**
 * The check-in as a plain notification: the view's name, as stored ([viewLabel]), over "Anything
 * to log today?", which names no substance and assumes nothing, with "None yesterday" as its
 * action. A tap opens Substances on the day it asks about, even after that day has become yesterday.
 */
internal class SubstanceCheckInNotification(
    context: Context,
    private val viewLabel: () -> UiText = { PreferencesTrackerRepository(context.applicationContext).load().substances.viewLabel() },
) : SubstanceCheckInNotifier {
    private val applicationContext = context.applicationContext

    override fun show(dateKey: String): Boolean = showTest(dateKey, opens = true)

    /** The same check-in sent as a test; where Home cannot open Substances, a tap opens the app where it was. */
    fun showTest(dateKey: String, opens: Boolean): Boolean = notify(
        message = MoReminderMessage(title = viewLabel(), text = uiText(R.string.substance_check_in_question)),
        dateKey = dateKey,
        opens = opens,
        withAction = true,
        onlyAlertOnce = false,
    )

    /** "None yesterday" met a use: the day stays as it is, and a tap opens it; the action is gone, as it could do nothing. */
    fun showHoldsRecord(dateKey: String): Boolean = notify(
        message = MoReminderMessage(title = viewLabel(), text = uiText(R.string.substance_check_in_holds_record)),
        dateKey = dateKey,
        opens = true,
        withAction = false,
        onlyAlertOnce = true,
    )

    fun showActionFailure(dateKey: String): Boolean = notify(
        message = MoReminderMessage(title = uiText(R.string.substance_check_in_failed_title), text = uiText(R.string.substance_check_in_failed_text, viewLabel())),
        dateKey = dateKey,
        opens = true,
        withAction = true,
        onlyAlertOnce = true,
    )

    private fun notify(message: MoReminderMessage, dateKey: String, opens: Boolean, withAction: Boolean, onlyAlertOnce: Boolean): Boolean {
        if (!notificationsAllowed()) return false
        val resources = applicationContext.withAppLanguage().resources
        val text = message.text.resolve(resources)
        val builder = NotificationCompat.Builder(applicationContext, ReminderNotificationChannel.Id)
            .setSmallIcon(R.drawable.ic_notification_substances)
            .setContentTitle(message.title.resolve(resources))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(if (opens) substancesDestinationPendingIntent(applicationContext, dateKey) else appPendingIntent(applicationContext))
            .setAutoCancel(true)
            .setOnlyAlertOnce(onlyAlertOnce)
        if (withAction) builder.addAction(noneTodayAction(dateKey, resources))
        return runCatching { NotificationManagerCompat.from(applicationContext).notify(NotificationId, builder.build()); true }.getOrDefault(false)
    }

    private fun noneTodayAction(dateKey: String, resources: Resources): NotificationCompat.Action {
        val pendingIntent = PendingIntent.getBroadcast(
            applicationContext,
            NoneTodayRequestCode,
            Intent(applicationContext, SubstanceNoneTodayReceiver::class.java).apply {
                action = ActionNoneToday
                putExtra(ExtraReminderTargetDateKey, dateKey)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Action.Builder(
            R.drawable.ic_notification_substances,
            resources.getString(R.string.substance_check_in_none_today),
            pendingIntent,
        ).build()
    }

    private fun notificationsAllowed(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    companion object {
        internal const val NotificationId = 8_901
        internal const val NoneTodayRequestCode = 8_903
        internal const val ActionNoneToday = "com.example.workouttracker.action.SUBSTANCES_NONE_TODAY"
    }
}
