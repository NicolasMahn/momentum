package com.example.workouttracker.feature.settings

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat

/**
 * What pressing "Allow" next to notifications should actually do.
 *
 * On Android 13+ the notification switch is a runtime permission, and the system can decide not to
 * show its dialog at all: once the user has declined twice, and sometimes on a fresh install, the
 * request returns "denied" immediately with no UI. A button that only ever launches the request
 * therefore looks broken. Falling through to the system notification screen keeps it honest: the
 * button always leads somewhere the switch can actually be flipped.
 */
internal enum class NotificationAccessAction {
    /** Ask the system to show its runtime permission dialog. */
    RequestPermission,

    /** Open this app's notification screen in the phone's settings. */
    OpenSystemSettings,

    /** Notifications already arrive; there is nothing to ask for. */
    None,
}

/** What the button does when pressed. */
internal fun notificationAccessAction(
    sdkInt: Int,
    notificationsAllowed: Boolean,
): NotificationAccessAction = when {
    notificationsAllowed -> NotificationAccessAction.None
    // Before Android 13 notifications are not a runtime permission, so only the system screen can
    // switch them back on.
    sdkInt < Build.VERSION_CODES.TIRAMISU -> NotificationAccessAction.OpenSystemSettings
    else -> NotificationAccessAction.RequestPermission
}

/**
 * What to do once the runtime request has come back. [canAskAgain] is
 * `shouldShowRequestPermissionRationale` read *after* the attempt: true means the system did show
 * its dialog and would show it again, false means it will not ask any more.
 */
internal fun notificationAccessFollowUp(
    granted: Boolean,
    canAskAgain: Boolean,
): NotificationAccessAction = when {
    granted -> NotificationAccessAction.None
    // The dialog was shown and declined. Pushing the user into system settings on top of that
    // would be nagging; the row simply stays until they press Allow again.
    canAskAgain -> NotificationAccessAction.None
    else -> NotificationAccessAction.OpenSystemSettings
}

/**
 * The single truth for "can a reminder reach this phone". It covers both the Android 13+ runtime
 * permission and a user who switched the app's notifications off in system settings.
 */
internal fun notificationsEnabled(context: Context): Boolean =
    NotificationManagerCompat.from(context).areNotificationsEnabled()

internal fun canAskForNotificationsAgain(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
    val activity = context.hostActivity() ?: return false
    return ActivityCompat.shouldShowRequestPermissionRationale(
        activity,
        Manifest.permission.POST_NOTIFICATIONS,
    )
}

internal fun appNotificationSettingsIntent(context: Context): Intent =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:${context.packageName}"))
    }

internal tailrec fun Context.hostActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.hostActivity()
    else -> null
}
