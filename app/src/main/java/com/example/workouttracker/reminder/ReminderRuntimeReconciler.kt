package com.example.workouttracker.reminder

import com.example.workouttracker.domain.reminder.ReminderSettings
import com.example.workouttracker.domain.settings.DashboardFeature
import com.example.workouttracker.domain.settings.FeatureVisibilitySettings
import java.time.DayOfWeek

/**
 * Sets the alarms the reminder settings own from the settings, the visibility and [activityDays],
 * the chosen days of Activity's week, which the workout reminder comes on: the workout reminder
 * and Mo's check-in. Every view's checkpoint reminds through the shared coordinator.
 */
internal class ReminderRuntimeReconciler(
    private val workoutScheduler: WorkoutReminderScheduler,
    private val notificationCanceller: FeatureNotificationCanceller,
    private val moCheckInScheduler: MoCheckInScheduler,
) {
    fun reconcile(
        rawSettings: ReminderSettings,
        visibility: FeatureVisibilitySettings,
        activityDays: Set<DayOfWeek>,
    ): Boolean {
        val settings = rawSettings.gatedBy(visibility)
        val results = listOf(
            workoutScheduler.replace(settings.workout, activityDays),
            moCheckInScheduler.replace(settings.moCheckIn),
        )
        DashboardFeature.entries.filterNot(visibility::isVisible).forEach(notificationCanceller::cancel)
        return results.all { it }
    }
}
