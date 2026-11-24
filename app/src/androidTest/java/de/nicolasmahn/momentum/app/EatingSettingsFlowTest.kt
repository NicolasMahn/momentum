package de.nicolasmahn.momentum.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import de.nicolasmahn.momentum.R
import de.nicolasmahn.momentum.TestWeightMeasurement
import de.nicolasmahn.momentum.core.ui.theme.WorkoutTrackerTheme
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.checklist.ViewHue
import de.nicolasmahn.momentum.domain.checklist.ViewIcon
import de.nicolasmahn.momentum.domain.checklist.WhenAway
import de.nicolasmahn.momentum.testfixtures.defaultEatingQuestions
import de.nicolasmahn.momentum.testfixtures.eatingQuestions
import de.nicolasmahn.momentum.domain.settings.DashboardFeature
import de.nicolasmahn.momentum.domain.settings.FeatureVisibilitySettings
import de.nicolasmahn.momentum.core.time.currentHabitDate
import de.nicolasmahn.momentum.testing.appString
import de.nicolasmahn.momentum.testing.assertInputEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

/**
 * Eating's settings on the page a questions view a person made has, over its stored
 * configuration: Look, Questions, Reminders and Streak, saved by one press, its reminder among
 * them, and Delete. The stored configuration is the proof, and the places that wear the name show
 * what a rename reaches.
 */
class EatingSettingsFlowTest {
    @get:Rule
    val composeRule = createComposeRule()

    /** Eating as it is seeded, its reminder on. */
    private val seeded = defaultEatingQuestions(label = "Eating", remind = true)

    private var state by mutableStateOf(AppState(measurements = listOf(TestWeightMeasurement), questions = listOf(seeded)))

    /** Whether the store refuses a save, as a write that failed does. */
    private var storeFails = false

    private val eating get() = requireNotNull(state.eatingQuestions)

    private fun setApp() {
        composeRule.setContent {
            WorkoutTrackerTheme {
                MomentumApp(
                    state = state,
                    onStateChange = { updated ->
                        if (storeFails) {
                            false
                        } else {
                            state = updated
                            true
                        }
                    },
                    onActivitySave = ActivitySaveNotExpected,
                    // Statistics starts hidden, and one flow follows Eating's name into it.
                    featureVisibility = FeatureVisibilitySettings().withVisibility(DashboardFeature.Statistics, true, currentHabitDate()),
                )
            }
        }
    }

    /** From the Settings list, which every Save returns to. */
    private fun openEatingsPage() {
        composeRule.onNodeWithTag("settings_group_questions_eating").performScrollTo().performClick()
        composeRule.onNodeWithTag("questions_settings").assertIsDisplayed()
    }

    private fun openPart(part: String) {
        composeRule.onNodeWithTag("questions_settings_tab_$part").performScrollTo().performClick()
        composeRule.onNodeWithTag("questions_settings_tab_$part").assertIsSelected()
    }

    private fun keptDays(days: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.resources.getQuantityString(R.plurals.checklist_editor_days, days, days)

    private fun daysToAToken(done: Int, goal: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.resources.getQuantityString(R.plurals.mo_progress_days, goal, done, goal)

    /**
     * The page is a questions view's, under its kind's tags: Look as it is stored, its two
     * stored questions shown and editable, its reminder fifteen minutes after waking, and the switch for the
     * streak with its bank and holiday rule. Delete asks first, naming it.
     */
    @Test
    fun aNamedQuestionsPageShowsItsStoredEditableQuestionsAndDelete() {
        setApp()
        composeRule.onNodeWithTag("home_settings").performClick()
        openEatingsPage()

        composeRule.onNodeWithTag("questions_settings_tab_look").assertIsSelected()
        composeRule.onNodeWithTag("questions_settings_tab_items").assertDoesNotExist()
        composeRule.onNodeWithTag("questions_settings_name").performScrollTo().assertInputEquals(seeded.label)
        composeRule.onNodeWithTag("questions_settings_icon_food").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("questions_settings_hue_orange").performScrollTo().assertIsSelected()

        openPart("questions")
        composeRule.onNodeWithTag("questions_settings_about_yesterday").performScrollTo().assertIsSelected().assertIsEnabled()
        seeded.questions.forEach { question ->
            composeRule.onNodeWithTag("questions_settings_question_${question.id}_text").performScrollTo()
                .assertInputEquals(question.text).assertIsEnabled()
            question.answers.forEach { answer ->
                composeRule.onNodeWithTag("questions_settings_question_${question.id}_answer_${answer.id}").performScrollTo()
                    .assertInputEquals(answer.label).assertIsEnabled()
            }
        }
        composeRule.onNodeWithTag("questions_settings_question_quality_remove").performScrollTo().assertIsEnabled()
        composeRule.onNodeWithTag("questions_settings_add_question").performScrollTo().assertIsEnabled()

        openPart("reminders")
        composeRule.onNodeWithTag("questions_settings_reminder").performScrollTo().assertIsOn()
        composeRule.onNodeWithTag("questions_settings_reminder_time_after_wake").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("settings_eating_reminder").assertDoesNotExist()

        openPart("streak")
        composeRule.onNodeWithTag("questions_settings_rewards").performScrollTo().assertIsOn()
        composeRule.onNodeWithTag("questions_settings_max_passes_value").performScrollTo().assertTextEquals("3")
        composeRule.onNodeWithTag("questions_settings_every_kept_days_value").performScrollTo().assertTextEquals(keptDays(7))
        composeRule.onNodeWithTag("questions_settings_away_due").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("questions_settings_away_excused").assertIsNotSelected()

        composeRule.onNodeWithTag("questions_settings_delete_reason").assertDoesNotExist()
        composeRule.onNodeWithTag("questions_settings_delete").assertIsDisplayed().assertIsEnabled().performClick()
        composeRule.onNodeWithText(appString(R.string.measurement_settings_delete_title, "Eating")).assertIsDisplayed()
        composeRule.onNodeWithTag("questions_settings_delete_cancel").performClick()
        composeRule.onNodeWithTag("questions_settings_delete_dialog").assertDoesNotExist()
        // Untouched, there is nothing to save, and back leaves at once.
        composeRule.onNodeWithTag("questions_settings_save").assertIsNotEnabled()
        composeRule.onNodeWithTag("top_back").performClick()
        composeRule.onNodeWithTag("questions_settings_discard_dialog").assertDoesNotExist()
        composeRule.onNodeWithTag("questions_settings").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(seeded, eating) }
    }

    /**
     * Renamed and recolored, Eating is stored so on Save and wears the new name in the Settings
     * list, on Home, in the header of its screen and in Statistics, and its bank stays on the
     * token page. A cleared name holds Save back. That the tile and the bank wear the new color
     * is read from their pixels in `EatingLookTest`, since no color is said to TalkBack.
     */
    @Test
    fun aRenamedAndRecoloredEatingShowsItsNameOnHomeOnItsScreenAndInStatistics() {
        val name = "Food diary"
        setApp()
        composeRule.onNodeWithTag("home_settings").performClick()
        openEatingsPage()

        composeRule.onNodeWithTag("questions_settings_hue_plum").performScrollTo().performClick()
        composeRule.onNodeWithTag("questions_settings_hue_plum").assertIsSelected()
        composeRule.onNodeWithTag("questions_settings_icon_heart").performScrollTo().performClick()
        composeRule.onNodeWithTag("questions_settings_save").assertIsEnabled()
        composeRule.onNodeWithTag("questions_settings_name").performScrollTo().performTextReplacement("")
        composeRule.onNodeWithTag("questions_settings_save").assertIsNotEnabled()
        composeRule.onNodeWithTag("questions_settings_name").performTextReplacement(name)
        composeRule.onNodeWithTag("questions_settings_save").assertIsEnabled()
        composeRule.runOnIdle { assertEquals(seeded, eating) }

        Espresso.closeSoftKeyboard()
        composeRule.onNodeWithTag("questions_settings_save").performClick()
        // The look judges no day, so nothing else of the configuration moved.
        composeRule.runOnIdle { assertEquals(seeded.copy(label = name, icon = ViewIcon.Heart, hue = ViewHue.Plum), eating) }

        // Saved, the page returned to the list, whose row wears the name.
        composeRule.onNodeWithTag("questions_settings").assertDoesNotExist()
        composeRule.onNode(hasTestTag("settings_group_questions_eating") and hasAnyDescendant(hasText(name)), useUnmergedTree = true).performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("top_back").performClick()
        composeRule.onNode(hasTestTag("home_questions_eating") and hasAnyDescendant(hasText(name)), useUnmergedTree = true).assertIsDisplayed()

        // Its screen is Eating's, under the new name.
        composeRule.onNodeWithTag("home_questions_eating").performClick()
        composeRule.onNodeWithText(name).assertIsDisplayed()
        composeRule.onNodeWithTag("questions_eating_save").assertExists()
        composeRule.onNodeWithTag("top_back").performClick()

        composeRule.onNodeWithTag("home_streak").performClick()
        composeRule.onNodeWithTag("streak_questions_eating").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("top_back").performClick()

        composeRule.onNodeWithTag("home_statistics").performClick()
        composeRule.onNodeWithTag("statistics_consistency_questions_eating").performScrollTo().assert(hasContentDescription(name, substring = true))
        composeRule.onNodeWithTag("top_back").performClick()

        // Opened again, the page shows what was stored and has nothing left to save.
        composeRule.onNodeWithTag("home_settings").performClick()
        openEatingsPage()
        composeRule.onNodeWithTag("questions_settings_name").performScrollTo().assertInputEquals(name)
        composeRule.onNodeWithTag("questions_settings_icon_heart").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("questions_settings_hue_plum").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("questions_settings_save").assertIsNotEnabled()
    }

    /**
     * The bank's size, the kept days a token takes and "Not due" on holiday are stored on Save,
     * dated from today, and the token page shows the new bank and the new goal.
     */
    @Test
    fun aChangedBankAndHolidayRuleAreOnTheTokenPageAfterSave() {
        setApp()
        composeRule.onNodeWithTag("home_streak").performClick()
        composeRule.onNodeWithTag("streak_questions_eating").performScrollTo().assert(hasContentDescription(daysToAToken(0, 7), substring = true))
        composeRule.onNodeWithTag("top_back").performClick()

        composeRule.onNodeWithTag("home_settings").performClick()
        openEatingsPage()
        openPart("streak")
        repeat(2) { composeRule.onNodeWithTag("questions_settings_max_passes_less").performScrollTo().performClick() }
        composeRule.onNodeWithTag("questions_settings_max_passes_value").assertTextEquals("1")
        composeRule.onNodeWithTag("questions_settings_max_passes_less").assertIsNotEnabled()
        repeat(3) { composeRule.onNodeWithTag("questions_settings_every_kept_days_less").performScrollTo().performClick() }
        composeRule.onNodeWithTag("questions_settings_every_kept_days_value").assertTextEquals(keptDays(4))
        composeRule.onNodeWithTag("questions_settings_away_excused").performScrollTo().performClick()
        composeRule.onNodeWithTag("questions_settings_away_excused").assertIsSelected()
        composeRule.runOnIdle { assertEquals(seeded, eating) }

        composeRule.onNodeWithTag("questions_settings_save").performClick()
        composeRule.runOnIdle {
            val bank = requireNotNull(eating.rewards)
            assertEquals(listOf(3, 1), bank.maxPasses.revisions.map { it.value })
            assertEquals(listOf(7, 4), bank.everyKeptDays.revisions.map { it.value })
            assertEquals(listOf(WhenAway.Due, WhenAway.Excused), eating.whenAway.revisions.map { it.value })
            // Each is dated: what applied before today is still stored, without a day of its own.
            assertEquals(listOf(null), listOf(bank.maxPasses, bank.everyKeptDays, eating.whenAway).map { it.revisions.first().from }.distinct())
            assertEquals(1, listOf(bank.maxPasses, bank.everyKeptDays, eating.whenAway).map { it.revisions.last().from }.distinct().size)
        }
        composeRule.onNodeWithTag("questions_settings").assertDoesNotExist()

        composeRule.onNodeWithTag("top_back").performClick()
        composeRule.onNodeWithTag("home_streak").performClick()
        composeRule.onNodeWithTag("streak_questions_eating").performScrollTo()
            .assert(hasContentDescription(daysToAToken(0, 4), substring = true))
            // The one token Eating starts with fills a bank of one.
            .assert(hasContentDescription(appString(R.string.mo_full), substring = true))
    }

    /** One Save stores a Look change and the reminder switched off, both in the configuration, and returns to the list. */
    @Test
    fun oneSaveStoresALookChangeAndAReminderChange() {
        setApp()
        composeRule.onNodeWithTag("home_settings").performClick()
        openEatingsPage()
        composeRule.onNodeWithTag("questions_settings_hue_plum").performScrollTo().performClick()
        openPart("reminders")
        composeRule.onNodeWithTag("questions_settings_reminder").performScrollTo().assertIsOn().performClick()
        composeRule.onNodeWithTag("questions_settings_reminder").assertIsOff()
        composeRule.runOnIdle { assertEquals(seeded, eating) }

        composeRule.onNodeWithTag("questions_settings_save").performClick()
        composeRule.runOnIdle {
            assertEquals(ViewHue.Plum, eating.hue)
            assertFalse(eating.checkpoint.remind)
        }
        composeRule.onNodeWithTag("questions_settings").assertDoesNotExist()
        composeRule.onNodeWithTag("settings_group_questions_eating").performScrollTo().assertIsDisplayed()
    }

    /**
     * The store refuses the save. Nothing is stored, the Look change and the reminder alike; the
     * page stays with what went wrong and both still there to save, and leaving asks about them.
     * Once the store works, Save stores both and returns to the list.
     */
    @Test
    fun aRefusedSaveStoresNothingAndKeepsThePageOpenWithItsError() {
        setApp()
        composeRule.onNodeWithTag("home_settings").performClick()
        openEatingsPage()
        composeRule.onNodeWithTag("questions_settings_hue_navy").performScrollTo().performClick()
        openPart("reminders")
        composeRule.onNodeWithTag("questions_settings_reminder").performScrollTo().assertIsOn().performClick()

        storeFails = true
        composeRule.onNodeWithTag("questions_settings_save").performClick()
        composeRule.onNodeWithTag("questions_settings_error").assertIsDisplayed().assertTextEquals(appString(R.string.settings_error_view))
        composeRule.onNodeWithTag("questions_settings").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(seeded, eating) }
        composeRule.onNodeWithTag("questions_settings_reminder").assertIsOff()
        composeRule.onNodeWithTag("questions_settings_save").assertIsEnabled()

        // Leaving asks, as the changes would be lost; staying keeps them.
        composeRule.onNodeWithTag("top_back").performClick()
        composeRule.onNodeWithTag("questions_settings_discard_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("questions_settings_discard_dialog_stay").performClick()
        composeRule.onNodeWithTag("questions_settings_reminder").assertIsOff()

        storeFails = false
        composeRule.onNodeWithTag("questions_settings_save").performClick()
        composeRule.runOnIdle {
            assertEquals(ViewHue.Navy, eating.hue)
            assertFalse(eating.checkpoint.remind)
        }
        composeRule.onNodeWithTag("questions_settings").assertDoesNotExist()
        composeRule.onNodeWithTag("settings_group_questions_eating").performScrollTo().assertIsDisplayed()
    }
}
