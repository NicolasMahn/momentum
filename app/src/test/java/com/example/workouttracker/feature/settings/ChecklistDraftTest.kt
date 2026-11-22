package com.example.workouttracker.feature.settings

import androidx.compose.runtime.saveable.SaverScope
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.checklist.ChecklistConfiguration
import com.example.workouttracker.domain.checklist.ChecklistEdit
import com.example.workouttracker.domain.checklist.ChecklistItem
import com.example.workouttracker.domain.checklist.ChecklistTarget
import com.example.workouttracker.domain.checklist.Checkpoint
import com.example.workouttracker.domain.checklist.CheckpointTime
import com.example.workouttracker.domain.checklist.Dated
import com.example.workouttracker.domain.checklist.ViewHue
import com.example.workouttracker.domain.checklist.ViewIcon
import com.example.workouttracker.domain.checklist.WhenAway
import com.example.workouttracker.domain.checklist.applyEdit
import com.example.workouttracker.domain.checklist.checklist
import com.example.workouttracker.domain.checklist.liveItems
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A checklist's page keeps the edits made; the stored configuration waits for Save. */
class ChecklistDraftTest {
    private val today = LocalDate.parse("2026-10-14")
    private val plants = ChecklistConfiguration(
        id = "chk_plants",
        label = "Plants",
        icon = ViewIcon.Plant,
        hue = ViewHue.Forest,
        checkpoints = listOf(Checkpoint("cpt_1", CheckpointTime.Fixed(LocalTime.of(9, 0)), remind = false)),
        items = listOf(ChecklistItem("itm_1", "Water the plants")),
    )
    private val addFeeding = ChecklistEdit.AddItem("itm_2", "Feed the plants")

    private fun ChecklistDraft.draft() = draft(today, recordedDates = emptySet())

    @Test
    fun anEditIsKeptAndShownOverTheStoredConfigurationWhichWaits() {
        val draft = ChecklistDraft(plants)
        assertFalse(draft.isDirty)

        assertTrue(draft.edit(addFeeding, today, recordedDates = emptySet()))

        assertTrue(draft.isDirty)
        assertTrue(draft.canSave)
        assertEquals(listOf(addFeeding), draft.edits)
        assertEquals(plants, draft.stored)
        val storedPath = AppState(checklists = listOf(plants)).applyEdit(plants.id, addFeeding, today).checklist(plants.id)
        assertEquals(storedPath, draft.draft())
    }

    /** Weekly and then 5 is one change: Weekly 1 on the way cannot hold fewer times, which must survive. */
    @Test
    fun aRhythmChosenRightAfterAnotherReplacesIt() {
        val draft = ChecklistDraft(plants)
        assertTrue(draft.edit(ChecklistEdit.SetItemWhenAway("itm_1", WhenAway.LowerTarget), today, recordedDates = emptySet()))
        assertTrue(draft.edit(ChecklistEdit.SetTarget(ChecklistTarget.PerWeek(1)), today, recordedDates = emptySet()))
        assertTrue(draft.edit(ChecklistEdit.SetTarget(ChecklistTarget.PerWeek(5)), today, recordedDates = emptySet()))
        assertEquals(ChecklistEdit.SetTarget(ChecklistTarget.PerWeek(5)), draft.edits.last())
        assertEquals(2, draft.edits.size)
        assertEquals(WhenAway.LowerTarget, draft.draft().liveItems(today).single().whenAway.on(today))

        assertTrue(draft.edit(ChecklistEdit.SetTarget(ChecklistTarget.PerDay), today, recordedDates = emptySet()))
        assertEquals(1, draft.edits.size)
    }

    /** An item's holiday rule is a page choice like any other: the draft takes it, the stored configuration waits for Save. */
    @Test
    fun anItemsAwayRuleChangesTheDraftAndWaitsForSave() {
        val draft = ChecklistDraft(plants)
        assertTrue(draft.edit(ChecklistEdit.SetItemWhenAway("itm_1", WhenAway.Excused), today, recordedDates = emptySet()))
        assertEquals(WhenAway.Excused, draft.draft().items.single().whenAway.on(today))
        assertEquals(plants, draft.stored)
        assertTrue(draft.isDirty)
        // A count no fewer than the usual one is refused and leaves the draft as it was.
        assertFalse(draft.edit(ChecklistEdit.SetItemAwayTimes("itm_1", 7), today, recordedDates = emptySet()))
        assertEquals(null, draft.draft().items.single().awayTimes)
        assertTrue(draft.edit(ChecklistEdit.SetItemAwayTimes("itm_1", 3), today, recordedDates = emptySet()))
        assertTrue(draft.edit(ChecklistEdit.SetItemWhenAway("itm_1", WhenAway.LowerTarget), today, recordedDates = emptySet()))
        assertEquals(3 to WhenAway.LowerTarget, draft.draft().liveItems(today).single().let { it.awayTimes to it.whenAway.on(today) })
    }

    /** A week item that lowered without a count before Block 17 goes to still due and back on one page as it was: no default count slips in. */
    @Test
    fun aLegacyCountSurvivesStillDueAndBackOnOnePage() {
        val violin = ChecklistItem("itm_v", "Violin", target = ChecklistTarget.PerWeek(times = 5), whenAway = Dated.always(WhenAway.LowerTarget))
        val practice = plants.copy(id = "chk_practice", items = listOf(violin))
        val draft = ChecklistDraft(practice)
        val legacy = legacyAwayTimes(violin, violin.target!!, WhenAway.LowerTarget)
        assertTrue(draft.edit(ChecklistEdit.SetItemWhenAway("itm_v", WhenAway.Due), today, recordedDates = emptySet()))
        fewerTimesEdits(draft.draft().liveItems(today).single(), violin.target!!, WhenAway.LowerTarget, legacy)
            .forEach { assertTrue(draft.edit(it, today, recordedDates = emptySet())) }
        assertEquals(practice, draft.draft())
        // Without the legacy reading the same toggle would pick a first count.
        assertEquals(2, fewerTimesEdits(draft.draft().liveItems(today).single(), violin.target!!, WhenAway.LowerTarget).filterIsInstance<ChecklistEdit.SetItemAwayTimes>().single().times)
    }

    @Test
    fun aRefusedEditIsNotKept() {
        val draft = ChecklistDraft(plants)
        assertFalse(draft.edit(ChecklistEdit.RemoveItem("itm_1"), today, recordedDates = emptySet()))
        assertEquals(plants, draft.draft())
        assertFalse(draft.isDirty)
    }

    /** What was stored meanwhile, such as a renamed label, stays under the page's own edits. */
    @Test
    fun aChangeStoredMeanwhileStaysUnderTheEdits() {
        val draft = ChecklistDraft(plants)
        draft.edit(addFeeding, today, recordedDates = emptySet())

        draft.follow(plants.copy(label = "Garden"))

        assertEquals("Garden", draft.draft().label)
        assertEquals(listOf("itm_1", "itm_2"), draft.draft().items.map { it.id })
        assertEquals(listOf(addFeeding), draft.edits)
    }

    /** An edit the latest configuration refuses, such as removing an item removed meanwhile, is left out. */
    @Test
    fun anEditTheLatestConfigurationRefusesIsLeftOut() {
        val twoItems = plants.copy(items = plants.items + ChecklistItem("itm_2", "Feed the plants"))
        val draft = ChecklistDraft(twoItems)
        assertTrue(draft.edit(ChecklistEdit.RemoveItem("itm_2"), today, recordedDates = emptySet()))

        draft.follow(plants)

        assertEquals(plants, draft.draft())
    }

    @Test
    fun savedThereIsNothingLeftToSaveAndTheDraftIsTheStoredConfiguration() {
        val draft = ChecklistDraft(plants)
        draft.edit(addFeeding, today, recordedDates = emptySet())
        val stored = draft.draft()

        draft.saved()
        draft.follow(stored)

        assertFalse(draft.isDirty)
        assertFalse(draft.hasUnsavedWork)
        assertEquals(stored, draft.draft())
    }

    /** A cleared name keeps the last one in the draft and holds Save back; text typed but not added is unsaved work. */
    @Test
    fun aBlankNameHoldsSaveBackAndTypedTextIsUnsavedWork() {
        val draft = ChecklistDraft(plants)
        draft.edit(ChecklistEdit.Rename("G"), today, recordedDates = emptySet())
        draft.named(plants.id, null)
        assertEquals("G", draft.draft().label)
        assertFalse(draft.canSave)
        assertTrue(draft.hasUnsavedWork)

        draft.named(plants.id, "Garden")
        draft.edit(ChecklistEdit.Rename("Garden"), today, recordedDates = emptySet())
        assertTrue(draft.canSave)

        val clean = ChecklistDraft(plants)
        clean.typing("checklist_editor_add_item", "Feed")
        assertFalse(clean.isDirty)
        assertTrue(clean.hasUnsavedWork)
        clean.typing("checklist_editor_add_item", "")
        assertFalse(clean.hasUnsavedWork)
    }

    /** Typed text is all a page holds once nothing waits for Save and no name is blank: that is when a saved page stays open for it. */
    @Test
    fun typedTextIsAllThePageHoldsOnceItsEditsAreSavedAndEveryNameIsFilledIn() {
        val draft = ChecklistDraft(plants)
        draft.typing("checklist_editor_add_item", "Prune")
        draft.edit(addFeeding, today, recordedDates = emptySet())
        assertFalse(draft.holdsOnlyTypedText)

        val stored = draft.draft()
        draft.saved()
        draft.follow(stored)
        assertTrue(draft.holdsOnlyTypedText)
        assertTrue(draft.hasUnsavedWork)
        assertFalse(draft.canSave)

        draft.named("itm_1", null)
        assertFalse(draft.holdsOnlyTypedText)
        draft.named("itm_1", "Water the plants")
        assertTrue(draft.holdsOnlyTypedText)

        draft.typing("checklist_editor_add_item", "")
        assertFalse(draft.holdsOnlyTypedText)
        assertFalse(draft.hasUnsavedWork)
    }

    /** Each name is stored on its own, so a saved field follows later renames whatever other fields hold. */
    @Test
    fun eachNameIsStoredOnItsOwn() {
        val draft = ChecklistDraft(plants)
        draft.edit(ChecklistEdit.Rename("Garden"), today, recordedDates = emptySet())
        draft.typing("checklist_editor_add_item", "Feed")
        assertFalse(draft.nameStored(plants.id))
        assertTrue(draft.nameStored("itm_1"))

        draft.saved()
        draft.named("itm_1", null)

        assertTrue(draft.nameStored(plants.id))
        assertFalse(draft.nameStored("itm_1"))
        draft.edit(ChecklistEdit.AddItem("itm_2", "Feed the plants"), today, recordedDates = emptySet())
        assertFalse(draft.nameStored("itm_2"))
    }

    @Test
    fun removingAnItemLetsGoOfItsBlankName() {
        val twoItems = plants.copy(items = plants.items + ChecklistItem("itm_2", "Feed the plants"))
        val draft = ChecklistDraft(twoItems)
        draft.named("itm_2", null)
        assertFalse(draft.canSave)
        assertTrue(draft.edit(ChecklistEdit.RemoveItem("itm_2"), today, recordedDates = emptySet()))
        assertTrue(draft.canSave)
    }

    @Test
    fun theDraftSurvivesBeingSavedAndRestored() {
        val draft = ChecklistDraft(plants)
        draft.edit(addFeeding, today, recordedDates = emptySet())
        draft.edit(ChecklistEdit.SetItemWhenAway("itm_1", WhenAway.Excused), today, recordedDates = emptySet())
        draft.named("itm_1", null)
        draft.typing("checklist_editor_add_item", "Prune")

        val saved = with(ChecklistDraft.Saver) { SaverScope { true }.save(draft) }
        val restored = requireNotNull(ChecklistDraft.Saver.restore(requireNotNull(saved)))

        assertEquals(draft.stored, restored.stored)
        assertEquals(draft.edits, restored.edits)
        assertEquals(draft.draft(), restored.draft())
        assertEquals(setOf("itm_1"), restored.blankNames)
        assertEquals(setOf("checklist_editor_add_item"), restored.pendingText)
        assertFalse(restored.canSave)
    }
}
