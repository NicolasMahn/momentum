package de.nicolasmahn.momentum.reminder

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import de.nicolasmahn.momentum.R
import de.nicolasmahn.momentum.app.activityDestinationPendingIntent
import de.nicolasmahn.momentum.app.workoutDestinationPendingIntent
import de.nicolasmahn.momentum.core.locale.withAppLanguage
import de.nicolasmahn.momentum.core.text.resolve
import de.nicolasmahn.momentum.core.time.currentHabitDate

internal class WorkoutReminderNotification(
    context: Context,
) : WorkoutReminderNotifier {
    private val applicationContext = context.applicationContext

    /** Posts [subject]; its tap opens Activity on the planned activity it names, and the screen as it is otherwise. */
    @SuppressLint("MissingPermission")
    override fun show(subject: MoReminderSubject.Activity): Boolean {
        if (!notificationsAllowed()) return false

        val face = moReminderFace(applicationContext)
        val message = MoReminderVoice.speak(
            subject = subject,
            mood = face.mood,
            day = currentHabitDate(),
        )
        val resources = applicationContext.withAppLanguage().resources
        val text = message.text.resolve(resources)
        val notification = NotificationCompat.Builder(
            applicationContext,
            ReminderNotificationChannel.Id,
        )
            .setSmallIcon(R.drawable.ic_notification_workout)
            .setLargeIcon(face.largeIcon)
            .setContentTitle(message.title.resolve(resources))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(
                subject.activityId?.let { workoutDestinationPendingIntent(applicationContext, it) }
                    ?: activityDestinationPendingIntent(applicationContext),
            )
            .setAutoCancel(true)
            .build()

        return runCatching {
            NotificationManagerCompat.from(applicationContext).notify(
                WorkoutNotificationId,
                notification,
            )
            true
        }.getOrDefault(false)
    }

    private fun notificationsAllowed(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    companion object {
        internal const val WorkoutNotificationId = 8_101
    }
}
