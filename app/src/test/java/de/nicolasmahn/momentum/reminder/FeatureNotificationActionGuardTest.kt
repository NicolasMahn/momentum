package de.nicolasmahn.momentum.reminder

import de.nicolasmahn.momentum.domain.settings.DashboardFeature
import de.nicolasmahn.momentum.domain.settings.FeatureVisibilitySettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureNotificationActionGuardTest {
    @Test
    fun hiddenFeatureCancelsItsNotificationWithoutRunningTheStaleAction() {
        val cancelled = mutableListOf<Int>()
        var actionCalls = 0
        val guard = FeatureNotificationActionGuard(
            visibilitySource = FeatureVisibilitySource {
                FeatureVisibilitySettings(setOf(DashboardFeature.Substances))
            },
            notificationCanceller = FeatureNotificationCanceller(cancelled::add),
        )

        assertFalse(
            guard.run(DashboardFeature.Substances) {
                actionCalls += 1
            },
        )

        assertEquals(0, actionCalls)
        assertEquals(listOf(SubstanceCheckInNotification.NotificationId), cancelled)
    }

    @Test
    fun visibleFeatureRunsTheActionWithoutCancellingItsNotification() {
        val cancelled = mutableListOf<Int>()
        var actionCalls = 0
        val guard = FeatureNotificationActionGuard(
            visibilitySource = FeatureVisibilitySource { FeatureVisibilitySettings() },
            notificationCanceller = FeatureNotificationCanceller(cancelled::add),
        )

        assertTrue(
            guard.run(DashboardFeature.Substances) {
                actionCalls += 1
            },
        )

        assertEquals(1, actionCalls)
        assertEquals(emptyList<Int>(), cancelled)
    }
}
