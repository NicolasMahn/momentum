package de.nicolasmahn.momentum.domain.eating

import de.nicolasmahn.momentum.domain.compatibility.eatingDateKeyFor
import org.junit.Assert.assertEquals
import org.junit.Test

class EatingTransitionsTest {
    @Test
    fun defaultRatedDateIsYesterdayAcrossMonthYearAndLeapDayBoundaries() {
        assertEquals("2025-12-31", eatingDateKeyFor("2026-01-01"))
        assertEquals("2024-02-29", eatingDateKeyFor("2024-03-01"))
        assertEquals("2026-09-15", eatingDateKeyFor("2026-09-16"))
    }
}
