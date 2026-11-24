package de.nicolasmahn.momentum.reminder

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import de.nicolasmahn.momentum.domain.settings.DashboardFeature

internal fun interface FeatureNotificationGateway {
    fun cancel(notificationId: Int)
}

/**
 * Takes down the notifications of the reminders that have one fixed ID each, Activity's and
 * Substances' check-in. Every view's checkpoint reminds through the shared coordinator, which takes
 * its own notifications down by their keys.
 */
internal class FeatureNotificationCanceller(
    private val gateway: FeatureNotificationGateway,
) {
    constructor(context: Context) : this(
        FeatureNotificationGateway { notificationId ->
            NotificationManagerCompat.from(context.applicationContext).cancel(notificationId)
        },
    )

    fun cancel(feature: DashboardFeature) {
        feature.notificationIds.forEach(gateway::cancel)
    }

    fun cancel(target: ReminderCompletionNotification) {
        gateway.cancel(target.notificationId)
    }
}

internal sealed interface ReminderCompletionNotification {
    /** Activity's reminder, answered by anything logged on the day it asked about: an entry, or a session with a set. */
    data object Activity : ReminderCompletionNotification

    /** Substances' check-in, answered by a day logged with a use or with "none". */
    data object Substances : ReminderCompletionNotification
}

/** The one notification [this] is. */
internal val ReminderCompletionNotification.notificationId: Int
    get() = when (this) {
        ReminderCompletionNotification.Activity -> WorkoutReminderNotification.WorkoutNotificationId
        ReminderCompletionNotification.Substances -> SubstanceCheckInNotification.NotificationId
    }

private val DashboardFeature.notificationIds: List<Int>
    get() = when (this) {
        DashboardFeature.Activity -> listOf(WorkoutReminderNotification.WorkoutNotificationId)
        DashboardFeature.Substances -> listOf(SubstanceCheckInNotification.NotificationId)
        DashboardFeature.Statistics -> emptyList()
    }
