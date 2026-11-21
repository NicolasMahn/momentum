package com.example.workouttracker.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import com.example.workouttracker.core.ui.theme.WorkoutTrackerTheme
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.activity.withActivity
import com.example.workouttracker.domain.checklist.deleteChecklist
import com.example.workouttracker.domain.compatibility.WeightMeasurementId
import com.example.workouttracker.domain.measurement.deleteMeasurement
import com.example.workouttracker.domain.measurement.withWeight
import com.example.workouttracker.domain.compatibility.SupplementsChecklistId
import com.example.workouttracker.domain.supplement.YearRoundSupplementIds
import com.example.workouttracker.domain.supplement.supplementDay
import com.example.workouttracker.domain.compatibility.supplementsChecklist
import com.example.workouttracker.domain.supplement.withSupplements
import com.example.workouttracker.feature.weight.transcript
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Weight, Eating, Reading, Supplements and Care are views of their kind: once deleted, or on a new
 * installation that never had them, nothing a person sees shows them. No tile, no row in
 * Settings, no screen from a request, and a screen open at the moment of the delete leaves.
 */
@RunWith(RobolectricTestRunner::class)
class DeletedViewsTest {
    @get:Rule
    val compose = createComposeRule()

    private var state by mutableStateOf(AppState())
    private var request by mutableStateOf<AppNavigationRequest?>(null)

    private val theFive = listOf("weight", "supplements", "reading", "eating", "care")

    private fun show(initial: AppState) {
        state = initial
        compose.setContent {
            WorkoutTrackerTheme {
                MomentumApp(
                    state = state,
                    onStateChange = { state = it; true },
                    onActivitySave = ActivitySaveNotExpected,
                    onChecklistDelete = { id -> state = state.deleteChecklist(id); true },
                    requestedNavigation = request,
                    onDestinationConsumed = { request = null },
                )
            }
        }
    }

    private fun click(tag: String) {
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private fun onHome() {
        compose.onNodeWithTag("home_settings").assertExists()
        compose.onNodeWithTag("top_back").assertDoesNotExist()
    }

    /** A new installation holds Activity alone: Home and Settings show it, Substances and nothing of the five, and none of them opens. */
    @Test
    fun aStateWithoutTheFiveShowsNoneOfThemAndOpensNone() {
        show(AppState().withActivity())

        compose.onNodeWithTag("home_log_activity").assertExists()
        compose.onNodeWithTag("home_substances").assertExists()
        theFive.forEach { compose.onNodeWithTag("home_$it").assertDoesNotExist() }

        listOf(
            AppNavigationRequest(AppDestination.Measurement, loggedViewId = "weight"),
            AppNavigationRequest(AppDestination.Checklist, checklistId = "supplements"),
            AppNavigationRequest(AppDestination.Time, loggedViewId = "reading"),
            AppNavigationRequest(AppDestination.Questions, loggedViewId = "eating"),
            AppNavigationRequest(AppDestination.Checklist, checklistId = "care"),
        ).forEach { asked ->
            request = asked
            compose.waitForIdle()
            onHome()
        }

        click("home_settings")
        compose.onNodeWithTag("settings_group_activity").assertExists()
        compose.onNodeWithTag("settings_group_substances").assertExists()
        theFive.forEach { compose.onNodeWithTag("settings_group_$it").assertDoesNotExist() }
    }

    /**
     * Supplements' page offers Delete as any checklist's does, asking first with its recorded
     * days; deleted, its page closes and it has no row, no tile and no screen.
     */
    @Test
    fun supplementsDeletedFromItsPageIsGoneFromSettingsAndHome() {
        show(AppState().withActivity().withSupplements(supplementDay("2026-10-12", 1L, YearRoundSupplementIds), supplementDay("2026-10-13", 2L, YearRoundSupplementIds)))
        compose.onNodeWithTag("home_checklist_supplements").assertExists()

        click("home_settings")
        click("settings_group_checklist_supplements")
        compose.onNodeWithTag("checklist_editor_delete").assertIsEnabled()
        compose.onNodeWithTag("checklist_editor_delete_reason").assertDoesNotExist()
        click("checklist_editor_delete")
        compose.onNodeWithTag("checklist_editor_delete_dialog").assertExists()
        assertTrue(compose.transcript().toString(), "text=\"Its settings and 2 recorded days are deleted. This cannot be undone.\"" in compose.transcript())
        click("checklist_editor_delete_confirm")

        assertNull(state.supplementsChecklist)
        assertEquals(emptyList<Any>(), state.checklistDays.filter { it.checklistId == SupplementsChecklistId })
        compose.onNodeWithTag("checklist_editor").assertDoesNotExist()
        compose.onNodeWithTag("settings_group_checklist_supplements").assertDoesNotExist()
        click("top_back")
        onHome()
        compose.onNodeWithTag("home_checklist_supplements").assertDoesNotExist()
        request = AppNavigationRequest(AppDestination.Checklist, checklistId = SupplementsChecklistId)
        compose.waitForIdle()
        onHome()
    }

    /** Weight's screen open while Weight is deleted has nothing left to show, so the app goes back to Home. */
    @Test
    fun aScreenOpenWhileItsViewIsDeletedLeaves() {
        show(AppState().withActivity().withWeight())
        click("home_measurement_weight")
        compose.onNodeWithTag("measurement_value").assertExists()

        state = state.deleteMeasurement(WeightMeasurementId)
        compose.waitForIdle()
        onHome()
        compose.onNodeWithTag("home_measurement_weight").assertDoesNotExist()
    }
}
