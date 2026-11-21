package com.example.workouttracker.feature.settings

import androidx.compose.runtime.saveable.SaverScope
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.questions.QuestionsConfiguration
import com.example.workouttracker.domain.questions.QuestionsEdit
import com.example.workouttracker.domain.questions.starterQuestion
import com.example.workouttracker.domain.questions.starterQuestions
import com.example.workouttracker.domain.questions.withEating
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A new questions view's setup walks four steps over one draft. Look waits for a name and
 * Questions for every question's and every answer's text; it creates the view once.
 */
class QuestionsSetupStateTest {
    private val today = LocalDate.parse("2026-10-14")
    private val starter = AppState().withEating().starterQuestions("Questions", starterQuestion("Question", listOf("Very bad", "Bad", "Okay", "Good", "Very good")), today)
    private val question = starter.questions.single()

    private fun QuestionsSetupState.restored(): QuestionsSetupState {
        val saved = requireNotNull(with(QuestionsSetupState.Saver) { SaverScope { true }.save(this@restored) })
        return requireNotNull(QuestionsSetupState.Saver.restore(saved))
    }

    /** As it opens the setup holds one question with five answers and no text, which alone is nothing to lose. */
    @Test
    fun asItOpensNothingWasTypedAndTheFirstStepWaitsForAName() {
        val state = QuestionsSetupState(starter, today)

        assertEquals(listOf(ChecklistPage.Look, ChecklistPage.Items, ChecklistPage.Reminders, ChecklistPage.Streak), state.steps.parts)
        assertFalse(state.touched)
        assertFalse(state.canContinue)
        assertFalse(state.canCreate)
        assertFalse(state.isComplete(ChecklistPage.Items))
        assertFalse(state.showsMissingName)
        state.next()
        assertEquals(ChecklistPage.Look, state.steps.step)

        state.rename("Sleep")
        assertTrue(state.touched)
        state.next()
        assertEquals(ChecklistPage.Items, state.steps.step)
    }

    /** The Questions step waits for every question's text and every answer's label, and says so on that step and on the last. */
    @Test
    fun theQuestionsStepWaitsForEveryTextAndCreateWaitsForEveryStep() {
        val state = QuestionsSetupState(starter, today)
        state.rename("Sleep")
        state.next()
        assertTrue(state.waitsForText(ChecklistPage.Items))
        assertFalse(state.waitsForText(ChecklistPage.Look))
        state.next()
        assertEquals(ChecklistPage.Items, state.steps.step)

        state.form.typeQuestion(question.id, "How rested do you feel?")
        assertTrue(state.canContinue)
        assertFalse(state.waitsForText(ChecklistPage.Items))

        // An answer cleared, or a second question without its text, holds the step again.
        state.form.typeAnswer(question.id, question.answers.first().id, "")
        assertFalse(state.canContinue)
        state.form.typeAnswer(question.id, question.answers.first().id, "Not at all")
        state.form.addQuestion("Question", listOf("No", "Yes"), NamedQuestionIds())
        assertFalse(state.canContinue)
        state.form.removeQuestion(state.form.edited.questions.last().id)
        assertTrue(state.canContinue)

        state.next()
        state.next()
        assertEquals(ChecklistPage.Streak, state.steps.step)
        assertTrue(state.canCreate)
        // Back on Questions a field is emptied: the last step can be shown and says what Create waits for.
        state.form.typeQuestion(question.id, "")
        assertFalse(state.canCreate)
        assertTrue(state.waitsForText(ChecklistPage.Streak))
        assertFalse(state.waitsForText(ChecklistPage.Reminders))
    }

    /** Create hands over the starter and the edits once every step is complete, and never twice; a creation that stored nothing can be tried again under the same ID. */
    @Test
    fun createHandsOverTheStarterAndItsEditsOnce() {
        val state = QuestionsSetupState(starter, today)
        val asked = mutableListOf<Pair<QuestionsConfiguration, List<QuestionsEdit>>>()
        assertEquals(ViewWrite.NotStored, state.create { view, edits -> asked += view to edits; ViewWrite.Stored })
        assertEquals(0, asked.size)

        state.rename("Sleep")
        state.form.typeQuestion(question.id, "How rested do you feel?")
        assertEquals(ViewWrite.NotStored, state.create { view, edits -> asked += view to edits; ViewWrite.NotStored })
        assertFalse(state.created)
        assertEquals(ViewWrite.StoredWithoutAlarm, state.create { view, edits -> asked += view to edits; ViewWrite.StoredWithoutAlarm })
        assertTrue(state.created)
        assertFalse(state.touched)
        assertEquals(ViewWrite.Stored, state.create { _, _ -> error("It is stored already.") })

        assertEquals(listOf(starter, starter), asked.map { it.first })
        assertEquals(listOf(QuestionsEdit.Rename("Sleep"), QuestionsEdit.RenameQuestion(question.id, "How rested do you feel?")), asked.last().second)
    }

    /** The setup survives process death with its ID, its step, what was typed and what still waits. */
    @Test
    fun theSetupSurvivesProcessDeath() {
        val state = QuestionsSetupState(starter, today)
        state.rename("Sleep")
        state.next()
        val untouched = state.restored()
        assertEquals(starter, untouched.starter)
        assertEquals(ChecklistPage.Items, untouched.steps.step)
        assertEquals("Sleep", untouched.name)
        assertFalse(untouched.canContinue)

        state.form.typeQuestion(question.id, "How rested do you feel?")
        state.form.takePart(true)
        val restored = state.restored()
        assertEquals(state.form.edited, restored.form.edited)
        assertTrue(restored.canContinue)
        assertTrue(restored.touched)
    }
}
