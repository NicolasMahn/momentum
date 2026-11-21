package com.example.workouttracker.reminder

import com.example.workouttracker.domain.reminder.ReminderSettings
import com.example.workouttracker.domain.settings.FeatureVisibilitySettings
import com.example.workouttracker.persistence.ReminderSettingsRepository
import java.time.DayOfWeek

/**
 * Stores changed reminder settings once every changed alarm they own could be set, the workout
 * reminder's and Mo's check-in's, and sets the old ones again where one could not. [activityDays]
 * reads the chosen days of Activity's stored week at the moment of a change, which the workout
 * reminder is scheduled on in both directions. A checkpoint that follows the wake time or bedtime
 * is set again by the shared coordinator after the save.
 */
internal class ReminderSettingsUpdateCoordinator(
    private val repository: ReminderSettingsRepository,
    private val workoutScheduler: WorkoutReminderScheduler,
    private val moCheckInScheduler: MoCheckInScheduler,
    private val featureVisibility: FeatureVisibilitySource = FeatureVisibilitySource {
        FeatureVisibilitySettings()
    },
    private val activityDays: () -> Set<DayOfWeek>,
) {
    fun apply(
        current: ReminderSettings,
        updated: ReminderSettings,
    ): Boolean {
        val visibility = featureVisibility.load()
        val days = activityDays()
        val currentEffective = current.gatedBy(visibility)
        val updatedEffective = updated.gatedBy(visibility)
        val forceReschedule = current == updated
        val workoutChanged = forceReschedule || current.workout != updated.workout
        val moCheckInChanged = forceReschedule || current.moCheckIn != updated.moCheckIn

        fun restore() {
            if (workoutChanged) workoutScheduler.replace(currentEffective.workout, activityDays())
            if (moCheckInChanged) moCheckInScheduler.replace(currentEffective.moCheckIn)
        }

        if (workoutChanged && !workoutScheduler.replace(updatedEffective.workout, days)) {
            restore()
            return false
        }
        if (moCheckInChanged && !moCheckInScheduler.replace(updatedEffective.moCheckIn)) {
            restore()
            return false
        }
        if (repository.save(updated)) return true
        restore()
        return false
    }
}
