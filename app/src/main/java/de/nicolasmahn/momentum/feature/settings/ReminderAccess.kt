package de.nicolasmahn.momentum.feature.settings

import de.nicolasmahn.momentum.domain.checklist.ChecklistConfiguration
import de.nicolasmahn.momentum.domain.LoggedView
import de.nicolasmahn.momentum.domain.checklist.isInUse
import de.nicolasmahn.momentum.domain.ref
import de.nicolasmahn.momentum.domain.reminder.ReminderSettings
import de.nicolasmahn.momentum.domain.settings.DashboardFeature
import de.nicolasmahn.momentum.domain.settings.FeatureVisibilitySettings
import de.nicolasmahn.momentum.domain.shows
import java.time.LocalDate

internal enum class ReminderAccessRequirement {
    Ready,
    ExactAlarm,
    Notifications,
    Stop,
}

internal fun reminderAccessRequirement(
    enabled: Boolean,
    exactAlarmAllowed: Boolean,
    notificationsAllowed: Boolean,
): ReminderAccessRequirement = when {
    !enabled -> ReminderAccessRequirement.Ready
    !exactAlarmAllowed -> ReminderAccessRequirement.ExactAlarm
    !notificationsAllowed -> ReminderAccessRequirement.Notifications
    else -> ReminderAccessRequirement.Ready
}

internal fun reminderAccessAfterExactAlarm(
    granted: Boolean,
    notificationsAllowed: Boolean,
): ReminderAccessRequirement = when {
    !granted -> ReminderAccessRequirement.Stop
    !notificationsAllowed -> ReminderAccessRequirement.Notifications
    else -> ReminderAccessRequirement.Ready
}

internal fun reminderAccessAfterNotifications(granted: Boolean): ReminderAccessRequirement =
    if (granted) ReminderAccessRequirement.Ready else ReminderAccessRequirement.Stop

/**
 * What stands between a feature's reminder and actually arriving. Settings states this next to the
 * control itself, so nothing depends on the reader inferring it from where a row sits.
 */
internal enum class ReminderDependency {
    /** The feature is hidden on Home, so its reminder is off the table. */
    Hidden,
    PreciseTiming,
    Notifications,
    Ready,
}

/** Same order as [reminderAccessRequirement], so the note names the permission asked for first. */
internal fun reminderDependency(
    featureVisible: Boolean,
    exactAlarmAllowed: Boolean,
    notificationsAllowed: Boolean,
): ReminderDependency = when {
    !featureVisible -> ReminderDependency.Hidden
    !exactAlarmAllowed -> ReminderDependency.PreciseTiming
    !notificationsAllowed -> ReminderDependency.Notifications
    else -> ReminderDependency.Ready
}

/** Global reminder settings; tracker checkpoint commands own their own access requirements. */
internal fun reminderUpdateNeedsPlatformAccess(current: ReminderSettings, updated: ReminderSettings): Boolean =
    if (current == updated) updated.workout.enabled || updated.moCheckIn.enabled
    else (current.workout != updated.workout && updated.workout.enabled) ||
        (current.moCheckIn != updated.moCheckIn && updated.moCheckIn.enabled)

/** Global reminders, before adding the enabled checkpoints discovered from tracker modules. */
internal fun remindersOn(settings: ReminderSettings, visibility: FeatureVisibilitySettings, substanceCheckIn: Boolean): Int =
    (if (settings.workout.enabled && visibility.isVisible(DashboardFeature.Activity)) 1 else 0) +
        (if (substanceCheckIn && visibility.isVisible(DashboardFeature.Substances)) 1 else 0) +
        (if (settings.moCheckIn.enabled) 1 else 0)
