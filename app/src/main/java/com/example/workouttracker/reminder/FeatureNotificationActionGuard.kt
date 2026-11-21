package com.example.workouttracker.reminder

import android.content.Context
import com.example.workouttracker.domain.settings.DashboardFeature
import com.example.workouttracker.persistence.PreferencesFeatureVisibilityRepository

internal class FeatureNotificationActionGuard(
    private val visibilitySource: FeatureVisibilitySource,
    private val notificationCanceller: FeatureNotificationCanceller,
) {
    fun run(feature: DashboardFeature, action: () -> Unit): Boolean {
        if (!visibilitySource.isVisible(feature)) {
            notificationCanceller.cancel(feature)
            return false
        }
        action()
        return true
    }
}

internal fun Context.featureNotificationActionGuard() = FeatureNotificationActionGuard(
    visibilitySource = FeatureVisibilitySource {
        PreferencesFeatureVisibilityRepository(applicationContext).load()
    },
    notificationCanceller = FeatureNotificationCanceller(applicationContext),
)
