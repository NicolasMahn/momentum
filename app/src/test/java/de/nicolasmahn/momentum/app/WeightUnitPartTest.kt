package de.nicolasmahn.momentum.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.AnnotatedString
import de.nicolasmahn.momentum.core.time.currentDateKey
import de.nicolasmahn.momentum.core.ui.theme.WorkoutTrackerTheme
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.measurement.MeasurementLog
import de.nicolasmahn.momentum.domain.measurement.MeasurementStep
import de.nicolasmahn.momentum.domain.compatibility.WeightMeasurementId
import de.nicolasmahn.momentum.domain.measurement.testWeightMeasurement
import de.nicolasmahn.momentum.domain.compatibility.weightMeasurement
import de.nicolasmahn.momentum.domain.measurement.withWeight
import de.nicolasmahn.momentum.domain.questions.withEating
import de.nicolasmahn.momentum.domain.reminder.ReminderSettings
import de.nicolasmahn.momentum.domain.settings.DashboardFeature
import de.nicolasmahn.momentum.domain.settings.FeatureVisibilitySettings
import de.nicolasmahn.momentum.feature.weight.transcript
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import de.nicolasmahn.momentum.domain.time.withReading
import de.nicolasmahn.momentum.domain.activity.withActivity

/**
 * Weight's page has the Unit part a made measurement's has, between Look and Reminders, over the
 * page's one draft and its one Save. Its unit and its step are stored like any other change, no
 * record is converted, and from then on Weight's screen and Statistics speak in the new unit. Text
 * that is no unit or no step stays in its field and holds Save back, on Weight's page too.
 */
@RunWith(RobolectricTestRunner::class)
class WeightUnitPartTest {
    @get:Rule
    val compose = createComposeRule()

    private val records = listOf(
        MeasurementLog(WeightMeasurementId, currentDateKey(), 2L, 181.5),
        MeasurementLog(WeightMeasurementId, "2026-01-05", 1L, 183.5),
    )
    private var state by mutableStateOf(AppState(measurementLogs = records).withWeight().withEating().withReading().withActivity())
    private var reminders by mutableStateOf(ReminderSettings())

    private val weight get() = requireNotNull(state.weightMeasurement)

    @Composable
    private fun App() {
        WorkoutTrackerTheme {
            MomentumApp(
                state = state,
                onStateChange = { state = it; true },
                onActivitySave = ActivitySaveNotExpected,
                // Statistics starts hidden, and one flow follows Weight's unit into it.
                featureVisibility = FeatureVisibilitySettings(disabledFeatures = emptySet()),
                reminderSettings = reminders,
                onReminderSettingsChange = { reminders = it; true },
            )
        }
    }

    private fun click(tag: String) {
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private fun type(tag: String, text: String) {
        compose.onNodeWithTag(tag).performTextReplacement(text)
        compose.waitForIdle()
    }

    private fun holds(text: String) = SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(text))

    private val refused = SemanticsMatcher.keyIsDefined(SemanticsProperties.Error)

    private fun openUnit() {
        click("home_settings")
        click("settings_group_measurement_weight")
        compose.onNodeWithTag("measurement_settings").assertExists()
        click("measurement_settings_tab_unit")
        compose.onNodeWithTag("measurement_settings_tab_unit").assertIsSelected()
    }

    private fun assertNoKilograms(shown: List<String>) = shown.forEach { line ->
        listOf("kg", "kilogram").forEach { word -> assertFalse("\"$line\" says \"$word\"", line.lowercase().contains(word)) }
    }

    @Test
    fun weightsPageHasTheUnitPartBetweenLookAndReminders() {
        compose.setContent { App() }
        click("home_settings")
        click("settings_group_measurement_weight")

        compose.onNodeWithTag("measurement_settings_tab_look").assertTextEquals("Look").assertIsSelected()
        compose.onNodeWithTag("measurement_settings_tab_unit").assertTextEquals("Unit")
        compose.onNodeWithTag("measurement_settings_tab_reminders").assertTextEquals("Reminders")
        compose.onNodeWithTag("measurement_settings_tab_streak").assertTextEquals("Streak")
        compose.onNodeWithTag("measurement_settings_tab_items").assertDoesNotExist()
        compose.onNodeWithTag("measurement_settings_unit").assertDoesNotExist()

        click("measurement_settings_tab_unit")
        compose.onNodeWithTag("measurement_settings_unit").assert(holds("kg")).assert(!refused)
        compose.onNodeWithTag("measurement_settings_step").assert(holds("0.1")).assert(!refused)
        compose.onNodeWithTag("measurement_settings_unit_note").assertTextEquals("Changing the unit converts nothing: the numbers stay as they were recorded.")
        // Untouched, there is nothing to save and nothing to ask about.
        compose.onNodeWithTag("measurement_settings_save").assertIsNotEnabled()
        click("top_back")
        compose.onNodeWithTag("measurement_settings_discard_dialog").assertDoesNotExist()
        compose.onNodeWithTag("measurement_settings").assertDoesNotExist()
    }

    /** The unit and the step are stored by the page's one Save, convert no record, and are what Weight speaks in from then on. */
    @Test
    fun aChangedUnitAndStepAreStoredAndWeightSpeaksInThem() {
        compose.setContent { App() }
        openUnit()

        type("measurement_settings_unit", "lb")
        type("measurement_settings_step", "0,5")
        compose.onNodeWithTag("measurement_settings_save").assertIsEnabled()
        assertEquals(testWeightMeasurement(), weight)
        click("measurement_settings_save")

        assertEquals(testWeightMeasurement().copy(unit = "lb", step = MeasurementStep("0.5")), weight)
        assertEquals(records, state.measurementLogs)
        compose.onNodeWithTag("measurement_settings").assertDoesNotExist()

        // Its screen: the words of its kind with its name and the unit, the buttons in the new step, no kilograms.
        click("top_back")
        click("home_measurement_weight")
        val screen = compose.transcript()
        assertNoKilograms(screen)
        assertTrue(screen.toString(), "text=\"Weight in ⁨lb⁩\"" in screen)
        assertTrue(screen.toString(), "[measurement_value] field=\"181.5\"" in screen)
        assertEquals(listOf("text=\"−0.5 ⁨lb⁩\"", "text=\"+0.5 ⁨lb⁩\""), screen.filter { it.startsWith("text=\"−") || it.startsWith("text=\"+") })
        assertTrue(screen.toString(), "spoken=\"181.5 lb\"" in screen)

        // Statistics: the caption and the chart say the unit too.
        click("top_back")
        click("home_statistics")
        val statistics = compose.transcript()
        assertNoKilograms(statistics)
        assertTrue(statistics.toString(), "text=\"Weight trend · now 181.5 ⁨lb⁩\"" in statistics)

        // Back on the page the part shows what is stored, and kilograms bring Weight's own words back.
        click("top_back")
        openUnit()
        compose.onNodeWithTag("measurement_settings_unit").assert(holds("lb"))
        compose.onNodeWithTag("measurement_settings_step").assert(holds("0.5"))
        type("measurement_settings_unit", "kg")
        click("measurement_settings_save")
        assertEquals(testWeightMeasurement().copy(step = MeasurementStep("0.5")), weight)
        click("top_back")
        click("home_measurement_weight")
        assertTrue(compose.transcript().toString(), "text=\"Weight in ⁨kg⁩\"" in compose.transcript())
        assertTrue(compose.transcript().toString(), "spoken=\"181.5 kg\"" in compose.transcript())
    }

    /** Without a unit Weight is stored without one, which every measurement may be. */
    @Test
    fun weightsUnitCanBeEmptied() {
        compose.setContent { App() }
        openUnit()

        type("measurement_settings_unit", "")
        compose.onNodeWithTag("measurement_settings_unit").assert(!refused)
        click("measurement_settings_save")

        assertEquals(testWeightMeasurement().copy(unit = ""), weight)
        click("top_back")
        click("home_measurement_weight")
        assertNoKilograms(compose.transcript())
        assertTrue(compose.transcript().toString(), "text=\"Weight\"" in compose.transcript())
    }

    /**
     * Text that is no step, or no unit, stays in its field across process death, says why, holds
     * the page's Save back whatever else was changed, and is asked about before the page is left.
     * Nothing is stored in its place.
     */
    @Test
    fun aRefusedStepOrUnitHoldsWeightsSaveBackAndIsAskedAboutBeforeLeaving() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { App() }
        openUnit()

        type("measurement_settings_step", "0.125")
        compose.onNodeWithTag("measurement_settings_step").assert(holds("0.125")).assert(refused)
        compose.onNodeWithTag("measurement_settings_save").assertIsNotEnabled()
        // A change on another part does not free Save while the step is refused.
        click("measurement_settings_tab_look")
        type("measurement_settings_name", "Scale")
        compose.onNodeWithTag("measurement_settings_save").assertIsNotEnabled()
        click("measurement_settings_tab_unit")

        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()

        compose.onNodeWithTag("measurement_settings_tab_unit").assertIsSelected()
        compose.onNodeWithTag("measurement_settings_step").assert(holds("0.125")).assert(refused)
        compose.onNodeWithTag("measurement_settings_save").assertIsNotEnabled()
        assertEquals(testWeightMeasurement(), weight)
        click("top_back")
        compose.onNodeWithTag("measurement_settings_discard_dialog").assertExists()
        click("measurement_settings_discard_dialog_stay")

        // A unit that cannot be one holds Save back on its own as well.
        type("measurement_settings_step", "0.25")
        compose.onNodeWithTag("measurement_settings_save").assertIsEnabled()
        type("measurement_settings_unit", "kilograms and more")
        compose.onNodeWithTag("measurement_settings_unit").assert(refused)
        compose.onNodeWithTag("measurement_settings_save").assertIsNotEnabled()
        type("measurement_settings_unit", "st")
        click("measurement_settings_save")

        assertEquals(testWeightMeasurement().copy(label = "Scale", unit = "st", step = MeasurementStep("0.25")), weight)
        assertEquals(records, state.measurementLogs)
    }
}
