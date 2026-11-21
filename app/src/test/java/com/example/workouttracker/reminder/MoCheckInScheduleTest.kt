package com.example.workouttracker.reminder

import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class MoCheckInScheduleTest {
    @Test
    fun heLooksInEveryThreeHoursFromNoonToNine() {
        assertEquals(listOf("12:00", "15:00", "18:00", "21:00").map(LocalTime::parse), moNudgeTimes())
    }

    @Test
    fun theNextNudgeIsStrictlyAfterNowAndNeverAddsA20CheckIn() {
        val expected = mapOf(
            "2026-09-16T00:00" to "2026-09-16T12:00",
            "2026-09-16T11:59:59" to "2026-09-16T12:00",
            "2026-09-16T12:00" to "2026-09-16T15:00",
            "2026-09-16T13:00" to "2026-09-16T15:00",
            "2026-09-16T18:00" to "2026-09-16T21:00",
            "2026-09-16T19:59" to "2026-09-16T21:00",
            "2026-09-16T20:00" to "2026-09-16T21:00",
            "2026-09-16T21:00" to "2026-09-17T12:00",
            "2026-12-31T22:00" to "2027-01-01T12:00",
        )
        assertEquals(expected, expected.keys.associateWith { nextMoNudgeAfter(LocalDateTime.parse(it)).toString() })
    }

    @Test
    fun anAlarmDeliveredEarlyMovesOnFromTheNudgeItWasSetFor() {
        val early = ZonedDateTime.parse("2026-09-16T17:59:58+02:00[Europe/Berlin]")
        assertEquals(LocalDateTime.parse("2026-09-16T21:00"), nextMoNudge(early, LocalDateTime.parse("2026-09-16T18:00")))
    }

    @Test
    fun lateDeliverySkipsNudgesThatHaveAlreadyPassed() {
        val late = ZonedDateTime.parse("2026-09-16T18:20:00+02:00[Europe/Berlin]")
        assertEquals(LocalDateTime.parse("2026-09-16T21:00"), nextMoNudge(late, LocalDateTime.parse("2026-09-16T15:00")))
    }
}
