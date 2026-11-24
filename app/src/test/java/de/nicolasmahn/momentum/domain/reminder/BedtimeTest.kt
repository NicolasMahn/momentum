package de.nicolasmahn.momentum.domain.reminder

import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class BedtimeTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val fallback = ReminderTime(8, 0)

    @Test
    fun theNightIsCountedBackFromWaking() {
        val bedtime = Bedtime(LocalTime.of(8, 0), fixedBedtime = null)

        assertEquals(LocalTime.of(23, 0), bedtime.windDown)
        assertEquals(LocalTime.MIDNIGHT, bedtime.asleepBy)
        assertEquals(LocalTime.of(23, 45), bedtime.beforeSleep(Duration.ofMinutes(15)))
    }

    @Test
    fun thePhasesFollowTheNightAcrossMidnight() {
        val bedtime = Bedtime(LocalTime.of(8, 0), fixedBedtime = null)
        fun phase(time: String) = bedtime.phaseAt(LocalTime.parse(time))

        assertEquals(BedtimePhase.Awake, phase("22:59"))
        assertEquals(BedtimePhase.WindingDown, phase("23:00"))
        assertEquals(BedtimePhase.WindingDown, phase("23:59"))
        assertEquals(BedtimePhase.InBed, phase("00:00"))
        assertEquals(BedtimePhase.InBed, phase("07:59"))
        assertEquals(BedtimePhase.Awake, phase("08:00"))
        assertEquals(BedtimePhase.Awake, phase("15:00"))
    }

    @Test
    fun aLateWakeTimeKeepsTheEveningReminderOnItsOwnEvening() {
        fun eveningBefore(wake: String) = Bedtime(LocalTime.parse(wake), fixedBedtime = null).beforeSleep(Duration.ofMinutes(15))

        assertEquals(LocalTime.of(22, 45), eveningBefore("07:00"))
        listOf("08:01", "08:14", "08:15", "08:59", "09:00", "11:30").forEach { wake ->
            assertEquals(wake, Bedtime.LatestEveningReminder, eveningBefore(wake))
        }
        assertEquals(LocalTime.of(20, 15), eveningBefore("04:30"))
    }

    @Test
    fun aFixedBedtimeDecidesSleepWhateverTheWakeTime() {
        listOf("06:00", "07:00", "09:30").forEach { wake ->
            val bedtime = Bedtime(LocalTime.parse(wake), fixedBedtime = LocalTime.of(22, 30))

            assertEquals(wake, LocalTime.of(22, 30), bedtime.asleepBy)
            assertEquals(wake, LocalTime.of(21, 30), bedtime.windDown)
            assertEquals(wake, LocalTime.of(22, 15), bedtime.beforeSleep(Duration.ofMinutes(15)))
        }
    }

    /** He sleeps from the fixed bedtime until the wake time, however long that is. */
    @Test
    fun aFixedBedtimeKeepsHimInBedUntilWaking() {
        val bedtime = Bedtime(LocalTime.of(6, 0), fixedBedtime = LocalTime.of(22, 30))
        fun phase(time: String) = bedtime.phaseAt(LocalTime.parse(time))

        assertEquals(BedtimePhase.Awake, phase("21:29"))
        assertEquals(BedtimePhase.WindingDown, phase("21:30"))
        assertEquals(BedtimePhase.InBed, phase("22:30"))
        assertEquals(BedtimePhase.InBed, phase("05:59"))
        assertEquals(BedtimePhase.Awake, phase("06:00"))
    }

    /**
     * After midnight, the bedtime is when he goes to bed, but a time before sleep still belongs to
     * the evening it asks about: what would fall after 23:45 is 23:45, and what reaches back
     * further keeps its own time.
     */
    @Test
    fun aBedtimeAfterMidnightKeepsTimesBeforeSleepOnTheEvening() {
        val bedtime = Bedtime(LocalTime.of(7, 0), fixedBedtime = LocalTime.of(0, 30))

        assertEquals(LocalTime.of(0, 30), bedtime.asleepBy)
        assertEquals(LocalTime.of(23, 30), bedtime.windDown)
        assertEquals(Bedtime.LatestEveningReminder, bedtime.beforeSleep(Duration.ofMinutes(15)))
        assertEquals(Bedtime.LatestEveningReminder, bedtime.beforeSleep(Duration.ofMinutes(45)))
        assertEquals(LocalTime.of(23, 29), bedtime.beforeSleep(Duration.ofMinutes(61)))
        assertEquals(LocalTime.of(22, 30), bedtime.beforeSleep(Duration.ofHours(2)))

        assertEquals(BedtimePhase.Awake, bedtime.phaseAt(LocalTime.of(23, 29)))
        assertEquals(BedtimePhase.WindingDown, bedtime.phaseAt(LocalTime.of(0, 29)))
        assertEquals(BedtimePhase.InBed, bedtime.phaseAt(LocalTime.of(0, 30)))
        assertEquals(BedtimePhase.InBed, bedtime.phaseAt(LocalTime.of(6, 59)))
        assertEquals(BedtimePhase.Awake, bedtime.phaseAt(LocalTime.of(7, 0)))
    }

    @Test
    fun withoutAFixedBedtimeTheNightIsStillEightHours() {
        val settings = ReminderSettings(fallbackAlarmTime = ReminderTime(6, 30))

        assertEquals(LocalTime.of(22, 30), settings.usualNight.asleepBy)
        assertEquals(LocalTime.of(23, 15), settings.copy(fixedBedtime = ReminderTime(23, 15)).usualNight.asleepBy)
        assertEquals(LocalTime.of(6, 30), settings.copy(fixedBedtime = ReminderTime(23, 15)).usualNight.wake)
    }

    @Test
    fun thePhoneAlarmDecidesOnlyTheNightRightBeforeIt() {
        val evening = ZonedDateTime.of(2026, 9, 16, 21, 0, 0, 0, zone)
        fun alarm(day: Int, hour: Int, minute: Int) =
            ZonedDateTime.of(2026, 9, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

        assertEquals(LocalTime.of(6, 30), nextWakeTime(evening, alarm(17, 6, 30), fallback))
        assertEquals(LocalTime.of(8, 0), nextWakeTime(evening, alarm(19, 6, 30), fallback))
        assertEquals(LocalTime.of(8, 0), nextWakeTime(evening, alarm(16, 6, 30), fallback))
        assertEquals(LocalTime.of(8, 0), nextWakeTime(evening, null, fallback))
    }

    @Test
    fun aNapOrAppointmentAlarmIsNotAWakeTime() {
        val morning = ZonedDateTime.of(2026, 9, 16, 9, 0, 0, 0, zone)
        fun alarmAt(hour: Int) = ZonedDateTime.of(2026, 9, 16, hour, 0, 0, 0, zone).toInstant().toEpochMilli()

        assertEquals(LocalTime.of(8, 0), nextWakeTime(morning, alarmAt(15), fallback))
        assertEquals(LocalTime.of(8, 0), nextWakeTime(morning, alarmAt(23), fallback))
        assertEquals(LocalTime.of(11, 0), nextWakeTime(morning, alarmAt(11), fallback))
    }

    @Test
    fun anAlarmThatJustRangStillEndsTheNight() {
        val rang = ZonedDateTime.of(2026, 9, 18, 6, 30, 0, 0, zone)
        val monday = ZonedDateTime.of(2026, 9, 21, 6, 30, 0, 0, zone).toInstant().toEpochMilli()
        fun wakeAt(now: ZonedDateTime) = nextWakeTime(now, monday, fallback, lastRungAlarmMillis = rang.toInstant().toEpochMilli())

        assertEquals(LocalTime.of(6, 30), wakeAt(rang.plusMinutes(1)))
        assertEquals(BedtimePhase.Awake, Bedtime(wakeAt(rang.plusMinutes(1)), fixedBedtime = null).phaseAt(LocalTime.of(6, 31)))
        assertEquals(LocalTime.of(8, 0), wakeAt(rang.withHour(22)))
    }

    @Test
    fun aPhaseDoesNotStartBeforeItsMinute() {
        val bedtime = Bedtime(LocalTime.of(8, 0), fixedBedtime = null)

        assertEquals(BedtimePhase.Awake, bedtime.phaseAt(LocalTime.of(22, 59, 30)))
        assertEquals(BedtimePhase.WindingDown, bedtime.phaseAt(LocalTime.of(23, 59, 30)))
    }
}
