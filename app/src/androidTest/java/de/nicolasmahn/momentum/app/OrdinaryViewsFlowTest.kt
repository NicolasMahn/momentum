package de.nicolasmahn.momentum.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import de.nicolasmahn.momentum.R
import de.nicolasmahn.momentum.TestSupplementsChecklist
import de.nicolasmahn.momentum.TestWeightMeasurement
import de.nicolasmahn.momentum.YearRoundSupplementIds
import de.nicolasmahn.momentum.core.time.currentHabitDate
import de.nicolasmahn.momentum.core.ui.theme.WorkoutTrackerTheme
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.activity.SeededActivityRules
import de.nicolasmahn.momentum.domain.checklist.Dated
import de.nicolasmahn.momentum.domain.checklist.deleteChecklist
import de.nicolasmahn.momentum.domain.measurement.MeasurementLog
import de.nicolasmahn.momentum.testfixtures.WeightMeasurementId
import de.nicolasmahn.momentum.testfixtures.weightMeasurement
import de.nicolasmahn.momentum.testfixtures.TestTask
import de.nicolasmahn.momentum.domain.settings.FeatureVisibilitySettings
import de.nicolasmahn.momentum.domain.settings.NewInstallationVisibility
import de.nicolasmahn.momentum.testfixtures.SupplementsChecklistId
import de.nicolasmahn.momentum.testfixtures.supplementsChecklist
import de.nicolasmahn.momentum.supplementDay
import de.nicolasmahn.momentum.testing.appString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Weight, Eating, Reading, Supplements and Care are views of their kind: a new installation starts
 * with Activity alone, any of them goes with its records when deleted, and the Settings list's
 * switch hides one from the day it is pressed.
 */
class OrdinaryViewsFlowTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var state by mutableStateOf(AppState())
    private var visibility by mutableStateOf(FeatureVisibilitySettings())

    private fun setApp(initial: AppState, shown: FeatureVisibilitySettings = FeatureVisibilitySettings()) {
        state = initial
        visibility = shown
        composeRule.setContent {
            WorkoutTrackerTheme {
                MomentumApp(
                    state = state,
                    onStateChange = { state = it; true },
                    onActivitySave = ActivitySaveNotExpected,
                    featureVisibility = visibility,
                    onFeatureVisibilityChange = { visibility = it; true },
                    onChecklistDelete = { id -> state = state.deleteChecklist(id); true },
                    requestedNavigation = request,
                    onDestinationConsumed = { request = null },
                )
            }
        }
    }

    private var request by mutableStateOf<AppNavigationRequest?>(null)

    private val theFive = listOf("measurement_weight", "checklist_supplements", "time_reading", "questions_eating", "checklist_care")

    /** A new installation has nothing on Home but Settings; Activity, Substances and Statistics are there to turn on. */
    @Test
    fun aNewInstallationStartsWithNothingOnHome() {
        setApp(AppState(activity = SeededActivityRules), NewInstallationVisibility)

        composeRule.onNodeWithTag("home_log_activity").assertDoesNotExist()
        composeRule.onNodeWithTag("home_settings").assertIsDisplayed()
        composeRule.onNodeWithTag("home_substances").assertDoesNotExist()
        // Statistics is a place of the app, always there, as Settings is.
        composeRule.onNodeWithTag("home_statistics").assertIsDisplayed()
        theFive.forEach { composeRule.onNodeWithTag("home_$it").assertDoesNotExist() }

        composeRule.onNodeWithTag("home_settings").performClick()
        composeRule.onNodeWithTag("settings_feature_activity").performScrollTo().assertIsOff()
        composeRule.onNodeWithTag("settings_feature_substances").performScrollTo().assertIsOff()
        composeRule.onNodeWithTag("settings_feature_statistics").assertDoesNotExist()
        theFive.forEach { composeRule.onNodeWithTag("settings_group_$it").assertDoesNotExist() }
        composeRule.onNodeWithTag("settings_new").performScrollTo().assertIsDisplayed()
    }

    /** Deleted from its page, Weight goes with every record of it; Home has no tile for it, and a way to it lands on Home. */
    @Test
    fun weightDeletedFromItsPageIsGoneFromHome() {
        val today = currentHabitDate().toString()
        setApp(AppState(activity = SeededActivityRules, measurements = listOf(TestWeightMeasurement), measurementLogs = listOf(MeasurementLog(WeightMeasurementId, today, 1L, 81.0))))
        composeRule.onNodeWithTag("home_measurement_weight").assertIsDisplayed()

        composeRule.onNodeWithTag("home_settings").performClick()
        composeRule.onNodeWithTag("settings_group_measurement_weight").performScrollTo().performClick()
        composeRule.onNodeWithTag("measurement_settings_delete").performClick()
        composeRule.onNodeWithText(appString(R.string.measurement_settings_delete_title, "Weight")).assertIsDisplayed()
        composeRule.onNodeWithText(InstrumentationRegistry.getInstrumentation().targetContext.resources.getQuantityString(R.plurals.measurement_settings_delete_message, 1, 1)).assertIsDisplayed()
        composeRule.onNodeWithTag("measurement_settings_delete_confirm").performClick()

        composeRule.runOnIdle {
            assertNull(state.weightMeasurement)
            assertTrue(state.measurementLogs.isEmpty())
        }
        composeRule.onNodeWithTag("measurement_settings").assertDoesNotExist()
        composeRule.onNodeWithTag("settings_group_measurement_weight").assertDoesNotExist()
        composeRule.onNodeWithTag("top_back").performClick()
        composeRule.onNodeWithTag("home_measurement_weight").assertDoesNotExist()

        composeRule.runOnIdle { request = AppNavigationRequest(AppDestination.Measurement, loggedViewId = WeightMeasurementId) }
        composeRule.onNodeWithTag("home_log_activity").assertIsDisplayed()
        composeRule.onNodeWithTag("measurement_value").assertDoesNotExist()
    }

    /** Deleted from its editor, Supplements goes with its days; it has no row and no tile. */
    @Test
    fun supplementsDeletedFromItsEditorIsGone() {
        val today = currentHabitDate().toString()
        setApp(AppState(activity = SeededActivityRules, checklists = listOf(TestSupplementsChecklist), checklistDays = listOf(supplementDay(today, 1L, YearRoundSupplementIds))))
        composeRule.onNodeWithTag("home_checklist_supplements").assertIsDisplayed()

        composeRule.onNodeWithTag("home_settings").performClick()
        composeRule.onNodeWithTag("settings_group_checklist_supplements").performScrollTo().performClick()
        composeRule.onNodeWithTag("checklist_editor_delete").performClick()
        composeRule.onNodeWithTag("checklist_editor_delete_confirm").performClick()

        composeRule.runOnIdle {
            assertNull(state.supplementsChecklist)
            assertTrue(state.checklistDays.none { it.checklistId == SupplementsChecklistId })
        }
        composeRule.onNodeWithTag("checklist_editor").assertDoesNotExist()
        composeRule.onNodeWithTag("settings_group_checklist_supplements").assertDoesNotExist()
        composeRule.onNodeWithTag("top_back").performClick()
        composeRule.onNodeWithTag("home_checklist_supplements").assertDoesNotExist()
    }

    /** The Settings list's switch hides Weight from today, earlier days keeping what they were; switched back the same day, nothing is left to date. */
    @Test
    fun theListsSwitchHidesAViewFromToday() {
        val today = currentHabitDate()
        setApp(AppState(activity = SeededActivityRules, measurements = listOf(TestWeightMeasurement)))
        composeRule.onNodeWithTag("home_settings").performClick()

        composeRule.onNodeWithTag("settings_feature_measurement_weight").performScrollTo().assertIsOn().performClick()
        composeRule.onNodeWithTag("settings_feature_measurement_weight").assertIsOff()
        composeRule.runOnIdle {
            assertEquals(Dated(listOf(Dated.Revision(true), Dated.Revision(false, today))), visibility.visibilityOf(TestTask.Weight.view))
            assertTrue(visibility.isVisibleOn(TestTask.Weight.view, today.minusDays(1)))
        }

        composeRule.onNodeWithTag("settings_feature_measurement_weight").performClick()
        composeRule.runOnIdle { assertEquals(FeatureVisibilitySettings(), visibility) }
    }
}
