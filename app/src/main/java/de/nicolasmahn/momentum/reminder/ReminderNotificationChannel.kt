package de.nicolasmahn.momentum.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import de.nicolasmahn.momentum.R
import de.nicolasmahn.momentum.core.locale.withAppLanguage

internal object ReminderNotificationChannel {
    const val Id = "reminders"

    /** Run again when the app language changes: re-creating a channel only renames it. */
    fun create(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val resources = context.withAppLanguage().resources
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                Id,
                resources.getString(R.string.reminder_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = resources.getString(R.string.reminder_channel_description)
            },
        )
    }
}
