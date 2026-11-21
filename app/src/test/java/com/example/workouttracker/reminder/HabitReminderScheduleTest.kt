package com.example.workouttracker.reminder

import com.example.workouttracker.domain.checklist.CheckpointTime
import com.example.workouttracker.core.time.habitDate
import com.example.workouttracker.domain.reminder.ReminderTime
import java.time.LocalDate
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class HabitReminderScheduleTest {
    @Test
    fun handledRepeatedHourSkipsItsSecondOccurrenceAndUsesTheNextHabitDay() {
        val now = ZonedDateTime.parse("2026-10-25T02:30:00+02:00[Europe/Berlin]")
        val earliest = habitDate(now).plusDays(1)
        val next = nextReminderOnOrAfterHabitDate(now, earliest) { after ->
            nextDailyOccurrence(after, ReminderTime(2, 30), earliest).toInstant()
        }
        assertEquals(ZonedDateTime.parse("2026-10-26T02:30:00+01:00[Europe/Berlin]").toInstant(), next)
    }

    @Test
    fun exactFourIsEligible() {
        val now = ZonedDateTime.parse("2026-09-17T03:00:00+02:00[Europe/Berlin]")
        val earliest = LocalDate.parse("2026-09-17")
        val next = nextReminderOnOrAfterHabitDate(now, earliest) { after ->
            nextDailyOccurrence(after, ReminderTime(4, 0), earliest).toInstant()
        }
        assertEquals(now.withHour(4).toInstant(), next)
    }

    @Test
    fun wakeAlarmBeforeFourCanStillProduceAnEligibleReminderAfterFour() {
        val now = ZonedDateTime.parse("2026-09-17T03:00:00+02:00[Europe/Berlin]")
        val earliest = LocalDate.parse("2026-09-17")
        val alarm = now.withMinute(55).toInstant().toEpochMilli()
        val next = nextReminderOnOrAfterHabitDate(now, earliest) { after ->
            nextCheckpointReminderInstant(after, CheckpointTime.AfterWake(15), ReminderTime(8, 0), fixedBedtime = null, alarm, earliestLocalDate = earliest)
        }
        assertEquals(now.withHour(4).withMinute(10).toInstant(), next)
    }
}
