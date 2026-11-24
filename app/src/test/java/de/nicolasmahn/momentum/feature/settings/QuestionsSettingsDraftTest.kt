package de.nicolasmahn.momentum.feature.settings

import androidx.compose.runtime.saveable.SaverScope
import de.nicolasmahn.momentum.domain.checklist.AboutDay
import de.nicolasmahn.momentum.domain.checklist.CheckpointTime
import de.nicolasmahn.momentum.domain.checklist.ViewHue
import de.nicolasmahn.momentum.domain.checklist.ViewIcon
import de.nicolasmahn.momentum.domain.checklist.WhenAway
import de.nicolasmahn.momentum.domain.questions.Answer
import de.nicolasmahn.momentum.domain.questions.Question
import de.nicolasmahn.momentum.domain.questions.QuestionsEdit
import de.nicolasmahn.momentum.domain.questions.applyingEdits
import de.nicolasmahn.momentum.domain.questions.testEatingQuestions
import de.nicolasmahn.momentum.domain.questions.testMadeQuestions
import de.nicolasmahn.momentum.domain.questions.testQuestion
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import de.nicolasmahn.momentum.domain.checklist.RewardPeriod

/**
 * A questions view's page keeps its four parts as one draft. A question's or an answer's field
 * that is empty, or holds text that cannot be stored, stays the page's business: it holds Save
 * back, is guarded on leaving, and nothing is saved in its place.
 */
class QuestionsSettingsDraftTest {
    private val today = LocalDate.parse("2026-10-14")
    private val sleep = testMadeQuestions(questions = listOf(testQuestion(id = "qn_rest", answers = 3), testQuestion(id = "qn_mood", text = "Mood?", answers = 2)))
    private val fiveAnswers = listOf("Very bad", "Bad", "Okay", "Good", "Very good")

    private fun QuestionsSettingsDraft.restored(): QuestionsSettingsDraft {
        val saved = requireNotNull(with(QuestionsSettingsDraft.Saver) { SaverScope { true }.save(this@restored) })
        return requireNotNull(QuestionsSettingsDraft.Saver.restore(saved))
    }

    /**
     * Look, Questions, Reminders and Streak change the one draft: its edits, in the order they were
     * made, are what the page shows over the stored configuration and what Save hands over. Once
     * they are stored nothing is left, and the draft survives process death with every one of them.
     */
    @Test
    fun theDraftHoldsTheEditsOfAllFourParts() {
        val draft = QuestionsSettingsDraft(sleep, today)
        assertFalse(draft.canSave)
        assertFalse(draft.hasUnsavedWork)

        assertTrue(draft.rename("Nights"))
        assertTrue(draft.setIcon(ViewIcon.Mind))
        assertTrue(draft.setHue(ViewHue.Teal))
        assertTrue(draft.setAbout(AboutDay.Yesterday))
        draft.typeQuestion("qn_rest", "  How did you sleep?  ")
        draft.typeAnswer("qn_rest", "a1", "Badly")
        assertTrue(draft.typeHelp("qn_rest", "  Think of the whole night.\nNot only the morning.  "))
        assertTrue(draft.setRemind(true))
        assertTrue(draft.setReminderTime(CheckpointTime.AfterWake(20)))
        assertTrue(draft.takePart(true))
        assertTrue(draft.setBankRules(maxPasses = 4, everyKeptDays = 5))
        assertTrue(draft.setWhenAway(WhenAway.Excused))

        val edited = draft.edited
        assertEquals("Nights", edited.label)
        assertEquals(Question("qn_rest", "How did you sleep?", listOf(Answer("a1", "Badly"), Answer("a2", "Level 2"), Answer("a3", "Level 3")), help = "Think of the whole night.\nNot only the morning."), edited.questions.first())
        assertEquals(sleep.applyingEdits(draft.edits, today), edited)
        assertTrue(draft.canSave)
        // The spaces of the space bar around what was typed are left out, and nothing else is.
        assertEquals(QuestionsEdit.RenameQuestion("qn_rest", "How did you sleep?"), draft.edits.filterIsInstance<QuestionsEdit.RenameQuestion>().single())

        val restored = draft.restored()
        assertEquals(draft.edits, restored.edits)
        assertEquals(edited, restored.edited)

        draft.follow(edited, today)
        draft.saved()
        assertFalse(draft.hasUnsavedWork)
        assertTrue(draft.textStored("qn_rest") && draft.helpStored("qn_rest"))
    }

    /**
     * A question added on the page has five answers and no text of its own yet: Save waits until
     * it is typed, and what is stored then never holds the text that stood in for it. Its ID and
     * its answers' are none the view is named by, an entry's included, nor one the page drew before.
     */
    @Test
    fun anAddedQuestionWaitsForItsTextUnderIdsNothingNames() {
        val draft = QuestionsSettingsDraft(sleep, today)
        val named = NamedQuestionIds(questions = setOf("qn_rest", "qn_mood", "qn_gone"))

        draft.addQuestion("Question", fiveAnswers, named)

        val added = draft.edited.questions.last()
        assertFalse(added.id in named.questions)
        assertEquals(fiveAnswers, added.answers.map { it.label })
        assertEquals(5, added.answers.map { it.id }.toSet().size)
        assertEquals(setOf(added.id), draft.unsettled)
        assertFalse(draft.canSave)
        assertTrue(draft.hasUnsavedWork)
        assertFalse(draft.textStored(added.id))
        // Its answers came with their labels, so their fields hold what the draft holds.
        assertTrue(draft.textStored(QuestionsSettingsDraft.answerField(added.id, added.answers.first().id)))

        // Unsettled fields are kept across process death, so Save still waits afterwards.
        assertEquals(setOf(added.id), draft.restored().unsettled)

        draft.typeQuestion(added.id, "Energy?")
        assertEquals(emptySet<String>(), draft.unsettled)
        assertTrue(draft.canSave)
        assertEquals("Energy?", sleep.applyingEdits(draft.edits, today).questions.last().text)

        // A second one, and one added after the first was removed again, each have an ID of their own.
        draft.addQuestion("Question", fiveAnswers, named)
        val second = draft.edited.questions.last()
        draft.removeQuestion(second.id)
        draft.addQuestion("Question", fiveAnswers, named)
        assertEquals(3, setOf(added.id, second.id, draft.edited.questions.last().id).size)
    }

    /**
     * Text that cannot be stored, and a field left empty, never replace what the draft holds: the
     * draft keeps the last text, Save waits, and leaving would lose what was typed.
     */
    @Test
    fun aFieldHoldingNothingOrTextThatCannotBeStoredHoldsSaveBack() {
        val draft = QuestionsSettingsDraft(sleep, today)
        val answer = QuestionsSettingsDraft.answerField("qn_rest", "a2")

        listOf("", "   ", "x".repeat(121), "Two\nlines").forEach { refused ->
            draft.typeQuestion("qn_rest", refused)
            assertEquals(refused, setOf("qn_rest"), draft.unsettled)
            assertEquals(sleep, draft.edited)
            assertFalse(draft.canSave)
            assertTrue(draft.hasUnsavedWork)
        }
        draft.typeAnswer("qn_rest", "a2", "x".repeat(25))
        assertEquals(setOf("qn_rest", answer), draft.unsettled)

        // Typed again as it can be stored, each is the draft's.
        draft.typeQuestion("qn_rest", "x".repeat(120))
        draft.typeAnswer("qn_rest", "a2", "x".repeat(24))
        assertEquals(emptySet<String>(), draft.unsettled)
        assertTrue(draft.canSave)
        assertEquals("x".repeat(24), draft.edited.questions.first().answers[1].label)

        // A help text is taken without the space around it, and none is left where nothing is.
        assertTrue(draft.typeHelp("qn_rest", "  \n "))
        assertEquals(null, draft.edited.questions.first().help)
    }

    /** An answer added waits for its label as a question does; removing it, or its question, takes its field's wait with it. */
    @Test
    fun anAddedAnswerWaitsForItsLabelAndRemovalTakesTheWaitAway() {
        val draft = QuestionsSettingsDraft(sleep, today)
        val named = NamedQuestionIds(answers = { questionId -> if (questionId == "qn_mood") setOf("a1", "a2", "a_gone") else emptySet() })

        draft.addAnswer("qn_mood", "Answer", named)
        val added = draft.edited.questions.last().answers.last()
        assertFalse(added.id in setOf("a1", "a2", "a_gone"))
        assertEquals(setOf(QuestionsSettingsDraft.answerField("qn_mood", added.id)), draft.unsettled)

        draft.removeAnswer("qn_mood", added.id)
        assertEquals(emptySet<String>(), draft.unsettled)
        assertEquals(sleep, draft.edited)
        assertFalse(draft.hasUnsavedWork)

        draft.addAnswer("qn_mood", "Answer", named)
        draft.typeQuestion("qn_mood", "")
        assertEquals(2, draft.unsettled.size)
        draft.removeQuestion("qn_mood")
        assertEquals(emptySet<String>(), draft.unsettled)
        assertEquals(listOf("qn_rest"), draft.edited.questions.map { it.id })
        assertTrue(draft.canSave)
    }

    /** A view keeps one question and a question two answers, and has no more than five of either: the draft refuses the rest as the store would. */
    @Test
    fun theLastQuestionAndTheLastTwoAnswersStayAndFiveIsTheMost() {
        val draft = QuestionsSettingsDraft(testMadeQuestions(questions = listOf(testQuestion(answers = 2))), today)
        draft.removeQuestion("qn_rest")
        draft.removeAnswer("qn_rest", "a1")
        assertFalse(draft.isDirty)

        repeat(4) { draft.addAnswer("qn_rest", "Answer", NamedQuestionIds()) }
        repeat(5) { draft.addQuestion("Question", fiveAnswers, NamedQuestionIds()) }
        assertEquals(5, draft.edited.questions.size)
        assertEquals(5, draft.edited.questions.first().answers.size)
        // What was refused left no field waiting.
        assertEquals(3 + 4, draft.unsettled.size)
    }

    /** Eating's configuration is drafted by the same draft: its name, its look, its bank's two numbers, its holiday rule and its part in the streak; its prompts and record day remain editable. */
    @Test
    fun eatingsConfigurationIsDraftedToo() {
        val eating = testEatingQuestions()
        val draft = QuestionsSettingsDraft(eating, today)

        assertTrue(draft.rename("Meals"))
        assertTrue(draft.setHue(ViewHue.Teal))
        assertTrue(draft.setBankRules(maxPasses = 2, everyKeptDays = 5))
        assertTrue(draft.setWhenAway(WhenAway.Excused))
        // Prompt content and record-day ownership are ordinary editable settings.
        assertTrue(draft.setAbout(AboutDay.Today))
        assertEquals(AboutDay.Today, draft.edited.about.on(today))
        // Eating leaves and joins the streak as any view does; a draft knows no first record, so leaving leaves no period on it.
        assertTrue(draft.takePart(false))
        assertEquals(emptyList<RewardPeriod>(), draft.edited.rewards?.periods)
        assertTrue(draft.takePart(true))
        // Its reminder is its checkpoint, as a made view's is.
        assertTrue(draft.setRemind(true))
        draft.addQuestion("Question", fiveAnswers, NamedQuestionIds())
        val added = draft.edited.questions.last()
        assertEquals(setOf(added.id), draft.unsettled)
        assertFalse(draft.canSave)
        draft.typeQuestion(added.id, "What made the day worthwhile?")
        assertEquals("What made the day worthwhile?", draft.edited.questions.last().text)
        assertEquals(emptySet<String>(), draft.unsettled)
        assertEquals(eating.applyingEdits(draft.edits, today), draft.edited)
        assertEquals("Meals", draft.edited.label)
        assertEquals(eating.questions.size + 1, draft.edited.questions.size)
        assertEquals(eating.questions, draft.edited.questions.take(eating.questions.size))
        assertEquals(draft.edited, draft.restored().edited)
    }
}
