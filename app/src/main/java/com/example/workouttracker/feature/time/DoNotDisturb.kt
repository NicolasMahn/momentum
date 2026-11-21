package com.example.workouttracker.feature.time

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings

/** Do Not Disturb as a session sees it; an interface so its rules run without a phone. */
internal interface DoNotDisturb {
    val hasAccess: Boolean

    /** The system's interruption filter, one of `NotificationManager.INTERRUPTION_FILTER_*`. */
    var filter: Int
}

/** What a session changed, kept so ending it undoes exactly that and nothing more. */
internal data class FocusChange(
    val previousFilter: Int,
    val sessionFilter: Int,
)

/**
 * Priority rather than total silence: it is the system's own Do Not Disturb, so alarms and the
 * callers a person allowed there still get through. A phone that is already quieted is their choice,
 * and the session leaves it alone.
 */
internal fun focusChangeFor(currentFilter: Int, hasAccess: Boolean): FocusChange? =
    if (hasAccess && currentFilter == NotificationManager.INTERRUPTION_FILTER_ALL) {
        FocusChange(previousFilter = currentFilter, sessionFilter = NotificationManager.INTERRUPTION_FILTER_PRIORITY)
    } else {
        null
    }

/** The filter to put back, or null when he changed it himself during the session: that is his now. */
internal fun filterToRestore(change: FocusChange, currentFilter: Int): Int? =
    change.previousFilter.takeIf { currentFilter == change.sessionFilter }

internal class AndroidDoNotDisturb(context: Context) : DoNotDisturb {
    private val notifications = context.applicationContext.getSystemService(NotificationManager::class.java)

    override val hasAccess: Boolean
        get() = notifications?.isNotificationPolicyAccessGranted == true

    override var filter: Int
        get() = notifications?.currentInterruptionFilter ?: NotificationManager.INTERRUPTION_FILTER_UNKNOWN
        set(value) {
            notifications?.setInterruptionFilter(value)
        }
}

/** Where Android lets the user grant Do Not Disturb access; there is no in-app prompt for it. */
internal fun doNotDisturbAccessSettings(): Intent =
    Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
