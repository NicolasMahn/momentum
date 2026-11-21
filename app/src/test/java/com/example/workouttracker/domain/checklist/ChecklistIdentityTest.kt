package com.example.workouttracker.domain.checklist

import com.example.workouttracker.domain.AppState
import java.time.LocalDate
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ChecklistIdentityTest {
    private val today = LocalDate.parse("2026-10-25")

    @Test fun creationCannotAdoptAnOrphanRecord() {
        val candidate = AppState().starterChecklist("Eating", today, Random(1))
        val record = ChecklistDay(candidate.id, today.toString(), 1L, setOf("day/old-item"))
        val state = AppState(checklistDays = listOf(record))

        assertNotEquals(candidate.id, state.starterChecklist("Eating", today, Random(1)).id)
        assertThrows(IllegalArgumentException::class.java) { state.addChecklist(candidate) }
        assertEquals(listOf(record), state.checklistDays)
    }

    @Test fun creationCannotAdoptRetainedVisibilityHistory() {
        val candidate = AppState().starterChecklist("Weight", today, Random(1))
        val reserved = setOf(candidate.id)

        assertNotEquals(candidate.id, AppState().starterChecklist("Weight", today, Random(1), hidden = reserved).id)
        assertThrows(IllegalArgumentException::class.java) { AppState().addChecklist(candidate, reserved) }
    }

    @Test fun aWriteMustNotMergeDuplicateConfigurationOrDayIdentities() {
        val checklist = AppState().starterChecklist("Reading", today)
        val first = ChecklistDay(checklist.id, today.toString(), 1L, setOf("day/first"))
        val second = first.copy(timestampMillis = 2L, ticks = setOf("day/second"))

        assertThrows(IllegalArgumentException::class.java) { requireStorableChecklists(listOf(checklist, checklist), emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { requireStorableChecklists(listOf(checklist), listOf(first, second)) }
        requireStorableChecklists(listOf(checklist), listOf(first))
    }

    @Test fun unknownOwnersAndMarksRemainRecordedFacts() {
        val orphan = ChecklistDay("not-configured", today.toString(), 1L, setOf("day/unknown"))
        requireStorableChecklists(emptyList(), listOf(orphan))
        assertThrows(IllegalArgumentException::class.java) { requireStorableChecklists(emptyList(), listOf(orphan.copy(checklistId = " "))) }
        assertThrows(IllegalArgumentException::class.java) { requireStorableChecklists(emptyList(), listOf(orphan.copy(ticks = setOf(" ")))) }
    }
}
