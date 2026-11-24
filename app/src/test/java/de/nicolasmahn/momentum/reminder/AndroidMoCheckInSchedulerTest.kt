package de.nicolasmahn.momentum.reminder

import de.nicolasmahn.momentum.domain.reminder.MoCheckInSettings
import de.nicolasmahn.momentum.domain.reminder.ReminderTime
import java.time.LocalDateTime
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidMoCheckInSchedulerTest {
    private val now = ZonedDateTime.parse("2026-09-16T17:30:00+02:00[Europe/Berlin]")

    @Test
    fun disabledCheckInCancelsWithoutExactAccess() {
        val gateway = FakeAlarmGateway(exactAllowed = false)

        assertEquals(
            true,
            AndroidMoCheckInScheduler(gateway) { now }.replace(MoCheckInSettings(enabled = false)),
        )
        assertEquals(1, gateway.cancelCalls)
        assertEquals(emptyList<Long>(), gateway.exactTriggers)
    }

    @Test
    fun enabledCheckInRequiresExactAccessAndSchedulesTheNextNudgeWithItsMoment() {
        val denied = FakeAlarmGateway(exactAllowed = false)
        assertEquals(false, AndroidMoCheckInScheduler(denied) { now }.replace(MoCheckInSettings()))

        val allowed = FakeAlarmGateway(exactAllowed = true)
        assertEquals(true, AndroidMoCheckInScheduler(allowed) { now }.replace(MoCheckInSettings()))
        assertEquals(listOf(millis("2026-09-16T18:00:00+02:00[Europe/Berlin]")), allowed.exactTriggers)
        assertEquals(listOf(LocalDateTime.parse("2026-09-16T18:00")), allowed.nudges)
    }

    @Test
    fun theArchivedChosenTimeDoesNotAddANudgeAndTheScheduleMovesToTomorrow() {
        val chosen = MoCheckInSettings(fixedTime = ReminderTime(17, 45))

        val before = FakeAlarmGateway(exactAllowed = true)
        AndroidMoCheckInScheduler(before) { now }.replace(chosen)
        assertEquals(listOf(millis("2026-09-16T18:00:00+02:00[Europe/Berlin]")), before.exactTriggers)

        val night = FakeAlarmGateway(exactAllowed = true)
        AndroidMoCheckInScheduler(night) { now.withHour(21) }.replace(MoCheckInSettings())
        assertEquals(listOf(millis("2026-09-17T12:00:00+02:00[Europe/Berlin]")), night.exactTriggers)
    }

    @Test
    fun aNudgeThatJustFiredIsNotScheduledAgain() {
        val gateway = FakeAlarmGateway(exactAllowed = true)
        val firedEarly = ZonedDateTime.parse("2026-09-16T19:59:58+02:00[Europe/Berlin]")

        AndroidMoCheckInScheduler(gateway) { firedEarly }.replace(
            settings = MoCheckInSettings(),
            firedNudge = LocalDateTime.parse("2026-09-16T20:00"),
        )

        assertEquals(listOf(millis("2026-09-16T21:00:00+02:00[Europe/Berlin]")), gateway.exactTriggers)
    }

    @Test
    fun moCheckInSharesNoAlarmOrNotificationIdentityWithAnotherReminder() {
        val requestCodes = listOf(WorkoutReminderRequestCode, ChecklistReminderRequestCode)
        val notificationIds = listOf(
            WorkoutReminderNotification.WorkoutNotificationId,
            SubstanceCheckInNotification.NotificationId,
            ChecklistReminderRequestCode,
        )

        assertEquals(false, MoCheckInRequestCode in requestCodes)
        assertEquals(false, MoCheckInNotification.MoCheckInNotificationId in notificationIds)
    }

    @Test
    fun exactAlarmWriteFailureIsObservable() {
        val gateway = FakeAlarmGateway(exactAllowed = true, failExactWrite = true)

        assertEquals(false, AndroidMoCheckInScheduler(gateway) { now }.replace(MoCheckInSettings()))
    }

    private fun millis(zonedDateTime: String): Long =
        ZonedDateTime.parse(zonedDateTime).toInstant().toEpochMilli()

    private class FakeAlarmGateway(
        private val exactAllowed: Boolean,
        private val failExactWrite: Boolean = false,
    ) : MoCheckInAlarmGateway {
        var cancelCalls = 0
        val exactTriggers = mutableListOf<Long>()
        val nudges = mutableListOf<LocalDateTime>()

        override fun canScheduleExact(): Boolean = exactAllowed

        override fun cancel() {
            cancelCalls += 1
        }

        override fun setExact(triggerAtMillis: Long, nudge: LocalDateTime) {
            if (failExactWrite) error("AlarmManager rejected the alarm")
            exactTriggers += triggerAtMillis
            nudges += nudge
        }
    }
}
