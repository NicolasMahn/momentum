package de.nicolasmahn.momentum.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import de.nicolasmahn.momentum.core.time.currentHabitDate
import de.nicolasmahn.momentum.core.ui.theme.WorkoutTrackerTheme
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.checklist.AboutDay
import de.nicolasmahn.momentum.domain.checklist.RewardPeriod
import de.nicolasmahn.momentum.domain.measurement.withWeight
import de.nicolasmahn.momentum.domain.questions.Answer
import de.nicolasmahn.momentum.domain.questions.QuestionEntry
import de.nicolasmahn.momentum.domain.questions.QuestionsConfiguration
import de.nicolasmahn.momentum.domain.compatibility.madeQuestions
import de.nicolasmahn.momentum.domain.questions.testMadeQuestions
import de.nicolasmahn.momentum.domain.questions.testQuestion
import de.nicolasmahn.momentum.domain.questions.withEating
import de.nicolasmahn.momentum.domain.settings.FeatureVisibilitySettings
import de.nicolasmahn.momentum.feature.weight.selectedHistoryDay
import de.nicolasmahn.momentum.feature.weight.transcript
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import de.nicolasmahn.momentum.domain.time.withReading
import de.nicolasmahn.momentum.domain.activity.withActivity
import de.nicolasmahn.momentum.domain.LoggedViewKind
import de.nicolasmahn.momentum.domain.LoggedViewRef

/**
 * A questions view is made, changed, hidden and deleted through the app as a person meets it: the
 * third card under "Track something new", four numbered steps, a page with four parts over one
 * Save, the hide switch on its row and Delete with its answers.
 */
@RunWith(RobolectricTestRunner::class)
class MadeQuestionsSettingsTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = currentHabitDate()
    private var state by mutableStateOf(AppState().withWeight().withEating().withReading().withActivity())
    private var visibility by mutableStateOf(FeatureVisibilitySettings())

    @Composable
    private fun App() {
        WorkoutTrackerTheme {
            MomentumApp(
                state = state,
                onStateChange = { state = it; true },
                onActivitySave = ActivitySaveNotExpected,
                featureVisibility = visibility,
                onFeatureVisibilityChange = { visibility = it; true },
            )
        }
    }

    private fun click(tag: String) {
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private fun type(tag: String, text: String) {
        compose.onNodeWithTag(tag).performTextReplacement(text)
        compose.waitForIdle()
    }

    private fun line(tag: String): String = compose.transcript().single { it.startsWith("[$tag]") }

    private fun openSetup() {
        click("home_settings")
        click("settings_new")
        click("settings_questions_new")
    }

    /** The one question a setup starts with, read from the field its text is typed in. */
    private fun starterQuestionTag(): String =
        compose.transcript().single { it.startsWith("[questions_setup_question_") && "_text]" in it }.substringAfter("[").substringBefore("_text]")

    private fun made(): QuestionsConfiguration = state.madeQuestions.single()

    @Test
    fun aQuestionsViewIsMadeInFourStepsAndHasItsRowItsTileAndItsScreen() {
        compose.setContent { App() }
        openSetup()

        compose.onNodeWithTag("questions_setup_tab_look").assertTextEquals("1 Look").assertIsSelected()
        compose.onNodeWithTag("questions_setup_tab_questions").assertTextEquals("2 Questions").assertIsNotEnabled()
        compose.onNodeWithTag("questions_setup_tab_reminders").assertTextEquals("3 Reminders")
        compose.onNodeWithTag("questions_setup_tab_streak").assertTextEquals("4 Streak")
        compose.onNodeWithTag("questions_setup_next").assertIsNotEnabled()
        type("questions_setup_name", "Sleep")
        click("questions_setup_next")

        // Step 2: about today, and one question with the five default answers and no text yet, which the step waits for.
        compose.onNodeWithTag("questions_setup_tab_questions").assertIsSelected()
        compose.onNodeWithTag("questions_setup_about_today").assertIsSelected()
        val question = starterQuestionTag()
        assertEquals("[${question}_text] field=\"\"", line("${question}_text"))
        assertEquals(
            listOf("Very bad", "Bad", "Okay", "Good", "Very good"),
            compose.transcript().filter { it.startsWith("[${question}_answer_") && "field=" in it }.map { it.substringAfter("field=\"").substringBefore("\"") },
        )
        compose.onNodeWithTag("questions_setup_next").assertIsNotEnabled()
        // A step that is not ready says why only when its button is pressed.
        compose.onNodeWithTag("questions_setup_next").performClick()
        compose.waitForIdle()
        assertEquals("[questions_setup_hint] text=\"Give every question and answer its text to continue\"", line("questions_setup_hint"))
        compose.onNodeWithTag("${question}_remove").assertIsNotEnabled()

        type("${question}_text", "How rested do you feel?")
        compose.onNodeWithTag("questions_setup_hint").assertDoesNotExist()
        click("questions_setup_about_yesterday")
        click("questions_setup_next")

        // Step 3: the reminder is a made measurement's, off at 09:00. Made today about yesterday, today asks about no day.
        compose.onNodeWithTag("questions_setup_tab_reminders").assertIsSelected()
        compose.onNodeWithTag("questions_setup_reminder").assertIsOff()
        compose.onNodeWithTag("questions_setup_reminder_preview_title").assertDoesNotExist()
        compose.onNodeWithTag("questions_setup_reminder_test").assertIsNotEnabled()
        click("questions_setup_next")

        // Step 4: the streak, and Create.
        compose.onNodeWithTag("questions_setup_tab_streak").assertIsSelected()
        click("questions_setup_rewards")
        compose.onNodeWithTag("questions_setup_create").assertIsEnabled()
        assertEquals(emptyList<QuestionsConfiguration>(), state.madeQuestions)
        click("questions_setup_create")

        val view = made()
        assertEquals("Sleep", view.label)
        assertEquals("How rested do you feel?", view.questions.single().text)
        assertEquals(listOf("Very bad", "Bad", "Okay", "Good", "Very good"), view.questions.single().answers.map { it.label })
        assertEquals(AboutDay.Yesterday, view.about.on(today))
        assertEquals(today, view.madeOn)
        assertEquals(listOf(RewardPeriod(today)), view.rewards?.periods)

        // The setup closed onto the list, where the view has its row with the hide switch.
        compose.onNodeWithTag("questions_setup").assertDoesNotExist()
        compose.onNodeWithTag("settings_group_questions_${view.id}").assertExists()
        compose.onNodeWithTag("settings_feature_questions_${view.id}").assertIsOn()

        // Its tile opens its screen on today, since yesterday is before it was made.
        click("top_back")
        click("home_questions_${view.id}")
        assertTrue(compose.transcript().toString(), "text=\"How rested do you feel?\"" in compose.transcript())
        assertEquals(today.toString(), compose.transcript().selectedHistoryDay("questions_${view.id}"))
        compose.onNodeWithTag("questions_${view.id}_selected_day").assertDoesNotExist()
    }

    /** A setup under way survives process death with its ID, its step and what was typed, and creates the view once. */
    @Test
    fun aSetupUnderWaySurvivesProcessDeath() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { App() }
        openSetup()
        type("questions_setup_name", "Sleep")
        click("questions_setup_next")
        val question = starterQuestionTag()
        type("${question}_text", "How rested do you feel?")

        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()

        compose.onNodeWithTag("questions_setup_tab_questions").assertIsSelected()
        assertEquals("[${question}_text] field=\"How rested do you feel?\"", line("${question}_text"))
        click("questions_setup_next")
        click("questions_setup_next")
        click("questions_setup_create")
        assertEquals(question.removePrefix("questions_setup_question_"), made().questions.single().id)
        assertEquals("Sleep", made().label)
    }

    private val sleep = testMadeQuestions(
        questions = listOf(testQuestion(id = "qn_rest", answers = 3), testQuestion(id = "qn_mood", text = "How was your mood?", answers = 2)),
        madeOn = today.minusDays(30),
    )
    private val answered = listOf(
        QuestionEntry("s2", sleep.id, today.minusDays(1).toString(), 2L, mapOf("qn_rest" to "a1", "qn_mood" to "a2")),
        QuestionEntry("s1", sleep.id, today.minusDays(2).toString(), 1L, mapOf("qn_rest" to "a3")),
        QuestionEntry("e1", "eating", today.minusDays(2).toString(), 1L, mapOf("quality" to "good")),
    )

    /**
     * The page has four parts over one Save: a rename, a question added with its text, an answer
     * removed and the day asked about are stored together. Save waits while the new question has
     * no text. A removed answer rewrites no entry.
     */
    @Test
    fun thePageChangesTheViewOverOneSave() {
        state = AppState(questionEntries = answered).withWeight().withEating().withReading().withActivity().let { it.copy(questions = it.questions + sleep) }
        compose.setContent { App() }
        click("home_settings")
        click("settings_group_questions_${sleep.id}")

        compose.onNodeWithTag("questions_settings_tab_look").assertIsSelected()
        compose.onNodeWithTag("questions_settings_save").assertIsNotEnabled()
        type("questions_settings_name", "Nights")

        click("questions_settings_tab_questions")
        assertEquals("[questions_settings_question_qn_rest_text] field=\"How rested do you feel?\"", line("questions_settings_question_qn_rest_text"))
        // Two answers stay: the mood's cannot be removed, the first of the rest's three can, on the second tap.
        compose.onNodeWithTag("questions_settings_question_qn_mood_answer_a1_remove").assertIsNotEnabled()
        click("questions_settings_question_qn_rest_answer_a1_remove")
        assertEquals(3, state.madeQuestions.single().questions.first().answers.size)
        click("questions_settings_question_qn_rest_answer_a1_remove")
        compose.onNodeWithTag("questions_settings_question_qn_rest_answer_a1").assertDoesNotExist()
        click("questions_settings_about_yesterday")

        click("questions_settings_add_question")
        val added = compose.transcript().single { it.startsWith("[questions_settings_question_") && "_text] field=\"\"" in it }.substringAfter("[").substringBefore("_text]")
        compose.onNodeWithTag("questions_settings_save").assertIsNotEnabled()
        // A step that is not ready says why only when its button is pressed.
        compose.onNodeWithTag("questions_settings_save").performClick()
        compose.waitForIdle()
        assertEquals("[questions_settings_hint] text=\"Give every question and answer its text to save\"", line("questions_settings_hint"))
        type("${added}_text", "Energy?")
        compose.onNodeWithTag("questions_settings_hint").assertDoesNotExist()

        compose.onNodeWithTag("questions_settings_save").assertIsEnabled()
        assertEquals(sleep, made())
        click("questions_settings_save")

        val saved = made()
        assertEquals("Nights", saved.label)
        assertEquals(listOf("How rested do you feel?", "How was your mood?", "Energy?"), saved.questions.map { it.text })
        assertEquals(listOf(Answer("a2", "Level 2"), Answer("a3", "Level 3")), saved.questions.first().answers)
        assertEquals(AboutDay.Yesterday, saved.about.on(today))
        // Saved, the page returned to the list; the entries are as they were, the removed answer's too.
        compose.onNodeWithTag("questions_settings").assertDoesNotExist()
        assertEquals(answered, state.questionEntries)
    }

    /** Hidden, the view has no tile and keeps its page; shown again it is back. Deleted, it goes with every day's answers and Eating's stay. */
    @Test
    fun theViewIsHiddenShownAndDeletedWithItsAnswers() {
        state = AppState(questionEntries = answered).withWeight().withEating().withReading().withActivity().let { it.copy(questions = it.questions + sleep) }
        compose.setContent { App() }
        compose.onNodeWithTag("home_questions_${sleep.id}").assertExists()

        click("home_settings")
        click("settings_feature_questions_${sleep.id}")
        assertEquals(FeatureVisibilitySettings().withVisibility(LoggedViewRef(LoggedViewKind.Questions, sleep.id), visible = false, today = currentHabitDate()), visibility)
        click("top_back")
        compose.onNodeWithTag("home_questions_${sleep.id}").assertDoesNotExist()

        click("home_settings")
        click("settings_group_questions_${sleep.id}")
        compose.onNodeWithTag("questions_settings").assertExists()
        click("top_back")
        click("settings_feature_questions_${sleep.id}")
        assertEquals(FeatureVisibilitySettings(), visibility)

        click("settings_group_questions_${sleep.id}")
        click("questions_settings_delete")
        val dialog = compose.transcript()
        assertTrue(dialog.toString(), "text=\"Delete Sleep?\"" in dialog && "text=\"Its settings and 2 answered days are deleted. This cannot be undone.\"" in dialog)
        click("questions_settings_delete_cancel")
        assertEquals(sleep, made())
        click("questions_settings_delete")
        click("questions_settings_delete_confirm")

        assertEquals(emptyList<QuestionsConfiguration>(), state.madeQuestions)
        assertEquals(answered.filter { it.viewId == "eating" }, state.questionEntries)
        compose.onNodeWithTag("questions_settings").assertDoesNotExist()
        compose.onNodeWithTag("settings_group_questions_${sleep.id}").assertDoesNotExist()
        compose.onNodeWithTag("settings_new").assertExists()
    }

    /** The setup speaks German, and the five default answers are named in the app's language as the view is made: they are the person's text from then on. */
    @Test
    @Config(qualifiers = "de")
    fun theSetupSpeaksGermanAndNamesTheDefaultAnswersInIt() {
        compose.setContent { App() }
        openSetup()
        compose.onNodeWithTag("questions_setup_tab_questions").assertTextEquals("2 Fragen")
        type("questions_setup_name", "Schlaf")
        click("questions_setup_next")
        val question = starterQuestionTag()
        // A step that is not ready says why only when its button is pressed.
        compose.onNodeWithTag("questions_setup_next").performClick()
        compose.waitForIdle()
        assertEquals("[questions_setup_hint] text=\"Gib jeder Frage und jeder Antwort einen Text, um weiterzugehen\"", line("questions_setup_hint"))
        type("${question}_text", "Wie erholt bist du?")
        click("questions_setup_next")
        click("questions_setup_next")
        click("questions_setup_create")

        assertEquals(listOf("Sehr schlecht", "Schlecht", "Okay", "Gut", "Sehr gut"), made().questions.single().answers.map { it.label })
        assertNull(made().rewards)
        assertFalse(made().checkpoint?.remind ?: true)
    }
}
