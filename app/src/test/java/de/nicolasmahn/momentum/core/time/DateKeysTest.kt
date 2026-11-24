package de.nicolasmahn.momentum.core.time

import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class DateKeysTest {

    @Test
    fun lateNightStaysOnThePreviousHabitDayUntilExactlyFour() {
        listOf("00:00:00", "02:30:00", "03:59:59").forEach { time ->
            assertEquals("2026-09-16", habitDate(ZonedDateTime.parse(
                "2026-09-17T${time}+02:00[Europe/Berlin]",
            )).toString())
        }
        assertEquals("2026-09-17", habitDate(ZonedDateTime.parse(
            "2026-09-17T04:00:00+02:00[Europe/Berlin]",
        )).toString())
    }

    @Test
    fun fallBackDayWaitsTwentyFiveHoursBetweenBoundaries() {
        val now = ZonedDateTime.parse("2026-10-24T04:00:00+02:00[Europe/Berlin]")
        assertEquals(25 * 60 * 60 * 1_000L, millisUntilNextDay(now))
    }

    @Test
    fun springForwardDoesNotMoveTheBoundaryToFive() {
        val now = ZonedDateTime.parse("2026-03-29T04:00:00+02:00[Europe/Berlin]")
        assertEquals("2026-03-29", habitDate(now).toString())
        assertEquals(24 * 60 * 60 * 1_000L, millisUntilNextDay(now))
    }

    @Test
    fun bothOccurrencesOfRepeatedClockHourBelongToPreviousDay() {
        listOf("+02:00", "+01:00").forEach { offset ->
            assertEquals("2026-10-24", habitDate(ZonedDateTime.parse(
                "2026-10-25T02:30:00${offset}[Europe/Berlin]",
            )).toString())
        }
    }

    @Test
    fun refreshWaitsUntilTheNextLocalDay() {
        val now = ZonedDateTime.parse("2026-09-17T03:59:30+02:00[Europe/Berlin]")

        assertEquals(30_000L, millisUntilNextDay(now))
    }

    @Test
    fun refreshUsesTheShortSpringForwardDay() {
        val now = ZonedDateTime.parse("2026-03-28T04:00:00+01:00[Europe/Berlin]")

        assertEquals(23 * 60 * 60 * 1_000L, millisUntilNextDay(now))
    }

    @Test
    fun nextDateRefreshUsesTheActualLocalDayBoundary() {
        val now = ZonedDateTime.of(2026, 3, 29, 3, 59, 30, 0, ZoneId.of("Europe/Berlin"))

        assertEquals(30_000L, millisUntilNextDay(now))
    }

    @Test
    fun emitsTheNewKeyOnceFourAmHasPassed() = runBlocking {
        var now = ZonedDateTime.parse("2026-09-17T03:59:30+02:00[Europe/Berlin]")

        val keys = localDateKeys(now = { now }, recheckMillis = RecheckMillis)
            .onEach { now = ZonedDateTime.parse("2026-09-17T04:00:00+02:00[Europe/Berlin]") }
            .take(2)
            .toList()

        assertEquals(listOf("2026-09-16", "2026-09-17"), keys)
    }

    @Test
    fun emitsTheNewKeyWhenTheTimeZoneChanges() {
        // Same instant, a long flight later: the local day moved without crossing 04:00 here.
        val instant = ZonedDateTime.parse("2026-09-16T23:30:00+02:00[Europe/Berlin]").toInstant()
        var zone = ZoneId.of("Europe/Berlin")

        val keys = runBlocking {
            localDateKeys(
                now = { ZonedDateTime.ofInstant(instant, zone) },
                recheckMillis = RecheckMillis,
            )
                .onEach { zone = ZoneId.of("Pacific/Auckland") }
                .take(2)
                .toList()
        }

        assertEquals(listOf("2026-09-16", "2026-09-17"), keys)
    }

    @Test
    fun repeatsNothingWhileTheDayStandsStill() = runBlocking {
        val now = ZonedDateTime.parse("2026-09-16T12:00:00+02:00[Europe/Berlin]")

        val keys = localDateKeys(now = { now }, recheckMillis = RecheckMillis)
            .take(1)
            .toList()

        assertEquals(listOf("2026-09-16"), keys)
    }

    private companion object {
        /** Real milliseconds: the test drives the clock, so the wait only has to be short. */
        const val RecheckMillis = 5L
    }
}
