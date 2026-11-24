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
import androidx.compose.ui.test.assertTextContains
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
import de.nicolasmahn.momentum.TestEatingQuestions
import de.nicolasmahn.momentum.TestWeightMeasurement
import de.nicolasmahn.momentum.core.ui.theme.WorkoutTrackerTheme
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.checklist.ViewHue
import de.nicolasmahn.momentum.domain.checklist.ViewIcon
import de.nicolasmahn.momentum.domain.checklist.WhenAway
import de.nicolasmahn.momentum.domain.settings.DashboardFeature
import de.nicolasmahn.momentum.domain.settings.FeatureVisibilitySettings
import de.nicolasmahn.momentum.core.time.currentHabitDate
import de.nicolasmahn.momentum.domain.time.DefaultTokenSteps
import de.nicolasmahn.momentum.domain.time.TokenStep
import de.nicolasmahn.momentum.domain.time.TokenSteps
import de.nicolasmahn.momentum.testfixtures.defaultReadingTime
import de.nicolasmahn.momentum.testfixtures.readingTime
import de.nicolasmahn.momentum.testing.appString
import de.nicolasmahn.momentum.testing.assertInputEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

/**
 * Reading's settings on the page a time view a person made has, over its stored configuration:
 * Look, Goal, Reminders and Streak, saved by one press, its reminder among them, and Delete. The
 * stored configuration is the proof, and the places that wear the name and read the goal show
 * what a change reaches.
 */
class ReadingSettingsFlowTest {
    @get:Rule
    val composeRule = createComposeRule()

    /** Reading as it is seeded, its reminder on. */
    private val seeded = defaultReadingTime(label = "Reading", remind = true)

    private var state by mutableStateOf(AppState(measurements = listOf(TestWeightMeasurement), questions = listOf(TestEatingQuestions), time = listOf(seeded)))

    /** Whether the store refuses a save, as a write that failed does. */
    private var storeFails = false

    private val reading get() = requireNotNull(state.readingTime)

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
                    // Statistics starts hidden, and one flow follows Reading's name into it.
                    featureVisibility = FeatureVisibilitySettings().withVisibility(DashboardFeature.Statistics, true, currentHabitDate()),
                )
            }
        }
    }

    /** From the Settings list, which every Save returns to. */
    private fun openReadingsPage() {
        composeRule.onNodeWithTag("settings_group_time_reading").performScrollTo().performClick()
        composeRule.onNodeWithTag("time_settings").assertIsDisplayed()
    }

    private fun openPart(part: String) {
        composeRule.onNodeWithTag("time_settings_tab_$part").performScrollTo().performClick()
        composeRule.onNodeWithTag("time_settings_tab_$part").assertIsSelected()
    }

    private fun typeGoal(text: String) {
        composeRule.onNodeWithTag("time_settings_goal").performScrollTo().performTextReplacement(text)
        Espresso.closeSoftKeyboard()
    }

    private val resources get() = InstrumentationRegistry.getInstrumentation().targetContext.resources

    /** The Goal part's line: what [goal] minutes earn, and with more than one step the most a week can earn, [most]. */
    private fun goalNote(goal: Int, most: Int? = 2): String = listOfNotNull(
        resources.getQuantityString(R.plurals.time_settings_goal_note, goal, goal, resources.getQuantityString(R.plurals.settings_tokens, 1, 1)),
        most?.let { resources.getQuantityString(R.plurals.time_settings_goal_note_more, it, it) },
    ).joinToString(" ")

    private fun minutesToAToken(done: Int, goal: Int): String = resources.getQuantityString(R.plurals.mo_progress_minutes_this_week, goal, done, goal)

    /** What the reminder's preview says while the week's [goal] minutes are all still to read. */
    private fun toGo(goal: Int): String = resources.getQuantityString(R.plurals.time_reminder_week_remaining, goal, goal)

    /**
     * The page is a time view's, under its kind's tags: Look as it is stored, ending with Mo's
     * flavor on Reading, its minutes a week, its reminder at six in the evening, and the switch
     * for the streak with its bank's size, its holiday rule and the steps a week earns tokens by.
     * Delete asks first, naming it.
     */
    @Test
    fun readingsPageIsATimeViewsPageWithDelete() {
        setApp()
        composeRule.onNodeWithTag("home_settings").performClick()
        openReadingsPage()

        composeRule.onNodeWithTag("time_settings_tab_look").assertIsSelected()
        composeRule.onNodeWithTag("time_settings_tab_items").assertDoesNotExist()
        composeRule.onNodeWithTag("time_settings_name").performScrollTo().assertInputEquals(seeded.label)
        composeRule.onNodeWithTag("time_settings_icon_book").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("time_settings_hue_yellow").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("time_settings_motif_reading").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("time_settings_motif_none").performScrollTo().assertIsNotSelected()
        composeRule.onNodeWithTag("time_settings_motif_pills").assertDoesNotExist()

        openPart("goal")
        composeRule.onNodeWithTag("time_settings_goal").performScrollTo().assertInputEquals("60")
        composeRule.onNodeWithTag("time_settings_goal_note").performScrollTo().assertTextEquals(goalNote(60))

        openPart("reminders")
        composeRule.onNodeWithTag("time_settings_reminder").performScrollTo().assertIsOn()
        composeRule.onNodeWithTag("time_settings_reminder_time_fixed").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("time_settings_reminder_preview_text").performScrollTo().assertTextEquals(toGo(60))
        composeRule.onNodeWithTag("settings_reading_reminder").assertDoesNotExist()

        openPart("streak")
        composeRule.onNodeWithTag("time_settings_rewards").performScrollTo().assertIsOn()
        composeRule.onNodeWithTag("time_settings_every_kept_days_value").assertDoesNotExist()
        composeRule.onNodeWithTag("time_settings_max_passes_value").performScrollTo().assertTextEquals("3")
        composeRule.onNodeWithTag("time_settings_away_due").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("time_settings_away_excused").assertIsNotSelected()
        composeRule.onNodeWithTag("time_settings_step_1_minutes").performScrollTo().assertTextEquals(appString(R.string.time_settings_step_minutes, 60))
        composeRule.onNodeWithTag("time_settings_step_1_tokens_value").performScrollTo().assertTextEquals("+1")
        composeRule.onNodeWithTag("time_settings_step_2_percent").performScrollTo().assertInputEquals("200")
        composeRule.onNodeWithTag("time_settings_step_2_minutes").assertTextEquals(appString(R.string.time_settings_step_minutes, 120))
        composeRule.onNodeWithTag("time_settings_step_2_tokens_value").performScrollTo().assertTextEquals("+1")
        composeRule.onNodeWithTag("time_settings_step_3_percent").assertDoesNotExist()
        composeRule.onNodeWithTag("time_settings_steps_total").performScrollTo()
            .assertTextEquals(resources.getQuantityString(R.plurals.time_settings_steps_total, 120, 120, resources.getQuantityString(R.plurals.settings_tokens, 2, 2)))
        composeRule.onNodeWithTag("time_settings_add_step").performScrollTo().assertIsEnabled()

        composeRule.onNodeWithTag("time_settings_delete_reason").assertDoesNotExist()
        composeRule.onNodeWithTag("time_settings_delete").assertIsDisplayed().assertIsEnabled().performClick()
        composeRule.onNodeWithText(appString(R.string.measurement_settings_delete_title, "Reading")).assertIsDisplayed()
        composeRule.onNodeWithTag("time_settings_delete_cancel").performClick()
        composeRule.onNodeWithTag("time_settings_delete_dialog").assertDoesNotExist()
        // Untouched, there is nothing to save, and back leaves at once.
        composeRule.onNodeWithTag("time_settings_save").assertIsNotEnabled()
        composeRule.onNodeWithTag("top_back").performClick()
        composeRule.onNodeWithTag("time_settings_discard_dialog").assertDoesNotExist()
        composeRule.onNodeWithTag("time_settings").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(seeded, reading) }
    }

    /**
     * Renamed and recolored, Reading is stored so on Save and wears the new name in the Settings
     * list, on Home, in the header of its screen and in Statistics, and its bank stays on the
     * token page. A cleared name holds Save back. That the tile and the token wear the new color
     * is read from their pixels in `ReadingLookTest`, since no color is said to TalkBack.
     */
    @Test
    fun aRenamedAndRecoloredReadingShowsItsNameOnHomeOnItsScreenAndInStatistics() {
        val name = "Books"
        setApp()
        composeRule.onNodeWithTag("home_settings").performClick()
        openReadingsPage()

        composeRule.onNodeWithTag("time_settings_hue_plum").performScrollTo().performClick()
        composeRule.onNodeWithTag("time_settings_hue_plum").assertIsSelected()
        composeRule.onNodeWithTag("time_settings_icon_heart").performScrollTo().performClick()
        composeRule.onNodeWithTag("time_settings_save").assertIsEnabled()
        composeRule.onNodeWithTag("time_settings_name").performScrollTo().performTextReplacement("")
        composeRule.onNodeWithTag("time_settings_save").assertIsNotEnabled()
        composeRule.onNodeWithTag("time_settings_name").performTextReplacement(name)
        composeRule.onNodeWithTag("time_settings_save").assertIsEnabled()
        composeRule.runOnIdle { assertEquals(seeded, reading) }

        Espresso.closeSoftKeyboard()
        composeRule.onNodeWithTag("time_settings_save").performClick()
        // The look judges no week, so nothing else of the configuration moved.
        composeRule.runOnIdle { assertEquals(seeded.copy(label = name, icon = ViewIcon.Heart, hue = ViewHue.Plum), reading) }

        // Saved, the page returned to the list, whose row wears the name.
        composeRule.onNodeWithTag("time_settings").assertDoesNotExist()
        composeRule.onNode(hasTestTag("settings_group_time_reading") and hasAnyDescendant(hasText(name)), useUnmergedTree = true).performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("top_back").performClick()
        composeRule.onNode(hasTestTag("home_time_reading") and hasAnyDescendant(hasText(name)), useUnmergedTree = true).assertIsDisplayed()

        // Its screen is Reading's, in Reading's own words, under the new name.
        composeRule.onNodeWithTag("home_time_reading").performClick()
        composeRule.onNodeWithText(name).assertIsDisplayed()
        composeRule.onNodeWithTag("time_reading_start").assertTextContains(appString(R.string.time_start))
        composeRule.onNodeWithTag("top_back").performClick()

        composeRule.onNodeWithTag("home_streak").performClick()
        composeRule.onNodeWithTag("streak_time_reading").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("top_back").performClick()

        composeRule.onNodeWithTag("home_statistics").performClick()
        composeRule.onNodeWithTag("statistics_consistency_time_reading").performScrollTo().assert(hasContentDescription(name, substring = true))
        composeRule.onNodeWithTag("top_back").performClick()

        // Opened again, the page shows what was stored and has nothing left to save.
        composeRule.onNodeWithTag("home_settings").performClick()
        openReadingsPage()
        composeRule.onNodeWithTag("time_settings_name").performScrollTo().assertInputEquals(name)
        composeRule.onNodeWithTag("time_settings_icon_heart").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("time_settings_hue_plum").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("time_settings_save").assertIsNotEnabled()
    }

    /**
     * Ninety minutes a week, a bank of one, one token a week at most and "Not due" on holiday
     * are stored on Save, dated from today. Text that is no goal holds Save back until it is one,
     * the reminder's preview says the goal the page holds, the Goal part's line
     * says a week earns one, and after Save the token page and the week on Reading's screen show
     * the new goal.
     */
    @Test
    fun aChangedGoalBankAndHolidayRuleAreOnTheTokenPageAndOnItsScreenAfterSave() {
        setApp()
        composeRule.onNodeWithTag("home_streak").performClick()
        composeRule.onNodeWithTag("streak_time_reading").performScrollTo().assert(hasContentDescription(minutesToAToken(0, 60), substring = true))
        composeRule.onNodeWithTag("top_back").performClick()

        composeRule.onNodeWithTag("home_settings").performClick()
        openReadingsPage()
        openPart("goal")
        typeGoal("an hour")
        composeRule.onNodeWithTag("time_settings_save").assertIsNotEnabled()
        typeGoal("90")
        composeRule.onNodeWithTag("time_settings_goal_note").assertTextEquals(goalNote(90))
        composeRule.onNodeWithTag("time_settings_save").assertIsEnabled()

        openPart("reminders")
        composeRule.onNodeWithTag("time_settings_reminder_preview_text").performScrollTo().assertTextEquals(toGo(90))

        openPart("streak")
        repeat(2) { composeRule.onNodeWithTag("time_settings_max_passes_less").performScrollTo().performClick() }
        composeRule.onNodeWithTag("time_settings_max_passes_value").assertTextEquals("1")
        composeRule.onNodeWithTag("time_settings_max_passes_less").assertIsNotEnabled()
        composeRule.onNodeWithTag("time_settings_away_excused").performScrollTo().performClick()
        composeRule.onNodeWithTag("time_settings_away_excused").assertIsSelected()
        // The 200 % step goes with two taps, so the goal is the one step and its line says so alone.
        composeRule.onNodeWithTag("time_settings_step_2_remove").performScrollTo().performClick()
        composeRule.onNodeWithTag("time_settings_step_2_remove").performScrollTo().performClick()
        composeRule.onNodeWithTag("time_settings_step_2_percent").assertDoesNotExist()
        openPart("goal")
        composeRule.onNodeWithTag("time_settings_goal_note").performScrollTo().assertTextEquals(goalNote(90, most = null))
        composeRule.runOnIdle { assertEquals(seeded, reading) }

        composeRule.onNodeWithTag("time_settings_save").performClick()
        composeRule.runOnIdle {
            val bank = requireNotNull(reading.rewards)
            assertEquals(listOf(60, 90), reading.goal.revisions.map { it.value })
            assertEquals(listOf(3, 1), bank.maxPasses.revisions.map { it.value })
            assertEquals(listOf(DefaultTokenSteps, TokenSteps(listOf(TokenStep(100, 1)))), bank.tokenSteps.revisions.map { it.value })
            assertEquals(listOf(WhenAway.Due, WhenAway.Excused), reading.whenAway.revisions.map { it.value })
            // Each is dated: what applied before today is still stored, without a day of its own.
            val dated = listOf(reading.goal, bank.maxPasses, bank.tokenSteps, reading.whenAway)
            assertEquals(listOf(null), dated.map { it.revisions.first().from }.distinct())
            assertEquals(1, dated.map { it.revisions.last().from }.distinct().size)
        }
        composeRule.onNodeWithTag("time_settings").assertDoesNotExist()

        composeRule.onNodeWithTag("top_back").performClick()
        composeRule.onNodeWithTag("home_streak").performClick()
        composeRule.onNodeWithTag("streak_time_reading").performScrollTo().assert(hasContentDescription(minutesToAToken(0, 90), substring = true))
        composeRule.onNodeWithTag("top_back").performClick()

        composeRule.onNodeWithTag("home_time_reading").performClick()
        composeRule.onNodeWithTag("time_reading_week_minutes").assertTextContains("0 / 90 min")
    }

    /** One Save stores a goal change and the reminder switched off, both in the configuration, and returns to the list. */
    @Test
    fun oneSaveStoresAGoalChangeAndAReminderChange() {
        setApp()
        composeRule.onNodeWithTag("home_settings").performClick()
        openReadingsPage()
        openPart("goal")
        typeGoal("45")
        openPart("reminders")
        composeRule.onNodeWithTag("time_settings_reminder").performScrollTo().assertIsOn().performClick()
        composeRule.onNodeWithTag("time_settings_reminder").assertIsOff()
        composeRule.runOnIdle { assertEquals(seeded, reading) }

        composeRule.onNodeWithTag("time_settings_save").performClick()
        composeRule.runOnIdle {
            assertEquals(45, reading.goal.current)
            assertFalse(reading.checkpoint.remind)
        }
        composeRule.onNodeWithTag("time_settings").assertDoesNotExist()
        composeRule.onNodeWithTag("settings_group_time_reading").performScrollTo().assertIsDisplayed()
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
        openReadingsPage()
        composeRule.onNodeWithTag("time_settings_hue_navy").performScrollTo().performClick()
        openPart("reminders")
        composeRule.onNodeWithTag("time_settings_reminder").performScrollTo().assertIsOn().performClick()

        storeFails = true
        composeRule.onNodeWithTag("time_settings_save").performClick()
        composeRule.onNodeWithTag("time_settings_error").assertIsDisplayed().assertTextEquals(appString(R.string.settings_error_view))
        composeRule.onNodeWithTag("time_settings").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(seeded, reading) }
        composeRule.onNodeWithTag("time_settings_reminder").assertIsOff()
        composeRule.onNodeWithTag("time_settings_save").assertIsEnabled()

        // Leaving asks, as the changes would be lost; staying keeps them.
        composeRule.onNodeWithTag("top_back").performClick()
        composeRule.onNodeWithTag("time_settings_discard_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("time_settings_discard_dialog_stay").performClick()
        composeRule.onNodeWithTag("time_settings_reminder").assertIsOff()

        storeFails = false
        composeRule.onNodeWithTag("time_settings_save").performClick()
        composeRule.runOnIdle {
            assertEquals(ViewHue.Navy, reading.hue)
            assertFalse(reading.checkpoint.remind)
        }
        composeRule.onNodeWithTag("time_settings").assertDoesNotExist()
        composeRule.onNodeWithTag("settings_group_time_reading").performScrollTo().assertIsDisplayed()
    }
}
