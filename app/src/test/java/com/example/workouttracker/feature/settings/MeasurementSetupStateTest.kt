package com.example.workouttracker.feature.settings

import androidx.compose.runtime.saveable.SaverScope
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.checklist.Checkpoint
import com.example.workouttracker.domain.checklist.CheckpointTime
import com.example.workouttracker.domain.checklist.Dated
import com.example.workouttracker.domain.checklist.RewardPeriod
import com.example.workouttracker.domain.checklist.ViewHue
import com.example.workouttracker.domain.checklist.ViewIcon
import com.example.workouttracker.domain.checklist.WhenAway
import com.example.workouttracker.domain.measurement.DefaultMeasurementCheckpoint
import com.example.workouttracker.domain.measurement.MeasurementConfiguration
import com.example.workouttracker.domain.measurement.MeasurementEdit
import com.example.workouttracker.domain.measurement.MeasurementStep
import com.example.workouttracker.domain.measurement.addMeasurement
import com.example.workouttracker.domain.measurement.applyingMeasurementEdits
import com.example.workouttracker.domain.compatibility.madeMeasurements
import com.example.workouttracker.domain.measurement.starterMeasurement
import com.example.workouttracker.domain.measurement.withWeight
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The setup of a new measurement keeps what was typed, valid or not, with the ID the measurement
 * will have; Create waits for a name and a unit that can be stored, and creates once.
 */
class MeasurementSetupStateTest {
    private val today = LocalDate.parse("2026-10-14")
    private val records = AppState().withWeight()
    private val tooLong = "kilometres per hour"

    private fun setup() = MeasurementSetupState(records.starterMeasurement("Measurement"), today)

    private fun MeasurementSetupState.restored(): MeasurementSetupState {
        val saved = requireNotNull(with(MeasurementSetupState.Saver) { SaverScope { true }.save(this@restored) })
        return requireNotNull(MeasurementSetupState.Saver.restore(saved))
    }

    private fun created(starter: MeasurementConfiguration, edits: List<MeasurementEdit>): MeasurementConfiguration =
        records.addMeasurement(starter).applyingMeasurementEdits(starter.id, edits, today).madeMeasurements.single()

    @Test
    fun createWaitsForANameAndHandsOverTheStarterWithItsEdits() {
        val state = setup()
        assertFalse(state.canCreate)
        assertFalse(state.showsMissingName)
        assertEquals(ViewWrite.NotStored, state.create { _, _ -> error("Nothing is created without a name.") })

        state.rename("Waist")
        state.form.typeUnit("cm")
        state.form.edit(MeasurementEdit.SetStep(MeasurementStep("1")))
        state.form.edit(MeasurementEdit.SetIcon(ViewIcon.Heart))
        state.form.edit(MeasurementEdit.SetHue(ViewHue.Teal))
        assertTrue(state.canCreate)

        val creations = mutableListOf<MeasurementConfiguration>()
        assertEquals(ViewWrite.Stored, state.create { starter, edits -> creations += created(starter, edits); ViewWrite.Stored })
        assertEquals(state.starter.copy(label = "Waist", unit = "cm", step = MeasurementStep("1"), icon = ViewIcon.Heart, hue = ViewHue.Teal), creations.single())
        assertEquals(state.form.edited, creations.single())
        assertNull(creations.single().rewards)
        assertEquals(Dated.always(WhenAway.Due), creations.single().whenAway)
        assertEquals(DefaultMeasurementCheckpoint, creations.single().checkpoint)
    }

    /** A second press after the first succeeded creates nothing more. */
    @Test
    fun twoPressesCreateOne() {
        val state = setup()
        state.rename("Waist")
        var creations = 0
        repeat(2) { assertEquals(ViewWrite.Stored, state.create { _, _ -> creations++; ViewWrite.Stored }) }
        assertEquals(1, creations)
    }

    /** A store that refused leaves the setup as it was, under the ID it opened with, for the next press. */
    @Test
    fun aRefusedCreateLeavesTheDraftWithItsIdToTryAgain() {
        val state = setup()
        val id = state.starter.id
        state.rename("Waist")
        state.form.typeUnit("cm")
        val edits = state.form.edits

        assertEquals(ViewWrite.NotStored, state.create { _, _ -> ViewWrite.NotStored })
        assertEquals(id, state.starter.id)
        assertEquals(edits, state.form.edits)
        assertTrue(state.canCreate)

        val handedOver = mutableListOf<String>()
        assertEquals(ViewWrite.Stored, state.create { starter, _ -> handedOver += starter.id; ViewWrite.Stored })
        assertEquals(listOf(id), handedOver)
    }

    /**
     * The setup walks the four parts of a measurement's page as steps 1 to 4. Look waits for a
     * name alone; Unit for a unit that can be stored and a step that is one; Reminders and Streak
     * wait for nothing. A step ahead is held back until its turn, and going back holds the later
     * ones back again.
     */
    @Test
    fun theFourStepsAreNumberedLookWaitsForANameAndUnitForAUnitAndAStep() {
        val state = setup()
        assertEquals(listOf(ChecklistPage.Look, ChecklistPage.Items, ChecklistPage.Reminders, ChecklistPage.Streak), state.steps.parts)
        assertEquals(listOf(1, 2, 3, 4), state.steps.parts.map(state.steps::numberOf))
        assertEquals(ChecklistPage.Look, state.steps.step)

        // Without a name Next does nothing, and no later step can be opened.
        assertFalse(state.canContinue)
        state.next()
        state.steps.show(ChecklistPage.Streak)
        assertEquals(ChecklistPage.Look, state.steps.step)

        // A name is all Look waits for: a unit or a step that cannot be stored is the next step's to say.
        state.rename("Waist")
        assertTrue(state.canContinue)
        state.form.typeUnit(tooLong)
        state.form.typeStep("0.125")
        assertTrue(state.isComplete(ChecklistPage.Look))
        assertTrue(state.canContinue)
        state.next()
        assertEquals(ChecklistPage.Items, state.steps.step)

        // Unit waits for both of its fields, each on its own.
        assertFalse(state.canContinue)
        state.next()
        assertEquals(ChecklistPage.Items, state.steps.step)
        state.form.typeUnit("cm")
        assertFalse(state.canContinue)
        state.form.typeStep("0,5")
        assertTrue(state.canContinue)
        state.form.typeUnit(tooLong)
        assertFalse(state.canContinue)
        state.form.typeUnit("cm")

        state.next()
        assertEquals(ChecklistPage.Reminders, state.steps.step)
        assertTrue(state.canContinue)
        assertFalse(state.steps.isLast)
        state.next()
        assertEquals(ChecklistPage.Streak, state.steps.step)
        assertTrue(state.steps.isLast)
        assertTrue(state.canCreate)
        state.next()
        assertEquals(ChecklistPage.Streak, state.steps.step)

        // Back, and a tab of a step passed, lead back; cleared on Look, the name holds Next and Create.
        state.steps.back()
        assertEquals(ChecklistPage.Reminders, state.steps.step)
        state.steps.show(ChecklistPage.Look)
        assertEquals(ChecklistPage.Look, state.steps.step)
        assertFalse(state.steps.canShow(ChecklistPage.Items))
        assertFalse(state.steps.canShow(ChecklistPage.Reminders))
        state.rename(null)
        assertFalse(state.canContinue)
        assertFalse(state.canCreate)
        assertEquals(listOf(false, true, true, true), state.steps.parts.map(state::isComplete))

        // A step that is no step holds Create back from wherever the setup stands, as a refused unit does.
        state.rename("Waist")
        state.form.typeStep("0")
        assertEquals(listOf(true, false, true, true), state.steps.parts.map(state::isComplete))
        assertFalse(state.canCreate)
        assertTrue(state.touched)
        state.form.typeStep("2.5")
        assertTrue(state.canCreate)
        val creations = mutableListOf<MeasurementConfiguration>()
        assertEquals(ViewWrite.Stored, state.create { starter, edits -> creations += created(starter, edits); ViewWrite.Stored })
        assertEquals(state.starter.copy(label = "Waist", unit = "cm", step = MeasurementStep("2.5")), creations.single())
    }

    /** What is chosen on the Reminders and Streak steps is an edit like any other, created with the measurement on the last step. */
    @Test
    fun aReminderAndTheStreakChosenInTheSetupAreCreatedWithTheMeasurement() {
        val state = setup()
        state.rename("Waist")
        state.next()
        state.next()
        assertEquals(ChecklistPage.Reminders, state.steps.step)
        assertTrue(state.form.edit(MeasurementEdit.SetRemind(true)))
        assertTrue(state.form.edit(MeasurementEdit.SetReminderTime(CheckpointTime.AfterWake(15))))
        state.next()
        assertTrue(state.form.edit(MeasurementEdit.JoinRewards))
        assertTrue(state.form.edit(MeasurementEdit.SetWhenAway(WhenAway.Excused)))

        val creations = mutableListOf<MeasurementConfiguration>()
        assertEquals(ViewWrite.Stored, state.create { starter, edits -> creations += created(starter, edits); ViewWrite.Stored })

        val made = creations.single()
        assertEquals(Checkpoint("day", CheckpointTime.AfterWake(15), remind = true), made.checkpoint)
        assertEquals(listOf(RewardPeriod(today)), made.standardRewards?.periods)
        assertEquals(WhenAway.Excused, made.whenAway.on(today))
        assertEquals("Waist", made.label)
    }

    /** At each step the setup survives process death on that step, with its ID, its edits and what was typed. */
    @Test
    fun theSetupSurvivesProcessDeathAtEachStep() {
        val state = setup()
        state.rename("Waist")
        state.steps.parts.forEach { step ->
            if (step != state.steps.parts.first()) state.next()
            when (step) {
                ChecklistPage.Items -> {
                    state.form.typeUnit("cm")
                    state.form.typeStep("0.5")
                }
                ChecklistPage.Reminders -> state.form.edit(MeasurementEdit.SetRemind(true))
                ChecklistPage.Streak -> state.form.edit(MeasurementEdit.JoinRewards)
                else -> Unit
            }
            assertEquals(step, state.steps.step)

            val restored = state.restored()

            assertEquals(step, restored.steps.step)
            assertEquals(state.starter, restored.starter)
            assertEquals(state.form.edits, restored.form.edits)
            assertEquals("Waist", restored.name)
            // Every step passed can still be opened, and Create still waits for the last step's turn.
            assertEquals(state.steps.parts.map(state.steps::canShow), restored.steps.parts.map(restored.steps::canShow))
            assertEquals(state.canCreate, restored.canCreate)
        }
        val creations = mutableListOf<MeasurementConfiguration>()
        assertEquals(ViewWrite.Stored, state.restored().create { starter, edits -> creations += created(starter, edits); ViewWrite.Stored })
        assertEquals(true, creations.single().checkpoint?.remind)
        assertEquals(state.starter.id, creations.single().id)
    }


    /**
     * A creation stored without the alarm it asked for is made all the same: the setup knows it
     * created, has nothing left that leaving would lose, and never creates it a second time.
     */
    @Test
    fun aCreationStoredWithoutItsAlarmIsMadeAndNeverCreatedAgain() {
        val state = setup()
        state.rename("Waist")
        assertTrue(state.touched)
        assertFalse(state.created)

        assertEquals(ViewWrite.StoredWithoutAlarm, state.create { _, _ -> ViewWrite.StoredWithoutAlarm })

        assertTrue(state.created)
        assertFalse(state.touched)
        assertEquals(ViewWrite.Stored, state.create { _, _ -> error("It is made already.") })
    }

    /**
     * Text that cannot be a unit is not taken into the measurement and not thrown away either:
     * the setup knows its field is refused, Create waits, and leaving asks first.
     */
    @Test
    fun aUnitThatCannotBeStoredHoldsCreateBackAndIsGuarded() {
        val state = setup()
        state.rename("Speed")
        state.form.typeUnit("km/h")
        assertTrue(state.canCreate)

        listOf(tooLong, "k\nm", "km‏").forEach { refused ->
            state.form.typeUnit(refused)
            assertTrue(refused, state.form.unitRefused)
            assertFalse(refused, state.canCreate)
            assertTrue(refused, state.touched)
            assertEquals(ViewWrite.NotStored, state.create { _, _ -> error("Nothing is created while the unit is refused.") })
            // No edit carries the refused text, and none was made up in its place.
            assertEquals("km/h", state.form.edited.unit)
        }

        // Spaces around a unit are left out, as around a name; those inside it are kept.
        state.form.typeUnit("  beats / min ")
        assertFalse(state.form.unitRefused)
        assertEquals("beats / min", state.form.edited.unit)
        assertTrue(state.canCreate)
    }

    /**
     * Only the spaces of the space bar are left out around a unit. A line break, a tab or another
     * control character at its edge is refused like one inside it: it is not cut off to make what
     * is left a unit, which would create something other than what was typed or pasted.
     */
    @Test
    fun aForbiddenCharacterAtTheEdgeOfAUnitIsRefusedNotCutOff() {
        listOf("cm\n", "\ncm", "cm\r\n", "\tcm", "cm\t", "\u0000cm", "cm\u0007", "\u2028cm", "cm\u2029", " cm\n ", "\n").forEach { typed ->
            val state = setup()
            state.rename("Waist")
            state.form.typeUnit(typed)

            val escaped = typed.map { it.code }.toString()
            assertTrue(escaped, state.form.unitRefused)
            assertFalse(escaped, state.canCreate)
            assertTrue(escaped, state.touched)
            assertEquals(escaped, "", state.form.edited.unit)
            assertTrue(escaped, state.form.edits.none { it is MeasurementEdit.SetUnit })
            assertEquals(escaped, ViewWrite.NotStored, state.create { _, _ -> error("Nothing is created while the unit is refused.") })
        }
    }

    @Test
    fun aClearedNameHoldsCreateBackAndIsGuarded() {
        val state = setup()
        state.rename("Waist")
        state.rename(null)

        assertTrue(state.showsMissingName)
        assertFalse(state.canCreate)
        assertTrue(state.touched)
        // The draft keeps the last name, so the look stays what was chosen for it.
        assertEquals("Waist", state.form.edited.label)
    }

    @Test
    fun onlyATouchedSetupAsksOnLeaving() {
        assertFalse(setup().touched)
        assertTrue(setup().apply { rename("Waist") }.touched)
        assertTrue(setup().apply { form.typeUnit("cm") }.touched)
        assertTrue(setup().apply { form.typeUnit(tooLong) }.touched)
        assertTrue(setup().apply { form.edit(MeasurementEdit.SetStep(MeasurementStep("0.01"))) }.touched)
        assertTrue(setup().apply { form.edit(MeasurementEdit.SetHue(ViewHue.Teal)) }.touched)
    }

    /** Across process death the setup keeps its ID, what was chosen, and what was typed, also where that cannot be stored. */
    @Test
    fun theSetupSurvivesProcessDeathWithItsIdAndWhatWasTypedValidOrNot() {
        val state = setup()
        state.rename("Speed")
        state.form.typeUnit("km/h")
        state.form.edit(MeasurementEdit.SetStep(MeasurementStep("1")))
        state.form.typeUnit(tooLong)
        state.rename(null)

        val restored = state.restored()

        assertEquals(state.starter, restored.starter)
        assertEquals(state.form.edits, restored.form.edits)
        assertEquals(state.form.edited, restored.form.edited)
        assertTrue(restored.form.unitRefused)
        assertNull(restored.name)
        assertTrue(restored.showsMissingName)
        assertFalse(restored.canCreate)
        assertTrue(restored.touched)

        // Completed after the restore, it is created under the ID it had before.
        restored.rename("Speed")
        restored.form.typeUnit("km/h")
        val creations = mutableListOf<MeasurementConfiguration>()
        assertEquals(ViewWrite.Stored, restored.create { starter, edits -> creations += created(starter, edits); ViewWrite.Stored })
        assertEquals(state.starter.copy(label = "Speed", unit = "km/h", step = MeasurementStep("1")), creations.single())

        // An untouched setup is restored untouched.
        assertFalse(setup().restored().touched)
    }
}
