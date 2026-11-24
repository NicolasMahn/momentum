package de.nicolasmahn.momentum.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import de.nicolasmahn.momentum.core.time.currentDateKey
import de.nicolasmahn.momentum.core.ui.theme.WorkoutTrackerTheme
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.checklist.ChecklistItem
import de.nicolasmahn.momentum.domain.checklist.starterChecklist
import de.nicolasmahn.momentum.domain.measurement.MeasurementLog
import de.nicolasmahn.momentum.domain.measurement.MeasurementStep
import de.nicolasmahn.momentum.domain.compatibility.WeightMeasurementId
import de.nicolasmahn.momentum.domain.measurement.deleteMeasurement
import de.nicolasmahn.momentum.domain.measurement.recordsOf
import de.nicolasmahn.momentum.domain.measurement.testMadeMeasurement
import de.nicolasmahn.momentum.domain.measurement.withWeight
import de.nicolasmahn.momentum.feature.weight.transcript
import de.nicolasmahn.momentum.reminder.ReminderCompletionNotification
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A measurement a person made has a tile on Home that opens its screen. Which one is open is part
 * of where the app is: two made measurements are two places, the one selected survives process
 * death, and one that is gone falls back as a missing checklist does.
 */
@RunWith(RobolectricTestRunner::class)
class MadeMeasurementRouteTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = currentDateKey()
    private val waist = testMadeMeasurement()
    private val mood = testMadeMeasurement(id = "msr_m7xd", label = "Mood", unit = "", step = "1")
    private val plants = AppState().starterChecklist("Plants", LocalDate.parse(today)).let { it.copy(items = listOf(ChecklistItem("itm_water", "Water"))) }
    private val weights = listOf(MeasurementLog(WeightMeasurementId, today, 1L, 81.4))
    private var state by mutableStateOf(
        AppState(checklists = listOf(plants), measurementLogs = weights + MeasurementLog(mood.id, today, 2L, 7.0)).withWeight().let { it.copy(measurements = it.measurements + waist + mood) },
    )
    private val answered = mutableListOf<ReminderCompletionNotification>()

    @Composable
    private fun App() {
        WorkoutTrackerTheme { MomentumApp(state = state, onStateChange = { state = it; true }, onActivitySave = ActivitySaveNotExpected, onReminderCompletion = { answered += it }) }
    }

    private fun click(tag: String) {
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private fun said(text: String): Boolean = "text=\"$text\"" in compose.transcript()

    /** Where a tile sits in reading order: its row, then its column. */
    private fun place(tag: String): Pair<Float, Float> = compose.onNodeWithTag(tag).fetchSemanticsNode().positionInRoot.let { it.y to it.x }

    @Test
    fun measurementsFollowChecklistsAndRecordedFactsWearTheDoneBadge() {
        compose.setContent { App() }

        // What is still open comes before what is done today: Weight and Mood are recorded.
        val tiles = listOf("home_checklist_${plants.id}", "home_measurement_${waist.id}", "home_measurement_weight", "home_measurement_${mood.id}")
        val places = tiles.map(::place)
        assertEquals(places.sortedWith(compareBy({ it.first }, { it.second })), places)
        assertTrue(said("Waist") && said("Mood"))

        // Recorded facts show completion independently of reward enrollment.
        val status = SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription)
        compose.onNodeWithTag("home_measurement_weight").assert(status)
        compose.onNodeWithTag("home_measurement_${waist.id}").assert(!status)
        compose.onNodeWithTag("home_measurement_${mood.id}").assert(hasContentDescription("Done today"))
    }

    @Test
    fun twoMadeMeasurementsAreTwoPlacesEachWithItsOwnNumbers() {
        compose.setContent { App() }

        click("home_measurement_${waist.id}")
        // The unit stands apart from the words around it, so the label is found by its beginning.
        assertTrue(compose.transcript().toString(), said("Waist") && compose.transcript().any { "text=\"Waist in " in it })
        compose.onNodeWithTag("measurement_value").performTextReplacement("92.5")
        click("measurement_save")

        // Its day is stored under its ID, Weight's and Mood's records are as they were, and no reminder was answered.
        assertEquals(listOf(92.5), state.recordsOf(waist.id).map { it.value })
        assertEquals(weights + MeasurementLog(mood.id, today, 2L, 7.0), state.measurementLogs.filter { it.measurementId != waist.id })
        assertEquals(emptyList<ReminderCompletionNotification>(), answered)

        click("top_back")
        click("home_measurement_${mood.id}")
        val shown = compose.transcript()
        assertTrue(shown.toString(), "text=\"Mood\"" in shown && "[measurement_value] field=\"7\"" in shown)
        assertFalse(shown.toString(), shown.any { "92.5" in it || "81.4" in it || "Waist" in it })

        click("top_back")
        click("home_measurement_weight")
        assertTrue(compose.transcript().toString(), "[measurement_value] field=\"81.4\"" in compose.transcript())
    }

    @Test
    fun theOpenMeasurementSurvivesProcessDeath() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { App() }
        click("home_measurement_${mood.id}")

        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()

        val shown = compose.transcript()
        assertTrue(shown.toString(), "text=\"Mood\"" in shown && "[measurement_value] field=\"7\"" in shown)
        assertFalse(shown.toString(), shown.any { "Waist" in it })
    }

    /** Its page too, also while the way out waits behind the question about unsaved work. */
    @Test
    fun theOpenPageOfAMeasurementSurvivesProcessDeathWithAWayWaiting() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { App() }
        click("home_settings")
        click("settings_group_measurement_${mood.id}")
        click("measurement_settings_tab_unit")
        compose.onNodeWithTag("measurement_settings_step").performTextReplacement("0.01")
        click("measurement_settings_tab_look")
        click("top_back")
        compose.onNodeWithTag("measurement_settings_discard_dialog").assertExists()

        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()

        compose.onNodeWithTag("measurement_settings").assertExists()
        assertTrue(compose.transcript().toString(), "[measurement_settings_name] field=\"Mood\"" in compose.transcript())
        compose.onNodeWithTag("measurement_settings_discard_dialog").assertExists()
        click("measurement_settings_discard_dialog_confirm")
        compose.onNodeWithTag("measurement_settings").assertDoesNotExist()
        compose.onNodeWithTag("settings_group_measurement_${mood.id}").assertExists()
        assertEquals(MeasurementStep("1"), state.measurements.single { it.id == mood.id }.step)
    }

    @Test
    fun aMeasurementThatIsGoneFallsBackAsAMissingChecklistDoes() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { App() }

        // Its screen falls back to Home, where its tile is gone and the other's is not.
        click("home_measurement_${waist.id}")
        compose.onNodeWithTag("measurement_value").assertExists()
        state = state.deleteMeasurement(waist.id)
        compose.waitForIdle()
        compose.onNodeWithTag("measurement_value").assertDoesNotExist()
        compose.onNodeWithTag("home_measurement_${waist.id}").assertDoesNotExist()
        compose.onNodeWithTag("home_measurement_${mood.id}").assertExists()

        // Its page falls back to the Settings list.
        click("home_settings")
        click("settings_group_measurement_${mood.id}")
        compose.onNodeWithTag("measurement_settings").assertExists()
        state = state.deleteMeasurement(mood.id)
        compose.waitForIdle()
        compose.onNodeWithTag("measurement_settings").assertDoesNotExist()
        compose.onNodeWithTag("settings_new").assertExists()

        // And a place kept across process death for a measurement that is gone by then leads Home.
        state = state.copy(measurements = state.measurements + waist)
        click("top_back")
        click("home_measurement_${waist.id}")
        state = state.deleteMeasurement(waist.id)
        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        compose.onNodeWithTag("measurement_value").assertDoesNotExist()
        compose.onNodeWithTag("home_settings").assertExists()
    }
}
