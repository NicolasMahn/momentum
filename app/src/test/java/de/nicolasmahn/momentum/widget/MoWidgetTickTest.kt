package de.nicolasmahn.momentum.widget

import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class MoWidgetTickTest {
    @Test
    fun theTickFallsOnTheNextQuarterHourStrictlyAfterNow() {
        assertEquals(time("2026-10-03T15:00:00+02:00[Europe/Berlin]"), nextMoWidgetTick(time("2026-10-03T14:59:30+02:00[Europe/Berlin]")))
        assertEquals(time("2026-10-03T15:15:00+02:00[Europe/Berlin]"), nextMoWidgetTick(time("2026-10-03T15:00:00+02:00[Europe/Berlin]")))
        assertEquals(time("2026-10-04T00:00:00+02:00[Europe/Berlin]"), nextMoWidgetTick(time("2026-10-03T23:50:00+02:00[Europe/Berlin]")))
    }

    @Test
    fun theTickCountsRealMinutesAcrossTheRepeatedAutumnHour() {
        val firstPass = time("2026-10-25T02:50:00+02:00[Europe/Berlin]")

        assertEquals(time("2026-10-25T02:00:00+01:00[Europe/Berlin]"), nextMoWidgetTick(firstPass))
    }

    private fun time(text: String) = ZonedDateTime.parse(text)
}
