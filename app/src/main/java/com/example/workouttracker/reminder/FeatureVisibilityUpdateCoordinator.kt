package com.example.workouttracker.reminder

import com.example.workouttracker.domain.settings.DashboardFeature
import com.example.workouttracker.domain.settings.FeatureVisibilitySettings
import com.example.workouttracker.persistence.FeatureVisibilityRepository
import com.example.workouttracker.persistence.ReminderSettingsRepository
import java.time.DayOfWeek

/**
 * Stores a changed visibility once the workout reminder could be set or cancelled for it, and sets
 * the old one again where it could not. [activityDays] reads the chosen days of Activity's stored
 * week at the moment of a change, which the workout reminder is scheduled on in both directions.
 * Every view's checkpoint is set again for the stored visibility by the shared coordinator.
 */
internal class FeatureVisibilityUpdateCoordinator(
    private val visibilityRepository: FeatureVisibilityRepository,
    private val reminderRepository: ReminderSettingsRepository,
    private val workoutScheduler: WorkoutReminderScheduler,
    private val notificationCanceller: FeatureNotificationCanceller,
    private val activityDays: () -> Set<DayOfWeek>,
) {
    fun apply(
        current: FeatureVisibilitySettings,
        updated: FeatureVisibilitySettings,
    ): Boolean {
        if (current == updated) return true

        val changedFeatures = DashboardFeature.entries.filter { feature ->
            current.isVisible(feature) != updated.isVisible(feature)
        }
        val activityChanged = DashboardFeature.Activity in changedFeatures
        val rawReminders = reminderRepository.load()
        fun scheduleFor(visibility: FeatureVisibilitySettings) = workoutScheduler.replace(rawReminders.gatedBy(visibility).workout, activityDays())

        if (activityChanged && !scheduleFor(updated)) {
            scheduleFor(current)
            return false
        }
        if (!visibilityRepository.save(updated)) {
            if (activityChanged) scheduleFor(current)
            return false
        }

        changedFeatures
            .filterNot(updated::isVisible)
            .forEach(notificationCanceller::cancel)
        return true
    }
}
