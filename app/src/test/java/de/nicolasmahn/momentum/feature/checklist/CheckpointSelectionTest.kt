package de.nicolasmahn.momentum.feature.checklist

import de.nicolasmahn.momentum.domain.compatibility.LegacyCareAction
import de.nicolasmahn.momentum.domain.compatibility.CareChecklistId
import de.nicolasmahn.momentum.domain.care.CareDone
import de.nicolasmahn.momentum.domain.compatibility.LegacyCareSession
import de.nicolasmahn.momentum.domain.care.careState
import de.nicolasmahn.momentum.domain.compatibility.isCareSessionComplete
import de.nicolasmahn.momentum.domain.care.testCareChecklist
import de.nicolasmahn.momentum.domain.checklist.DayRecord
import de.nicolasmahn.momentum.domain.checklist.setCheckpointRecord
import de.nicolasmahn.momentum.domain.checklist.tickOf
import de.nicolasmahn.momentum.domain.checklist.recordOn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import de.nicolasmahn.momentum.domain.holiday.HolidaySchedule
import de.nicolasmahn.momentum.domain.compatibility.careChecklist
import de.nicolasmahn.momentum.domain.checklist.ChecklistEdit
import de.nicolasmahn.momentum.domain.checklist.ChecklistTarget
import de.nicolasmahn.momentum.domain.checklist.WeekStanding
import de.nicolasmahn.momentum.domain.checklist.applyEdit
import de.nicolasmahn.momentum.domain.checklist.progressOf

/** A checklist with several checkpoints, as Care is: each checkpoint keeps and saves its own draft. */
class CheckpointSelectionTest {
    private val care = testCareChecklist()
    private val wednesday = "2026-10-07"
    private val saturday = "2026-10-10"
    private fun tick(session: LegacyCareSession, action: LegacyCareAction) = tickOf(session.storageId, action.storageId)

    @Test
    fun togglingChangesOnlyItsOwnCheckpointsDraft() {
        val (morning, evening) = checkpointSelections(careState(), care, wednesday, HolidaySchedule())
        val toggled = morning.toggle(tick(LegacyCareSession.MORNING, LegacyCareAction.TOOTHBRUSHING))
        assertEquals(setOf(tick(LegacyCareSession.MORNING, LegacyCareAction.TOOTHBRUSHING)), toggled.selected.ticks)
        assertFalse(evening.hasChanges)
    }

    @Test
    fun savingTheMorningKeepsTheEveningOtherDatesAndTicksThatAreNotDue() {
        val state = careState(
            CareDone(wednesday, LegacyCareSession.EVENING, LegacyCareAction.TOOTHBRUSHING),
            // Recorded on a Wednesday, when it is not asked for; a save never erases it.
            CareDone(wednesday, LegacyCareSession.MORNING, LegacyCareAction.CLEAN_BETWEEN_TEETH),
            CareDone("2026-10-06", LegacyCareSession.MORNING, LegacyCareAction.HAIR_FOAM),
        )
        val saved = setCheckpointRecord(state, care, wednesday, LegacyCareSession.MORNING.storageId, DayRecord(setOf(tick(LegacyCareSession.MORNING, LegacyCareAction.HAIR_FOAM))), 5L)

        assertEquals(
            setOf(
                tick(LegacyCareSession.MORNING, LegacyCareAction.HAIR_FOAM),
                tick(LegacyCareSession.MORNING, LegacyCareAction.CLEAN_BETWEEN_TEETH),
                tick(LegacyCareSession.EVENING, LegacyCareAction.TOOTHBRUSHING),
            ),
            saved.recordOn(CareChecklistId, wednesday).ticks,
        )
        assertEquals(state.recordOn(CareChecklistId, "2026-10-06").ticks, saved.recordOn(CareChecklistId, "2026-10-06").ticks)
    }

    @Test
    fun anEmptyMorningClearsOnlyTheMorning() {
        val state = careState(
            CareDone(wednesday, LegacyCareSession.MORNING, LegacyCareAction.TOOTHBRUSHING),
            CareDone(wednesday, LegacyCareSession.EVENING, LegacyCareAction.HAIR_FOAM),
        )
        val saved = setCheckpointRecord(state, care, wednesday, LegacyCareSession.MORNING.storageId, DayRecord(), 5L)
        assertEquals(setOf(tick(LegacyCareSession.EVENING, LegacyCareAction.HAIR_FOAM)), saved.recordOn(CareChecklistId, wednesday).ticks)
    }

    @Test
    fun aSaturdayEveningAsksForTheBetweenTeethCleaningAndSavesIt() {
        val evening = checkpointSelections(careState(), care, saturday, HolidaySchedule()).single { it.checkpoint.id == LegacyCareSession.EVENING.storageId }
        val between = tick(LegacyCareSession.EVENING, LegacyCareAction.CLEAN_BETWEEN_TEETH)
        assertEquals(listOf("toothbrushing", "hair_foam", "clean_between_teeth"), evening.items.map { it.id })
        val saved = setCheckpointRecord(careState(), care, saturday, LegacyCareSession.EVENING.storageId, DayRecord(evening.toggle(between).selected.ticks), 5L)
        assertEquals(setOf(between), saved.recordOn(CareChecklistId, saturday).ticks)
    }

    /** Everything can be recorded on any day; only its days count, and before it began it is not there. */
    @Test
    fun theBetweenTeethCleaningCanBeRecordedOnAWeekdayWithoutCounting() {
        val evening = checkpointSelections(careState(), care, wednesday, HolidaySchedule()).single { it.checkpoint.id == LegacyCareSession.EVENING.storageId }
        assertEquals(listOf("toothbrushing", "hair_foam", "clean_between_teeth"), evening.items.map { it.id })
        val daily = setOf(tick(LegacyCareSession.EVENING, LegacyCareAction.TOOTHBRUSHING), tick(LegacyCareSession.EVENING, LegacyCareAction.HAIR_FOAM))
        val between = tick(LegacyCareSession.EVENING, LegacyCareAction.CLEAN_BETWEEN_TEETH)

        val withBetween = setCheckpointRecord(careState(), care, wednesday, LegacyCareSession.EVENING.storageId, DayRecord(daily + between), 5L)
        assertEquals(daily + between, withBetween.recordOn(CareChecklistId, wednesday).ticks)
        assertTrue(withBetween.isCareSessionComplete(wednesday, LegacyCareSession.EVENING, HolidaySchedule()))
        assertFalse(setCheckpointRecord(careState(), care, wednesday, LegacyCareSession.EVENING.storageId, DayRecord(setOf(between)), 5L).isCareSessionComplete(wednesday, LegacyCareSession.EVENING, HolidaySchedule()))
        // Before its first Saturday the cleaning did not exist yet.
        assertEquals(listOf("toothbrushing", "hair_foam"), checkpointSelections(careState(), care, "2026-09-30", HolidaySchedule()).last().items.map { it.id })
    }

    /** A week item is offered every evening; the day's progress says whether skipping it today would cost. */
    @Test
    fun aWeekItemIsOfferedEveryDayAndSaysWhereItsWeekStands() {
        val monday = "2026-10-05"
        val state = careState().applyEdit(CareChecklistId, ChecklistEdit.SetItemTarget(LegacyCareAction.CLEAN_BETWEEN_TEETH.storageId, ChecklistTarget.PerWeek(times = 1)), java.time.LocalDate.parse(monday))
        val weeklyCare = state.careChecklist!!
        fun evening(dateKey: String) = checkpointSelections(state, weeklyCare, dateKey, HolidaySchedule()).single { it.checkpoint.id == LegacyCareSession.EVENING.storageId }
        fun weekItems(dateKey: String) = state.progressOf(weeklyCare, dateKey, HolidaySchedule()).weekItems
        val between = weeklyCare.items.last { it.id == LegacyCareAction.CLEAN_BETWEEN_TEETH.storageId }

        assertEquals(listOf("toothbrushing", "hair_foam", "clean_between_teeth"), evening(wednesday).items.map { it.id })
        assertEquals(WeekStanding.Free, weekItems(wednesday).of(between)?.standing)
        assertEquals(0 to 1, weekItems(wednesday).of(between)?.let { it.done to it.asked })
        assertEquals(WeekStanding.Gap, weekItems("2026-10-11").of(between)?.standing)
        assertEquals(null, weekItems(wednesday).of(weeklyCare.items.first()))
    }

    /** Saving the evening's cleaning changes where the week stands, but not the morning's draft, which keys on its own ticks alone. */
    @Test
    fun savingOneCheckpointLeavesAnotherCheckpointsDraftAsItWas() {
        val sunday = "2026-10-11"
        val state = careState().applyEdit(CareChecklistId, ChecklistEdit.SetItemTarget(LegacyCareAction.CLEAN_BETWEEN_TEETH.storageId, ChecklistTarget.PerWeek(times = 1)), java.time.LocalDate.parse("2026-10-05"))
        val weeklyCare = state.careChecklist!!
        val morningBefore = checkpointSelections(state, weeklyCare, sunday, HolidaySchedule()).first()
        val saved = setCheckpointRecord(state, weeklyCare, sunday, LegacyCareSession.EVENING.storageId, DayRecord(setOf(tick(LegacyCareSession.EVENING, LegacyCareAction.CLEAN_BETWEEN_TEETH))), 5L)
        assertEquals(morningBefore, checkpointSelections(saved, weeklyCare, sunday, HolidaySchedule()).first())
        assertEquals(WeekStanding.Gap, state.progressOf(weeklyCare, sunday, HolidaySchedule()).weekItems.single().standing)
        assertEquals(WeekStanding.Done, saved.progressOf(weeklyCare, sunday, HolidaySchedule()).weekItems.single().standing)
    }
}
