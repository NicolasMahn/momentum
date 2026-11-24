package de.nicolasmahn.momentum.domain.reward

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** A week's kept days earn by steps: the passes of the highest step reached, in all, and the next pass is the next step's days. */
class KeptDaysInWeekTest {
    private val steps = Earn.KeptDaysInWeek(listOf(Earn.KeptDaysStep(5, 1), Earn.KeptDaysStep(6, 2), Earn.KeptDaysStep(7, 4)))

    @Test fun aWeekEarnsThePassesOfTheHighestStepItReaches() {
        assertEquals(listOf(0, 0, 0, 0, 0, 1, 2, 4), (0..7).map(steps::passesFor))
        assertEquals(listOf(5, 5, 5, 5, 5, 6, 7, 7), (0..7).map(steps::nextDaysAfter))
    }

    /** One step of one pass at the days is the rule every such bank started with. */
    @Test fun oneStepIsOnePassAtTheDays() {
        val one = Earn.KeptDaysInWeek(5)
        assertEquals(Earn.KeptDaysInWeek(listOf(Earn.KeptDaysStep(5, 1))), one)
        assertEquals(listOf(0, 0, 0, 0, 0, 1, 1, 1), (0..7).map(one::passesFor))
        assertEquals(List(8) { 5 }, (0..7).map(one::nextDaysAfter))
    }

    @Test fun stepsAskMoreDaysAndHaveMorePassesThanTheOneBefore() {
        assertThrows(IllegalArgumentException::class.java) { Earn.KeptDaysInWeek(emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { Earn.KeptDaysInWeek(listOf(Earn.KeptDaysStep(5, 0))) }
        assertThrows(IllegalArgumentException::class.java) { Earn.KeptDaysInWeek(listOf(Earn.KeptDaysStep(0, 1))) }
        assertThrows(IllegalArgumentException::class.java) { Earn.KeptDaysInWeek(listOf(Earn.KeptDaysStep(8, 1))) }
        assertThrows(IllegalArgumentException::class.java) { Earn.KeptDaysInWeek(listOf(Earn.KeptDaysStep(5, 1), Earn.KeptDaysStep(5, 2))) }
        assertThrows(IllegalArgumentException::class.java) { Earn.KeptDaysInWeek(listOf(Earn.KeptDaysStep(5, 1), Earn.KeptDaysStep(6, 1))) }
    }
}
