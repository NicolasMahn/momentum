package com.example.workouttracker.app

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
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.AnnotatedString
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.workouttracker.R
import com.example.workouttracker.core.time.currentDateKey
import com.example.workouttracker.core.ui.theme.WorkoutTrackerTheme
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.checklist.Checkpoint
import com.example.workouttracker.domain.checklist.CheckpointTime
import com.example.workouttracker.domain.checklist.Dated
import com.example.workouttracker.domain.checklist.ViewHue
import com.example.workouttracker.domain.checklist.ViewIcon
import com.example.workouttracker.domain.checklist.WhenAway
import com.example.workouttracker.domain.measurement.MeasurementConfiguration
import com.example.workouttracker.domain.measurement.MeasurementLog
import com.example.workouttracker.domain.measurement.MeasurementStep
import com.example.workouttracker.domain.compatibility.WeightMeasurementId
import com.example.workouttracker.domain.measurement.applyingMeasurementEdits
import com.example.workouttracker.domain.compatibility.madeMeasurements
import com.example.workouttracker.domain.measurement.testMadeMeasurement
import com.example.workouttracker.domain.measurement.testWeightMeasurement
import com.example.workouttracker.domain.measurement.withWeight
import com.example.workouttracker.feature.settings.ViewWrite
import com.example.workouttracker.feature.weight.transcript
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Settings makes a measurement, changes it and deletes it, through the app as a person meets it:
 * the setup and the page keep what was typed, valid or not, across process death, hold Create and
 * Save back while it cannot be stored, and ask before it is lost.
 */
@RunWith(RobolectricTestRunner::class)
class MadeMeasurementSettingsTest {
    @get:Rule
    val compose = createComposeRule()

    private val weights = listOf(MeasurementLog(WeightMeasurementId, "2026-10-13", 1L, 81.4))
    private var state by mutableStateOf(AppState(measurementLogs = weights).withWeight())
    private val tooLong = "kilometres per hour"

    @Composable
    private fun App() {
        WorkoutTrackerTheme { MomentumApp(state = state, onStateChange = { state = it; true }, onActivitySave = ActivitySaveNotExpected) }
    }

    private fun click(tag: String) {
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    /** From the Settings list: the one button asks what kind, and the measurement's card opens its setup. */
    private fun openSetup() {
        click("settings_new")
        click("settings_measurement_new")
    }

    /** From Look, complete, to Unit, the second step, where the unit and the step are typed. */
    private fun openUnitStep() {
        click("measurement_setup_next")
        compose.onNodeWithTag("measurement_setup_tab_unit").assertIsSelected()
    }

    /** From Unit, complete: the two steps after it ask for nothing, and the last one creates. */
    private fun createFromUnit() {
        click("measurement_setup_next")
        click("measurement_setup_next")
        click("measurement_setup_create")
    }

    private fun type(tag: String, text: String) {
        compose.onNodeWithTag(tag).performTextReplacement(text)
        compose.waitForIdle()
    }

    private fun holds(text: String) = SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(text))

    private val refused = SemanticsMatcher.keyIsDefined(SemanticsProperties.Error)

    /**
     * A Save that is stored without the alarm it asked for keeps the page open on what is stored,
     * with nothing left to save, and says so; one that stored nothing says that instead and keeps
     * the edits. Neither is said to be the other.
     */
    @Test
    fun aSaveStoredWithoutItsAlarmShowsWhatIsStoredAndSaysSo() {
        val waist = testMadeMeasurement()
        state = state.copy(measurements = state.measurements + waist)
        var outcome = ViewWrite.StoredWithoutAlarm
        compose.setContent {
            WorkoutTrackerTheme {
                MomentumApp(
                    state = state,
                    onStateChange = { state = it; true },
                    onActivitySave = ActivitySaveNotExpected,
                    onMeasurementSave = { measurementId, edits ->
                        if (outcome != ViewWrite.NotStored) state = state.applyingMeasurementEdits(measurementId, edits, LocalDate.parse(currentDateKey()))
                        outcome
                    },
                )
            }
        }
        click("home_settings")
        click("settings_group_measurement_${waist.id}")
        type("measurement_settings_name", "Hips")
        click("measurement_settings_save")

        compose.onNodeWithTag("measurement_settings").assertExists()
        assertEquals("Hips", state.madeMeasurements.single().label)
        assertTrue(compose.transcript().toString(), "[measurement_settings_error] text=\"${string(R.string.measurement_settings_alarm_not_set)}\"" in compose.transcript())
        compose.onNodeWithTag("measurement_settings_save").assertIsNotEnabled()
        // Nothing is left that leaving would lose.
        click("top_back")
        compose.onNodeWithTag("measurement_settings_discard_dialog").assertDoesNotExist()
        compose.onNodeWithTag("settings_group_measurement_${waist.id}").assertExists()

        outcome = ViewWrite.NotStored
        click("settings_group_measurement_${waist.id}")
        type("measurement_settings_name", "Waistline")
        click("measurement_settings_save")
        assertEquals("Hips", state.madeMeasurements.single().label)
        assertTrue(compose.transcript().toString(), "[measurement_settings_error] text=\"${string(R.string.settings_error_view)}\"" in compose.transcript())
        compose.onNodeWithTag("measurement_settings_save").assertIsEnabled()
    }

    private fun string(id: Int): String = ApplicationProvider.getApplicationContext<Context>().getString(id)

    @Test
    fun aMeasurementIsCreatedChangedAndDeletedWithItsRecords() {
        compose.setContent { App() }
        click("home_settings")
        openSetup()

        // Its first step, Look, with Next and no Delete; nothing says a name is missing before anyone typed.
        compose.onNodeWithTag("measurement_setup").assertExists()
        compose.onNodeWithTag("measurement_setup_delete").assertDoesNotExist()
        compose.onNodeWithTag("measurement_setup_create").assertDoesNotExist()
        compose.onNodeWithTag("measurement_setup_next").assertIsNotEnabled()
        compose.onNodeWithTag("measurement_setup_name").assert(!refused)
        type("measurement_setup_name", "Waist")
        click("measurement_setup_icon_heart")
        click("measurement_setup_hue_teal")
        compose.onNodeWithTag("measurement_setup_next").assertIsEnabled()
        // Its second step, Unit: the unit, the step as it starts, and the line that says nothing is converted.
        openUnitStep()
        compose.onNodeWithTag("measurement_setup_unit").assert(holds(""))
        compose.onNodeWithTag("measurement_setup_step").assert(holds("0.1"))
        compose.onNodeWithTag("measurement_setup_unit_note").assertTextEquals(string(R.string.measurement_settings_unit_note))
        type("measurement_setup_unit", "cm")
        type("measurement_setup_step", "1")
        assertEquals(emptyList<MeasurementConfiguration>(), state.madeMeasurements)
        compose.onNodeWithTag("measurement_setup_next").assertIsEnabled()
        createFromUnit()

        // Created outside the streak, and nothing else was stored. The list is back, with its row and its switch, on.
        val made = state.madeMeasurements.single()
        assertTrue(made.id, made.id.matches(Regex("msr_[a-z2-9]{4}")))
        val asMade = MeasurementConfiguration(
            made.id,
            "Waist",
            ViewIcon.Heart,
            ViewHue.Teal,
            unit = "cm",
            step = MeasurementStep("1"),
            rewards = null,
            whenAway = Dated.always(WhenAway.Due),
            checkpoint = Checkpoint("day", CheckpointTime.Fixed(LocalTime.of(9, 0)), remind = false),
        )
        assertEquals(asMade, made)
        assertEquals(AppState(measurements = listOf(testWeightMeasurement(), made), measurementLogs = weights), state)
        compose.onNodeWithTag("measurement_setup").assertDoesNotExist()
        compose.onNodeWithTag("settings_group_measurement_${made.id}").assertExists()
        compose.onNodeWithTag("settings_feature_measurement_${made.id}").assertIsOn()
        compose.onNodeWithTag("settings_feature_measurement_weight").assertExists()

        // Its page, under its name, holds what was set up; Save waits for a change and returns to the list.
        val waists = listOf(
            MeasurementLog(made.id, "2026-10-13", 2L, 92.0),
            MeasurementLog(made.id, "2026-10-13", 3L, 92.5),
            MeasurementLog(made.id, "2026-10-12", 4L, 93.0),
        )
        state = state.copy(measurementLogs = waists + weights)
        click("settings_group_measurement_${made.id}")
        compose.onNodeWithTag("measurement_settings").assertExists()
        assertTrue(compose.transcript().toString(), "text=\"Waist\"" in compose.transcript())
        compose.onNodeWithTag("measurement_settings_name").assert(holds("Waist"))
        compose.onNodeWithTag("measurement_settings_unit").assertDoesNotExist()
        compose.onNodeWithTag("measurement_settings_save").assertIsNotEnabled()
        type("measurement_settings_name", "Hips")
        click("measurement_settings_hue_pink")
        // Unit is its second part, between Look and Reminders, over the same draft and the same Save.
        compose.onNodeWithTag("measurement_settings_tab_unit").assertTextEquals("Unit")
        click("measurement_settings_tab_unit")
        compose.onNodeWithTag("measurement_settings_unit").assert(holds("cm"))
        compose.onNodeWithTag("measurement_settings_step").assert(holds("1"))
        compose.onNodeWithTag("measurement_settings_unit_note").assertTextEquals(string(R.string.measurement_settings_unit_note))
        type("measurement_settings_unit", "in")
        type("measurement_settings_step", "0.01")
        click("measurement_settings_save")
        val changed = made.copy(label = "Hips", unit = "in", step = MeasurementStep("0.01"), hue = ViewHue.Pink)
        assertEquals(listOf(changed), state.madeMeasurements)
        assertEquals(waists + weights, state.measurementLogs)
        compose.onNodeWithTag("measurement_settings").assertDoesNotExist()

        // Delete asks first, naming it and its days, two records of one day being one day.
        click("settings_group_measurement_${made.id}")
        click("measurement_settings_delete")
        compose.onNodeWithTag("measurement_settings_delete_dialog").assertExists()
        val question = compose.transcript().toString()
        assertTrue(question, "Delete Hips?" in question)
        assertTrue(question, "Its settings and 2 recorded days are deleted. This cannot be undone." in question)
        click("measurement_settings_delete_cancel")
        assertEquals(listOf(changed), state.madeMeasurements)

        click("measurement_settings_delete")
        click("measurement_settings_delete_confirm")
        assertEquals(AppState(measurementLogs = weights).withWeight(), state)
        compose.onNodeWithTag("measurement_settings").assertDoesNotExist()
        compose.onNodeWithTag("settings_group_measurement_${made.id}").assertDoesNotExist()
        compose.onNodeWithTag("settings_new").assertExists()
    }

    @Test
    fun onlyATouchedSetupAsksBeforeItIsLeftAndADiscardedOneStoresNothing() {
        compose.setContent { App() }
        click("home_settings")
        openSetup()
        click("top_back")
        compose.onNodeWithTag("measurement_setup_discard_dialog").assertDoesNotExist()
        compose.onNodeWithTag("measurement_setup").assertDoesNotExist()
        // Back from a setup is the Settings list, not the page that asked what kind.
        compose.onNodeWithTag("settings_new").assertExists()
        compose.onNodeWithTag("settings_measurement_new").assertDoesNotExist()

        openSetup()
        click("measurement_setup_hue_teal")
        click("top_back")
        compose.onNodeWithTag("measurement_setup_discard_dialog").assertExists()
        click("measurement_setup_discard_dialog_stay")
        compose.onNodeWithTag("measurement_setup").assertExists()

        click("top_back")
        click("measurement_setup_discard_dialog_confirm")
        compose.onNodeWithTag("measurement_setup").assertDoesNotExist()
        assertEquals(AppState(measurementLogs = weights).withWeight(), state)

        // The next setup starts afresh.
        openSetup()
        compose.onNodeWithTag("measurement_setup_name").assert(holds(""))
        click("top_back")
        compose.onNodeWithTag("measurement_setup_discard_dialog").assertDoesNotExist()
    }

    @Test
    fun theSetupKeepsWhatWasTypedValidOrNotAcrossProcessDeath() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { App() }
        click("home_settings")
        openSetup()
        type("measurement_setup_name", "Speed")
        openUnitStep()
        type("measurement_setup_unit", tooLong)
        type("measurement_setup_step", "0,125")
        compose.onNodeWithTag("measurement_setup_next").assertIsNotEnabled()
        // Back on Look the name is cleared again: neither step can be left forward now.
        click("top_back")
        type("measurement_setup_name", "")
        compose.onNodeWithTag("measurement_setup_next").assertIsNotEnabled()

        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()

        compose.onNodeWithTag("measurement_setup").assertExists()
        compose.onNodeWithTag("measurement_setup_name").assert(holds("")).assert(refused)
        compose.onNodeWithTag("measurement_setup_next").assertIsNotEnabled()
        // Still guarded: what cannot be stored is not thrown away on the way out either.
        click("top_back")
        compose.onNodeWithTag("measurement_setup_discard_dialog").assertExists()
        click("measurement_setup_discard_dialog_stay")

        // With its name Look can be left, and Unit holds what was typed, each field saying why it is refused.
        type("measurement_setup_name", "Speed")
        openUnitStep()
        compose.onNodeWithTag("measurement_setup_unit").assert(holds(tooLong)).assert(refused)
        compose.onNodeWithTag("measurement_setup_step").assert(holds("0,125")).assert(refused)
        compose.onNodeWithTag("measurement_setup_next").assertIsNotEnabled()
        // A unit alone is not enough while the step is refused, nor the step alone before it.
        type("measurement_setup_unit", "km/h")
        compose.onNodeWithTag("measurement_setup_unit").assert(!refused)
        compose.onNodeWithTag("measurement_setup_next").assertIsNotEnabled()
        type("measurement_setup_step", "2,5")
        compose.onNodeWithTag("measurement_setup_step").assert(!refused)
        createFromUnit()

        val made = state.madeMeasurements.single()
        assertEquals("Speed" to "km/h", made.label to made.unit)
        assertEquals(MeasurementStep("2.5"), made.step)
    }

    /**
     * A unit pasted with a line break or a tab at its edge is not cut down to what is left of it:
     * on the setup and on the page the field keeps what was pasted and says it cannot be stored,
     * Create and Save wait, and nothing is stored until the person has changed it.
     */
    @Test
    fun aUnitPastedWithALineBreakOrATabAtItsEdgeIsRefusedOnTheSetupAndOnThePage() {
        compose.setContent { App() }
        click("home_settings")
        openSetup()
        type("measurement_setup_name", "Waist")
        openUnitStep()
        listOf("cm\n", "\ncm", "\tcm", "cm\t", "cm\u0007").forEach { pasted ->
            type("measurement_setup_unit", pasted)
            compose.onNodeWithTag("measurement_setup_unit").assert(holds(pasted)).assert(refused)
            compose.onNodeWithTag("measurement_setup_next").assertIsNotEnabled()
        }
        assertEquals(emptyList<MeasurementConfiguration>(), state.madeMeasurements)
        type("measurement_setup_unit", " cm ")
        compose.onNodeWithTag("measurement_setup_unit").assert(!refused)
        createFromUnit()
        val made = state.madeMeasurements.single()
        assertEquals("cm", made.unit)

        click("settings_group_measurement_${made.id}")
        click("measurement_settings_tab_unit")
        type("measurement_settings_step", "0.01")
        listOf("in\n", "\nin", "\tin", "in\t", "in\u0007").forEach { pasted ->
            type("measurement_settings_unit", pasted)
            compose.onNodeWithTag("measurement_settings_unit").assert(holds(pasted)).assert(refused)
            compose.onNodeWithTag("measurement_settings_save").assertIsNotEnabled()
        }
        assertEquals(listOf(made), state.madeMeasurements)
        // Leaving asks, as what was pasted would be lost; changed to a unit, it is saved as typed.
        click("top_back")
        compose.onNodeWithTag("measurement_settings_discard_dialog").assertExists()
        click("measurement_settings_discard_dialog_stay")
        type("measurement_settings_unit", "in")
        click("measurement_settings_save")
        assertEquals(listOf(made.copy(unit = "in", step = MeasurementStep("0.01"))), state.madeMeasurements)
    }

    @Test
    fun aMeasurementsPageKeepsWhatWasTypedValidOrNotAcrossProcessDeathAndHoldsSaveBack() {
        val waist = testMadeMeasurement()
        state = state.copy(measurements = state.measurements + waist)
        val restoration = StateRestorationTester(compose)
        restoration.setContent { App() }
        click("home_settings")
        click("settings_group_measurement_${waist.id}")
        type("measurement_settings_name", " ")
        click("measurement_settings_tab_unit")
        type("measurement_settings_step", "0.01")
        type("measurement_settings_unit", tooLong)
        compose.onNodeWithTag("measurement_settings_save").assertIsNotEnabled()

        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()

        compose.onNodeWithTag("measurement_settings").assertExists()
        compose.onNodeWithTag("measurement_settings_tab_unit").assertIsSelected()
        compose.onNodeWithTag("measurement_settings_unit").assert(holds(tooLong)).assert(refused)
        compose.onNodeWithTag("measurement_settings_step").assert(holds("0.01")).assert(!refused)
        compose.onNodeWithTag("measurement_settings_save").assertIsNotEnabled()
        assertEquals(listOf(waist), state.madeMeasurements)

        // Leaving asks; staying, the name alone does not free Save while the unit is refused.
        click("top_back")
        compose.onNodeWithTag("measurement_settings_discard_dialog").assertExists()
        click("measurement_settings_discard_dialog_stay")
        click("measurement_settings_tab_look")
        compose.onNodeWithTag("measurement_settings_name").assert(holds(" ")).assert(refused)
        type("measurement_settings_name", "Waist")
        compose.onNodeWithTag("measurement_settings_save").assertIsNotEnabled()

        // With a unit that can be stored, the step typed before the process died is saved with it.
        click("measurement_settings_tab_unit")
        type("measurement_settings_unit", "mm")
        click("measurement_settings_save")
        assertEquals(listOf(waist.copy(unit = "mm", step = MeasurementStep("0.01"))), state.madeMeasurements)
        compose.onNodeWithTag("measurement_settings").assertDoesNotExist()
    }

    /**
     * The step is a number a person types, with either decimal separator. Text that is no step, or
     * one the rule does not allow, stays in the field, which says why in its label's place, holds
     * Save back and is asked about before the page is left; nothing is saved in its place. A step
     * the rule allows is saved as that number, and the field then shows it as the screen writes it.
     */
    @Test
    fun theStepIsTypedAndTextThatIsNoStepStaysInTheFieldAndHoldsSaveBack() {
        val waist = testMadeMeasurement()
        state = state.copy(measurements = state.measurements + waist)
        compose.setContent { App() }
        click("home_settings")
        click("settings_group_measurement_${waist.id}")
        click("measurement_settings_tab_unit")
        compose.onNodeWithTag("measurement_settings_step").assert(holds("0.1")).assert(!refused)

        listOf("0", "0,00", "-1", "1000.01", "0.125", "half", "1e2", "", "0.5 cm").forEach { typed ->
            type("measurement_settings_step", typed)
            compose.onNodeWithTag("measurement_settings_step").assert(holds(typed)).assert(refused)
            compose.onNodeWithTag("measurement_settings_save").assertIsNotEnabled()
        }
        assertTrue(compose.transcript().toString(), compose.transcript().any { "Above 0, up to 1000, at most 2 decimal places" in it })
        assertEquals(listOf(waist), state.madeMeasurements)
        // Another change on the page does not free Save while the step is refused.
        type("measurement_settings_unit", "in")
        compose.onNodeWithTag("measurement_settings_save").assertIsNotEnabled()
        click("top_back")
        compose.onNodeWithTag("measurement_settings_discard_dialog").assertExists()
        click("measurement_settings_discard_dialog_stay")

        type("measurement_settings_step", "0,50")
        compose.onNodeWithTag("measurement_settings_step").assert(holds("0,50")).assert(!refused)
        click("measurement_settings_save")
        assertEquals(listOf(waist.copy(unit = "in", step = MeasurementStep("0.5"))), state.madeMeasurements)

        // Stored, the field shows the step as the screen writes it, and each size the rule allows is taken.
        click("settings_group_measurement_${waist.id}")
        click("measurement_settings_tab_unit")
        compose.onNodeWithTag("measurement_settings_step").assert(holds("0.5"))
        listOf("0.05" to "0.05", "2.5" to "2.5", "1000" to "1000", "1" to "1").forEach { (typed, stored) ->
            type("measurement_settings_step", typed)
            click("measurement_settings_save")
            assertEquals(typed, MeasurementStep(stored), state.madeMeasurements.single().step)
            click("settings_group_measurement_${waist.id}")
            click("measurement_settings_tab_unit")
            compose.onNodeWithTag("measurement_settings_step").assert(holds(stored))
        }
    }
}
