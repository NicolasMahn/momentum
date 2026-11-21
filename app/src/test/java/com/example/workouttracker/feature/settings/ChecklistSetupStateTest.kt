package com.example.workouttracker.feature.settings

import androidx.compose.runtime.saveable.SaverScope
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.checklist.AboutDay
import com.example.workouttracker.domain.checklist.ChecklistConfiguration
import com.example.workouttracker.domain.checklist.ChecklistEdit
import com.example.workouttracker.domain.checklist.Dated
import com.example.workouttracker.domain.checklist.WhenAway
import com.example.workouttracker.domain.checklist.recordDayFor
import com.example.workouttracker.domain.checklist.applyingEdits
import com.example.workouttracker.domain.checklist.isInUse
import com.example.workouttracker.domain.checklist.startedOn
import com.example.workouttracker.domain.checklist.starterChecklist
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The setup refuses to go on without what a step must settle, and creates the checklist once. */
class ChecklistSetupStateTest {
    private val today = LocalDate.parse("2026-10-14")
    private val addWatering = ChecklistEdit.AddItem("itm_1", "Water the plants")

    private fun setup() = ChecklistSetupState(AppState().starterChecklist("Checklist", today))

    /** What the store makes of a creation: the starter as of the day, with the edits. */
    private fun created(starter: ChecklistConfiguration, edits: List<ChecklistEdit>) = starter.startedOn(today).applyingEdits(edits, today)

    @Test
    fun theFirstStepWaitsForAName() {
        val state = setup()
        assertFalse(state.isComplete(ChecklistPage.Look, today))
        state.next(today)
        assertEquals(ChecklistPage.Look, state.step)

        state.rename("Plants", today)
        assertTrue(state.isComplete(ChecklistPage.Look, today))
        assertEquals("Plants", state.draft(today).label)
        state.next(today)
        assertEquals(ChecklistPage.Items, state.step)
    }

    /** The Streak step's holiday choice is an edit like any other, created with the checklist on the last step. */
    @Test
    fun anAwayRuleChosenInTheSetupIsCreatedWithTheChecklist() {
        val state = setup()
        state.rename("Plants", today)
        assertTrue(state.edit(addWatering, today))
        assertTrue(state.edit(ChecklistEdit.SetItemWhenAway("itm_1", WhenAway.Excused), today))
        assertEquals(WhenAway.Excused, state.draft(today).items.single().whenAway.on(today))

        var checklist: ChecklistConfiguration? = null
        assertTrue(state.create(today) { starter, edits -> checklist = created(starter, edits); true })
        assertEquals(WhenAway.Excused, checklist!!.items.single().whenAway.on(today))
    }

    /** Which day the items are about is chosen on the Items step like any edit; no day was judged yet, so it is set plainly. */
    @Test
    fun aChecklistAboutYesterdayIsCreatedAsChosenInTheSetup() {
        val state = setup()
        state.rename("Yesterday", today)
        assertTrue(state.edit(addWatering, today))
        assertTrue(state.edit(ChecklistEdit.SetAbout(AboutDay.Yesterday), today))
        assertEquals(Dated.always(AboutDay.Yesterday), state.draft(today).about)

        var checklist: ChecklistConfiguration? = null
        assertTrue(state.create(today) { starter, edits -> checklist = created(starter, edits); true })
        assertEquals(today.minusDays(1), checklist!!.recordDayFor(today))
    }

    /** A blank field takes the step's completion back; the draft keeps the last name, which is never blank. */
    @Test
    fun aBlankNameUndoesTheFirstStep() {
        val state = setup()
        state.rename("Plants", today)
        state.rename(null, today)
        assertFalse(state.isComplete(ChecklistPage.Look, today))
        assertNull(state.name)
        assertEquals("Plants", state.draft(today).label)
        assertTrue(state.touched)
    }

    @Test
    fun theSecondStepWaitsForAnItemAndTheLastTwoWaitForNothing() {
        val state = setup()
        state.rename("Plants", today)
        state.next(today)
        assertFalse(state.isComplete(ChecklistPage.Items, today))
        state.next(today)
        assertEquals(ChecklistPage.Items, state.step)

        assertTrue(state.edit(addWatering, today))
        state.next(today)
        assertEquals(ChecklistPage.Reminders, state.step)
        state.next(today)
        assertEquals(ChecklistPage.Streak, state.step)
        assertTrue(state.isLastStep)
        state.next(today)
        assertEquals(ChecklistPage.Streak, state.step)
    }

    @Test
    fun stepsAheadCannotBeShownAndStepsPassedCan() {
        val state = setup()
        assertFalse(state.canShow(ChecklistPage.Items))
        state.show(ChecklistPage.Items)
        assertEquals(ChecklistPage.Look, state.step)

        state.rename("Plants", today)
        state.next(today)
        state.edit(addWatering, today)
        state.next(today)
        assertEquals(ChecklistPage.Reminders, state.step)
        assertTrue(state.canShow(ChecklistPage.Look))
        assertFalse(state.canShow(ChecklistPage.Streak))
        state.show(ChecklistPage.Look)
        assertEquals(ChecklistPage.Look, state.step)
        state.back()
        assertEquals(ChecklistPage.Look, state.step)
    }

    @Test
    fun createHandsOverTheStarterAndItsEditsOnceAndOnlyWhenEveryStepIsComplete() {
        val state = setup()
        val creations = mutableListOf<ChecklistConfiguration>()
        val onCreate: (ChecklistConfiguration, List<ChecklistEdit>) -> Boolean = { starter, edits -> creations += created(starter, edits); true }

        state.rename("Plants", today)
        assertFalse(state.create(today, onCreate))
        assertTrue(creations.isEmpty())

        state.edit(addWatering, today)
        assertTrue(state.create(today, onCreate))
        assertTrue(state.create(today, onCreate))
        assertEquals(1, creations.size)

        // Valid, from the starter's defaults: one checkpoint from today, the item from today, no rewards, a deep hue.
        val checklist = creations.single()
        assertEquals("Plants", checklist.label)
        assertTrue(checklist.isInUse(today))
        assertEquals(today, checklist.items.single().from)
        assertEquals(today, checklist.checkpoints.single().from)
        assertNull(checklist.rewards)
        assertTrue(checklist.hue.isDeep)
        assertEquals(state.draft(today), checklist)
    }

    /** Text typed but not added, or an item left without a name, holds Next and Create back until it is settled. */
    @Test
    fun nextAndCreateWaitForEveryFieldToBeSettled() {
        val state = setup()
        state.rename("Plants", today)
        state.next(today)
        state.edit(addWatering, today)
        assertTrue(state.canContinue(ChecklistPage.Items, today))

        state.form.typing("checklist_editor_add_item", "Prune")
        assertFalse(state.canContinue(ChecklistPage.Items, today))
        assertFalse(state.canCreate(today))
        state.next(today)
        assertEquals(ChecklistPage.Items, state.step)
        assertFalse(state.create(today) { _, _ -> true })

        state.form.typing("checklist_editor_add_item", "")
        state.form.named("itm_1", null)
        assertEquals(setOf("itm_1"), state.blankItemNames)
        assertFalse(state.canCreate(today))

        state.form.named("itm_1", "Water")
        assertTrue(state.canCreate(today))
        assertTrue(state.create(today) { _, _ -> true })
    }

    /** A name cleared on Items holds only Items: Look's Next still leads back to where it is fixed. */
    @Test
    fun aNameClearedOnItemsNeverLocksLook() {
        val state = setup()
        state.rename("Plants", today)
        state.next(today)
        state.edit(addWatering, today)
        state.form.named("itm_1", null)
        state.back()
        assertEquals(ChecklistPage.Look, state.step)

        assertNull(state.waitingFor(ChecklistPage.Look, today))
        assertTrue(state.canContinue(ChecklistPage.Look, today))
        state.next(today)
        assertEquals(ChecklistPage.Items, state.step)
        assertEquals(SetupWait.ItemNames, state.waitingFor(ChecklistPage.Items, today))
        assertFalse(state.canContinue(ChecklistPage.Items, today))

        state.form.named("itm_1", "Water")
        assertTrue(state.canContinue(ChecklistPage.Items, today))
    }

    /**
     * Text typed into a "New item", the checklist's or a checkpoint's own, holds Items, where the
     * checklist's parts are made; Reminders has nothing of its own to settle, and the last step
     * waits for the whole form.
     */
    @Test
    fun eachStepWaitsForItsOwnFieldsAndTheLastForAll() {
        val state = setup()
        state.rename("Plants", today)
        state.next(today)
        state.edit(addWatering, today)
        state.form.typing("${AddItemField}_cpt_evening", "Mist")
        assertFalse(state.canContinue(ChecklistPage.Items, today))
        assertEquals(SetupWait.TypedText, state.waitingFor(ChecklistPage.Items, today))
        assertNull(state.waitingFor(ChecklistPage.Reminders, today))

        state.form.typing("${AddItemField}_cpt_evening", "")
        state.next(today)
        state.next(today)
        state.form.typing(AddItemField, "Prune")
        assertEquals(SetupWait.TypedText, state.waitingFor(ChecklistPage.Streak, today))
        assertFalse(state.canCreate(today))
    }

    /** The checklist's own blank name is Look's to say, not an item's. */
    @Test
    fun aBlankChecklistNameIsNotAnItemWithoutAName() {
        val state = setup()
        state.rename("Plants", today)
        state.rename(null, today)
        assertTrue(state.blankItemNames.isEmpty())
        assertTrue(state.fieldsSettled)
    }

    /** A store that refuses leaves the setup ready to try again. */
    @Test
    fun aRefusedCreateCanBeTriedAgain() {
        val state = setup()
        state.rename("Plants", today)
        state.edit(addWatering, today)
        assertFalse(state.create(today) { _, _ -> false })
        assertTrue(state.create(today) { _, _ -> true })
    }

    @Test
    fun theSetupSurvivesBeingSavedAndRestored() {
        val state = setup()
        state.rename("Plants", today)
        state.next(today)
        state.edit(addWatering, today)

        val saved = with(ChecklistSetupState.Saver) { SaverScope { true }.save(state) }
        val restored = requireNotNull(ChecklistSetupState.Saver.restore(requireNotNull(saved)))

        assertEquals(state.starter, restored.starter)
        assertEquals(state.form.edits, restored.form.edits)
        assertEquals(state.draft(today), restored.draft(today))
        assertEquals("Plants", restored.name)
        assertEquals(ChecklistPage.Items, restored.step)
        assertTrue(restored.touched)

        val untouched = requireNotNull(ChecklistSetupState.Saver.restore(requireNotNull(with(ChecklistSetupState.Saver) { SaverScope { true }.save(setup()) })))
        assertNull(untouched.name)
        assertFalse(untouched.touched)
    }
}
