package de.nicolasmahn.momentum.reminder

import de.nicolasmahn.momentum.domain.settings.DashboardFeature
import org.junit.Assert.assertEquals
import org.junit.Test

class FeatureNotificationCancellerTest {
    /** Only Activity and Substances have a notification of a fixed ID; every view's is the shared coordinator's to take down. */
    @Test
    fun eachFeatureCancelsOnlyItsOwnNotification() {
        val cancelled = mutableListOf<Int>()
        val canceller = FeatureNotificationCanceller(cancelled::add)

        DashboardFeature.entries.forEach(canceller::cancel)

        assertEquals(listOf(WorkoutReminderNotification.WorkoutNotificationId, SubstanceCheckInNotification.NotificationId), cancelled)
    }

    @Test
    fun anAnsweredActivityReminderCancelsTheWorkoutNotification() {
        val cancelled = mutableListOf<Int>()
        val canceller = FeatureNotificationCanceller(cancelled::add)

        canceller.cancel(ReminderCompletionNotification.Activity)

        assertEquals(listOf(WorkoutReminderNotification.WorkoutNotificationId), cancelled)
    }
}
