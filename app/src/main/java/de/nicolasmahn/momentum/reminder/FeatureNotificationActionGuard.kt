package de.nicolasmahn.momentum.reminder

import android.content.Context
import de.nicolasmahn.momentum.domain.settings.DashboardFeature
import de.nicolasmahn.momentum.persistence.PreferencesFeatureVisibilityRepository

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
