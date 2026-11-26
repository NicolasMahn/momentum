package de.nicolasmahn.momentum.gallery

import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import de.nicolasmahn.momentum.app.AppDestination
import de.nicolasmahn.momentum.core.ui.theme.WorkoutTrackerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The gallery shows a time view's setup at each of its four steps, a made view's page and
 * Reading's on each of their four parts, the Goal part and the Streak part on their own, and a
 * joined view on Home and on the token page. Each specimen is the real component, opened where its
 * name says.
 */
@RunWith(RobolectricTestRunner::class)
class TimeSettingsSpecimensTest {
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

        show("TimeSetup, step 1 Look")
        compose.onNodeWithTag("time_setup_tab_look").assertIsSelected().assertTextEquals("1 Look")
        compose.onNodeWithTag("time_setup_motif_none").assertIsSelected()
        compose.onNodeWithTag("time_setup_tab_goal").assertTextEquals("2 Goal").assertIsNotEnabled()
        compose.onNodeWithTag("time_setup_next").assertIsNotEnabled()

        show("TimeSetup, step 2 Goal")
        compose.onNodeWithTag("time_setup_tab_goal").assertIsSelected()
        compose.onNodeWithTag("time_setup_goal").assertExists()
        compose.onNodeWithTag("time_setup_goal_note").assertTextEquals("60 minutes in a week earn a token. More minutes earn up to 2 tokens a week.")
        // Text that is no goal holds the step, and a goal lets it go on.
        compose.onNodeWithTag("time_setup_goal").performTextReplacement("an hour")
        compose.waitForIdle()
        compose.onNodeWithTag("time_setup_next").assertIsNotEnabled()
        compose.onNodeWithTag("time_setup_goal").performTextReplacement("90")
        compose.waitForIdle()
        compose.onNodeWithTag("time_setup_next").assertIsEnabled()

        show("TimeSetup, step 3 Reminders")
        compose.onNodeWithTag("time_setup_tab_reminders").assertIsSelected().assertTextEquals("3 Reminders")
        compose.onNodeWithTag("time_setup_reminder_preview_title").assertTextEquals("Piano")
        compose.onNodeWithTag("time_setup_reminder_preview_text").assertTextEquals("90 minutes to go this week")

        show("TimeSetup, step 4 Streak")
        compose.onNodeWithTag("time_setup_tab_streak").assertIsSelected().assertTextEquals("4 Streak")
        compose.onNodeWithTag("time_setup_rewards").assertIsOn()
        compose.onNodeWithTag("time_setup_max_passes_value").assertTextEquals("3")
        compose.onNodeWithTag("time_setup_every_kept_days_value").assertDoesNotExist()
        compose.onNodeWithTag("time_setup_step_1_minutes").assertTextEquals("90 min")
        compose.onNodeWithTag("time_setup_step_2_tokens_value").assertTextEquals("+1")
        compose.onNodeWithTag("time_setup_create").assertIsEnabled()

        show("MadeTimeSettingsPage, Look")
        compose.onNodeWithTag("time_settings_tab_look").assertIsSelected()
        compose.onNodeWithTag("time_settings_name").assertExists()
        compose.onNodeWithTag("time_settings_delete").assertIsEnabled()
        // Look ends with Mo's flavor, on Plain, and choosing the other leaves something to save.
        compose.onNodeWithTag("time_settings_motif_none").assertIsSelected()
        compose.onNodeWithTag("time_settings_save").assertIsNotEnabled()
        click("time_settings_motif_reading")
        compose.onNodeWithTag("time_settings_motif_reading").assertIsSelected()
        compose.onNodeWithTag("time_settings_save").assertIsEnabled()

        show("MadeTimeSettingsPage, Goal")
        compose.onNodeWithTag("time_settings_tab_goal").assertIsSelected()
        compose.onNodeWithTag("time_settings_goal_note").assertTextEquals("90 minutes in a week earn a token. More minutes earn up to 2 tokens a week.")

        show("MadeTimeSettingsPage, Reminders")
        compose.onNodeWithTag("time_settings_reminder_time_before_sleep").assertIsSelected()
        compose.onNodeWithTag("time_settings_reminder_test").assertIsEnabled()
        show("MadeTimeSettingsPage, Reminders, hidden")
        compose.onNodeWithTag("time_settings_reminder_test").assertIsNotEnabled()

        show("MadeTimeSettingsPage, Streak")
        compose.onNodeWithTag("time_settings_rewards").assertIsOn()
        compose.onNodeWithTag("time_settings_away_excused").assertIsSelected()
        compose.onNodeWithTag("time_settings_step_2_minutes").assertTextEquals("180 min")
        compose.onNodeWithTag("time_settings_add_step").assertIsEnabled()
    }

    /** Reading's page, the made view's, opens on each of its four parts, with the switch for the streak and Delete, and its Save stores a change. */
    @Test
    fun readingsPageOpensOnEachOfItsFourParts() {
        compose.setContent { WorkoutTrackerTheme { shown?.let { specimen -> key(specimen.name) { specimen.content() } } } }

        show("MadeTimeSettingsPage, Reading, Look")
        compose.onNodeWithTag("time_settings_tab_look").assertIsSelected()
        compose.onNodeWithTag("time_settings_icon_book").assertIsSelected()
        compose.onNodeWithTag("time_settings_motif_reading").assertIsSelected()
        compose.onNodeWithTag("time_settings_delete").assertIsEnabled()
        compose.onNodeWithTag("time_settings_save").assertIsNotEnabled()

        show("MadeTimeSettingsPage, Reading, Goal")
        compose.onNodeWithTag("time_settings_tab_goal").assertIsSelected().assertTextEquals("Goal")
        compose.onNodeWithTag("time_settings_goal_note").assertTextEquals("60 minutes in a week earn a token. More minutes earn up to 2 tokens a week.")
        compose.onNodeWithTag("time_settings_goal").performTextReplacement("90")
        compose.waitForIdle()
        compose.onNodeWithTag("time_settings_save").assertIsEnabled()
        // Saved, the page shows what its host stored and has nothing left to save.
        click("time_settings_save")
        compose.onNodeWithTag("time_settings_goal_note").assertTextEquals("90 minutes in a week earn a token. More minutes earn up to 2 tokens a week.")
        compose.onNodeWithTag("time_settings_save").assertIsNotEnabled()

        show("MadeTimeSettingsPage, Reading, Reminders")
        compose.onNodeWithTag("time_settings_tab_reminders").assertIsSelected()

        show("MadeTimeSettingsPage, Reading, Streak")
        compose.onNodeWithTag("time_settings_tab_streak").assertIsSelected()
        compose.onNodeWithTag("time_settings_rewards").assertIsOn()
        compose.onNodeWithTag("time_settings_every_kept_days_value").assertDoesNotExist()
        compose.onNodeWithTag("time_settings_max_passes_value").assertTextEquals("3")
        compose.onNodeWithTag("time_settings_away_due").assertIsSelected()
        compose.onNodeWithTag("time_settings_step_1_minutes").assertTextEquals("60 min")
        compose.onNodeWithTag("time_settings_step_2_percent").assertExists()
        compose.onNodeWithTag("time_settings_step_2_tokens_value").assertTextEquals("+1")
    }

    @Test
    fun thePartsOfItsOwnAndAJoinedViewAreOnShow() {
        compose.setContent { WorkoutTrackerTheme { shown?.let { specimen -> key(specimen.name) { specimen.content() } } } }

        show("TimeGoalPart, a time view's Goal part")
        compose.onNodeWithTag("gallery_time_goal").assertExists()
        compose.onNodeWithTag("gallery_time_goal").performTextReplacement("120")
        compose.waitForIdle()
        compose.onNodeWithTag("gallery_time_goal_note").assertTextEquals("120 minutes in a week earn a token. More minutes earn up to 2 tokens a week.")

        show("TimeGoalPart, one step")
        compose.onNodeWithTag("gallery_time_goal_note").assertTextEquals("90 minutes in a week earn 3 tokens.")
        show("TimeGoalPart, five steps")
        compose.onNodeWithTag("gallery_time_goal_note").assertTextEquals("90 minutes in a week earn a token. More minutes earn up to 10 tokens a week.")

        show("TimeStreak, a made view's Streak part")
        compose.onNodeWithTag("gallery_time_streak_rewards").assertIsOn()
        compose.onNodeWithTag("gallery_time_streak_max_passes_value").assertTextEquals("3")
        click("gallery_time_streak_max_passes_less")
        compose.onNodeWithTag("gallery_time_streak_max_passes_value").assertTextEquals("2")
        compose.onNodeWithTag("gallery_time_streak_step_1_minutes").assertTextEquals("90 min")
        compose.onNodeWithTag("gallery_time_streak_step_2_minutes").assertTextEquals("180 min")
        compose.onNodeWithTag("gallery_time_streak_step_2_tokens_value").assertTextEquals("+1")
        compose.onNodeWithTag("gallery_time_streak_steps_total").assertTextEquals("A week of 180 minutes or more earns 2 tokens.")
        click("gallery_time_streak_step_2_tokens_more")
        compose.onNodeWithTag("gallery_time_streak_step_2_tokens_value").assertTextEquals("+2")
        click("gallery_time_streak_add_step")
        compose.onNodeWithTag("gallery_time_streak_step_3_minutes").assertTextEquals("270 min")
        compose.onNodeWithTag("gallery_time_streak_step_3_tokens_value").assertTextEquals("+1")
        compose.onNodeWithTag("gallery_time_streak_steps_total").assertTextEquals("A week of 270 minutes or more earns 4 tokens.")

        show("TimeStreak, Reading's Streak part")
        compose.onNodeWithTag("gallery_reading_streak_rewards").assertIsOn()
        compose.onNodeWithTag("gallery_reading_streak_max_passes_value").assertTextEquals("3")
        compose.onNodeWithTag("gallery_reading_streak_away_due").assertIsSelected()
        compose.onNodeWithTag("gallery_reading_streak_step_1_minutes").assertTextEquals("60 min")
        compose.onNodeWithTag("gallery_reading_streak_step_2_minutes").assertTextEquals("120 min")

        // One step has no remove; five has no step to add; a refused percent says why and holds the button back too. The button stays where it is.
        show("TimeStreak, one step")
        compose.onNodeWithTag("gallery_time_streak_step_1_tokens_value").assertTextEquals("+3")
        compose.onNodeWithTag("gallery_time_streak_step_1_remove").assertDoesNotExist()
        compose.onNodeWithTag("gallery_time_streak_step_2_percent").assertDoesNotExist()
        compose.onNodeWithTag("gallery_time_streak_steps_total").assertTextEquals("A week of 90 minutes or more earns 3 tokens.")
        compose.onNodeWithTag("gallery_time_streak_add_step").assertIsEnabled()
        show("TimeStreak, five steps")
        compose.onNodeWithTag("gallery_time_streak_step_5_minutes").assertTextEquals("900 min")
        compose.onNodeWithTag("gallery_time_streak_step_5_tokens_value").assertTextEquals("+5")
        compose.onNodeWithTag("gallery_time_streak_steps_total").assertTextEquals("A week of 900 minutes or more earns 10 tokens.")
        compose.onNodeWithTag("gallery_time_streak_add_step").assertIsNotEnabled()
        show("TimeStreak, a refused percent")
        compose.onNodeWithTag("gallery_time_streak_step_2_percent").assert(hasText("101–1000"))
        compose.onNodeWithTag("gallery_time_streak_add_step").assertIsNotEnabled()

        // Mo's flavor is one control: a time view is offered Plain and Reading, a checklist Plain and every motif.
        show("MoFlavorPicker, a time view's flavors")
        compose.onNodeWithTag("gallery_time_flavor_motif_none").assertIsSelected()
        compose.onNodeWithTag("gallery_time_flavor_motif_pills").assertDoesNotExist()
        click("gallery_time_flavor_motif_reading")
        compose.onNodeWithTag("gallery_time_flavor_motif_reading").assertIsSelected()
        show("MoFlavorPicker, a checklist's flavors")
        listOf("pills", "teeth", "food", "running", "reading", "scale").forEach { compose.onNodeWithTag("gallery_checklist_flavor_motif_$it").assertExists() }

        show("HomeScreen, a time view that joined")
        compose.onNodeWithTag("home_time_${DemoPiano.id}").assertExists()
        show("MoStreakScreen, a time view that joined")
        compose.onNodeWithTag("streak_time_${DemoPiano.id}").assertExists()
    }
}
