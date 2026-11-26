package de.nicolasmahn.momentum.feature.settings

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import de.nicolasmahn.momentum.core.ui.theme.WorkoutTrackerTheme
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.checklist.ChecklistConfiguration
import de.nicolasmahn.momentum.domain.checklist.ChecklistEdit
import de.nicolasmahn.momentum.domain.checklist.asSchedule
import de.nicolasmahn.momentum.domain.checklist.progressOf
import de.nicolasmahn.momentum.domain.checklist.starterChecklist
import de.nicolasmahn.momentum.domain.holiday.HolidaySchedule
import de.nicolasmahn.momentum.domain.reminder.Bedtime
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A schedule's parts are its checkpoints, made on Items with their own items: a new part never
 * takes the items there were, an item added under a part is due there alone, and the step waits
 * until every part has items and a name.
 */
@RunWith(RobolectricTestRunner::class)
class ChecklistCheckpointsTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.parse("2026-10-14")
    private val state = ChecklistSetupState(AppState().starterChecklist("Care", today).asSchedule())

    @Test
    fun aNewPartKeepsTheItemsThereWereAndTakesItsOwn() {
        state.rename("Care", today)
        state.next(today)
        val (morning, evening) = checklist().checkpoints.map { it.id }
        state.edit(ChecklistEdit.AddItem("itm_teeth", "Brush teeth", checkpoints = listOf(morning)), today)
        state.edit(ChecklistEdit.AddItem("itm_floss", "Floss", checkpoints = listOf(evening)), today)
        state.edit(ChecklistEdit.RenameCheckpoint(morning, "Morning"), today)
        state.edit(ChecklistEdit.RenameCheckpoint(evening, "Evening"), today)
        compose.setContent {
            WorkoutTrackerTheme {
                val draft = state.draft(today)
                ChecklistPageContent(
                    page = ChecklistPage.Items,
                    checklist = draft,
                    today = today,
                    bedtime = Bedtime(LocalTime.of(7, 0), null),
                    mood = null,
                    progress = AppState().progressOf(draft, today.toString(), HolidaySchedule()),
                    form = state.form,
                    onEdit = { state.edit(it, today) },
                    reminderRow = { _, _, _ -> },
                    onSendTest = null,
                    nameField = {},
                )
            }
        }

        compose.onNodeWithTag(AddCheckpointButton).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        val night = checklist().checkpoints.last().id
        assertEquals(3, checklist().checkpoints.size)
        assertEquals(listOf(morning), checklist().items.single { it.label == "Brush teeth" }.checkpoints)
        assertEquals(listOf(evening), checklist().items.single { it.label == "Floss" }.checkpoints)
        // A new part comes empty and without a name, and the step waits for both.
        assertEquals(SetupWait.CheckpointItems, state.waitingFor(ChecklistPage.Items, today))

        add("${AddItemField}_$night", "Stretch")
        assertEquals(listOf(night), checklist().items.single { it.label == "Stretch" }.checkpoints)
        assertEquals(SetupWait.CheckpointNames, state.waitingFor(ChecklistPage.Items, today))
        state.edit(ChecklistEdit.RenameCheckpoint(night, "Night"), today)
        assertNull(state.waitingFor(ChecklistPage.Items, today))
        compose.onNodeWithTag("checklist_editor_checkpoint_${night}_group").assertExists()
    }

    private fun checklist(): ChecklistConfiguration = state.draft(today)

    private fun add(field: String, text: String) {
        compose.onNodeWithTag(field).performTextReplacement(text)
        compose.onNodeWithTag("${field}_button").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }
}
