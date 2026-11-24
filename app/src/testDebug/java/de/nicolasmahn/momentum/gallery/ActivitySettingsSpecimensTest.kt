package de.nicolasmahn.momentum.gallery

import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import de.nicolasmahn.momentum.app.AppDestination
import de.nicolasmahn.momentum.core.ui.theme.WorkoutTrackerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The gallery shows Activity's page on each part and, on Activities, at each level, and the
 * parts of its own in the states a person meets: a long list, a name in use, a refused number, a
 * long routine, a weightless exercise, a week planned on every day and steps in days. Each
 * specimen is the real component, opened where its name says.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h2400dp-xhdpi")
class ActivitySettingsSpecimensTest {
    @get:Rule
    val compose = createComposeRule()

    private val specimens = viewSpecimens(AppDestination.Activity).associateBy { it.name }
    private var shown by mutableStateOf<GallerySpecimen?>(null)

    private fun show(name: String) {
        shown = specimens.getValue(name)
        compose.waitForIdle()
    }

    @Test
    fun everySpecimenOpensWhereItsNameSays() {
        compose.setContent { WorkoutTrackerTheme { shown?.let { specimen -> key(specimen.name) { specimen.content() } } } }

        show("ActivitySettingsPage, Look")
        compose.onNodeWithTag("feature_settings_tab_look").assertIsSelected()
        show("ActivitySettingsPage, Activities")
        compose.onNodeWithTag("feature_settings_activity_gym").assertTextContains("Routine · 3 days")
        show("ActivitySettingsPage, Activities, the gym's page")
        compose.onNodeWithTag("feature_settings_activity_gym_day_A").assertExists()
        show("ActivitySettingsPage, Activities, the gym's Day A")
        compose.onNodeWithTag("feature_settings_day_A_name").assertExists()
        show("ActivitySettingsPage, Week")
        compose.onNodeWithTag("feature_settings_tab_week").assertIsSelected()
        compose.onNodeWithTag("feature_settings_week_plan_1").assertExists()
        show("ActivitySettingsPage, Streak")
        compose.onNodeWithTag("feature_settings_step_1_days").assertExists()

        show("ActivitiesPart, a long list")
        compose.onNodeWithTag("gallery_activity_activity_act_more_7").assertTextContains("Golf driving range")
        show("ActivitiesPart, an activity without a routine")
        compose.onNodeWithTag("gallery_activity_activity_bouldering_add_routine").assertExists()
        show("ActivitiesPart, a name in use, the list")
        compose.onNodeWithTag("gallery_activity_activity_cycling").assertTextContains("Something to fix")
        show("ActivitiesPart, a name in use, Cycling's page")
        compose.onNodeWithTag("gallery_activity_activity_cycling_name").assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, "Already in use"))
        show("ActivitiesPart, a refused rep range on Day A")
        compose.onNodeWithTag("gallery_activity_exercise_bench_press_reps_from").assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, "At most 10"))
        show("ActivitiesPart, a long routine's day")
        compose.onNodeWithTag("gallery_activity_exercise_ex_long_12").assertExists()
        show("ActivitiesPart, an exercise done without a weight")
        compose.onNodeWithTag("gallery_activity_exercise_bench_press_plan").assert(SemanticsMatcher("no step") { node -> node.config[SemanticsProperties.Text].none { "kg" in it.text } })
        show("ActivityWeek, something planned on every day")
        compose.onNodeWithTag("gallery_activity_week_plan_7").assertTextContains("Sunday").assertTextContains("Bouldering")
        show("ActivityStreak, out of the streak since last week")
        compose.onNodeWithTag("gallery_activity_rewards").assertIsOff()
        compose.onNodeWithTag("gallery_activity_step_1_days").assertDoesNotExist()
        compose.onNodeWithTag("gallery_activity_away_due").assertExists()
        show("ActivityStreak, steps at the days and two days more")
        compose.onNodeWithTag("gallery_activity_step_3_days").assertTextContains("7 days")
        show("IconGrid, the longest labels on one line in English and German, at full width and at 320 dp")
        compose.onNodeWithTag("gallery_icons_narrow_de_icon_martial_arts").assertTextContains("Kampfsport")
        show("SettingsLinkRow, an icon in a color, a line kept at its tallest, and an action")
        compose.onNodeWithTag("gallery_link_plain").assertTextContains("Cycling").assertTextContains("No routine")
    }
}
