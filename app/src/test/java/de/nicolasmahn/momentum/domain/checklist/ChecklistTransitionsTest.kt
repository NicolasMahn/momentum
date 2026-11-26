package de.nicolasmahn.momentum.domain.checklist

import de.nicolasmahn.momentum.domain.measurement.recordsOf

import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.measurement.asMeasurementLogs
import de.nicolasmahn.momentum.domain.supplement.AllSupplementIds
import de.nicolasmahn.momentum.domain.compatibility.StandardSupplementIds
import de.nicolasmahn.momentum.domain.supplement.YearRoundSupplementIds
import de.nicolasmahn.momentum.domain.supplement.supplementDay
import de.nicolasmahn.momentum.domain.supplement.supplementsState
import de.nicolasmahn.momentum.domain.supplement.testSupplementsChecklist
import de.nicolasmahn.momentum.domain.measurement.MeasurementRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import de.nicolasmahn.momentum.domain.holiday.HolidaySchedule

/** The transitions Supplements' screen and notification use, with Supplements as the checklist. */
class ChecklistTransitionsTest {
    private val supplements = testSupplementsChecklist()

    @Test
    fun tickingAllDueIsAtomicAndIdempotent() {
        val initial = supplementsState(supplementDay("2026-09-16", 10L, setOf("future-supplement")))

        val updated = tickAllDue(initial, supplements, "2026-09-16", 20L, HolidaySchedule())
        val retried = tickAllDue(updated, supplements, "2026-09-16", 30L, HolidaySchedule())

        assertEquals(ticks("future-supplement", StandardSupplementIds.MULTIVITAMIN, StandardSupplementIds.CREATINE), updated.checklistDays.single().ticks)
        assertEquals(20L, updated.checklistDays.single().timestampMillis)
        assertSame(updated, retried)
    }

    @Test
    fun winterTicksVitaminDTooAndSummerEditPreservesIt() {
        assertEquals(ticks(*AllSupplementIds.toTypedArray()), tickAllDue(supplementsState(), supplements, "2026-10-01", 1L, HolidaySchedule()).checklistDays.single().ticks)

        val summer = "2027-04-01"
        val saved = setDayRecord(supplementsState(supplementDay(summer, 1L, AllSupplementIds)), supplements, summer, DayRecord(ticks(StandardSupplementIds.CREATINE)), 2L)
        assertEquals(ticks(StandardSupplementIds.CREATINE, StandardSupplementIds.VITAMIN_D), saved.checklistDays.single().ticks)
    }

    @Test
    fun selectionReplacesDueTicksAndPreservesUnknownOnes() {
        val initial = supplementsState(supplementDay("2026-09-16", 10L, YearRoundSupplementIds + "future-supplement"))

        val updated = setDayRecord(initial, supplements, "2026-09-16", DayRecord(ticks(StandardSupplementIds.CREATINE)), 20L)

        assertEquals(ticks(StandardSupplementIds.CREATINE, "future-supplement"), updated.checklistDays.single().ticks)
    }

    @Test
    fun clearingTheSelectionRemovesTheDayAndKeepsOtherDays() {
        val otherDay = supplementDay("2026-09-15", 1L, YearRoundSupplementIds)
        val initial = supplementsState(otherDay, supplementDay("2026-09-16", 2L, YearRoundSupplementIds))

        val updated = setDayRecord(initial, supplements, "2026-09-16", DayRecord(), 3L)

        assertEquals(listOf(otherDay), updated.checklistDays)
    }

    @Test
    fun clearingTheSelectionPreservesUnknownIds() {
        val initial = supplementsState(supplementDay("2026-09-16", 1L, YearRoundSupplementIds + "future-supplement"))

        val updated = setDayRecord(initial, supplements, "2026-09-16", DayRecord(), 2L)

        assertEquals(ticks("future-supplement"), updated.checklistDays.single().ticks)
    }

    @Test
    fun anUnchangedSelectionKeepsTheRecordAndItsTime() {
        val initial = supplementsState(supplementDay("2026-09-16", 1L, YearRoundSupplementIds))
        assertSame(initial, setDayRecord(initial, supplements, "2026-09-16", DayRecord(ticks(*YearRoundSupplementIds.toTypedArray())), 2L))
        val empty = supplementsState()
        assertSame(empty, setDayRecord(empty, supplements, "2026-09-16", DayRecord(), 2L))
    }

    @Test
    fun removingADateRemovesOnlyThatRecord() {
        val target = supplementDay("2026-09-16", 2L, YearRoundSupplementIds)
        val other = supplementDay("2026-09-15", 1L, setOf(StandardSupplementIds.MULTIVITAMIN))
        val weightLog = MeasurementRecord("2026-09-16", 3L, 87.5)
        val initial = supplementsState(target, other).copy(measurementLogs = listOf(weightLog).asMeasurementLogs("weight"))

        val updated = removeChecklistDay(initial, supplements.id, target.dateKey)

        assertEquals(listOf(other), updated.checklistDays)
        assertEquals(listOf(weightLog), updated.recordsOf("weight"))
        assertSame(updated, removeChecklistDay(updated, supplements.id, target.dateKey))
    }

    @Test
    fun aTickThatIsNotDueCannotBePicked() {
        assertThrows(IllegalArgumentException::class.java) {
            setDayRecord(supplementsState(), supplements, "2026-09-16", DayRecord(ticks(StandardSupplementIds.VITAMIN_D)), 1L)
        }
    }

    @Test
    fun transitionsRejectInvalidKeys() {
        assertThrows(IllegalArgumentException::class.java) { tickAllDue(AppState(), supplements, "2026-02-30", 1L, HolidaySchedule()) }
        assertThrows(IllegalArgumentException::class.java) { removeChecklistDay(AppState(), supplements.id, "2026-02-30") }
    }

    @Test
    fun aReminderSwitchChangesOnlyItsCheckpoint() {
        val on = setCheckpointRemind(supplementsState(), supplements.id, "midday", remind = true)
        assertEquals(true, on.checklists.single().checkpoints.single().remind)
        assertEquals(supplements.copy(checkpoints = supplements.checkpoints.map { it.copy(remind = true) }), on.checklists.single())
        assertSame(on, setCheckpointRemind(on, supplements.id, "midday", remind = true))
    }

    private fun ticks(vararg ids: String) = ids.mapTo(LinkedHashSet()) { tickOf("midday", it) }
}
