package com.example.workouttracker.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import com.example.workouttracker.core.ui.theme.WorkoutTrackerTheme
import com.example.workouttracker.domain.questions.testMadeQuestions
import com.example.workouttracker.domain.questions.testQuestion
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * What comes and goes in the Questions part as questions and answers are added and removed moves
 * nothing but the row that was added or removed. The two lines that say why something stays, "At
 * least one question stays." and the one about two answers, keep their space while they say
 * nothing; the two buttons that add stay where they are at five and cannot be pressed.
 */
@RunWith(RobolectricTestRunner::class)
class QuestionsPartLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.parse("2026-10-14")
    private val question = "part_question_qn_rest"

    private fun show(answers: Int, more: Int = 0): QuestionsSettingsDraft {
        val questions = listOf(testQuestion(id = "qn_rest", answers = answers)) + (1..more).map { testQuestion(id = "qn_more$it", text = "More $it?", answers = 2) }
        val draft = QuestionsSettingsDraft(testMadeQuestions(questions = questions), today)
        compose.setContent {
            WorkoutTrackerTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    QuestionsPart(view = draft.edited, draft = draft, today = today, testTag = "part")
                }
            }
        }
        compose.waitForIdle()
        return draft
    }

    private fun click(tag: String) {
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    /** The two taps that remove something. */
    private fun remove(tag: String) = repeat(2) { click(tag) }

    private fun top(tag: String): Float = compose.onNodeWithTag(tag).fetchSemanticsNode().positionInRoot.y
    private fun bottom(tag: String): Float = compose.onNodeWithTag(tag).fetchSemanticsNode().let { it.positionInRoot.y + it.size.height }

    @Test
    fun theLineUnderTheLastQuestionComesAndGoesAndNothingUnderItMoves() {
        val draft = show(answers = 3)
        compose.onNodeWithTag("${question}_keep").assertExists()
        val under = listOf("${question}_answer_a1", "${question}_answer_a3", "${question}_add_answer")
        val places = under.map(::top)
        // The line sits between the question's field and its first answer.
        assertTrue(bottom("${question}_text") <= top("${question}_keep") && bottom("${question}_keep") <= top("${question}_answer_a1"))

        click("part_add_question")
        compose.onNodeWithTag("${question}_keep").assertDoesNotExist()
        assertEquals(places, under.map(::top))

        remove("part_question_${draft.edited.questions.last().id}_remove")
        compose.onNodeWithTag("${question}_keep").assertExists()
        assertEquals(places, under.map(::top))
    }

    @Test
    fun theLineUnderTwoAnswersComesAndGoesAndTheButtonUnderItKeepsItsDistance() {
        val draft = show(answers = 3)
        compose.onNodeWithTag("${question}_keep_answers").assertDoesNotExist()
        val gap = top("${question}_add_answer") - bottom("${question}_answer_a3")

        remove("${question}_answer_a3_remove")
        compose.onNodeWithTag("${question}_keep_answers").assertExists()
        assertEquals(gap, top("${question}_add_answer") - bottom("${question}_answer_a2"))
        // The line is in the space that was kept for it.
        assertTrue(bottom("${question}_answer_a2") <= top("${question}_keep_answers") && bottom("${question}_keep_answers") <= top("${question}_add_answer"))

        click("${question}_add_answer")
        compose.onNodeWithTag("${question}_keep_answers").assertDoesNotExist()
        assertEquals(gap, top("${question}_add_answer") - bottom("${question}_answer_${draft.edited.questions.single().answers.last().id}"))
    }

    @Test
    fun theButtonThatAddsAnAnswerStaysAtFiveAndWhatIsUnderItKeepsItsDistance() {
        val draft = show(answers = 4)
        fun answers() = draft.edited.questions.single().answers
        fun gap() = top("${question}_more_options") - bottom("${question}_answer_${answers().last().id}")
        val gap = gap()
        compose.onNodeWithTag("${question}_add_answer").assertIsEnabled()

        click("${question}_add_answer")
        assertEquals(5, answers().size)
        compose.onNodeWithTag("${question}_add_answer").assertIsNotEnabled()
        assertEquals(gap, gap())

        remove("${question}_answer_a1_remove")
        compose.onNodeWithTag("${question}_add_answer").assertIsEnabled()
        assertEquals(gap, gap())
    }

    @Test
    fun theButtonThatAddsAQuestionStaysAtFiveAndWhatIsUnderItKeepsItsDistance() {
        val draft = show(answers = 2, more = 3)
        fun questions() = draft.edited.questions
        fun gap() = top("part_removal_note") - bottom("part_question_${questions().last().id}")
        val gap = gap()
        compose.onNodeWithTag("part_add_question").assertIsEnabled()

        click("part_add_question")
        assertEquals(5, questions().size)
        compose.onNodeWithTag("part_add_question").assertIsNotEnabled()
        assertEquals(gap, gap())

        remove("part_question_${questions().last().id}_remove")
        assertEquals(4, questions().size)
        compose.onNodeWithTag("part_add_question").assertIsEnabled()
        assertEquals(gap, gap())
    }
}
