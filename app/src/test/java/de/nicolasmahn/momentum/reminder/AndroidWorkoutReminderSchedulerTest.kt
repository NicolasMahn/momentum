package de.nicolasmahn.momentum.reminder

import de.nicolasmahn.momentum.domain.activity.DefaultActivityWeek
import de.nicolasmahn.momentum.domain.reminder.WorkoutReminderSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.ZonedDateTime

class AndroidWorkoutReminderSchedulerTest {
    private val now = ZonedDateTime.parse("2026-09-16T17:30:00+02:00[Europe/Berlin]")

    @Test
    fun disabledReminderCancelsWithoutExactAccess() {
        val gateway = FakeAlarmGateway(exactAllowed = false)

        assertEquals(
            true,
            AndroidWorkoutReminderScheduler(gateway) { now }
                .replace(WorkoutReminderSettings(enabled = false), DefaultActivityWeek.chosen),
        )
        assertEquals(1, gateway.cancelCalls)
        assertEquals(emptyList<Long>(), gateway.exactTriggers)
    }

    @Test
    fun enabledReminderRequiresExactAccessAndSchedulesItsFixedTime() {
        val denied = FakeAlarmGateway(exactAllowed = false)
        assertEquals(
            false,
            AndroidWorkoutReminderScheduler(denied) { now }
                .replace(WorkoutReminderSettings(enabled = true), DefaultActivityWeek.chosen),
        )

        val allowed = FakeAlarmGateway(exactAllowed = true)
        assertEquals(
            true,
            AndroidWorkoutReminderScheduler(allowed) { now }
                .replace(WorkoutReminderSettings(enabled = true), DefaultActivityWeek.chosen),
        )
        assertEquals(
            listOf(
                ZonedDateTime.parse("2026-09-16T18:00:00+02:00[Europe/Berlin]")
                    .toInstant()
                    .toEpochMilli(),
            ),
            allowed.exactTriggers,
        )
    }

    /** The alarm lands on the reminder's next day, skipping the days it does not come on. */
    @Test
    fun enabledReminderSchedulesTheNextOfItsDays() {
        val gateway = FakeAlarmGateway(exactAllowed = true)

        assertEquals(
            true,
            AndroidWorkoutReminderScheduler(gateway) { now }
                .replace(WorkoutReminderSettings(enabled = true), setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)),
        )
        assertEquals(
            listOf(ZonedDateTime.parse("2026-09-19T18:00:00+02:00[Europe/Berlin]").toInstant().toEpochMilli()),
            gateway.exactTriggers,
        )
    }

    @Test
    fun theWorkoutReminderSharesNoIdentityWithAViewsReminder() {
        assertNotEquals(ChecklistReminderRequestCode, WorkoutReminderRequestCode)
        assertNotEquals(ChecklistReminderRequestCode, WorkoutReminderNotification.WorkoutNotificationId)
    }

    @Test
    fun exactAlarmWriteFailureIsObservable() {
        val gateway = FakeAlarmGateway(exactAllowed = true, failExactWrite = true)

        assertEquals(
            false,
            AndroidWorkoutReminderScheduler(gateway) { now }
                .replace(WorkoutReminderSettings(enabled = true), DefaultActivityWeek.chosen),
        )
        assertEquals(emptyList<Long>(), gateway.exactTriggers)
    }

    private class FakeAlarmGateway(
        private val exactAllowed: Boolean,
        private val failExactWrite: Boolean = false,
    ) : WorkoutReminderAlarmGateway {
        var cancelCalls = 0
        val exactTriggers = mutableListOf<Long>()

        override fun canScheduleExact(): Boolean = exactAllowed

        override fun cancel() {
            cancelCalls += 1
        }

        override fun setExact(triggerAtMillis: Long) {
            if (failExactWrite) error("AlarmManager rejected the alarm")
            exactTriggers += triggerAtMillis
        }
    }
}
