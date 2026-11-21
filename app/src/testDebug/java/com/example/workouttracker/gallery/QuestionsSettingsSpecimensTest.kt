package com.example.workouttracker.gallery

import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import com.example.workouttracker.app.AppDestination
import com.example.workouttracker.core.ui.theme.WorkoutTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * The gallery shows a questions view's setup at each of its four steps, a made view's page and
 * Eating's on each of their four parts, and the Questions part as a made view's draft changes it
 * and with long fully stored prompts. Each specimen is the real component, opened
 * where its name says.
 */
@RunWith(RobolectricTestRunner::class)
class QuestionsSettingsSpecimensTest {
    @get:Rule
    val compose = createComposeRule()

    private val specimens = (viewSpecimens(AppDestination.SettingsPage) + viewSpecimens(AppDestination.Home) + viewSpecimens(AppDestination.Streak)).associateBy { it.name }
    private var shown by mutableStateOf<GallerySpecimen?>(null)

    private fun show(name: String) {
        shown = specimens.getValue(name)
        compose.waitForIdle()
    }

    private fun click(tag: String) {
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    @Test
    fun theSetupAndThePageOpenWhereTheirNamesSay() {
        compose.setContent { WorkoutTrackerTheme { shown?.let { specimen -> key(specimen.name) { specimen.content() } } } }

        show("QuestionsSetup, step 1 Look")
        compose.onNodeWithTag("questions_setup_tab_look").assertIsSelected().assertTextEquals("1 Look")
        compose.onNodeWithTag("questions_setup_tab_questions").assertTextEquals("2 Questions").assertIsNotEnabled()
        compose.onNodeWithTag("questions_setup_next").assertIsNotEnabled()

        show("QuestionsSetup, step 2 Questions")
        compose.onNodeWithTag("questions_setup_tab_questions").assertIsSelected()
        // A step that is not ready says why only when its button is pressed.
        compose.onNodeWithTag("questions_setup_next").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("questions_setup_hint").assertExists()
        compose.onNodeWithTag("questions_setup_next").assertIsNotEnabled()
        compose.onNodeWithTag("questions_setup_add_question").assertExists()

        show("QuestionsSetup, step 3 Reminders")
        compose.onNodeWithTag("questions_setup_tab_reminders").assertIsSelected().assertTextEquals("3 Reminders")
        compose.onNodeWithTag("questions_setup_reminder_preview_title").assertTextEquals("Sleep")
        compose.onNodeWithTag("questions_setup_next").assertIsEnabled()

        show("QuestionsSetup, step 4 Streak")
        compose.onNodeWithTag("questions_setup_tab_streak").assertIsSelected().assertTextEquals("4 Streak")
        compose.onNodeWithTag("questions_setup_rewards").assertIsOn()
        compose.onNodeWithTag("questions_setup_create").assertIsEnabled()

        show("MadeQuestionsSettingsPage, Look")
        compose.onNodeWithTag("questions_settings_tab_look").assertIsSelected()
        compose.onNodeWithTag("questions_settings_name").assertExists()
        compose.onNodeWithTag("questions_settings_delete").assertIsEnabled()

        show("MadeQuestionsSettingsPage, Questions")
        compose.onNodeWithTag("questions_settings_tab_questions").assertIsSelected()
        compose.onNodeWithTag("questions_settings_question_qn_rest_text").assertExists()
        compose.onNodeWithTag("questions_settings_question_qn_hours_answer_an_h8").assertExists()
        // Five answers are the most, so the last question's button for one more cannot be pressed; the others' can.
        compose.onNodeWithTag("questions_settings_question_qn_hours_add_answer").assertIsNotEnabled()
        compose.onNodeWithTag("questions_settings_question_qn_screen_add_answer").assertIsEnabled()

        show("MadeQuestionsSettingsPage, Reminders")
        compose.onNodeWithTag("questions_settings_reminder_time_after_wake").assertIsSelected()
        compose.onNodeWithTag("questions_settings_reminder_test").assertIsEnabled()

        show("MadeQuestionsSettingsPage, Streak")
        compose.onNodeWithTag("questions_settings_rewards").assertIsOn()
        compose.onNodeWithTag("questions_settings_away_excused").assertIsSelected()
    }

    /** Eating's page, the made view's, opens on each of its four parts, with its stored questions editable, the switch for the streak and Delete, and its Save stores a change. */
    @Test
    fun eatingsPageOpensOnEachOfItsFourParts() {
        compose.setContent { WorkoutTrackerTheme { shown?.let { specimen -> key(specimen.name) { specimen.content() } } } }

        show("MadeQuestionsSettingsPage, Eating, Look")
        compose.onNodeWithTag("questions_settings_tab_look").assertIsSelected()
        compose.onNodeWithTag("questions_settings_icon_food").assertIsSelected()
        compose.onNodeWithTag("questions_settings_delete").assertIsEnabled()
        compose.onNodeWithTag("questions_settings_save").assertIsNotEnabled()

        show("MadeQuestionsSettingsPage, Eating, Questions")
        compose.onNodeWithTag("questions_settings_tab_questions").assertIsSelected().assertTextEquals("Questions")
        compose.onNodeWithTag("questions_settings_question_quality_text").assertIsEnabled()
        compose.onNodeWithTag("questions_settings_about_yesterday").assertIsSelected().assertIsEnabled()
        compose.onNodeWithTag("questions_settings_question_amount_answer_much_less").assertIsEnabled()

        show("MadeQuestionsSettingsPage, Eating, Reminders")
        compose.onNodeWithTag("questions_settings_tab_reminders").assertIsSelected()

        show("MadeQuestionsSettingsPage, Eating, Streak")
        compose.onNodeWithTag("questions_settings_tab_streak").assertIsSelected()
        compose.onNodeWithTag("questions_settings_rewards").assertIsOn()
        compose.onNodeWithTag("questions_settings_max_passes_value").assertTextEquals("3")
        compose.onNodeWithTag("questions_settings_away_due").assertIsSelected()
        click("questions_settings_every_kept_days_less")
        compose.onNodeWithTag("questions_settings_every_kept_days_value").assertTextEquals("6 kept days")
        compose.onNodeWithTag("questions_settings_save").assertIsEnabled()
        click("questions_settings_save")
        compose.onNodeWithTag("questions_settings_every_kept_days_value").assertTextEquals("6 kept days")
        compose.onNodeWithTag("questions_settings_save").assertIsNotEnabled()
    }

    /** Both examples allow edits to stored questions, answers and recorded-day ownership. */
    @Test
    fun bothQuestionExamplesRemainEditable() {
        compose.setContent { WorkoutTrackerTheme { shown?.let { specimen -> key(specimen.name) { specimen.content() } } } }

        show("QuestionsPart, a made view's questions")
        compose.onNodeWithTag("gallery_questions_part_about_today").assertIsSelected()
        compose.onNodeWithTag("gallery_questions_part_question_qn_rest_text").performTextReplacement("")
        compose.onNodeWithTag("gallery_questions_part_question_qn_rest_text").assertTextEquals("Enter the question", "")
        click("gallery_questions_part_add_question")
        click("gallery_questions_part_question_qn_screen_add_answer")
        compose.onNodeWithTag("gallery_questions_part_question_qn_screen_remove").assertIsEnabled()

        show("QuestionsPart, long custom prompts")
        compose.onNodeWithTag("gallery_questions_part_long_question_quality_text").assertIsEnabled()
        compose.onNodeWithTag("gallery_questions_part_long_about_yesterday").assertIsSelected().assertIsEnabled()
        compose.onNodeWithTag("gallery_questions_part_long_question_quality_text").assertIsEnabled()
        compose.onNodeWithTag("gallery_questions_part_long_question_amount_answer_much_more").assertIsEnabled()
        compose.onNodeWithTag("gallery_questions_part_long_question_quality_remove").assertIsEnabled()
        compose.onNodeWithTag("gallery_questions_part_long_add_question").assertIsEnabled()
    }

    /** Long stored prompts stay editable, as the same component does for short prompts. */
    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun aLongCustomQuestionCanBeReadAndEdited() {
        compose.setContent { WorkoutTrackerTheme { shown?.let { specimen -> key(specimen.name) { specimen.content() } } } }
        show("QuestionsPart, long custom prompts")
        compose.onNodeWithTag("gallery_questions_part_long_question_quality_text")
            .assertIsEnabled()
            .performTextReplacement("How was your day?")
        compose.onNodeWithTag("gallery_questions_part_long_question_quality_text").assertTextEquals("Question", "How was your day?")
    }

    @Test
    fun aJoinedViewHasItsTileAndItsBank() {
        compose.setContent { WorkoutTrackerTheme { shown?.let { specimen -> key(specimen.name) { specimen.content() } } } }

        show("HomeScreen, a questions view that joined")
        compose.onNodeWithTag("home_questions_${DemoSleepQuestions.id}").assertExists()

        show("MoStreakScreen, a questions view that joined")
        compose.onNodeWithTag("streak_questions_${DemoSleepQuestions.id}").assertExists()
    }
}
