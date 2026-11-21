package com.example.workouttracker.reminder

import com.example.workouttracker.domain.settings.DashboardFeature
import com.example.workouttracker.domain.settings.FeatureVisibilitySettings
import com.example.workouttracker.domain.reminder.ReminderSettings

/**
 * The settings with the workout reminder off while Activity is hidden. It is the one reminder these
 * settings still switch for a view; every view's checkpoint is gated by the shared coordinator.
 */
internal fun ReminderSettings.gatedBy(
    visibility: FeatureVisibilitySettings,
): ReminderSettings = copy(
    workout = workout.copy(
        enabled = workout.enabled && visibility.isVisible(DashboardFeature.Activity),
    ),
)

internal fun interface FeatureVisibilitySource {
    fun load(): FeatureVisibilitySettings
}

internal fun FeatureVisibilitySource.isVisible(feature: DashboardFeature): Boolean =
    load().isVisible(feature)
