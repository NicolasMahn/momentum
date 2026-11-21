package com.example.workouttracker.app

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import com.example.workouttracker.core.time.currentDateKey
import com.example.workouttracker.core.ui.theme.WorkoutTrackerTheme
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.measurement.testMadeMeasurement
import com.example.workouttracker.domain.measurement.withWeight
import com.example.workouttracker.domain.settings.FeatureVisibilitySettings
import com.example.workouttracker.domain.ref
import com.example.workouttracker.core.time.currentHabitDate
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager

/**
 * A measurement a person made has the switch a made checklist has. Hidden, it has no tile and its
 * screen cannot be opened, by its tile or by a reminder's tap; its settings stay open. Shown
 * again it is back as it was.
 */
@RunWith(RobolectricTestRunner::class)
class MadeMeasurementHidingTest {
    @get:Rule
    val compose = createComposeRule()

    private val waist = testMadeMeasurement()
    private val mood = testMadeMeasurement(id = "msr_m7xd", label = "Mood", unit = "", step = "1").let { it.copy(checkpoint = it.checkpoint.copy(remind = true)) }
    private val state = AppState().withWeight().let { it.copy(measurements = it.measurements + waist + mood) }
    private var visibility by mutableStateOf(FeatureVisibilitySettings())
    private var request by mutableStateOf<AppNavigationRequest?>(null)
    private val changes = mutableListOf<FeatureVisibilitySettings>()
    private val today = currentHabitDate()

    @Before fun allowRemindersToArrive() {
        shadowOf(ApplicationProvider.getApplicationContext<Context>() as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    @Composable
    private fun App() {
        WorkoutTrackerTheme {
            MomentumApp(
                state = state,
                onStateChange = { true },
                onActivitySave = ActivitySaveNotExpected,
                featureVisibility = visibility,
                onFeatureVisibilityChange = { changed ->
                    changes += changed
                    visibility = changed
                    true
                },
                requestedNavigation = request,
                onDestinationConsumed = { request = null },
            )
        }
    }

    private fun click(tag: String) {
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    @Test
    fun aHiddenMeasurementHasNoTileAndNoScreenAndKeepsItsSettings() {
        compose.setContent { App() }
        compose.onNodeWithTag("home_measurement_${waist.id}").assertExists()

        click("home_settings")
        compose.onNodeWithTag("settings_feature_measurement_${waist.id}").assertIsOn()
        click("settings_feature_measurement_${waist.id}")
        assertEquals(listOf(FeatureVisibilitySettings().withVisibility(waist.ref, visible = false, today = today)), changes)
        compose.onNodeWithTag("settings_feature_measurement_${waist.id}").assertIsOff()
        compose.onNodeWithTag("settings_feature_measurement_${mood.id}").assertIsOn()

        // Its tile is gone and the other's is not; a reminder's tap falls back as for a missing one.
        click("top_back")
        compose.onNodeWithTag("home_measurement_${waist.id}").assertDoesNotExist()
        compose.onNodeWithTag("home_measurement_${mood.id}").assertExists()
        request = AppNavigationRequest(AppDestination.Measurement, loggedViewId = waist.id, loggedDateKey = currentDateKey())
        compose.waitForIdle()
        compose.onNodeWithTag("measurement_value").assertDoesNotExist()
        compose.onNodeWithTag("home_settings").assertExists()

        // Its settings stay open, and shown again it is back on Home.
        click("home_settings")
        click("settings_group_measurement_${waist.id}")
        compose.onNodeWithTag("measurement_settings").assertExists()
        click("top_back")
        click("settings_feature_measurement_${waist.id}")
        assertEquals(FeatureVisibilitySettings(), changes.last())
        click("top_back")
        click("home_measurement_${waist.id}")
        compose.onNodeWithTag("measurement_value").assertExists()
    }

    /** Hidden while its screen is open, the screen is left for Home at once. */
    @Test
    fun hidingAMeasurementWhoseScreenIsOpenLeavesIt() {
        compose.setContent { App() }
        click("home_measurement_${waist.id}")
        compose.onNodeWithTag("measurement_value").assertExists()

        visibility = FeatureVisibilitySettings().withVisibility(waist.ref, visible = false, today = today)
        compose.waitForIdle()

        compose.onNodeWithTag("measurement_value").assertDoesNotExist()
        compose.onNodeWithTag("home_settings").assertExists()
    }

    /**
     * Showing a measurement that reminds brings its reminder back, so the phone is asked first for
     * what that needs: without exact alarms the change waits for the answer instead of being
     * stored. Hiding one, and showing one that does not remind, ask for nothing.
     */
    @Test
    fun showingAMeasurementThatRemindsAsksForWhatItsReminderNeeds() {
        val bothHidden = FeatureVisibilitySettings(hiddenMeasurements = setOf(waist.id, mood.id))
        visibility = bothHidden
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        compose.setContent { App() }
        click("home_settings")

        click("settings_feature_measurement_${mood.id}")
        assertEquals(emptyList<FeatureVisibilitySettings>(), changes)
        compose.onNodeWithTag("settings_feature_measurement_${mood.id}").assertIsOff()

        click("settings_feature_measurement_${waist.id}")
        assertEquals(listOf(bothHidden.withVisibility(waist.ref, visible = true, today = today)), changes)
        click("settings_feature_measurement_${waist.id}")
        assertEquals(bothHidden, changes.last())
    }
}
