package de.nicolasmahn.momentum.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.Espresso
import de.nicolasmahn.momentum.R
import de.nicolasmahn.momentum.TestCareChecklist
import de.nicolasmahn.momentum.TestEatingQuestions
import de.nicolasmahn.momentum.TestReadingTime
import de.nicolasmahn.momentum.TestSecondChecklist
import de.nicolasmahn.momentum.TestSupplementsChecklist
import de.nicolasmahn.momentum.TestWeightMeasurement
import de.nicolasmahn.momentum.core.ui.theme.WorkoutTrackerTheme
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.testfixtures.defaultWeightMeasurement
import de.nicolasmahn.momentum.testfixtures.weightMeasurement
import de.nicolasmahn.momentum.testing.appString
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * What every view's settings page has since they share one frame: Delete, which every view but
 * the built-ins can use, and a Save that stores on purpose. The stored settings are the proof.
 */
class ViewSettingsFlowTest {
    @get:Rule
    val composeRule = createComposeRule()

    /** Weight as it is seeded, its reminder on. */
    private val seededWeight = defaultWeightMeasurement(label = "Weight", remind = true)

    private var state by mutableStateOf(AppState())

    /** Whether the store refuses a save, as a write that failed does. */
    private var storeFails = false

    private val weight get() = requireNotNull(state.weightMeasurement)

    /** Weight's page as the app shows it, over its stored configuration. */
    private fun setApp(initial: AppState = AppState(measurements = listOf(seededWeight))) {
        state = initial
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
                )
            }
        }
    }

    /**
     * Activity and Substances, the two built-in views, stay, so their pages offer no Delete. Weight,
     * Eating and Reading are views of their kind, so theirs is there and live.
     */
    @Test
    fun deleteIsOfferedOnMadeViewsAlone() {
        setApp(AppState(measurements = listOf(TestWeightMeasurement), questions = listOf(TestEatingQuestions), time = listOf(TestReadingTime)))

        composeRule.onNodeWithTag("home_settings").performClick()
        listOf("substances" to "substance_settings", "activity" to "feature_settings").forEach { (view, page) ->
            composeRule.onNodeWithTag("settings_group_$view").performScrollTo().performClick()
            composeRule.onNodeWithTag(page).assertIsDisplayed()
            composeRule.onNodeWithTag("${page}_delete").assertDoesNotExist()
            composeRule.onNodeWithTag("top_back").performClick()
            composeRule.onNodeWithTag(page).assertDoesNotExist()
        }
        listOf("measurement_weight" to "measurement_settings", "time_reading" to "time_settings", "questions_eating" to "questions_settings").forEach { (view, page) ->
            composeRule.onNodeWithTag("settings_group_$view").performScrollTo().performClick()
            composeRule.onNodeWithTag(page).assertIsDisplayed()
            composeRule.onNodeWithTag("${page}_delete").assertIsDisplayed().assertIsEnabled()
            composeRule.onNodeWithTag("top_back").performClick()
            composeRule.onNodeWithTag(page).assertDoesNotExist()
        }
    }

    /** Any checklist can go, Supplements and Care as well as one a person made, so its Delete is live and the bar has no reason to give. */
    @Test
    fun everyChecklistHasAWorkingDeleteAndNoReason() {
        setApp(AppState(checklists = listOf(TestSupplementsChecklist, TestCareChecklist, TestSecondChecklist)))

        composeRule.onNodeWithTag("home_settings").performClick()
        listOf("checklist_supplements", "checklist_care", "checklist_${TestSecondChecklist.id}").forEach { row ->
            composeRule.onNodeWithTag("settings_group_$row").performScrollTo().performClick()
            composeRule.onNodeWithTag("checklist_editor_delete").assertIsDisplayed().assertIsEnabled()
            composeRule.onNodeWithTag("checklist_editor_delete_reason").assertDoesNotExist()

            composeRule.onNodeWithTag("checklist_editor_delete").performClick()
            composeRule.onNodeWithTag("checklist_editor_delete_dialog").assertIsDisplayed()
            composeRule.onNodeWithTag("checklist_editor_delete_cancel").performClick()
            composeRule.onNodeWithTag("top_back").performClick()
        }
    }

    /** Opens Weight's page from the Settings list on its Reminders part; the page opens on Look. */
    private fun openWeightsReminders() {
        composeRule.onNodeWithTag("settings_group_measurement_weight").performScrollTo().performClick()
        composeRule.onNodeWithTag("measurement_settings").assertIsDisplayed()
        composeRule.onNodeWithTag("measurement_settings_tab_reminders").performScrollTo().performClick()
        composeRule.onNodeWithTag("measurement_settings_tab_reminders").assertIsSelected()
    }

    /**
     * Weight's reminder is a draft: the switch changes the page, Save stores it and returns to the
     * list, and leaving with the change asks first. Switching off needs no permission, so the flow
     * meets no system screen.
     */
    @Test
    fun weightsReminderChangesOnlyOnceSaveIsPressedAndLeavingWithTheChangeAsksFirst() {
        setApp()
        composeRule.onNodeWithTag("home_settings").performClick()
        openWeightsReminders()

        // Untouched, there is nothing to save, and back leaves at once.
        composeRule.onNodeWithTag("measurement_settings_save").assertIsNotEnabled()
        Espresso.pressBack()
        composeRule.onNodeWithTag("measurement_settings_discard_dialog").assertDoesNotExist()
        composeRule.onNodeWithTag("measurement_settings").assertDoesNotExist()

        // The switch changes the page only; discarded, the change is gone and nothing was stored.
        openWeightsReminders()
        composeRule.onNodeWithTag("measurement_settings_reminder").performScrollTo().assertIsOn().performClick()
        composeRule.onNodeWithTag("measurement_settings_reminder").assertIsOff()
        composeRule.onNodeWithTag("measurement_settings_save").assertIsEnabled()
        composeRule.runOnIdle { assertTrue(weight.checkpoint.remind) }
        composeRule.onNodeWithTag("top_back").performClick()
        composeRule.onNodeWithTag("measurement_settings_discard_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("measurement_settings_discard_dialog_stay").performClick()
        composeRule.onNodeWithTag("measurement_settings_reminder").assertIsOff()
        Espresso.pressBack()
        composeRule.onNodeWithTag("measurement_settings_discard_dialog_confirm").performClick()
        composeRule.onNodeWithTag("measurement_settings").assertDoesNotExist()
        composeRule.runOnIdle { assertTrue(weight.checkpoint.remind) }

        // Save stores it and returns to the list.
        openWeightsReminders()
        composeRule.onNodeWithTag("measurement_settings_reminder").performScrollTo().assertIsOn().performClick()
        composeRule.onNodeWithTag("measurement_settings_save").performClick()
        composeRule.runOnIdle { assertFalse(weight.checkpoint.remind) }
        composeRule.onNodeWithTag("measurement_settings").assertDoesNotExist()

        // Opened again from the list, the page shows what was stored and has nothing left to save
        // or ask about.
        composeRule.onNodeWithTag("settings_group_measurement_weight").performScrollTo().assertIsDisplayed()
        openWeightsReminders()
        composeRule.onNodeWithTag("measurement_settings_reminder").performScrollTo().assertIsOff()
        composeRule.onNodeWithTag("measurement_settings_save").assertIsNotEnabled()
        composeRule.onNodeWithTag("measurement_settings").assertIsDisplayed()
        Espresso.pressBack()
        composeRule.onNodeWithTag("measurement_settings_discard_dialog").assertDoesNotExist()
        composeRule.onNodeWithTag("measurement_settings").assertDoesNotExist()
    }

    /**
     * A page's Save the store refuses stays on the page, with what Settings says went wrong
     * and the change still there to save; stored, the page returns to the list.
     */
    @Test
    fun aSaveThatFailsStaysOnWeightsPageWithItsError() {
        setApp()
        composeRule.onNodeWithTag("home_settings").performClick()
        openWeightsReminders()
        composeRule.onNodeWithTag("measurement_settings_reminder").performScrollTo().assertIsOn().performClick()

        storeFails = true
        composeRule.onNodeWithTag("measurement_settings_save").performClick()
        composeRule.onNodeWithTag("measurement_settings_error").assertIsDisplayed().assertTextEquals(appString(R.string.settings_error_view))
        composeRule.onNodeWithTag("measurement_settings").assertIsDisplayed()
        composeRule.onNodeWithTag("measurement_settings_reminder").assertIsOff()
        composeRule.onNodeWithTag("measurement_settings_save").assertIsEnabled()
        composeRule.runOnIdle { assertTrue(weight.checkpoint.remind) }

        storeFails = false
        composeRule.onNodeWithTag("measurement_settings_save").performClick()
        composeRule.runOnIdle { assertFalse(weight.checkpoint.remind) }
        composeRule.onNodeWithTag("measurement_settings").assertDoesNotExist()
        composeRule.onNodeWithTag("settings_group_measurement_weight").performScrollTo().assertIsDisplayed()
    }

    /**
     * A page that was closed is gone at once, and the list it returns to is there once. The slot
     * on its way out used to show the list as well: built twice, every return was slower, and one
     * list faded into another.
     */
    @Test
    fun aClosedPageIsGoneAtOnceAndTheListIsBuiltOnce() {
        setApp()
        composeRule.onNodeWithTag("home_settings").performClick()
        composeRule.onNodeWithTag("settings_group_measurement_weight").performScrollTo().performClick()
        composeRule.onNodeWithTag("measurement_settings").assertIsDisplayed()

        // Mid-way through the motion, where both screens are still drawn.
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag("top_back").performClick()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithTag("measurement_settings").assertDoesNotExist()
        composeRule.onAllNodesWithTag("settings_group_measurement_weight").assertCountEquals(1)

        composeRule.mainClock.autoAdvance = true
        composeRule.onAllNodesWithTag("settings_group_measurement_weight").assertCountEquals(1)
    }
}
