package de.nicolasmahn.momentum.feature.settings

import de.nicolasmahn.momentum.domain.reminder.MoCheckInSettings
import de.nicolasmahn.momentum.domain.reminder.ReminderSettings
import de.nicolasmahn.momentum.domain.reminder.ReminderTime
import de.nicolasmahn.momentum.domain.reminder.WorkoutReminderSettings
import de.nicolasmahn.momentum.domain.settings.DashboardFeature
import de.nicolasmahn.momentum.domain.settings.FeatureVisibilitySettings
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderAccessTest {
    @Test
    fun enablingRequestsExactAccessBeforeNotificationPermission() {
        assertEquals(
            ReminderAccessRequirement.ExactAlarm,
            reminderAccessRequirement(
                enabled = true,
                exactAlarmAllowed = false,
                notificationsAllowed = false,
            ),
        )
        assertEquals(
            ReminderAccessRequirement.Notifications,
            reminderAccessRequirement(
                enabled = true,
                exactAlarmAllowed = true,
                notificationsAllowed = false,
            ),
        )
        assertEquals(
            ReminderAccessRequirement.Ready,
            reminderAccessRequirement(
                enabled = true,
                exactAlarmAllowed = true,
                notificationsAllowed = true,
            ),
        )
    }

    @Test
    fun disablingNeverRequiresPlatformAccess() {
        assertEquals(
            ReminderAccessRequirement.Ready,
            reminderAccessRequirement(
                enabled = false,
                exactAlarmAllowed = false,
                notificationsAllowed = false,
            ),
        )
    }

    @Test
    fun denialStopsWithoutReachingPersistence() {
        assertEquals(
            ReminderAccessRequirement.Stop,
            reminderAccessAfterExactAlarm(
                granted = false,
                notificationsAllowed = true,
            ),
        )
        assertEquals(
            ReminderAccessRequirement.Stop,
            reminderAccessAfterNotifications(granted = false),
        )
    }

    @Test
    fun exactGrantContinuesToNotificationBeforeReady() {
        assertEquals(
            ReminderAccessRequirement.Notifications,
            reminderAccessAfterExactAlarm(
                granted = true,
                notificationsAllowed = false,
            ),
        )
        assertEquals(
            ReminderAccessRequirement.Ready,
            reminderAccessAfterNotifications(granted = true),
        )
    }

    @Test
    fun aHiddenFeatureReportsItsOwnSwitchBeforeAnyPermission() {
        assertEquals(
            ReminderDependency.Hidden,
            reminderDependency(
                featureVisible = false,
                exactAlarmAllowed = false,
                notificationsAllowed = false,
            ),
        )
    }

    @Test
    fun aVisibleFeatureNamesTheAccessItIsAskedForFirst() {
        assertEquals(
            ReminderDependency.PreciseTiming,
            reminderDependency(
                featureVisible = true,
                exactAlarmAllowed = false,
                notificationsAllowed = false,
            ),
        )
        assertEquals(
            ReminderDependency.Notifications,
            reminderDependency(
                featureVisible = true,
                exactAlarmAllowed = true,
                notificationsAllowed = false,
            ),
        )
        assertEquals(
            ReminderDependency.Ready,
            reminderDependency(
                featureVisible = true,
                exactAlarmAllowed = true,
                notificationsAllowed = true,
            ),
        )
    }

    @Test
    fun moCheckInNeedsPlatformAccessOnlyWhenItIsTurnedOnOrMoved() {
        val off = ReminderSettings(moCheckIn = MoCheckInSettings(enabled = false))
        val on = ReminderSettings()

        assertEquals(true, reminderUpdateNeedsPlatformAccess(off, on))
        assertEquals(
            true,
            reminderUpdateNeedsPlatformAccess(
                on,
                on.copy(moCheckIn = on.moCheckIn.copy(fixedTime = ReminderTime(21, 0))),
            ),
        )
        assertEquals(false, reminderUpdateNeedsPlatformAccess(on, off))
    }

    /** Settings reschedules unchanged global reminders when saved, if Activity or Mo is on. */
    @Test
    fun anUnchangedGlobalReminderSaveNeedsAccessOnlyWhileOneIsOn() {
        fun unchanged(settings: ReminderSettings) = reminderUpdateNeedsPlatformAccess(settings, settings)
        val allOff = ReminderSettings(
            workout = WorkoutReminderSettings(enabled = false),
            moCheckIn = MoCheckInSettings(enabled = false),
        )
        val workoutOn = allOff.copy(workout = WorkoutReminderSettings(enabled = true))
        val moOn = allOff.copy(moCheckIn = MoCheckInSettings(enabled = true))

        assertEquals(true, unchanged(workoutOn))
        assertEquals(true, unchanged(moOn))
        assertEquals(false, unchanged(allOff))
    }

    @Test
    fun disablingAnOnGlobalReminderDoesNotRestoreRevokedAccess() {
        val bothOn = ReminderSettings(
            workout = WorkoutReminderSettings(enabled = true),
            moCheckIn = MoCheckInSettings(enabled = true),
        )
        val workoutOff = bothOn.copy(workout = bothOn.workout.copy(enabled = false))
        val bothOff = workoutOff.copy(moCheckIn = workoutOff.moCheckIn.copy(enabled = false))

        assertEquals(false, reminderUpdateNeedsPlatformAccess(bothOn, workoutOff))
        assertEquals(false, reminderUpdateNeedsPlatformAccess(workoutOff, bothOff))
        assertEquals(true, reminderUpdateNeedsPlatformAccess(bothOn, bothOn))
    }

    @Test
    fun changingAnEnabledWorkoutReminderStillRequiresPlatformAccess() {
        val current = ReminderSettings(workout = WorkoutReminderSettings(enabled = true))
        val updated = current.copy(workout = current.workout.copy(fixedTime = ReminderTime(19, 0)))

        assertEquals(true, reminderUpdateNeedsPlatformAccess(current, updated))
    }

    /** These are the global reminder count; tracker checkpoint reminders are counted by their modules. */
    @Test
    fun globalReminderCountIncludesVisibleActivityAndSubstancesAndMo() {
        val settings = ReminderSettings(workout = WorkoutReminderSettings(enabled = true))
        val visibility = FeatureVisibilitySettings()

        assertEquals(2, remindersOn(settings, visibility, substanceCheckIn = false))
        assertEquals(3, remindersOn(settings, visibility, substanceCheckIn = true))
        assertEquals(2, remindersOn(settings, visibility.hiding(DashboardFeature.Activity), substanceCheckIn = true))
        assertEquals(2, remindersOn(settings, visibility.hiding(DashboardFeature.Substances), substanceCheckIn = true))

        val allOff = settings.copy(
            workout = settings.workout.copy(enabled = false),
            moCheckIn = settings.moCheckIn.copy(enabled = false),
        )
        assertEquals(0, remindersOn(allOff, visibility, substanceCheckIn = false))
    }
}
