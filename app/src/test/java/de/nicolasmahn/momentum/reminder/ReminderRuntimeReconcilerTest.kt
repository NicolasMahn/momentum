package de.nicolasmahn.momentum.reminder

import de.nicolasmahn.momentum.domain.activity.DefaultActivityWeek
import de.nicolasmahn.momentum.domain.reminder.MoCheckInSettings
import de.nicolasmahn.momentum.domain.reminder.ReminderSettings
import de.nicolasmahn.momentum.domain.reminder.WorkoutReminderSettings
import de.nicolasmahn.momentum.domain.settings.DashboardFeature
import de.nicolasmahn.momentum.domain.settings.FeatureVisibilitySettings
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderRuntimeReconcilerTest {
    /** Hidden Activity gates the workout reminder off without changing the stored switch, and its notification goes with Substances' while that is hidden. */
    @Test
    fun startupGatesHiddenFeaturesWithoutChangingRawPreferences() {
        val fixture = Fixture()
        val raw = ReminderSettings(workout = WorkoutReminderSettings(true))

        assertTrue(fixture.reconciler.reconcile(raw, FeatureVisibilitySettings(setOf(DashboardFeature.Activity, DashboardFeature.Substances)), setOf(DayOfWeek.SATURDAY)))

        assertEquals(listOf(false), fixture.workout.enabledCalls)
        assertEquals(listOf(setOf(DayOfWeek.SATURDAY)), fixture.workout.daysCalls)
        assertEquals(listOf(raw.moCheckIn), fixture.moCheckIn.calls)
        assertEquals(listOf(WorkoutReminderNotification.WorkoutNotificationId, SubstanceCheckInNotification.NotificationId), fixture.cancelled)
        assertTrue(raw.workout.enabled)
    }

    @Test
    fun oneSchedulerFailureStillReconcilesTheOther() {
        val fixture = Fixture(workoutResult = false)

        assertFalse(fixture.reconciler.reconcile(ReminderSettings(workout = WorkoutReminderSettings(true)), FeatureVisibilitySettings(), DefaultActivityWeek.chosen))
        assertEquals(1, fixture.workout.enabledCalls.size)
        assertEquals(1, fixture.moCheckIn.calls.size)
    }

    private class Fixture(workoutResult: Boolean = true) {
        val workout = FakeWorkoutScheduler(workoutResult)
        val moCheckIn = FakeMoCheckInScheduler()
        val cancelled = mutableListOf<Int>()
        val reconciler = ReminderRuntimeReconciler(
            workoutScheduler = workout,
            notificationCanceller = FeatureNotificationCanceller(cancelled::add),
            moCheckInScheduler = moCheckIn,
        )
    }

    private class FakeWorkoutScheduler(private val result: Boolean) : WorkoutReminderScheduler {
        val enabledCalls = mutableListOf<Boolean>()
        val daysCalls = mutableListOf<Set<DayOfWeek>>()

        override fun replace(settings: WorkoutReminderSettings, days: Set<DayOfWeek>, earliestLocalDate: LocalDate?): Boolean {
            enabledCalls += settings.enabled
            daysCalls += days
            return result
        }
    }

    private class FakeMoCheckInScheduler : MoCheckInScheduler {
        val calls = mutableListOf<MoCheckInSettings>()

        override fun replace(settings: MoCheckInSettings, firedNudge: LocalDateTime?): Boolean {
            calls += settings
            return true
        }
    }
}
