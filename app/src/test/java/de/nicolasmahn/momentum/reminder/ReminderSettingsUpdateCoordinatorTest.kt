package de.nicolasmahn.momentum.reminder

import de.nicolasmahn.momentum.domain.activity.DefaultActivityWeek
import de.nicolasmahn.momentum.domain.reminder.MoCheckInSettings
import de.nicolasmahn.momentum.domain.reminder.ReminderSettings
import de.nicolasmahn.momentum.domain.reminder.ReminderTime
import de.nicolasmahn.momentum.domain.reminder.WorkoutReminderSettings
import de.nicolasmahn.momentum.domain.settings.DashboardFeature
import de.nicolasmahn.momentum.domain.settings.FeatureVisibilitySettings
import de.nicolasmahn.momentum.persistence.ReminderSettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The reminder settings own two alarms, the workout reminder's and Mo's check-in's: a change is
 * stored once each changed one could be set, and the old ones are set again where one could not.
 */
class ReminderSettingsUpdateCoordinatorTest {
    private val disabled = ReminderSettings()
    private val enabled = ReminderSettings(workout = WorkoutReminderSettings(enabled = true))
    private val moved = disabled.copy(moCheckIn = MoCheckInSettings(fixedTime = ReminderTime(21, 30)))

    @Test
    fun movingMoCheckInReschedulesOnlyItAndPersists() {
        val repository = FakeRepository(disabled)
        val workout = FakeWorkoutScheduler(mutableListOf())
        val moCheckIn = FakeMoCheckInScheduler(mutableListOf(true))

        assertEquals(true, coordinator(repository, workout, moCheckIn).apply(disabled, moved))
        assertEquals(listOf(moved.moCheckIn), moCheckIn.settingsCalls)
        assertEquals(emptyList<WorkoutReminderSettings>(), workout.settingsCalls)
        assertEquals(moved, repository.load())
    }

    @Test
    fun moCheckInScheduleFailureRestoresItAndNeverPersists() {
        val repository = FakeRepository(disabled)
        val moCheckIn = FakeMoCheckInScheduler(mutableListOf(false, true))

        assertEquals(false, coordinator(repository, FakeWorkoutScheduler(mutableListOf()), moCheckIn).apply(disabled, moved))
        assertEquals(0, repository.saveCalls)
        assertEquals(listOf(moved.moCheckIn, disabled.moCheckIn), moCheckIn.settingsCalls)
    }

    /** The workout reminder is scheduled on the chosen days of Activity's week as they are stored at the change, and set again on them where the save fails. */
    @Test
    fun theWorkoutReminderIsScheduledOnTheWeeksDaysOnAChangeAndOnARestore() {
        val weekend = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
        val stored = FakeWorkoutScheduler(mutableListOf(true))
        assertEquals(true, coordinator(FakeRepository(disabled), stored, activityDays = { weekend }).apply(disabled, enabled))
        assertEquals(listOf(weekend), stored.daysCalls)

        val refused = FakeWorkoutScheduler(mutableListOf(true, true))
        assertEquals(false, coordinator(FakeRepository(disabled, acceptWrites = false), refused, activityDays = { weekend }).apply(disabled, enabled))
        assertEquals(listOf(enabled.workout, disabled.workout), refused.settingsCalls)
        assertEquals(listOf(weekend, weekend), refused.daysCalls)
    }

    @Test
    fun workoutScheduleFailureRestoresItAndNeverPersists() {
        val repository = FakeRepository(disabled)
        val workout = FakeWorkoutScheduler(mutableListOf(false, true))

        assertEquals(false, coordinator(repository, workout).apply(disabled, enabled))
        assertEquals(0, repository.saveCalls)
        assertEquals(listOf(enabled.workout, disabled.workout), workout.settingsCalls)
    }

    @Test
    fun repositoryFailureRestoresEveryChangedSchedule() {
        val repository = FakeRepository(disabled, acceptWrites = false)
        val workout = FakeWorkoutScheduler(mutableListOf(true, true))
        val moCheckIn = FakeMoCheckInScheduler(mutableListOf(true, true))
        val updated = enabled.copy(moCheckIn = moved.moCheckIn)

        assertEquals(false, coordinator(repository, workout, moCheckIn).apply(disabled, updated))
        assertEquals(1, repository.saveCalls)
        assertEquals(listOf(updated.workout, disabled.workout), workout.settingsCalls)
        assertEquals(listOf(updated.moCheckIn, disabled.moCheckIn), moCheckIn.settingsCalls)
        assertEquals(disabled, repository.load())
    }

    @Test
    fun successfulUpdateSchedulesThenPersistsExactlyOnce() {
        val repository = FakeRepository(disabled)
        val workout = FakeWorkoutScheduler(mutableListOf(true))

        assertEquals(true, coordinator(repository, workout).apply(disabled, enabled))
        assertEquals(1, repository.saveCalls)
        assertEquals(listOf(enabled.workout), workout.settingsCalls)
        assertEquals(enabled, repository.load())
    }

    /** A change of the wake time or bedtime alone moves neither alarm here: the checkpoints that follow them are the shared coordinator's. */
    @Test
    fun aChangedWakeTimeMovesNeitherAlarmOfTheseSettings() {
        val repository = FakeRepository(enabled)
        val workout = FakeWorkoutScheduler(mutableListOf())
        val moCheckIn = FakeMoCheckInScheduler(mutableListOf())
        val updated = enabled.copy(fallbackAlarmTime = ReminderTime(6, 30), fixedBedtime = ReminderTime(22, 0))

        assertEquals(true, coordinator(repository, workout, moCheckIn).apply(enabled, updated))
        assertEquals(emptyList<WorkoutReminderSettings>(), workout.settingsCalls)
        assertEquals(emptyList<MoCheckInSettings>(), moCheckIn.settingsCalls)
        assertEquals(updated, repository.load())
    }

    @Test
    fun hiddenActivityPersistsItsRawReminderButKeepsItsEffectiveScheduleDisabled() {
        val repository = FakeRepository(disabled)
        val workout = FakeWorkoutScheduler(mutableListOf(true))
        val coordinator = ReminderSettingsUpdateCoordinator(
            repository = repository,
            workoutScheduler = workout,
            moCheckInScheduler = FakeMoCheckInScheduler(),
            featureVisibility = FeatureVisibilitySource { FeatureVisibilitySettings(setOf(DashboardFeature.Activity)) },
            activityDays = { DefaultActivityWeek.chosen },
        )

        assertEquals(true, coordinator.apply(disabled, enabled))
        assertEquals(listOf(false), workout.settingsCalls.map(WorkoutReminderSettings::enabled))
        assertEquals(enabled, repository.load())
    }

    private fun coordinator(
        repository: FakeRepository,
        workout: FakeWorkoutScheduler,
        moCheckIn: FakeMoCheckInScheduler = FakeMoCheckInScheduler(),
        activityDays: () -> Set<DayOfWeek> = { DefaultActivityWeek.chosen },
    ) = ReminderSettingsUpdateCoordinator(repository, workout, moCheckIn, activityDays = activityDays)

    private class FakeRepository(
        private var settings: ReminderSettings,
        private val acceptWrites: Boolean = true,
    ) : ReminderSettingsRepository {
        var saveCalls = 0

        override fun load(): ReminderSettings = settings

        override fun save(settings: ReminderSettings): Boolean {
            saveCalls += 1
            if (!acceptWrites) return false
            this.settings = settings
            return true
        }
    }

    private class FakeWorkoutScheduler(
        private val results: MutableList<Boolean>,
    ) : WorkoutReminderScheduler {
        val settingsCalls = mutableListOf<WorkoutReminderSettings>()
        val daysCalls = mutableListOf<Set<DayOfWeek>>()

        override fun replace(
            settings: WorkoutReminderSettings,
            days: Set<DayOfWeek>,
            earliestLocalDate: LocalDate?,
        ): Boolean {
            settingsCalls += settings
            daysCalls += days
            return results.removeFirst()
        }
    }

    private class FakeMoCheckInScheduler(
        private val results: MutableList<Boolean> = MutableList(4) { true },
    ) : MoCheckInScheduler {
        val settingsCalls = mutableListOf<MoCheckInSettings>()

        override fun replace(
            settings: MoCheckInSettings,
            firedNudge: LocalDateTime?,
        ): Boolean {
            settingsCalls += settings
            return results.removeFirst()
        }
    }
}
