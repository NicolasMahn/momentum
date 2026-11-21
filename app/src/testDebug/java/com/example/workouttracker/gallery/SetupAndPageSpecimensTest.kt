package com.example.workouttracker.gallery

import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.example.workouttracker.app.AppDestination
import com.example.workouttracker.core.ui.theme.WorkoutTrackerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The gallery shows a measurement's setup at each of its four steps, its page on each of its four
 * parts, the Unit part on its own and Weight's page on Unit and Streak, and a checklist's setup still, which
 * walks its four on the same stepping. Each specimen is the real component, opened where its name
 * says.
 */
@RunWith(RobolectricTestRunner::class)
class SetupAndPageSpecimensTest {
    @get:Rule
    val compose = createComposeRule()

    private val specimens = viewSpecimens(AppDestination.SettingsPage).associateBy { it.name }
    private var shown by mutableStateOf<GallerySpecimen?>(null)

    private fun show(name: String) {
        shown = specimens.getValue(name)
        compose.waitForIdle()
    }

    @Test
    fun eachSpecimenOpensWhereItsNameSays() {
        compose.setContent { WorkoutTrackerTheme { shown?.let { specimen -> key(specimen.name) { specimen.content() } } } }

        show("MeasurementSetup, step 1 Look")
        compose.onNodeWithTag("measurement_setup_tab_look").assertIsSelected().assertTextEquals("1 Look")
        compose.onNodeWithTag("measurement_setup_tab_unit").assertIsNotEnabled()
        compose.onNodeWithTag("measurement_setup_next").assertIsNotEnabled()

        show("MeasurementSetup, step 2 Unit")
        compose.onNodeWithTag("measurement_setup_tab_unit").assertIsSelected().assertTextEquals("2 Unit")
        compose.onNodeWithTag("measurement_setup_tab_reminders").assertIsEnabled()
        compose.onNodeWithTag("measurement_setup_unit").assertExists()
        compose.onNodeWithTag("measurement_setup_step").assertTextContains("0.1")
        compose.onNodeWithTag("measurement_setup_unit_note").assertExists()
        compose.onNodeWithTag("measurement_setup_next").assertIsEnabled()

        show("MeasurementSetup, step 3 Reminders")
        compose.onNodeWithTag("measurement_setup_tab_reminders").assertIsSelected().assertTextEquals("3 Reminders")
        compose.onNodeWithTag("measurement_setup_tab_streak").assertIsEnabled()
        compose.onNodeWithTag("measurement_setup_reminder_preview_title").assertTextEquals("Waist")
        compose.onNodeWithTag("measurement_setup_next").assertIsEnabled()

        show("MeasurementSetup, step 4 Streak")
        compose.onNodeWithTag("measurement_setup_tab_streak").assertIsSelected().assertTextEquals("4 Streak")
        compose.onNodeWithTag("measurement_setup_rewards").assertIsOn()
        compose.onNodeWithTag("measurement_setup_create").assertIsEnabled()

        show("MadeMeasurementSettingsPage, Look")
        compose.onNodeWithTag("measurement_settings_tab_look").assertIsSelected()
        compose.onNodeWithTag("measurement_settings_name").assertExists()
        compose.onNodeWithTag("measurement_settings_unit").assertDoesNotExist()

        show("MadeMeasurementSettingsPage, Unit")
        compose.onNodeWithTag("measurement_settings_tab_unit").assertIsSelected().assertTextEquals("Unit")
        compose.onNodeWithTag("measurement_settings_unit").assertTextContains("cm")
        compose.onNodeWithTag("measurement_settings_step").assertTextContains("0.1")
        compose.onNodeWithTag("measurement_settings_unit_note").assertExists()

        show("MeasurementUnitPart, a measurement's Unit part")
        compose.onNodeWithTag("gallery_measurement_unit").assertTextContains("cm")
        compose.onNodeWithTag("gallery_measurement_step").assertTextContains("0.1")
        compose.onNodeWithTag("gallery_measurement_unit_note").assertExists()

        show("MadeMeasurementSettingsPage, Reminders")
        compose.onNodeWithTag("measurement_settings_tab_reminders").assertIsSelected()
        compose.onNodeWithTag("measurement_settings_reminder_time_after_wake").assertIsSelected()
        compose.onNodeWithTag("measurement_settings_reminder_test").assertIsEnabled()

        show("MadeMeasurementSettingsPage, Reminders, hidden")
        compose.onNodeWithTag("measurement_settings_tab_reminders").assertIsSelected()
        compose.onNodeWithTag("measurement_settings_reminder_test").assertIsNotEnabled()

        show("MadeMeasurementSettingsPage, Streak")
        compose.onNodeWithTag("measurement_settings_tab_streak").assertIsSelected()
        compose.onNodeWithTag("measurement_settings_rewards").assertIsOn()

        show("MadeMeasurementSettingsPage, Weight, Unit")
        compose.onNodeWithTag("measurement_settings_tab_unit").assertIsSelected().assertTextEquals("Unit")
        compose.onNodeWithTag("measurement_settings_unit").assertTextContains("kg")
        compose.onNodeWithTag("measurement_settings_step").assertTextContains("0.1")
        compose.onNodeWithTag("measurement_settings_unit_note").assertExists()
        compose.onNodeWithTag("measurement_settings_delete").assertIsEnabled()

        // Weight's Streak part is a made view's: it counts and shows its seven tokens and six days in the steppers every
        // daily bank has, with the line that says the days still count within a week.
        show("MadeMeasurementSettingsPage, Weight, Streak")
        compose.onNodeWithTag("measurement_settings_rewards").assertIsOn()
        compose.onNodeWithTag("measurement_settings_max_passes_value").assertTextEquals("7")
        compose.onNodeWithTag("measurement_settings_every_kept_days_value").assertTextEquals("6 kept days")
        compose.onNodeWithTag("measurement_settings_earn_note").assertExists()
        compose.onNodeWithTag("measurement_settings_away_due").assertExists()

        show("ChecklistSetup")
        compose.onNodeWithTag("checklist_setup_step_look").assertIsSelected().assertTextEquals("1 Look")
        compose.onNodeWithTag("checklist_setup_step_streak").assertTextEquals("4 Streak")
        compose.onNodeWithTag("checklist_setup_next").assertIsNotEnabled()
        compose.onNodeWithTag("measurement_settings_rewards").assertDoesNotExist()
    }

    /** The page as it was made: its reminder is off and it is outside the streak. */
    @Test
    fun thePageOnLookIsAMeasurementAsItWasMade() {
        compose.setContent { WorkoutTrackerTheme { shown?.let { specimen -> key(specimen.name) { specimen.content() } } } }
        show("MadeMeasurementSettingsPage, Look")
        compose.onNodeWithTag("measurement_settings_save").assertIsNotEnabled()
        compose.onNodeWithTag("measurement_settings_delete").assertIsEnabled()
        compose.onNodeWithTag("measurement_settings_reminder").assertDoesNotExist()
        compose.onNodeWithTag("measurement_settings_rewards").assertDoesNotExist()
        compose.onNodeWithTag("measurement_settings_tab_reminders").assertIsEnabled()
    }
}
