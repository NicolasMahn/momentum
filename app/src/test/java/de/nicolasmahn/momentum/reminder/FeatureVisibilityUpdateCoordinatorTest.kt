package de.nicolasmahn.momentum.reminder

import de.nicolasmahn.momentum.domain.settings.ViewRef
import de.nicolasmahn.momentum.domain.compatibility.MoNeed
import de.nicolasmahn.momentum.domain.reminder.ReminderSettings
import de.nicolasmahn.momentum.domain.reminder.WorkoutReminderSettings
import de.nicolasmahn.momentum.domain.settings.DashboardFeature
import de.nicolasmahn.momentum.domain.settings.FeatureVisibilitySettings
import de.nicolasmahn.momentum.persistence.FeatureVisibilityRepository
import de.nicolasmahn.momentum.persistence.ReminderSettingsRepository
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Hiding or showing a view stores the visibility once the one alarm the reminder settings own for
 * a view, Activity's workout reminder, could follow it. Every other view's checkpoint follows the
 * stored visibility through the shared coordinator.
 */
class FeatureVisibilityUpdateCoordinatorTest {
    /** Activity hidden and shown again cancels and sets the workout reminder on the chosen days of its week, and a refused save sets the old state on them again. */
    @Test
    fun hidingAndShowingActivityReschedulesTheWeeksDays() {
        val reminders = ReminderSettings(workout = WorkoutReminderSettings(enabled = true))
        val fixture = Fixture(reminders = reminders)
        assertTrue(fixture.applyHidden(ViewRef.Feature(DashboardFeature.Activity)))
        assertEquals(listOf(WorkoutReminderNotification.WorkoutNotificationId), fixture.cancelled)
        assertTrue(fixture.applyVisible(ViewRef.Feature(DashboardFeature.Activity)))
        assertEquals(listOf(false to WeekendDays, true to WeekendDays), fixture.workout.calls)
        assertTrue(fixture.reminderRepository.load().workout.enabled)

        val refused = Fixture(reminders = reminders, visibilityAcceptsWrites = false)
        assertFalse(refused.applyHidden(ViewRef.Feature(DashboardFeature.Activity)))
        assertEquals(listOf(false to WeekendDays, true to WeekendDays), refused.workout.calls)
        assertTrue(refused.visibilityRepository.load().isVisible(DashboardFeature.Activity))
        assertEquals(emptyList<Int>(), refused.cancelled)
    }

    /** A view's own reminder is not this coordinator's: hiding Weight stores the visibility and sets no alarm here. */
    @Test
    fun hidingAViewStoresTheVisibilityAndLeavesItsReminderToTheSharedCoordinator() {
        val fixture = Fixture()
        assertTrue(fixture.applyHidden(MoNeed.Weight.view))
        assertFalse(fixture.visibilityRepository.load().isVisible(MoNeed.Weight.view))
        assertEquals(emptyList<Pair<Boolean, Set<DayOfWeek>>>(), fixture.workout.calls)
        assertEquals(emptyList<Int>(), fixture.cancelled)
    }

    private class Fixture(
        reminders: ReminderSettings = ReminderSettings(),
        visibility: FeatureVisibilitySettings = FeatureVisibilitySettings(),
        visibilityAcceptsWrites: Boolean = true,
    ) {
        val visibilityRepository = FakeVisibilityRepository(visibility, visibilityAcceptsWrites)
        val reminderRepository = FakeReminderRepository(reminders)
        val workout = FakeWorkoutScheduler()
        val cancelled = mutableListOf<Int>()
        private val coordinator = FeatureVisibilityUpdateCoordinator(
            visibilityRepository = visibilityRepository,
            reminderRepository = reminderRepository,
            workoutScheduler = workout,
            notificationCanceller = FeatureNotificationCanceller { cancelled += it },
            activityDays = { WeekendDays },
        )

        fun applyHidden(view: ViewRef): Boolean = coordinator.apply(
            current = visibilityRepository.load(),
            updated = visibilityRepository.load().withVisibility(view, visible = false, today = LocalDate.parse("2026-10-14")),
        )

        fun applyVisible(view: ViewRef): Boolean = coordinator.apply(
            current = visibilityRepository.load(),
            updated = visibilityRepository.load().withVisibility(view, visible = true, today = LocalDate.parse("2026-10-14")),
        )
    }

    private class FakeVisibilityRepository(
        private var settings: FeatureVisibilitySettings,
        private val acceptWrites: Boolean,
    ) : FeatureVisibilityRepository {
        override fun load(): FeatureVisibilitySettings = settings

        override fun save(settings: FeatureVisibilitySettings): Boolean {
            if (!acceptWrites) return false
            this.settings = settings
            return true
        }
    }

    private class FakeReminderRepository(
        private var settings: ReminderSettings,
    ) : ReminderSettingsRepository {
        override fun load(): ReminderSettings = settings

        override fun save(settings: ReminderSettings): Boolean {
            this.settings = settings
            return true
        }
    }

    private class FakeWorkoutScheduler : WorkoutReminderScheduler {
        val calls = mutableListOf<Pair<Boolean, Set<DayOfWeek>>>()

        override fun replace(settings: WorkoutReminderSettings, days: Set<DayOfWeek>, earliestLocalDate: LocalDate?): Boolean {
            calls += settings.enabled to days
            return true
        }
    }
}

private val WeekendDays = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
