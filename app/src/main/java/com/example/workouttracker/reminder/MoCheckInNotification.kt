package com.example.workouttracker.reminder

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.workouttracker.R
import com.example.workouttracker.app.homeDestinationPendingIntent
import com.example.workouttracker.core.locale.withAppLanguage
import com.example.workouttracker.core.text.resolve
import com.example.workouttracker.domain.mo.MoMood

internal class MoCheckInNotification(
    context: Context,
) : MoCheckInNotifier {
    private val applicationContext = context.applicationContext

    override fun show(message: MoReminderMessage, mood: MoMood, angry: Boolean): Boolean =
        notify(message, mood, angry, MoCheckInNotificationId)

    /**
     * The check-in sent as a test, fuming under its own ID. The real one is taken down whenever
     * the app resumes or saves with nothing really overdue, which would take a test away the
     * moment it arrived; a test stays until it is swiped or tapped, beside a real one if need be.
     */
    fun showTest(message: MoReminderMessage, mood: MoMood): Boolean =
        notify(message, mood, angry = true, id = MoCheckInTestNotificationId)

    @SuppressLint("MissingPermission")
    private fun notify(message: MoReminderMessage, mood: MoMood, angry: Boolean, id: Int): Boolean {
        if (!notificationsAllowed()) return false

        val resources = applicationContext.withAppLanguage().resources
        val text = message.text.resolve(resources)
        val notification = NotificationCompat.Builder(
            applicationContext,
            ReminderNotificationChannel.Id,
        )
            .setSmallIcon(R.drawable.ic_notification_mo)
            .setLargeIcon(moLargeIcon(applicationContext, mood, angry))
            .setContentTitle(message.title.resolve(resources))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(homeDestinationPendingIntent(applicationContext))
            .setAutoCancel(true)
            // Every nudge reuses the one id, and each of them has to be heard: a nudge that only
            // rewrote a notification left in the shade would be no nudge at all.
            .setOnlyAlertOnce(false)
            .build()

        return runCatching {
            NotificationManagerCompat.from(applicationContext).notify(id, notification)
            true
        }.getOrDefault(false)
    }

    override fun cancel() {
        NotificationManagerCompat.from(applicationContext).cancel(MoCheckInNotificationId)
    }

    private fun notificationsAllowed(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    companion object {
        internal const val MoCheckInNotificationId = 8_601
        internal const val MoCheckInTestNotificationId = 8_603
    }
}
