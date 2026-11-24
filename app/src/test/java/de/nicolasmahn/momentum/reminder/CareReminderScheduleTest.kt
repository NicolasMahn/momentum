package de.nicolasmahn.momentum.reminder

import de.nicolasmahn.momentum.domain.compatibility.LegacyCareSession
import de.nicolasmahn.momentum.domain.reminder.ReminderTime
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Care's two sessions remind at their checkpoints' times by the rules every checkpoint has: the
 * morning five minutes after waking, the evening fifteen minutes before sleep, after forty-five of
 * wind-down, each following the phone's alarm for its own day.
 */
class CareReminderScheduleTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val fallback = ReminderTime(8, 0)

    @Test
    fun morningCareFollowsTheDaysPhoneAlarm() {
        val now = ZonedDateTime.of(2026, 9, 16, 3, 0, 0, 0, zone)
        val phoneAlarm = ZonedDateTime.of(2026, 9, 16, 10, 0, 0, 0, zone)
            .toInstant()
            .toEpochMilli()

        assertEquals(
            ZonedDateTime.of(2026, 9, 16, 10, 5, 0, 0, zone).toInstant(),
            nextCheckpointReminderInstant(
                now = now,
                at = LegacyCareSession.MORNING.builtInTime,
                fallbackAlarmTime = fallback,
                fixedBedtime = null,
                nextSystemAlarmMillis = phoneAlarm,
            ),
        )
    }

    @Test
    fun morningCareUsesASameDayAlarmAfterTheFallbackHasPassed() {
        val now = ZonedDateTime.of(2026, 9, 16, 9, 0, 0, 0, zone)
        val phoneAlarm = ZonedDateTime.of(2026, 9, 16, 10, 0, 0, 0, zone)
            .toInstant()
            .toEpochMilli()

        assertEquals(
            ZonedDateTime.of(2026, 9, 16, 10, 5, 0, 0, zone).toInstant(),
            nextCheckpointReminderInstant(
                now = now,
                at = LegacyCareSession.MORNING.builtInTime,
                fallbackAlarmTime = fallback,
                fixedBedtime = null,
                nextSystemAlarmMillis = phoneAlarm,
            ),
        )
    }

    @Test
    fun morningCareDoesNotSkipTomorrowFallbackForALaterDayAlarm() {
        val now = ZonedDateTime.of(2026, 9, 16, 9, 0, 0, 0, zone)
        val phoneAlarm = ZonedDateTime.of(2026, 9, 19, 7, 0, 0, 0, zone)
            .toInstant()
            .toEpochMilli()

        assertEquals(
            ZonedDateTime.of(2026, 9, 17, 8, 5, 0, 0, zone).toInstant(),
            nextCheckpointReminderInstant(
                now = now,
                at = LegacyCareSession.MORNING.builtInTime,
                fallbackAlarmTime = fallback,
                fixedBedtime = null,
                nextSystemAlarmMillis = phoneAlarm,
            ),
        )
    }

    @Test
    fun eveningCareLeavesFortyFiveMinutesOfWindDownBeforeTheFallbackWakeTime() {
        val now = ZonedDateTime.of(2026, 9, 16, 7, 0, 0, 0, zone)

        assertEquals(
            ZonedDateTime.of(2026, 9, 16, 23, 45, 0, 0, zone).toInstant(),
            nextCheckpointReminderInstant(
                now = now,
                at = LegacyCareSession.EVENING.builtInTime,
                fallbackAlarmTime = fallback,
                fixedBedtime = null,
                nextSystemAlarmMillis = null,
            ),
        )
    }

    @Test
    fun eveningCareFollowsTomorrowsPhoneAlarm() {
        val now = ZonedDateTime.of(2026, 9, 16, 18, 0, 0, 0, zone)
        val phoneAlarm = ZonedDateTime.of(2026, 9, 17, 6, 30, 0, 0, zone).toInstant().toEpochMilli()

        assertEquals(
            ZonedDateTime.of(2026, 9, 16, 22, 15, 0, 0, zone).toInstant(),
            nextCheckpointReminderInstant(now, LegacyCareSession.EVENING.builtInTime, fallback, fixedBedtime = null, phoneAlarm),
        )
    }

    @Test
    fun eveningCareAt2245FollowsAnActual0700WakeAlarm() {
        val now = ZonedDateTime.of(2026, 9, 29, 18, 0, 0, 0, zone)
        val phoneAlarm = ZonedDateTime.of(2026, 9, 30, 7, 0, 0, 0, zone).toInstant().toEpochMilli()

        assertEquals(
            ZonedDateTime.of(2026, 9, 29, 22, 45, 0, 0, zone).toInstant(),
            nextCheckpointReminderInstant(now, LegacyCareSession.EVENING.builtInTime, fallback, fixedBedtime = null, phoneAlarm),
        )
    }

    @Test
    fun eveningCareAt2345FollowsAnActual0800WakeAlarm() {
        val now = ZonedDateTime.of(2026, 9, 29, 18, 0, 0, 0, zone)
        val phoneAlarm = ZonedDateTime.of(2026, 9, 30, 8, 0, 0, 0, zone).toInstant().toEpochMilli()

        assertEquals(
            ZonedDateTime.of(2026, 9, 29, 23, 45, 0, 0, zone).toInstant(),
            nextCheckpointReminderInstant(now, LegacyCareSession.EVENING.builtInTime, fallback, fixedBedtime = null, phoneAlarm),
        )
    }

    @Test
    fun eveningCareStaysOnItsOwnEveningBeforeALateAlarm() {
        val now = ZonedDateTime.of(2026, 9, 16, 18, 0, 0, 0, zone)
        val phoneAlarm = ZonedDateTime.of(2026, 9, 17, 10, 0, 0, 0, zone).toInstant().toEpochMilli()

        assertEquals(
            ZonedDateTime.of(2026, 9, 16, 23, 45, 0, 0, zone).toInstant(),
            nextCheckpointReminderInstant(now, LegacyCareSession.EVENING.builtInTime, fallback, fixedBedtime = null, phoneAlarm),
        )
    }

    @Test
    fun eveningCareDoesNotSkipTonightForALaterDayAlarm() {
        val now = ZonedDateTime.of(2026, 9, 16, 18, 0, 0, 0, zone)
        val phoneAlarm = ZonedDateTime.of(2026, 9, 19, 6, 0, 0, 0, zone).toInstant().toEpochMilli()

        assertEquals(
            ZonedDateTime.of(2026, 9, 16, 23, 45, 0, 0, zone).toInstant(),
            nextCheckpointReminderInstant(now, LegacyCareSession.EVENING.builtInTime, fallback, fixedBedtime = null, phoneAlarm),
        )
    }

    /** A fixed bedtime decides the evening whatever the phone's alarm says, and after midnight it stays on its evening. */
    @Test
    fun eveningCareCountsBackFromAFixedBedtime() {
        val now = ZonedDateTime.of(2026, 9, 16, 18, 0, 0, 0, zone)
        val phoneAlarm = ZonedDateTime.of(2026, 9, 17, 6, 30, 0, 0, zone).toInstant().toEpochMilli()
        fun evening(bedtime: ReminderTime, alarm: Long?) =
            nextCheckpointReminderInstant(now, LegacyCareSession.EVENING.builtInTime, fallback, bedtime, alarm).atZone(zone).toLocalDateTime().toString()

        assertEquals("2026-09-16T21:45", evening(ReminderTime(22, 0), null))
        assertEquals("2026-09-16T21:45", evening(ReminderTime(22, 0), phoneAlarm))
        assertEquals("2026-09-16T23:45", evening(ReminderTime(0, 30), null))
        assertEquals("2026-09-16T23:45", evening(ReminderTime(0, 30), phoneAlarm))
    }

    @Test
    fun earliestDateMovesBothPoliciesToTheRequestedDay() {
        val now = ZonedDateTime.of(2026, 9, 16, 23, 0, 0, 0, zone)
        val tomorrow = LocalDate.parse("2026-09-17")

        assertEquals(
            ZonedDateTime.of(2026, 9, 17, 8, 5, 0, 0, zone).toInstant(),
            nextCheckpointReminderInstant(
                now = now,
                at = LegacyCareSession.MORNING.builtInTime,
                fallbackAlarmTime = fallback,
                fixedBedtime = null,
                nextSystemAlarmMillis = null,
                earliestLocalDate = tomorrow,
            ),
        )
        assertEquals(
            ZonedDateTime.of(2026, 9, 17, 23, 45, 0, 0, zone).toInstant(),
            nextCheckpointReminderInstant(
                now = now,
                at = LegacyCareSession.EVENING.builtInTime,
                fallbackAlarmTime = fallback,
                fixedBedtime = null,
                nextSystemAlarmMillis = null,
                earliestLocalDate = tomorrow,
            ),
        )
    }
}
