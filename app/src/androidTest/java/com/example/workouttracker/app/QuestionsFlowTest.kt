package com.example.workouttracker.app

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.example.workouttracker.R
import com.example.workouttracker.TestEatingQuestions
import com.example.workouttracker.TestWeightMeasurement
import com.example.workouttracker.core.time.currentDateKey
import com.example.workouttracker.core.ui.theme.WorkoutTrackerTheme
import com.example.workouttracker.domain.mo.TrackerMoPolicy
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.LoggedViewKind
import com.example.workouttracker.domain.LoggedViewRef
import com.example.workouttracker.domain.checklist.AboutDay
import com.example.workouttracker.domain.checklist.Dated
import com.example.workouttracker.domain.checklist.RewardPeriod
import com.example.workouttracker.domain.checklist.StarterDayCheckpoint
import com.example.workouttracker.domain.checklist.ViewHue
import com.example.workouttracker.domain.checklist.ViewIcon
import com.example.workouttracker.domain.questions.Answer
import com.example.workouttracker.domain.questions.MadeQuestionsWhenAway
import com.example.workouttracker.domain.questions.Question
import com.example.workouttracker.domain.questions.QuestionEntry
import com.example.workouttracker.domain.questions.QuestionsConfiguration
import com.example.workouttracker.domain.questions.QuestionsEdit
import com.example.workouttracker.domain.questions.applyingQuestionsEdits
import com.example.workouttracker.testfixtures.madeQuestions
import com.example.workouttracker.domain.settings.FeatureVisibilitySettings
import com.example.workouttracker.domain.ref
import com.example.workouttracker.reminder.ChecklistReminderRequestCode
import com.example.workouttracker.reminder.ReminderNotificationChannel
import com.example.workouttracker.reminder.ReminderViewKind
import com.example.workouttracker.reminder.slotKey
import com.example.workouttracker.testing.appString
import com.example.workouttracker.testing.assertInputEquals
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * A questions view a person makes, through the app as a person meets it: made in Settings in four
 * steps, answered, changed and removed on its own screen beside Eating, changed on its page,
 * hidden and shown again, in the streak with its socket and its coin, reminded of, and deleted
 * with its answers. The stored state is the proof, and Eating's entries are read before and after.
 */
class QuestionsFlowTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val today = currentDateKey()
    private val yesterday = LocalDate.parse(today).minusDays(1).toString()
    private val sleep = madeQuestions("qst_s1ee", "Sleep", AboutDay.Today)
    private val evening = madeQuestions("qst_eve1", "Evening", AboutDay.Yesterday)
    private val eatings = QuestionEntry("eating-y", "eating", yesterday, 1L, mapOf("quality" to "good"))

    private var state by mutableStateOf(AppState(measurements = listOf(TestWeightMeasurement), questions = listOf(TestEatingQuestions), questionEntries = listOf(eatings)))
    private var visibility by mutableStateOf(FeatureVisibilitySettings())

    /** What a tap on a notification asks for, as the activity hands it to the app. */
    private var request by mutableStateOf<AppNavigationRequest?>(null)

    /** A view made a month ago, with a question of three answers that has a help text and one of two. */
    private fun madeQuestions(id: String, label: String, about: AboutDay) = QuestionsConfiguration(
        id = id,
        label = label,
        icon = ViewIcon.Sleep,
        hue = ViewHue.Navy,
        questions = listOf(
            Question("qn_rest", "How rested do you feel?", listOf(Answer("an_low", "Not at all"), Answer("an_mid", "Somewhat"), Answer("an_high", "Fully")), help = "Think of the whole morning."),
            Question("qn_screen", "Phone away before bed?", listOf(Answer("an_no", "No"), Answer("an_yes", "Yes"))),
        ),
        about = Dated.always(about),
        rewards = null,
        whenAway = MadeQuestionsWhenAway,
        checkpoint = StarterDayCheckpoint,
        madeOn = LocalDate.parse(today).minusDays(30),
    )

    /** The app over [state], which every save, creation and deletion is decided against, with the switches of the Settings list kept in [visibility]. */
    private fun setApp() {
        composeRule.setContent { WorkoutTrackerTheme { App() } }
    }

    @Composable
    private fun App() {
        MomentumApp(
            state = state,
            onStateChange = { state = it; true },
            onActivitySave = ActivitySaveNotExpected,
            featureVisibility = visibility,
            onFeatureVisibilityChange = { visibility = it; true },
            requestedNavigation = request,
            onDestinationConsumed = { request = null },
        )
    }

    /** A tile of Home, brought into sight first where Home had to scroll to fit its tiles. */
    private fun tile(tag: String): SemanticsNodeInteraction {
        val tile = composeRule.onNodeWithTag(tag)
        if (!tile.isDisplayed()) tile.performScrollTo()
        return tile
    }

    private fun tileNamed(tag: String, name: String) = composeRule.onNode(hasTestTag(tag) and hasAnyDescendant(hasText(name)), useUnmergedTree = true)

    /** From the Settings list, which Create, Save and Delete return to. */
    private fun openPage(view: QuestionsConfiguration) {
        composeRule.onNodeWithTag("settings_group_questions_${view.id}").performScrollTo().performClick()
        composeRule.onNodeWithTag("questions_settings").assertIsDisplayed()
    }

    /** A part of a page or a step of a setup, which sits in the page's scroll; the bar's buttons are pinned and pressed directly. */
    private fun openPart(tag: String) {
        composeRule.onNodeWithTag(tag).performScrollTo().performClick()
        composeRule.onNodeWithTag(tag).assertIsSelected()
    }

    private fun press(tag: String) = composeRule.onNodeWithTag(tag).performScrollTo().performClick()

    private val refused = SemanticsMatcher.keyIsDefined(SemanticsProperties.Error)

    /** A tile in the streak says where today stands; one outside it says nothing. */
    private val status = SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription)

    private fun assertTileStatus(tag: String, description: String) {
        composeRule.onNode(hasContentDescription(description) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true).assertExists()
    }

    /** The tags of the questions on show on the page or the setup tagged [prefix], read from the fields their texts are typed in. */
    private fun questionTags(prefix: String): List<String> = composeRule
        .onAllNodes(SemanticsMatcher("a question's text field") { node -> node.config.getOrNull(SemanticsProperties.TestTag)?.let { it.startsWith("${prefix}_question_") && it.endsWith("_text") } == true })
        .fetchSemanticsNodes()
        .map { it.config[SemanticsProperties.TestTag].removeSuffix("_text") }

    private fun entriesOf(view: QuestionsConfiguration) = state.questionEntries.filter { it.viewId == view.id }.map { it.dateKey to it.answers }

    /**
     * "Track something new" has a third card, Questions, whose setup walks Look, Questions,
     * Reminders and Streak. Look waits for a name; Questions for the text of its one question,
     * which starts with five answers; Create, on the last step, stores the view and nothing else.
     * Its row is in the Settings list with its switch on, and its tile on Home opens its screen.
     */
    @Test
    fun aQuestionsViewIsMadeInFourStepsAndHasItsRowAndItsTile() {
        setApp()
        tile("home_settings").performClick()
        press("settings_new")
        tileNamed("settings_questions_new", appString(R.string.questions_destination)).assertIsDisplayed()
        tileNamed("settings_questions_new", appString(R.string.settings_new_questions_description)).assertIsDisplayed()
        composeRule.onNodeWithTag("settings_questions_new").performClick()
        composeRule.onNodeWithTag("questions_setup").assertIsDisplayed()

        fun step(number: Int, part: Int) = appString(R.string.checklist_setup_step, number, appString(part))
        composeRule.onNodeWithTag("questions_setup_tab_look").performScrollTo().assertIsSelected().assertTextEquals(step(1, R.string.checklist_editor_tab_look))
        composeRule.onNodeWithTag("questions_setup_tab_questions").assertIsNotEnabled().assertTextEquals(step(2, R.string.questions_settings_questions))
        composeRule.onNodeWithTag("questions_setup_tab_reminders").assertIsNotEnabled().assertTextEquals(step(3, R.string.checklist_editor_tab_reminders))
        composeRule.onNodeWithTag("questions_setup_tab_streak").assertIsNotEnabled().assertTextEquals(step(4, R.string.checklist_editor_rewards))
        composeRule.onNodeWithTag("questions_setup_delete").assertDoesNotExist()
        composeRule.onNodeWithTag("questions_setup_next").assertIsNotEnabled()
        composeRule.onNodeWithTag("questions_setup_name").performScrollTo().assert(!refused).performTextReplacement("Sleep")
        composeRule.onNodeWithTag("questions_setup_hue_teal").performScrollTo().performClick()
        Espresso.closeSoftKeyboard()
        composeRule.onNodeWithTag("questions_setup_next").assertIsEnabled().performClick()

        // Step 2: one question without its text, which nobody is told before typing, and Next waits with the bar saying why.
        composeRule.onNodeWithTag("questions_setup_tab_questions").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("questions_setup_about_today").performScrollTo().assertIsSelected()
        val question = questionTags("questions_setup").single()
        composeRule.onNodeWithTag("${question}_text").performScrollTo().assertInputEquals("").assert(!refused)
        composeRule.onNodeWithTag("questions_setup_next").assertIsNotEnabled()
        // A step that is not ready says why only when its button is pressed.
        composeRule.onNodeWithTag("questions_setup_next").performClick()
        composeRule.onNodeWithTag("questions_setup_hint").assertTextEquals(appString(R.string.questions_setup_text_needed))
        composeRule.onNodeWithTag("${question}_remove").assertIsNotEnabled()
        // The starter question has its five answers, so the button for one more is there and cannot be pressed.
        composeRule.onNodeWithTag("${question}_add_answer").assertIsNotEnabled()

        composeRule.onNodeWithTag("${question}_text").performTextReplacement("How rested do you feel?")
        composeRule.onNodeWithTag("questions_setup_hint").assertDoesNotExist()
        // Emptied again the field says what is missing, and Next waits again.
        composeRule.onNodeWithTag("${question}_text").performTextReplacement("")
        composeRule.onNodeWithTag("${question}_text").assert(refused)
        composeRule.onNodeWithTag("questions_setup_next").assertIsNotEnabled()
        composeRule.onNodeWithTag("${question}_text").performTextReplacement("How rested do you feel?")

        // A second question is added and removed again with two taps; nothing is stored before Create.
        Espresso.closeSoftKeyboard()
        press("questions_setup_add_question")
        composeRule.onNodeWithTag("questions_setup_next").assertIsNotEnabled()
        composeRule.onNodeWithTag("${question}_remove").performScrollTo().assertIsEnabled()
        val added = questionTags("questions_setup").single { it != question }
        press("${added}_remove")
        composeRule.onNodeWithTag("${added}_text").assertExists()
        press("${added}_remove")
        composeRule.onNodeWithTag("${added}_text").assertDoesNotExist()
        composeRule.runOnIdle { assertTrue(state.madeQuestions.isEmpty()) }
        composeRule.onNodeWithTag("questions_setup_next").assertIsEnabled().performClick()

        // Step 3 is a made measurement's reminder, previewed under the name just typed; step 4 creates.
        composeRule.onNodeWithTag("questions_setup_tab_reminders").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("questions_setup_reminder").performScrollTo().assertIsOff()
        composeRule.onNodeWithTag("questions_setup_reminder_preview_title").performScrollTo().assertTextEquals("Sleep")
        composeRule.onNodeWithTag("questions_setup_reminder_preview_text").assertTextEquals(appString(R.string.questions_reminder_open))
        composeRule.onNodeWithTag("questions_setup_next").performClick()
        composeRule.onNodeWithTag("questions_setup_tab_streak").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("questions_setup_rewards").performScrollTo().assertIsOff().performClick()
        composeRule.onNodeWithTag("questions_setup_create").assertIsEnabled().performClick()

        val made = composeRule.runOnIdle { state.madeQuestions.single() }
        composeRule.runOnIdle {
            assertTrue(made.id, made.id.matches(Regex("qst_[a-z2-9]{4}")))
            assertEquals("Sleep" to ViewHue.Teal, made.label to made.hue)
            assertEquals("How rested do you feel?", made.questions.single().text)
            assertEquals((1..5).map { appString(resourceOfDefaultAnswer(it)) }, made.questions.single().answers.map { it.label })
            assertEquals(LocalDate.parse(today), made.madeOn)
            assertEquals(listOf(RewardPeriod(LocalDate.parse(today))), made.rewards?.periods)
            assertTrue(!made.mo.enabled)
            assertEquals(listOf(eatings), state.questionEntries)
        }

        // Rewards invent no first record, and the streak asks for the view from today, so its tile says so; it opens its stored questions.
        composeRule.onNodeWithTag("questions_setup").assertDoesNotExist()
        tileNamed("settings_group_questions_${made.id}", "Sleep").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("settings_feature_questions_${made.id}").performScrollTo().assertIsOn()
        composeRule.onNodeWithTag("top_back").performClick()
        tileNamed("home_questions_${made.id}", "Sleep").assertExists()
        assertTileStatus("home_questions_${made.id}", appString(R.string.app_home_task_open))
        tile("home_questions_${made.id}").performClick()
        composeRule.onNodeWithText("How rested do you feel?").assertIsDisplayed()
        composeRule.onNodeWithTag("questions_${made.id}_history_$today").assertIsSelected()
    }

    private fun resourceOfDefaultAnswer(place: Int): Int = listOf(
        R.string.questions_answer_default_1,
        R.string.questions_answer_default_2,
        R.string.questions_answer_default_3,
        R.string.questions_answer_default_4,
        R.string.questions_answer_default_5,
    )[place - 1]

    /**
     * A day is answered on the view's screen, stays on show as it was saved, and is changed and
     * removed, each stored under the view's ID for the day it is about. A view about yesterday
     * opens on yesterday. Eating's entries are as they were, and Eating's screen opens on its own
     * reflection and shows nothing of the view.
     */
    @Test
    fun aDayIsAnsweredChangedAndRemovedAndEatingSeesNothingOfIt() {
        state = state.copy(questions = state.questions + sleep + evening)
        setApp()
        tile("home_questions_${sleep.id}").performClick()
        val screen = "questions_${sleep.id}"

        composeRule.onNodeWithTag("${screen}_history_$today").assertIsSelected()
        composeRule.onNodeWithTag("${screen}_qn_rest_help").assertExists()
        press("${screen}_qn_rest_2")
        press("${screen}_qn_screen_not_sure")
        press("${screen}_save")
        composeRule.runOnIdle { assertEquals(listOf(today to mapOf("qn_rest" to "an_high")), entriesOf(sleep)) }

        // The saved day stays on show with its answers and the lasting check, and Save has nothing left to do.
        composeRule.onNodeWithTag("${screen}_history_$today").assertIsSelected()
        composeRule.onNodeWithTag("${screen}_qn_rest_2").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("${screen}_qn_screen_not_sure").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("${screen}_save").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithTag("${screen}_save_check", useUnmergedTree = true).assertIsDisplayed()

        // A change makes Save the action again; saved, it is the day's one entry, and two taps remove it.
        press("${screen}_qn_screen_1")
        composeRule.onNodeWithTag("${screen}_save").performScrollTo().assertIsEnabled()
        composeRule.onNodeWithTag("${screen}_save_check", useUnmergedTree = true).assertDoesNotExist()
        press("${screen}_save")
        composeRule.runOnIdle { assertEquals(listOf(today to mapOf("qn_rest" to "an_high", "qn_screen" to "an_yes")), entriesOf(sleep)) }
        composeRule.onNodeWithTag("${screen}_save").performScrollTo().assertIsNotEnabled()
        press("${screen}_delete")
        composeRule.runOnIdle { assertEquals(1, entriesOf(sleep).size) }
        press("${screen}_delete")
        composeRule.runOnIdle {
            assertTrue(entriesOf(sleep).isEmpty())
            assertEquals(listOf(eatings), state.questionEntries)
        }
        // The day stays selected, with nothing picked.
        composeRule.onNodeWithTag("${screen}_history_$today").assertIsSelected()
        composeRule.onNodeWithTag("${screen}_qn_rest_2").performScrollTo().assertIsNotSelected()
        composeRule.onNodeWithTag("${screen}_delete").performScrollTo().assertIsNotEnabled()

        // Left and opened again, an answered day is shown as it is stored.
        press("${screen}_qn_rest_0")
        press("${screen}_save")
        composeRule.onNodeWithTag("top_back").performClick()
        tile("home_questions_${sleep.id}").performClick()
        composeRule.onNodeWithTag("${screen}_qn_rest_0").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("${screen}_save").performScrollTo().assertIsNotEnabled()
        press("${screen}_delete")
        press("${screen}_delete")
        composeRule.runOnIdle { assertTrue(entriesOf(sleep).isEmpty()) }

        // The view about yesterday opens on yesterday and stores its answers for that day.
        composeRule.onNodeWithTag("top_back").performClick()
        tile("home_questions_${evening.id}").performClick()
        composeRule.onNodeWithTag("questions_${evening.id}_history_$yesterday").assertIsSelected()
        press("questions_${evening.id}_qn_rest_0")
        press("questions_${evening.id}_save")
        composeRule.runOnIdle { assertEquals(listOf(yesterday to mapOf("qn_rest" to "an_low")), entriesOf(evening)) }

        // Eating's screen is Eating's: its own questions, open on yesterday's reflection as it was.
        composeRule.onNodeWithTag("top_back").performClick()
        tile("home_questions_eating").performClick()
        composeRule.onNodeWithText("How rested do you feel?").assertDoesNotExist()
        composeRule.onNodeWithTag("questions_eating_history_$yesterday").assertIsSelected()
        composeRule.onNodeWithTag("questions_eating_quality_2").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("questions_eating_save").performScrollTo().assertIsNotEnabled()
    }

    /**
     * Its page has four parts over one Save. Renamed, with a question added and an answer removed,
     * it is stored so on Save, which waits while the new question has no text and returns to the
     * list. The entries are as they were, and its screen asks the new question.
     */
    @Test
    fun itsPageRenamesItAddsAQuestionAndRemovesAnAnswerOverOneSave() {
        val answered = QuestionEntry("s1", sleep.id, yesterday, 1L, mapOf("qn_rest" to "an_low", "qn_screen" to "an_yes"))
        state = state.copy(questions = state.questions + sleep, questionEntries = listOf(answered, eatings))
        setApp()
        tile("home_settings").performClick()
        openPage(sleep)

        composeRule.onNodeWithTag("questions_settings_tab_look").assertIsSelected()
        composeRule.onNodeWithTag("questions_settings_save").assertIsNotEnabled()
        composeRule.onNodeWithTag("questions_settings_delete").assertIsEnabled()
        composeRule.onNodeWithTag("questions_settings_name").performScrollTo().performTextReplacement("Nights")
        Espresso.closeSoftKeyboard()

        openPart("questions_settings_tab_questions")
        composeRule.onNodeWithTag("questions_settings_question_qn_rest_text").performScrollTo().assertInputEquals("How rested do you feel?")
        // A question keeps two answers: the second question's cannot be removed, the first's can.
        composeRule.onNodeWithTag("questions_settings_question_qn_screen_answer_an_no_remove").performScrollTo().assertIsNotEnabled()
        press("questions_settings_question_qn_rest_answer_an_low_remove")
        press("questions_settings_question_qn_rest_answer_an_low_remove")
        composeRule.onNodeWithTag("questions_settings_question_qn_rest_answer_an_low").assertDoesNotExist()
        composeRule.onNodeWithTag("questions_settings_question_qn_rest_answer_an_mid").performScrollTo().performTextReplacement("A little")
        Espresso.closeSoftKeyboard()

        press("questions_settings_add_question")
        composeRule.onNodeWithTag("questions_settings_save").assertIsNotEnabled()
        // A step that is not ready says why only when its button is pressed.
        composeRule.onNodeWithTag("questions_settings_save").performClick()
        composeRule.onNodeWithTag("questions_settings_hint").assertTextEquals(appString(R.string.questions_settings_text_needed))
        val added = questionTags("questions_settings").single { !it.endsWith("qn_rest") && !it.endsWith("qn_screen") }
        composeRule.onNodeWithTag("${added}_text").performScrollTo().performTextReplacement("Energy?")
        Espresso.closeSoftKeyboard()
        composeRule.runOnIdle { assertEquals(listOf(sleep), state.madeQuestions) }

        // Leaving asks, as the changes would be lost; staying keeps them.
        composeRule.onNodeWithTag("top_back").performClick()
        composeRule.onNodeWithTag("questions_settings_discard_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("questions_settings_discard_dialog_stay").performClick()

        composeRule.onNodeWithTag("questions_settings_save").assertIsEnabled().performClick()
        val saved = composeRule.runOnIdle { state.madeQuestions.single() }
        composeRule.runOnIdle {
            assertEquals("Nights", saved.label)
            assertEquals(listOf("How rested do you feel?", "Phone away before bed?", "Energy?"), saved.questions.map { it.text })
            assertEquals(listOf(Answer("an_mid", "A little"), Answer("an_high", "Fully")), saved.questions.first().answers)
            // A removed answer rewrites no entry.
            assertEquals(listOf(answered, eatings), state.questionEntries)
        }

        composeRule.onNodeWithTag("questions_settings").assertDoesNotExist()
        tileNamed("settings_group_questions_${sleep.id}", "Nights").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("top_back").performClick()
        tileNamed("home_questions_${sleep.id}", "Nights").assertExists()
        tile("home_questions_${sleep.id}").performClick()
        composeRule.onNodeWithText("Energy?").performScrollTo().assertIsDisplayed()
        // Yesterday's entry names the removed answer, which is no longer shown: the question reads as not answered.
        press("questions_${sleep.id}_history_$yesterday")
        composeRule.onNodeWithTag("questions_${sleep.id}_qn_screen_1").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("questions_${sleep.id}_qn_rest_not_sure").performScrollTo().assertIsSelected()
    }

    /**
     * Counting toward the streak, a tile wears the socket until the day today asks about is
     * answered and the coin from then on: today for the view about today, yesterday for the one
     * about yesterday. The token page has a row for each.
     */
    @Test
    fun inTheStreakItsTileWearsTheSocketThenTheCoinAndTheTokenPageHasItsRow() {
        val joined = LocalDate.parse(today).minusDays(1)
        val previousEvening = LocalDate.parse(yesterday).minusDays(1).toString()
        val prior = listOf(
            QuestionEntry("sleep-prior", sleep.id, yesterday, 2L, mapOf("qn_rest" to "an_mid")),
            QuestionEntry("evening-prior", evening.id, previousEvening, 3L, mapOf("qn_screen" to "an_yes")),
        )
        state = state.copy(
            questions = state.questions + sleep.copy(mo = TrackerMoPolicy(enabled = true)) + evening.copy(mo = TrackerMoPolicy(enabled = true)),
            questionEntries = state.questionEntries + prior,
        )
            .applyingQuestionsEdits(sleep.id, listOf(QuestionsEdit.JoinRewards), joined)
            .applyingQuestionsEdits(evening.id, listOf(QuestionsEdit.JoinRewards), joined)
        setApp()

        assertTileStatus("home_questions_${sleep.id}", appString(R.string.app_home_task_open))
        assertTileStatus("home_questions_${evening.id}", appString(R.string.app_home_task_open))
        composeRule.onNodeWithTag("home_streak").performClick()
        composeRule.onNodeWithTag("streak_questions_${sleep.id}").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("streak_questions_${evening.id}").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("top_back").performClick()

        // Today's answers give the view about today its coin.
        tile("home_questions_${sleep.id}").performClick()
        press("questions_${sleep.id}_qn_rest_1")
        press("questions_${sleep.id}_save")
        composeRule.onNodeWithTag("top_back").performClick()
        assertTileStatus("home_questions_${sleep.id}", appString(R.string.app_home_task_done))

        // The view about yesterday opens on yesterday, and yesterday's answers give it its coin.
        tile("home_questions_${evening.id}").performClick()
        composeRule.onNodeWithTag("questions_${evening.id}_history_$yesterday").assertIsSelected()
        press("questions_${evening.id}_qn_screen_0")
        press("questions_${evening.id}_save")
        composeRule.runOnIdle {
            assertEquals(mapOf(yesterday to mapOf("qn_screen" to "an_no"), previousEvening to mapOf("qn_screen" to "an_yes")), entriesOf(evening).toMap())
            assertTrue(state.questionEntries.containsAll(prior + eatings))
            assertEquals(mapOf("qn_rest" to "an_mid"), state.questionEntries.single { it.id == "sleep-prior" }.answers)
        }
        composeRule.onNodeWithTag("top_back").performClick()
        assertTileStatus("home_questions_${evening.id}", appString(R.string.app_home_task_done))
    }

    /**
     * "Send a test" on its Reminders part posts its reminder, with the switch off, under the view's
     * name, about the day today asks about. The notification's tap is the one that opens this view
     * on that day, and arriving, that request opens its screen on it.
     */
    @Test
    fun sendATestShowsItsReminderAndATapOpensItOnTheDayItAskedAbout() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        // The suite grants no permission on its own, and the app creates the channel on start; the test host is not the app's activity.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        ReminderNotificationChannel.create(context)
        notificationManager.cancelAll()
        state = state.copy(questions = state.questions + evening, questionEntries = listOf(QuestionEntry("e1", evening.id, yesterday, 1L, mapOf("qn_rest" to "an_mid")), eatings))
        fun postedTest() = notificationManager.activeNotifications
            .firstOrNull { it.tag == slotKey(ReminderViewKind.Questions, evening.id, "day") && it.id == ChecklistReminderRequestCode }

        try {
            setApp()
            tile("home_settings").performClick()
            openPage(evening)
            openPart("questions_settings_tab_reminders")
            composeRule.onNodeWithTag("questions_settings_reminder").performScrollTo().assertIsOff()
            composeRule.onNodeWithTag("questions_settings_reminder_preview_text").performScrollTo().assertTextEquals(appString(R.string.questions_reminder_open_yesterday))
            composeRule.onNodeWithTag("questions_settings_reminder_test").performScrollTo().assertIsEnabled().performClick()

            composeRule.waitUntil(timeoutMillis = 5_000) { postedTest() != null }
            val posted = requireNotNull(postedTest()).notification
            assertEquals("Evening", posted.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString())
            assertEquals(appString(R.string.questions_reminder_open_yesterday), posted.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString())
            assertEquals(loggedViewDestinationPendingIntent(context, LoggedViewRef(LoggedViewKind.Questions, evening.id), yesterday), posted.contentIntent)

            // The tap's request arrives: the page held nothing unsaved, so the view opens, on yesterday, with what was answered.
            composeRule.runOnIdle { request = AppNavigationRequest(AppDestination.Questions, loggedViewId = evening.id, loggedDateKey = yesterday) }
            composeRule.onNodeWithTag("questions_${evening.id}_history_$yesterday").assertIsSelected()
            composeRule.onNodeWithTag("questions_${evening.id}_qn_rest_1").performScrollTo().assertIsSelected()
            composeRule.runOnIdle { assertEquals(null, request) }
        } finally {
            notificationManager.cancelAll()
        }
    }

    /**
     * Hidden with the switch on its Settings row, its tile is gone and a reminder's tap finds no
     * screen to open, while its settings stay open and its reminder's row says it is hidden. Shown
     * again, its tile is back and opens its screen.
     */
    @Test
    fun itIsHiddenAndShownAgainAndKeepsItsSettingsWhileHidden() {
        state = state.copy(questions = state.questions + sleep + evening)
        setApp()
        tile("home_questions_${sleep.id}").assertIsDisplayed()
        tile("home_settings").performClick()

        composeRule.onNodeWithTag("settings_feature_questions_${sleep.id}").performScrollTo().assertIsOn().performClick()
        composeRule.onNodeWithTag("settings_feature_questions_${sleep.id}").assertIsOff()
        composeRule.onNodeWithTag("settings_feature_questions_${evening.id}").performScrollTo().assertIsOn()
        composeRule.runOnIdle { assertEquals(false, visibility.shows(sleep.ref)) }

        composeRule.onNodeWithTag("top_back").performClick()
        composeRule.onNodeWithTag("home_questions_${sleep.id}").assertDoesNotExist()
        tile("home_questions_${evening.id}").assertIsDisplayed()
        composeRule.runOnIdle { request = AppNavigationRequest(AppDestination.Questions, loggedViewId = sleep.id, loggedDateKey = today) }
        composeRule.onNodeWithTag("questions_${sleep.id}_save").assertDoesNotExist()
        tile("home_settings").assertIsDisplayed()

        tile("home_settings").performClick()
        openPage(sleep)
        openPart("questions_settings_tab_reminders")
        composeRule.onNodeWithTag("questions_settings_reminder_dependency").performScrollTo().assertTextEquals(appString(R.string.settings_turn_feature_on, "Sleep"))
        composeRule.onNodeWithTag("questions_settings_reminder_test").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithTag("top_back").performClick()

        composeRule.onNodeWithTag("settings_feature_questions_${sleep.id}").performScrollTo().performClick()
        composeRule.onNodeWithTag("settings_feature_questions_${sleep.id}").assertIsOn()
        composeRule.runOnIdle { assertEquals(FeatureVisibilitySettings(), visibility) }
        composeRule.onNodeWithTag("top_back").performClick()
        tile("home_questions_${sleep.id}").performClick()
        composeRule.onNodeWithTag("questions_${sleep.id}_save").assertExists()
    }

    /**
     * Delete asks first, naming the view and the days that hold its answers. Confirmed, its
     * configuration and every entry of it are gone together, its row and its tile with them, and
     * Eating's entries and another view's are as they were.
     */
    @Test
    fun itIsDeletedWithItsAnswersAfterTheQuestionAndEatingsStay() {
        val others = listOf(QuestionEntry("ev", evening.id, yesterday, 2L, mapOf("qn_rest" to "an_mid")), eatings)
        val sleeps = listOf(QuestionEntry("s2", sleep.id, today, 4L, mapOf("qn_rest" to "an_high")), QuestionEntry("s1", sleep.id, yesterday, 3L))
        state = state.copy(questions = state.questions + sleep + evening, questionEntries = listOf(sleeps[0], sleeps[1], others[0], others[1]))
        setApp()
        tile("home_settings").performClick()
        openPage(sleep)

        composeRule.onNodeWithTag("questions_settings_delete").performClick()
        composeRule.onNodeWithTag("questions_settings_delete_dialog").assertIsDisplayed()
        composeRule.onNodeWithText(appString(R.string.measurement_settings_delete_title, "Sleep")).assertIsDisplayed()
        composeRule.onNodeWithText(InstrumentationRegistry.getInstrumentation().targetContext.resources.getQuantityString(R.plurals.questions_settings_delete_message, 2, 2)).assertIsDisplayed()
        composeRule.onNodeWithTag("questions_settings_delete_cancel").performClick()
        composeRule.runOnIdle { assertEquals(listOf(sleep, evening), state.madeQuestions) }

        composeRule.onNodeWithTag("questions_settings_delete").performClick()
        composeRule.onNodeWithTag("questions_settings_delete_confirm").performClick()
        composeRule.runOnIdle {
            assertEquals(listOf(evening), state.madeQuestions)
            assertEquals(others, state.questionEntries)
        }
        composeRule.onNodeWithTag("questions_settings").assertDoesNotExist()
        composeRule.onNodeWithTag("settings_group_questions_${sleep.id}").assertDoesNotExist()
        composeRule.onNodeWithTag("settings_group_questions_${evening.id}").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("top_back").performClick()
        composeRule.onNodeWithTag("home_questions_${sleep.id}").assertDoesNotExist()
        tile("home_questions_${evening.id}").assertIsDisplayed()
    }
}
