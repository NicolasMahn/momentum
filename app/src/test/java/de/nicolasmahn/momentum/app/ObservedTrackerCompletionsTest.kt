package de.nicolasmahn.momentum.app

import de.nicolasmahn.momentum.domain.TrackerRef
import de.nicolasmahn.momentum.domain.TrackerTypeId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ObservedTrackerCompletionsTest {
    private val ref = TrackerRef(TrackerTypeId("counter"), "water:daily")
    private val day = "2026-10-18"

    @Test fun aNewRecordCanCelebrateACompletionOnce() {
        val baseline = ObservedTrackerCompletions(day, emptySet(), mapOf(ref to null))
        val completed = baseline.seeing(day, setOf(ref), mapOf(ref to 100L))
        assertEquals(ref, completed.celebrating)
        assertNull(completed.copy(pending = emptyList()).seeing(day, setOf(ref), mapOf(ref to 100L)).celebrating)
    }

    @Test fun settingsAndProtectionCannotCelebrateWithoutANewRecord() {
        val baseline = ObservedTrackerCompletions(day, emptySet(), mapOf(ref to 100L))
        assertNull(baseline.seeing(day, setOf(ref), mapOf(ref to 100L)).celebrating)
        assertNull(ObservedTrackerCompletions(day, emptySet(), mapOf(ref to null)).seeing(day, setOf(ref), mapOf(ref to null)).celebrating)
    }

    @Test fun initialObservationAndAddingAModuleEstablishABaseline() {
        assertNull(ObservedTrackerCompletions(day, setOf(ref), mapOf(ref to 100L)).celebrating)
        val noModules = ObservedTrackerCompletions(day, emptySet(), emptyMap())
        assertNull(noModules.seeing(day, setOf(ref), mapOf(ref to 100L)).celebrating)
    }

    @Test fun aNewHabitDayAndRemovedCompletionDropPendingCelebrations() {
        val completed = ObservedTrackerCompletions(day, emptySet(), mapOf(ref to null)).seeing(day, setOf(ref), mapOf(ref to 100L))
        assertNull(completed.seeing("2026-10-19", setOf(ref), mapOf(ref to 100L)).celebrating)
        assertNull(completed.seeing(day, emptySet(), mapOf(ref to null)).celebrating)
    }

    @Test fun undoingAMissCannotCelebrateWhenAnOlderDueTickAlreadyExists() {
        val before = ObservedTrackerCompletions(
            day, emptySet(), mapOf(ref to 100L),
            recordedActionKeys = mapOf(ref to setOf("morning")),
            owedActionKeys = mapOf(ref to setOf("evening")),
        )
        val after = before.seeing(
            day, setOf(ref), mapOf(ref to 200L),
            recordedActionKeys = mapOf(ref to setOf("morning")),
            owedActionKeys = mapOf(ref to emptySet()),
        )
        assertNull(after.celebrating)
    }

    @Test fun onlyANewTickThatPreviouslyAnsweredSomethingOwedCanCelebrate() {
        val before = ObservedTrackerCompletions(
            day, emptySet(), mapOf(ref to 100L),
            recordedActionKeys = mapOf(ref to setOf("morning")),
            owedActionKeys = mapOf(ref to setOf("evening")),
        )
        val unrelatedTick = before.seeing(
            day, setOf(ref), mapOf(ref to 200L),
            recordedActionKeys = mapOf(ref to setOf("morning", "optional")),
            owedActionKeys = mapOf(ref to emptySet()),
        )
        assertNull(unrelatedTick.celebrating)
        val newlyOwedTick = before.seeing(
            day, setOf(ref), mapOf(ref to 100L),
            recordedActionKeys = mapOf(ref to setOf("morning", "evening")),
            owedActionKeys = mapOf(ref to emptySet()),
        )
        assertEquals(ref, newlyOwedTick.celebrating)
    }

    @Test fun anOwedActionPolicyIntroducedWithATrackerEstablishesABaseline() {
        val noTracker = ObservedTrackerCompletions(day, emptySet(), emptyMap())
        val newlyConfigured = noTracker.seeing(
            day, setOf(ref), mapOf(ref to 100L),
            recordedActionKeys = mapOf(ref to setOf("morning")),
            owedActionKeys = mapOf(ref to emptySet()),
        )
        assertNull(newlyConfigured.celebrating)
    }

}
