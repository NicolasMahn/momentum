package com.example.workouttracker.feature.settings

import androidx.compose.runtime.saveable.SaverScope
import com.example.workouttracker.domain.checklist.Checkpoint
import com.example.workouttracker.domain.checklist.CheckpointTime
import com.example.workouttracker.domain.checklist.ViewHue
import com.example.workouttracker.domain.checklist.WhenAway
import com.example.workouttracker.domain.measurement.MeasurementEdit
import com.example.workouttracker.domain.measurement.MeasurementStep
import com.example.workouttracker.domain.measurement.applyingEdits
import com.example.workouttracker.domain.measurement.testMadeMeasurement
import com.example.workouttracker.domain.measurement.testWeightMeasurement
import com.example.workouttracker.persistence.json.AppStateJsonCodec
import org.json.JSONObject
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The page of a measurement a person made keeps its name, unit, step and look as a draft. Text
 * that cannot be stored stays the page's business: it holds Save back and is guarded on leaving,
 * and nothing is saved in its place.
 */
class MadeMeasurementDraftTest {
    private val today = LocalDate.parse("2026-10-14")
    private val waist = testMadeMeasurement()

    private fun MeasurementDraft.restored(): MeasurementDraft {
        val saved = requireNotNull(with(MeasurementDraft.Saver) { SaverScope { true }.save(this@restored) })
        return requireNotNull(MeasurementDraft.Saver.restore(saved))
    }

    @Test
    fun theUnitTheStepAndTheLookAreSavedAsEdits() {
        val draft = MeasurementDraft(waist, today)
        assertFalse(draft.canSave)
        assertFalse(draft.hasUnsavedWork)
        assertTrue(draft.unitStored)

        draft.typeUnit("in")
        draft.edit(MeasurementEdit.SetStep(MeasurementStep("0.01")))
        draft.edit(MeasurementEdit.SetHue(ViewHue.Teal))

        assertTrue(draft.canSave)
        assertFalse(draft.unitStored)
        assertEquals(waist.copy(unit = "in", step = MeasurementStep("0.01"), hue = ViewHue.Teal), draft.edited)
        assertEquals(draft.edited, waist.applyingEdits(draft.edits, today))

        // Stored, nothing is left on the page, and the unit field follows what is stored again.
        draft.follow(draft.edited, today)
        draft.saved()
        assertFalse(draft.hasUnsavedWork)
        assertTrue(draft.unitStored)
    }

    /** The draft never swaps what was typed for the last unit that could be stored: Save waits instead. */
    /**
     * Look, Reminders and Streak change the one draft: its edits, in the order they were made, are
     * what the page shows over the stored configuration and what Save hands over. Once they are
     * stored nothing is left, and the draft survives process death with every one of them.
     */
    @Test
    fun theDraftHoldsTheEditsOfAllThreeParts() {
        val draft = MeasurementDraft(waist, today)
        val edits = listOf(
            MeasurementEdit.Rename("Hips"),
            MeasurementEdit.SetRemind(true),
            MeasurementEdit.SetReminderTime(CheckpointTime.BeforeSleep(30)),
            MeasurementEdit.JoinRewards,
            MeasurementEdit.SetBankRules(maxPasses = 4, everyKeptDays = 5),
            MeasurementEdit.SetWhenAway(WhenAway.Excused),
        )
        edits.forEach { assertTrue(it.toString(), draft.edit(it)) }

        assertEquals(edits, draft.edits)
        assertTrue(draft.canSave)
        val shown = draft.edited
        assertEquals("Hips", shown.label)
        assertEquals(Checkpoint("day", CheckpointTime.BeforeSleep(30), remind = true), shown.checkpoint)
        assertEquals(4, shown.standardRewards?.maxPasses?.on(today))
        assertEquals(WhenAway.Excused, shown.whenAway.on(today))
        assertEquals(shown, waist.applyingEdits(edits, today))

        val restored = draft.restored()
        assertEquals(edits, restored.edits)
        assertEquals(shown, restored.edited)

        // Weight's draft changes its reminder as any measurement's does.
        assertTrue(MeasurementDraft(testWeightMeasurement(), today).edit(MeasurementEdit.SetRemind(true)))

        // Stored on a later day, the edits replayed over what is stored change nothing more.
        val stored = waist.applyingEdits(edits, today.plusDays(1))
        draft.follow(stored, today.plusDays(1))
        assertFalse(draft.isDirty)
        assertFalse(draft.hasUnsavedWork)
    }

    @Test
    fun aUnitThatCannotBeStoredHoldsSaveBackAndIsGuarded() {
        val draft = MeasurementDraft(waist, today)
        draft.edit(MeasurementEdit.Rename("Waistline"))
        assertTrue(draft.canSave)

        draft.typeUnit("centimetres!!")
        assertTrue(draft.unitRefused)
        assertFalse(draft.canSave)
        assertTrue(draft.hasUnsavedWork)
        assertFalse(draft.unitStored)
        assertEquals(listOf<MeasurementEdit>(MeasurementEdit.Rename("Waistline")), draft.edits)

        // Alone on the page, the refused field still guards it, although nothing could be saved.
        val untouched = MeasurementDraft(waist, today).apply { typeUnit("centimetres!!") }
        assertFalse(untouched.isDirty)
        assertFalse(untouched.canSave)
        assertTrue(untouched.hasUnsavedWork)

        draft.typeUnit("mm")
        assertFalse(draft.unitRefused)
        assertTrue(draft.canSave)
        assertEquals("mm", draft.edited.unit)

        // Emptied, the measurement has no unit, which can be stored.
        draft.typeUnit("  ")
        assertFalse(draft.unitRefused)
        assertEquals("", draft.edited.unit)
    }

    /** A line break, a tab or another control character at the edge of a unit is refused, never cut off to save what is left. */
    @Test
    fun aForbiddenCharacterAtTheEdgeOfAUnitIsRefusedNotCutOff() {
        listOf("in\n", "\nin", "in\r\n", "\tin", "in\t", "\u0000in", "in\u0007", "\u2028in", "in\u2029", " in\n ", "\n").forEach { typed ->
            val draft = MeasurementDraft(waist, today)
            draft.edit(MeasurementEdit.SetStep(MeasurementStep("0.01")))
            draft.typeUnit(typed)

            val escaped = typed.map { it.code }.toString()
            assertTrue(escaped, draft.unitRefused)
            assertFalse(escaped, draft.canSave)
            assertTrue(escaped, draft.hasUnsavedWork)
            assertEquals(escaped, "cm", draft.edited.unit)
            assertEquals(escaped, listOf<MeasurementEdit>(MeasurementEdit.SetStep(MeasurementStep("0.01"))), draft.edits)
        }

        // The spaces of the space bar around a unit are still left out, and those inside it kept.
        val draft = MeasurementDraft(waist, today)
        draft.typeUnit("  beats / min ")
        assertFalse(draft.unitRefused)
        assertEquals("beats / min", draft.edited.unit)
    }

    @Test
    fun thePageSurvivesProcessDeathWithWhatWasTypedValidOrNot() {
        val draft = MeasurementDraft(waist, today)
        draft.typeUnit("in")
        draft.edit(MeasurementEdit.SetStep(MeasurementStep("1")))
        draft.named(waist.id, null)
        draft.typeUnit("centimetres!!")

        val restored = draft.restored()

        assertEquals(waist, restored.stored)
        assertEquals(draft.edits, restored.edits)
        assertEquals(waist.copy(unit = "in", step = MeasurementStep("1")), restored.edited)
        assertEquals(setOf(waist.id), restored.blankNames)
        assertTrue(restored.unitRefused)
        assertFalse(restored.canSave)
        assertTrue(restored.hasUnsavedWork)

        assertFalse(MeasurementDraft(waist, today).restored().hasUnsavedWork)
    }

    @Test
    fun aSavedBaselineRequiresTheWholeCurrentConfiguration() {
        fun restoring(configuration: String) = runCatching {
            MeasurementDraft.Saver.restore(listOf(configuration, "[]", arrayListOf<String>(), "2026-10-12", false, false))
        }
        listOf(testWeightMeasurement(), waist).forEach { configured ->
            val raw = AppStateJsonCodec.encodeMeasurement(configured)
            assertEquals(configured, restoring(raw).getOrThrow()?.stored)
            listOf("unit", "step", "mo", "valuePolicy", "checkpoints").forEach { field ->
                val incomplete = JSONObject(raw).apply { remove(field) }.toString()
                assertTrue(field, restoring(incomplete).isFailure)
            }
            listOf(
                JSONObject(raw).put("step", 0.1),
                JSONObject(raw).put("step", "0.125"),
                JSONObject(raw).apply { remove("step"); put("decimals", 1) },
                JSONObject(raw).put("valuePolicy", "unrecognized"),
            ).forEach { malformed -> assertTrue(malformed.toString(), restoring(malformed.toString()).isFailure) }
            val edited = configured.copy(unit = "in", step = MeasurementStep("0.5"))
            assertEquals(edited, restoring(AppStateJsonCodec.encodeMeasurement(edited)).getOrThrow()?.stored)
        }
    }
}
