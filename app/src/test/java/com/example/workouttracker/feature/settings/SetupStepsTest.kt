package com.example.workouttracker.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The stepping of a guided setup is written once: which step is open, Back and Next, the last
 * step, which steps can be opened and the number each wears come from the list of parts a setup
 * is given, by position, for a checklist's four as for a measurement's three.
 */
class SetupStepsTest {
    private val checklists = ChecklistPage.entries
    private val measurements = listOf(ChecklistPage.Look, ChecklistPage.Reminders, ChecklistPage.Streak)

    @Test
    fun aStepWearsTheNumberOfItsPlaceInTheListItIsGiven() {
        assertEquals(listOf(1, 2, 3, 4), checklists.map(SetupSteps(checklists)::numberOf))
        // Reminders is a checklist's third part and a measurement's second step.
        assertEquals(listOf(1, 2, 3), measurements.map(SetupSteps(measurements)::numberOf))
        assertEquals(2, SetupSteps(measurements).numberOf(ChecklistPage.Reminders))
        assertEquals(3, SetupSteps(checklists).numberOf(ChecklistPage.Reminders))
    }

    @Test
    fun backAndNextWalkTheListAndStopAtItsEnds() {
        listOf(checklists, measurements).forEach { parts ->
            val steps = SetupSteps(parts)
            assertEquals(parts.first(), steps.step)
            assertTrue(steps.isFirst)
            steps.back()
            assertEquals(parts.first(), steps.step)

            val walked = parts.drop(1).map {
                assertFalse(steps.isLast)
                steps.next()
                steps.step
            }
            assertEquals(parts.drop(1), walked)
            assertTrue(steps.isLast)
            assertFalse(steps.isFirst)
            steps.next()
            assertEquals(parts.last(), steps.step)

            steps.back()
            assertEquals(parts[parts.size - 2], steps.step)
            assertTrue(parts.all { steps.isLast(it) == (it == parts.last()) })
        }
    }

    /** A step ahead is shown and held back until its turn; any step passed can be opened again, and coming back holds the later ones back again. */
    @Test
    fun onlyTheOpenStepAndTheOnesPassedCanBeOpened() {
        val steps = SetupSteps(measurements)
        assertEquals(listOf(true, false, false), measurements.map(steps::canShow))
        steps.show(ChecklistPage.Streak)
        assertEquals(ChecklistPage.Look, steps.step)

        steps.next()
        steps.next()
        assertEquals(listOf(true, true, true), measurements.map(steps::canShow))
        steps.show(ChecklistPage.Look)
        assertEquals(ChecklistPage.Look, steps.step)
        assertEquals(listOf(true, false, false), measurements.map(steps::canShow))

        // A part the setup does not walk is no step of it.
        assertFalse(steps.canShow(ChecklistPage.Items))
        steps.show(ChecklistPage.Items)
        assertEquals(ChecklistPage.Look, steps.step)
    }

    @Test
    fun aSetupOpensOnTheStepItIsGivenAndOnItsFirstForOneItDoesNotWalk() {
        assertEquals(ChecklistPage.Reminders, SetupSteps(measurements, ChecklistPage.Reminders).step)
        assertEquals(ChecklistPage.Look, SetupSteps(measurements, ChecklistPage.Items).step)
        // The steps are walked in the order a page shows its parts.
        assertThrows(IllegalArgumentException::class.java) { SetupSteps(listOf(ChecklistPage.Streak, ChecklistPage.Look)) }
    }
}
