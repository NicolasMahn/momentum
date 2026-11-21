package com.example.workouttracker.reminder

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import com.example.workouttracker.app.TrackerStateRefreshSignal
import com.example.workouttracker.core.time.currentHabitDate
import com.example.workouttracker.domain.visibilityInUse
import com.example.workouttracker.persistence.PreferencesFeatureVisibilityRepository
import com.example.workouttracker.persistence.PreferencesReminderSettingsRepository
import com.example.workouttracker.persistence.PreferencesTrackerRepository
import com.example.workouttracker.widget.MoWidgetProvider

class WorkoutReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        context.workoutReminderCoordinator().onReminderDue()
    }
}

class MoCheckInReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        context.moCheckInCoordinator().onReminderDue(intent.getStringExtra(MoCheckInNudgeKey))
        // A check-in that fumes must not sit beside a calm Mo on the home screen.
        MoWidgetProvider.updateNow(context)
    }
}

class ReminderRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (isReminderRescheduleAction(intent.action)) context.reconcileEveryReminder()
    }
}

/**
 * Sets every reminder's alarm again, every slot attempted, once what the build before this one set
 * for Weight's, Supplements', Reading's, Care's and Eating's own reminders is retired
 * ([retireOldFeatureReminders]).
 */
internal fun Context.reconcileEveryReminder(): Boolean {
    retireOldFeatureReminders()
    return reconcileReminders(
        workout = { workoutReminderCoordinator().reconcile() },
        moCheckIn = { moCheckInCoordinator().reconcile() },
        checklists = { checklistReminderCoordinator().reconcile() },
        substances = { substanceCheckInCoordinator().reconcile() },
    )
}

/** Runs every reconcile, whatever the earlier ones answer, and answers whether all of them could set their alarms. */
internal fun reconcileReminders(
    workout: () -> Boolean,
    moCheckIn: () -> Boolean,
    checklists: () -> Boolean,
    substances: () -> Boolean,
): Boolean = listOf(workout, moCheckIn, checklists, substances).map { it() }.all { it }

/**
 * A notification action saved a record, possibly with no screen alive: an open screen reloads, and
 * Mo's nudge leaves if that record cleared the overdue tasks.
 */
internal fun Context.announceSavedRecord() {
    TrackerStateRefreshSignal.notifyChanged()
    moCheckInCoordinator().retireIfSettled()
    // The save's own refresh is only posted, which a receiver's process may not live to run.
    MoWidgetProvider.updateNow(this)
}

internal fun isReminderRescheduleAction(action: String?): Boolean = action in RescheduleActions

/** What clears or moves alarms: a restart, the clock, the exact-alarm permission, the phone's alarm, and an update of the app itself. */
private val RescheduleActions = setOf(
    Intent.ACTION_BOOT_COMPLETED,
    Intent.ACTION_MY_PACKAGE_REPLACED,
    Intent.ACTION_TIME_CHANGED,
    Intent.ACTION_TIMEZONE_CHANGED,
    AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
    AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED,
)

// A feature whose checklist has nothing on it is hidden to every alarm, as it is on Home.
internal fun Context.featureVisibilitySource() = FeatureVisibilitySource {
    PreferencesTrackerRepository(applicationContext).load()
        .visibilityInUse(PreferencesFeatureVisibilityRepository(applicationContext).load(), currentHabitDate())
}

internal fun Context.workoutReminderCoordinator() = WorkoutReminderCoordinator(
    settingsRepository = PreferencesReminderSettingsRepository(applicationContext),
    trackerRepository = PreferencesTrackerRepository(applicationContext),
    scheduler = AndroidWorkoutReminderScheduler(applicationContext),
    notifier = WorkoutReminderNotification(applicationContext),
    featureVisibility = featureVisibilitySource(),
    quietedByChoice = quietedByChoice(applicationContext),
)

internal fun Context.moCheckInCoordinator() = MoCheckInCoordinator(
    settingsRepository = PreferencesReminderSettingsRepository(applicationContext),
    trackerRepository = PreferencesTrackerRepository(applicationContext),
    scheduler = AndroidMoCheckInScheduler(applicationContext),
    notifier = MoCheckInNotification(applicationContext),
    rewardInputs = moRewardInputsSource(applicationContext),
    featureVisibility = featureVisibilitySource(),
    is24Hour = { DateFormat.is24HourFormat(applicationContext) },
)
