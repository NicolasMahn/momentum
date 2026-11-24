package de.nicolasmahn.momentum.reminder

import de.nicolasmahn.momentum.domain.reminder.ReminderSettings
import de.nicolasmahn.momentum.domain.reminder.WorkoutReminderSettings
import de.nicolasmahn.momentum.domain.settings.DashboardFeature
import de.nicolasmahn.momentum.domain.settings.FeatureVisibilitySettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureReminderGateTest {
    private val raw = ReminderSettings(workout = WorkoutReminderSettings(enabled = true))

    /** Only hiding Activity turns the workout reminder off; every view's checkpoint is gated by the shared coordinator. */
    @Test
    fun onlyHiddenActivityDisablesTheWorkoutReminder() {
        DashboardFeature.entries.forEach { hidden ->
            val effective = raw.gatedBy(FeatureVisibilitySettings(disabledFeatures = setOf(hidden)))
            assertEquals(hidden.name, hidden != DashboardFeature.Activity, effective.workout.enabled)
        }
    }

    @Test
    fun gatingNeverChangesRawReminderPreferences() {
        raw.gatedBy(FeatureVisibilitySettings(disabledFeatures = setOf(DashboardFeature.Activity)))
        assertTrue(raw.workout.enabled)
    }
}
