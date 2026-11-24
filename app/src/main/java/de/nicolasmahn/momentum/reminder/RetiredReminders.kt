package de.nicolasmahn.momentum.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat

/**
 * The receivers of Weight's, Supplements', Reading's, Care's two and Eating's own alarms, which
 * the build before this one set. They remind through the shared coordinator now, so these stay
 * registered for one version and only reconcile: an old alarm that rings before anything else
 * ran still reaches something, which retires it and sets its slot's.
 */
abstract class RetiredReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        context.reconcileEveryReminder()
    }
}

class WeightReminderReceiver : RetiredReminderReceiver()

class SupplementReminderReceiver : RetiredReminderReceiver()

class ReadingReminderReceiver : RetiredReminderReceiver()

class MorningCareReminderReceiver : RetiredReminderReceiver()

class EveningCareReminderReceiver : RetiredReminderReceiver()

class EatingReminderReceiver : RetiredReminderReceiver()

/**
 * Each old alarm's identity, the receiver and the request code it was set with, which is what
 * cancelling it needs, and the ID of the notification it posted, which is what taking that down needs.
 */
private val RetiredReminders: List<Pair<Class<out RetiredReminderReceiver>, Int>> = listOf(
    WeightReminderReceiver::class.java to 8_001,
    SupplementReminderReceiver::class.java to 8_201,
    ReadingReminderReceiver::class.java to 8_301,
    MorningCareReminderReceiver::class.java to 8_401,
    EveningCareReminderReceiver::class.java to 8_402,
    EatingReminderReceiver::class.java to 8_501,
)

/**
 * Retires what the build before this one set for the five's own reminders: each old alarm is
 * cancelled by its identity and each old notification is taken down. Repeating this is harmless.
 */
internal fun Context.retireOldFeatureReminders() {
    val alarms = applicationContext.getSystemService(AlarmManager::class.java)
    val notifications = NotificationManagerCompat.from(applicationContext)
    RetiredReminders.forEach { (receiver, code) ->
        PendingIntent.getBroadcast(applicationContext, code, Intent(applicationContext, receiver), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
            ?.let { alarm ->
                alarms.cancel(alarm)
                alarm.cancel()
            }
        // Each old notification had its fixed ID and no tag, so none of the shared coordinator's is touched.
        notifications.cancel(code)
    }
}
