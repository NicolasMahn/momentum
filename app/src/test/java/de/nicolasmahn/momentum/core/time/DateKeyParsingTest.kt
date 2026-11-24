package de.nicolasmahn.momentum.core.time

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/** The quick reading of a stored key gives exactly the answer of the strict round trip it stands in for. */
class DateKeyParsingTest {
    private fun roundTrip(key: String): LocalDate? = runCatching { LocalDate.parse(key) }.getOrNull()?.takeIf { it.toString() == key }

    @Test fun agreesWithTheRoundTripOnEveryKindOfText() {
        val keys = listOf(
            "2026-10-18", "2024-02-29", "2026-02-29", "2026-02-30", "2000-02-29", "1900-02-29", "0000-01-01", "9999-12-31",
            "2026-00-10", "2026-13-01", "2026-04-31", "2026-04-00", "2026-2-3", "2026-02-3x", "2026/02/03", "20260203",
            "+10000-01-01", "-0001-01-01", "2026-10-18T00:00", " 2026-10-18", "", "２０２６-10-18",
        )
        keys.forEach { key -> assertEquals(key, roundTrip(key), parseDateKey(key)) }
    }

    @Test fun agreesOnEveryDayOfALeapCycle() {
        generateSequence(LocalDate.parse("1999-12-25")) { it.plusDays(1) }.takeWhile { it.year < 2004 }.forEach { day ->
            assertEquals(day, parseDateKey(day.toString()))
        }
    }
}
