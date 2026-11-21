package com.example.workouttracker.reminder

import com.example.workouttracker.domain.reminder.ReminderSettings
import com.example.workouttracker.domain.reminder.WorkoutReminderSettings
import com.example.workouttracker.domain.settings.DashboardFeature
import com.example.workouttracker.domain.settings.FeatureVisibilitySettings
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
