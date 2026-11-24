package de.nicolasmahn.momentum.gallery

import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import de.nicolasmahn.momentum.app.AppDestination
import de.nicolasmahn.momentum.core.ui.theme.WorkoutTrackerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The gallery shows the questions screen as Eating's destination has it and for a view a person
 * made, with three questions of two, three and five answers. Each specimen is the real screen over
 * fake entries: a day can be answered in it and stays on show, and one specimen opens on a day
 * that has its answers.
 */
@RunWith(RobolectricTestRunner::class)
class QuestionsScreenSpecimensTest {
    @get:Rule
    val compose = createComposeRule()

    private val specimens = viewSpecimens(AppDestination.Questions).associateBy { it.name }
    private var shown by mutableStateOf<GallerySpecimen?>(null)

    private fun show(name: String) {
        shown = specimens.getValue(name)
        compose.waitForIdle()
    }

    @Test
    fun theScreenIsShownForEatingAndForAMadeView() {
        compose.setContent { WorkoutTrackerTheme { shown?.let { specimen -> key(specimen.name) { specimen.content() } } } }

        show("QuestionsScreen, Eating")
        compose.onNodeWithTag("gallery_eating_screen_quality_4").assertExists()
        compose.onNodeWithTag("gallery_eating_screen_amount_not_sure").assertExists()
        compose.onNodeWithTag("gallery_eating_screen_quality_help").assertExists()

        show("QuestionsScreen, a made view")
        val made = "gallery_${DemoSleepQuestions.id}"
        listOf("qn_rest" to 3, "qn_screen" to 2, "qn_hours" to 5).forEach { (question, answers) ->
            compose.onNodeWithTag("${made}_${question}_${answers - 1}").assertExists()
            compose.onNodeWithTag("${made}_${question}_$answers").assertDoesNotExist()
            compose.onNodeWithTag("${made}_${question}_not_sure").assertExists()
        }
        compose.onNodeWithTag("${made}_qn_rest_help").assertExists()
        compose.onNodeWithTag("${made}_qn_hours_help").assertDoesNotExist()

        // A day is answered and saved into the specimen's own memory, and stays on show with its answer and the check.
        compose.onNodeWithTag("${made}_save_check", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("${made}_qn_hours_3").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithTag("${made}_save").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithTag("${made}_qn_hours_3").assertIsSelected()
        compose.onNodeWithTag("${made}_save_check", useUnmergedTree = true).assertExists()

        // The answered day opens on its answers, complete as it stands.
        show("QuestionsScreen, an answered day")
        compose.onNodeWithTag("${made}_qn_rest_2").assertIsSelected()
        compose.onNodeWithTag("${made}_qn_screen_1").assertIsSelected()
        compose.onNodeWithTag("${made}_qn_hours_3").assertIsSelected()
        compose.onNodeWithTag("${made}_save_check", useUnmergedTree = true).assertExists()

        show("QuestionsScreen, nothing answered")
        compose.onNodeWithTag("${made}_save").assertExists()
    }
}
