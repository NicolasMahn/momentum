package de.nicolasmahn.momentum.app

import de.nicolasmahn.momentum.domain.LoggedViewKind
import de.nicolasmahn.momentum.domain.LoggedViewRef
import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.Locales
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import de.nicolasmahn.momentum.R
import de.nicolasmahn.momentum.TestCareChecklist
import de.nicolasmahn.momentum.TestEatingQuestions
import de.nicolasmahn.momentum.TestReadingTime
import de.nicolasmahn.momentum.TestSecondChecklist
import de.nicolasmahn.momentum.TestSupplementsChecklist
import de.nicolasmahn.momentum.TestWeightMeasurement
import de.nicolasmahn.momentum.core.format.formatMetricNumber
import de.nicolasmahn.momentum.core.format.formatSignedMetric
import de.nicolasmahn.momentum.core.locale.locale
import de.nicolasmahn.momentum.core.text.isolated
import de.nicolasmahn.momentum.core.time.currentDateKey
import de.nicolasmahn.momentum.core.ui.theme.WorkoutTrackerTheme
import de.nicolasmahn.momentum.domain.mo.TrackerMoPolicy
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.checklist.ChecklistRewards
import de.nicolasmahn.momentum.domain.checklist.Checkpoint
import de.nicolasmahn.momentum.domain.checklist.CheckpointTime
import de.nicolasmahn.momentum.domain.checklist.RewardPeriod
import de.nicolasmahn.momentum.domain.checklist.ViewHue
import de.nicolasmahn.momentum.domain.checklist.ViewIcon
import de.nicolasmahn.momentum.domain.measurement.DefaultMeasurementCheckpoint
import de.nicolasmahn.momentum.domain.measurement.MadeMeasurementWhenAway
import de.nicolasmahn.momentum.domain.measurement.MeasurementConfiguration
import de.nicolasmahn.momentum.domain.measurement.MeasurementEdit
import de.nicolasmahn.momentum.domain.measurement.MeasurementLog
import de.nicolasmahn.momentum.domain.measurement.MeasurementStep
import de.nicolasmahn.momentum.testfixtures.WeightMeasurementId
import de.nicolasmahn.momentum.domain.measurement.applyingMeasurementEdits
import de.nicolasmahn.momentum.testfixtures.madeMeasurements
import de.nicolasmahn.momentum.domain.measurement.recordsOf
import de.nicolasmahn.momentum.domain.settings.FeatureVisibilitySettings
import de.nicolasmahn.momentum.domain.ref
import de.nicolasmahn.momentum.reminder.ChecklistReminderRequestCode
import de.nicolasmahn.momentum.reminder.ReminderNotificationChannel
import de.nicolasmahn.momentum.reminder.ReminderViewKind
import de.nicolasmahn.momentum.reminder.slotKey
import de.nicolasmahn.momentum.testing.appString
import de.nicolasmahn.momentum.testing.assertInputEquals
import de.nicolasmahn.momentum.testing.tapChartDay
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * A measurement a person makes, through the app as a person meets it: made in Settings step by
 * step, with a name, a unit, a look, a reminder and a place in the streak, logged on its own
 * screen beside Weight, renamed and recolored, hidden and shown again, deleted with its records,
 * and on Home among the checklists. The stored state is the proof, and Weight's records are read
 * before and after.
 */
class MeasurementFlowTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val today = currentDateKey()
    private val yesterday = LocalDate.parse(today).minusDays(1).toString()
    private val waist = madeMeasurement("msr_wa1s", "Waist", ViewIcon.Ruler, ViewHue.Plum, unit = "cm", step = "0.1")
    private val mood = madeMeasurement("msr_m00d", "Mood", ViewIcon.Mood, ViewHue.Teal, unit = "", step = "1")

    private var state by mutableStateOf(AppState(measurements = listOf(TestWeightMeasurement)))
    private var visibility by mutableStateOf(FeatureVisibilitySettings())

    /** What a tap on a notification asks for, as the activity hands it to the app. */
    private var request by mutableStateOf<AppNavigationRequest?>(null)

    private val resources get() = InstrumentationRegistry.getInstrumentation().targetContext.resources

    private fun madeMeasurement(id: String, label: String, icon: ViewIcon, hue: ViewHue, unit: String, step: String) =
        MeasurementConfiguration(
            id = id,
            label = label,
            icon = icon,
            hue = hue,
            unit = unit,
            step = MeasurementStep(step),
            rewards = null,
            whenAway = MadeMeasurementWhenAway,
            checkpoint = DefaultMeasurementCheckpoint,
        )

    /**
     * The app over [state], which every save, creation and deletion is decided against, as a host
     * without a store has it, with the switches of the Settings list kept in [visibility].
     */
    private fun setApp(override: DeviceConfigurationOverride? = null) {
        composeRule.setContent {
            if (override == null) {
                WorkoutTrackerTheme { App() }
            } else {
                DeviceConfigurationOverride(override) {
                    WorkoutTrackerTheme { App() }
                }
            }
        }
    }

    @Composable
    private fun App() {
        MomentumApp(
            state = state,
            onStateChange = { state = it; true },
            onActivitySave = ActivitySaveNotExpected,
            featureVisibility = visibility,
            onFeatureVisibilityChange = { visibility = it; true },
            requestedNavigation = request,
            onDestinationConsumed = { request = null },
        )
    }

    /** A number as the device's language writes it. */
    private fun number(value: Double): String = formatMetricNumber(value, resources.locale)

    /** A value as a sentence on the screen shows it: the number, then the unit set apart from the words around it. */
    private fun shown(number: String, unit: String): String = appString(R.string.measurement_value_with_unit, number, isolated(unit))

    /** A value as TalkBack hears it: the unit as typed. */
    private fun said(value: Double, unit: String): String = appString(R.string.measurement_value_with_unit, number(value), unit)

    /** Picks a day as a person does: by a tap on its place in the chart. */
    private fun selectDate(date: String) = composeRule.tapChartDay(date, today)

    private fun save(typed: String) {
        composeRule.onNodeWithTag("measurement_value").performScrollTo().performTextReplacement(typed)
        composeRule.onNodeWithTag("measurement_save").performScrollTo().performClick()
    }

    /** A tile of Home, brought into sight first where Home had to scroll to fit its tiles. */
    private fun tile(tag: String): SemanticsNodeInteraction {
        val tile = composeRule.onNodeWithTag(tag)
        if (!tile.isDisplayed()) tile.performScrollTo()
        return tile
    }

    /** From the Settings list, which Create, Save and Delete return to. */
    private fun openPage(measurement: MeasurementConfiguration) {
        composeRule.onNodeWithTag("settings_group_measurement_${measurement.id}").performScrollTo().performClick()
        composeRule.onNodeWithTag("measurement_settings").assertIsDisplayed()
    }

    private fun tileNamed(tag: String, name: String) = composeRule.onNode(hasTestTag(tag) and hasAnyDescendant(hasText(name)), useUnmergedTree = true)

    private val refused = SemanticsMatcher.keyIsDefined(SemanticsProperties.Error)

    /** A tile in the streak says where today stands; one outside it says nothing. */
    private val status = SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription)

    /** What the badge on the Home tile [tag] says to TalkBack. */
    private fun assertTileStatus(tag: String, description: String) {
        composeRule.onNode(hasContentDescription(description) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true).assertExists()
    }

    /** A part of a page or a step of a setup, which sits in the page's scroll; the bar's buttons are pinned and pressed directly. */
    private fun openPart(tag: String) {
        composeRule.onNodeWithTag(tag).performScrollTo().performClick()
        composeRule.onNodeWithTag(tag).assertIsSelected()
    }

    /**
     * "Track something new" asks what kind, and Measurement opens its setup on Look, with Next and
     * no Delete. Next waits for a name there, and on Unit, the second step, for a unit that can be
     * stored and a step that is one. Create, on the last step, stores the measurement and nothing
     * else; its row is in the Settings list with its switch on, and its tile on Home opens its
     * screen.
     */
    @Test
    fun aMeasurementIsMadeWithANameAUnitAndALookAndHasItsRowAndItsTile() {
        setApp()
        tile("home_settings").performClick()

        // One button for whatever is added; the page behind it asks what kind, a card for each.
        composeRule.onNodeWithTag("settings_measurement_new").assertDoesNotExist()
        composeRule.onNodeWithTag("settings_new").performScrollTo().performClick()
        composeRule.onNodeWithText(appString(R.string.settings_new)).assertIsDisplayed()
        tileNamed("settings_checklist_new", appString(R.string.settings_new_checklist_description)).assertIsDisplayed()
        tileNamed("settings_measurement_new", appString(R.string.measurement_destination)).assertIsDisplayed()
        tileNamed("settings_measurement_new", appString(R.string.settings_new_measurement_description)).assertIsDisplayed()
        composeRule.onNodeWithTag("settings_measurement_new").performClick()
        composeRule.onNodeWithTag("measurement_setup").assertIsDisplayed()

        // The first step is Look: Next waits for a name, and Create comes on the last step.
        composeRule.onNodeWithTag("measurement_setup_delete").assertDoesNotExist()
        composeRule.onNodeWithTag("measurement_setup_create").assertDoesNotExist()
        composeRule.onNodeWithTag("measurement_setup_next").assertIsNotEnabled()
        // The name field is not told it is missing before anyone typed.
        composeRule.onNodeWithTag("measurement_setup_name").performScrollTo().assert(!refused)
        composeRule.onNodeWithTag("measurement_setup_name").performTextReplacement("Waist")
        composeRule.onNodeWithTag("measurement_setup_icon_heart").performScrollTo().performClick()
        composeRule.onNodeWithTag("measurement_setup_hue_teal").performScrollTo().performClick()
        Espresso.closeSoftKeyboard()
        composeRule.onNodeWithTag("measurement_setup_next").assertIsEnabled().performClick()

        // The second step is Unit. A unit that cannot be stored stays in its field, which says so, and holds Next back.
        composeRule.onNodeWithTag("measurement_setup_tab_unit").assertIsSelected()
        composeRule.onNodeWithTag("measurement_setup_unit").performScrollTo().performTextReplacement("centimetres!!")
        composeRule.onNodeWithTag("measurement_setup_unit").assertInputEquals("centimetres!!").assert(refused)
        composeRule.onNodeWithTag("measurement_setup_next").assertIsNotEnabled()
        composeRule.onNodeWithTag("measurement_setup_unit").performTextReplacement("cm")
        composeRule.onNodeWithTag("measurement_setup_unit").assert(!refused)

        // The step is typed: it starts at 0.1, and text that is no step stays, says so and holds Next back.
        composeRule.onNodeWithTag("measurement_setup_step").performScrollTo().assertInputEquals(number(0.1))
        composeRule.onNodeWithTag("measurement_setup_step").performTextReplacement("0")
        composeRule.onNodeWithTag("measurement_setup_step").assertInputEquals("0").assert(refused)
        composeRule.onNodeWithTag("measurement_setup_next").assertIsNotEnabled()
        composeRule.onNodeWithTag("measurement_setup_step").performTextReplacement("1")
        composeRule.onNodeWithTag("measurement_setup_step").assert(!refused)
        composeRule.onNodeWithText(appString(R.string.measurement_settings_unit_note)).performScrollTo().assertIsDisplayed()
        composeRule.runOnIdle { assertTrue(state.madeMeasurements.isEmpty()) }

        // The two steps after Unit ask for nothing, and the last one creates.
        Espresso.closeSoftKeyboard()
        composeRule.onNodeWithTag("measurement_setup_next").assertIsEnabled().performClick()
        composeRule.onNodeWithTag("measurement_setup_next").assertIsEnabled().performClick()
        composeRule.onNodeWithTag("measurement_setup_create").assertIsEnabled().performClick()
        val made = composeRule.runOnIdle { state.madeMeasurements.single() }
        composeRule.runOnIdle {
            assertTrue(made.id, made.id.matches(Regex("msr_[a-z2-9]{4}")))
            assertEquals(madeMeasurement(made.id, "Waist", ViewIcon.Heart, ViewHue.Teal, unit = "cm", step = "1"), made)
            assertEquals(AppState(measurements = listOf(TestWeightMeasurement, made)), state)
        }

        // Created, the setup returned to the list, where its row opens its page and its switch is on.
        composeRule.onNodeWithTag("measurement_setup").assertDoesNotExist()
        composeRule.onNodeWithTag("settings_new").performScrollTo().assertIsDisplayed()
        tileNamed("settings_group_measurement_${made.id}", "Waist").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("settings_feature_measurement_${made.id}").performScrollTo().assertIsOn()

        // Its tile wears its name and no status, and opens its screen, whose buttons step by one.
        composeRule.onNodeWithTag("top_back").performClick()
        tile("home_measurement_${made.id}").assert(!status)
        tileNamed("home_measurement_${made.id}", "Waist").assertIsDisplayed()
        tile("home_measurement_${made.id}").performClick()
        composeRule.onNodeWithText("Waist").assertIsDisplayed()
        composeRule.onNodeWithText(appString(R.string.measurement_field_label_with_unit, "Waist", isolated("cm"))).assertExists()
        composeRule.onNodeWithTag("measurement_value").performScrollTo().performTextReplacement("92")
        composeRule.onNodeWithTag("measurement_increment").performScrollTo().performClick()
        composeRule.onNodeWithTag("measurement_value").assertInputEquals(number(93.0))
    }

    /**
     * A number is logged on its screen and shown with its unit: as the large number, on the chart
     * and in the line about the day before, which says the change once a second day is logged.
     * Weight's screen shows none of it, and a weight logged the same day shows nowhere on this one.
     */
    @Test
    fun aNumberIsLoggedWithItsUnitAndWeightAndTheMeasurementSeeNothingOfEachOther() {
        state = state.copy(measurements = state.measurements + waist)
        setApp()
        tile("home_measurement_${waist.id}").performClick()

        selectDate(yesterday)
        save("92")
        composeRule.onNode(hasContentDescription(said(92.0, "cm"))).performScrollTo().assertIsDisplayed()
        selectDate(today)
        // A day without a record names the one before it, with its unit.
        composeRule.onNodeWithText(appString(R.string.measurement_previous, shown(number(92.0), "cm"), appString(R.string.date_yesterday))).performScrollTo().assertIsDisplayed()
        save("91.5")
        composeRule.runOnIdle {
            assertEquals(listOf(today to 91.5, yesterday to 92.0), state.measurementLogs.map { it.dateKey to it.value })
            assertTrue(state.measurementLogs.all { it.measurementId == waist.id })
            assertTrue(state.recordsOf("weight").isEmpty())
        }

        composeRule.onNodeWithTag("measurement_value").performScrollTo().assertInputEquals(number(91.5))
        val change = appString(
            R.string.measurement_previous_with_change,
            shown(number(92.0), "cm"),
            appString(R.string.date_yesterday),
            shown(formatSignedMetric(-0.5, resources.locale), "cm"),
        )
        composeRule.onNodeWithText(change).performScrollTo().assertIsDisplayed()
        composeRule.onNode(hasContentDescription(said(91.5, "cm"))).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("measurement_history_chart").performScrollTo()
            .assert(hasContentDescription("Waist", substring = true))
            .assert(hasContentDescription(said(91.5, "cm"), substring = true))
            .assert(hasContentDescription(said(92.0, "cm"), substring = true))

        // A recorded fact shows completion independently of reward enrollment.
        composeRule.onNodeWithTag("top_back").performClick()
        composeRule.onNodeWithTag("home_measurement_${waist.id}").assert(hasContentDescription(appString(R.string.app_home_task_done)))

        // Weight's screen has nothing logged and shows neither number.
        tile("home_measurement_weight").performClick()
        composeRule.onNodeWithTag("measurement_value").assertInputEquals("")
        composeRule.onNodeWithTag("measurement_selected_note").performScrollTo().assertTextEquals(appString(R.string.measurement_none_logged))
        listOf(91.5, 92.0).forEach { waists ->
            composeRule.onAllNodes(hasText(number(waists), substring = true)).assertCountEquals(0)
            composeRule.onAllNodes(hasContentDescription(number(waists), substring = true)).assertCountEquals(0)
        }

        // A weight logged the same day is Weight's alone.
        save("81.7")
        composeRule.runOnIdle {
            assertEquals(listOf(today to 81.7), state.recordsOf("weight").map { it.dateKey to it.value })
            assertEquals(listOf(today to 91.5, yesterday to 92.0), state.recordsOf(waist.id).map { it.dateKey to it.value })
        }
        composeRule.onNodeWithTag("top_back").performClick()
        composeRule.onNodeWithTag("home_measurement_weight").assert(status)
        tile("home_measurement_${waist.id}").assert(hasContentDescription(appString(R.string.app_home_task_done))).performClick()
        composeRule.onNodeWithTag("measurement_value").assertInputEquals(number(91.5))
        composeRule.onNodeWithText(change).performScrollTo().assertIsDisplayed()
        composeRule.onAllNodes(hasText(number(81.7), substring = true)).assertCountEquals(0)
        composeRule.onAllNodes(hasContentDescription(number(81.7), substring = true)).assertCountEquals(0)
    }

    /**
     * Renamed and recolored, with another unit and step, it is stored so on Save, which returns to
     * the list, and wears the new name in the list, on Home and on its screen. A cleared name holds
     * Save back, leaving asks first, and its records are the numbers they were.
     */
    @Test
    fun itIsRenamedAndRecoloredAndItsRecordsStayTheNumbersTheyWere() {
        val records = listOf(MeasurementLog(waist.id, today, 2L, 92.0), MeasurementLog(WeightMeasurementId, today, 1L, 81.4))
        state = state.copy(measurements = state.measurements + waist, measurementLogs = records)
        setApp()
        tile("home_settings").performClick()
        openPage(waist)

        // Its Look first, what is stored, nothing to save yet, and a Delete that can be pressed.
        composeRule.onNodeWithTag("measurement_settings_tab_look").assertIsSelected()
        composeRule.onNodeWithTag("measurement_settings_name").performScrollTo().assertInputEquals("Waist")
        composeRule.onNodeWithTag("measurement_settings_unit").assertDoesNotExist()
        composeRule.onNodeWithTag("measurement_settings_icon_ruler").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("measurement_settings_hue_plum").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("measurement_settings_save").assertIsNotEnabled()
        composeRule.onNodeWithTag("measurement_settings_delete").assertIsDisplayed().assertIsEnabled()
        composeRule.onNodeWithTag("measurement_settings_delete_reason").assertDoesNotExist()

        composeRule.onNodeWithTag("measurement_settings_hue_pink").performScrollTo().performClick()
        composeRule.onNodeWithTag("measurement_settings_icon_heart").performScrollTo().performClick()
        composeRule.onNodeWithTag("measurement_settings_save").assertIsEnabled()
        composeRule.onNodeWithTag("measurement_settings_name").performScrollTo().performTextReplacement("")
        composeRule.onNodeWithTag("measurement_settings_name").assert(refused)
        composeRule.onNodeWithTag("measurement_settings_save").assertIsNotEnabled()
        composeRule.onNodeWithTag("measurement_settings_name").performTextReplacement("Hips")

        // Unit, the second part, holds the unit and the step over the same draft and the same Save.
        Espresso.closeSoftKeyboard()
        openPart("measurement_settings_tab_unit")
        composeRule.onNodeWithTag("measurement_settings_unit").performScrollTo().assertInputEquals("cm")
        composeRule.onNodeWithTag("measurement_settings_step").performScrollTo().assertInputEquals(number(0.1))
        composeRule.onNodeWithText(appString(R.string.measurement_settings_unit_note)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("measurement_settings_unit").performTextReplacement("in")
        composeRule.onNodeWithTag("measurement_settings_step").performTextReplacement("0.125")
        composeRule.onNodeWithTag("measurement_settings_step").assertInputEquals("0.125").assert(refused)
        composeRule.onNodeWithTag("measurement_settings_save").assertIsNotEnabled()
        composeRule.onNodeWithTag("measurement_settings_step").performTextReplacement("0.01")
        composeRule.onNodeWithTag("measurement_settings_step").assert(!refused)
        composeRule.onNodeWithTag("measurement_settings_save").assertIsEnabled()
        composeRule.runOnIdle { assertEquals(listOf(waist), state.madeMeasurements) }

        // Leaving asks, as the changes would be lost; staying keeps them, on every part.
        Espresso.closeSoftKeyboard()
        composeRule.onNodeWithTag("top_back").performClick()
        composeRule.onNodeWithTag("measurement_settings_discard_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("measurement_settings_discard_dialog_stay").performClick()
        composeRule.onNodeWithTag("measurement_settings_step").performScrollTo().assertInputEquals("0.01")
        openPart("measurement_settings_tab_look")
        composeRule.onNodeWithTag("measurement_settings_name").performScrollTo().assertInputEquals("Hips")

        Espresso.closeSoftKeyboard()
        composeRule.onNodeWithTag("measurement_settings_save").performClick()
        val hips = waist.copy(label = "Hips", icon = ViewIcon.Heart, hue = ViewHue.Pink, unit = "in", step = MeasurementStep("0.01"))
        composeRule.runOnIdle {
            assertEquals(listOf(hips), state.madeMeasurements)
            // The unit and the step are labels: no stored number was converted.
            assertEquals(records, state.measurementLogs)
        }

        composeRule.onNodeWithTag("measurement_settings").assertDoesNotExist()
        tileNamed("settings_group_measurement_${waist.id}", "Hips").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("top_back").performClick()
        tile("home_measurement_${waist.id}")
        tileNamed("home_measurement_${waist.id}", "Hips").assertIsDisplayed()

        tile("home_measurement_${waist.id}").performClick()
        composeRule.onNodeWithText("Hips").assertIsDisplayed()
        composeRule.onNodeWithText(appString(R.string.measurement_field_label_with_unit, "Hips", isolated("in"))).assertExists()
        composeRule.onNodeWithTag("measurement_value").assertInputEquals(number(92.0))
        composeRule.onNode(hasContentDescription(said(92.0, "in"))).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("measurement_increment").performScrollTo().performClick()
        composeRule.onNodeWithTag("measurement_value").assertInputEquals(number(92.01))
    }

    /**
     * Delete asks first, naming the measurement and the days that hold a record of it. Confirmed,
     * its configuration and every record of it are gone together, its row and its tile with them,
     * and Weight's records, another measurement's and those nothing knows are as they were.
     */
    @Test
    fun itIsDeletedWithItsRecordsAfterTheQuestionAndWeightsRecordsStay() {
        val others = listOf(
            MeasurementLog(WeightMeasurementId, today, 9L, 81.4),
            MeasurementLog(mood.id, today, 8L, 7.0),
            MeasurementLog("msr_gone", today, 7L, 3.0),
            MeasurementLog(WeightMeasurementId, yesterday, 6L, 81.9),
        )
        // Two of today, one of yesterday and one of a day to come: three days.
        val waists = listOf(
            MeasurementLog(waist.id, LocalDate.parse(today).plusDays(3).toString(), 5L, 90.0),
            MeasurementLog(waist.id, today, 4L, 92.0),
            MeasurementLog(waist.id, today, 3L, 92.5),
            MeasurementLog(waist.id, yesterday, 2L, 93.0),
        )
        state = state.copy(
            measurements = state.measurements + waist + mood,
            measurementLogs = listOf(waists[0], others[0], waists[1], others[1], waists[2], others[2], others[3], waists[3]),
        )
        val before = state
        setApp()
        tile("home_settings").performClick()
        openPage(waist)

        composeRule.onNodeWithTag("measurement_settings_delete").performClick()
        composeRule.onNodeWithTag("measurement_settings_delete_dialog").assertIsDisplayed()
        composeRule.onNodeWithText(appString(R.string.measurement_settings_delete_title, "Waist")).assertIsDisplayed()
        composeRule.onNodeWithText(resources.getQuantityString(R.plurals.measurement_settings_delete_message, 3, 3)).assertIsDisplayed()
        composeRule.onNodeWithTag("measurement_settings_delete_cancel").performClick()
        composeRule.onNodeWithTag("measurement_settings_delete_dialog").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(before, state) }

        composeRule.onNodeWithTag("measurement_settings_delete").performClick()
        composeRule.onNodeWithTag("measurement_settings_delete_confirm").performClick()
        composeRule.runOnIdle {
            assertEquals(listOf(TestWeightMeasurement, mood), state.measurements)
            assertEquals(others, state.measurementLogs)
            assertEquals(before.recordsOf("weight"), state.recordsOf("weight"))
        }

        // Deleted, the page closed; the list and Home have the other measurement and not this one.
        composeRule.onNodeWithTag("measurement_settings").assertDoesNotExist()
        composeRule.onNodeWithTag("settings_group_measurement_${waist.id}").assertDoesNotExist()
        composeRule.onNodeWithTag("settings_group_measurement_${mood.id}").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("top_back").performClick()
        composeRule.onNodeWithTag("home_measurement_${waist.id}").assertDoesNotExist()
        tile("home_measurement_${mood.id}").assertIsDisplayed()
        tile("home_measurement_weight").performClick()
        composeRule.onNodeWithTag("measurement_value").assertInputEquals(number(81.4))
    }

    /**
     * A measurement is made step by step: Look, then Reminders with its reminder switched on at
     * its fixed time, then Streak with "Counts toward the streak", each step numbered and the ones
     * ahead held back until their turn. Created, it is stored with all of it, and its page shows
     * the three parts with what was chosen.
     */
    @Test
    fun itIsMadeStepByStepWithAReminderAndTheStreakAndItsPageShowsWhatWasChosen() {
        setApp()
        tile("home_settings").performClick()
        composeRule.onNodeWithTag("settings_new").performScrollTo().performClick()
        composeRule.onNodeWithTag("settings_measurement_new").performClick()

        // Four numbered steps; the three ahead are shown and cannot be opened before Look has a name.
        fun step(number: Int, part: Int) = appString(R.string.checklist_setup_step, number, appString(part))
        composeRule.onNodeWithTag("measurement_setup_tab_look").performScrollTo().assertIsSelected().assertTextEquals(step(1, R.string.checklist_editor_tab_look))
        composeRule.onNodeWithTag("measurement_setup_tab_unit").assertIsNotEnabled().assertTextEquals(step(2, R.string.measurement_settings_unit_part))
        composeRule.onNodeWithTag("measurement_setup_tab_reminders").assertIsNotEnabled().assertTextEquals(step(3, R.string.checklist_editor_tab_reminders))
        composeRule.onNodeWithTag("measurement_setup_tab_streak").assertIsNotEnabled().assertTextEquals(step(4, R.string.checklist_editor_rewards))
        composeRule.onNodeWithTag("measurement_setup_next").assertIsNotEnabled()
        composeRule.onNodeWithTag("measurement_setup_name").performScrollTo().performTextReplacement("Waist")
        Espresso.closeSoftKeyboard()
        composeRule.onNodeWithTag("measurement_setup_next").assertIsEnabled().performClick()

        // Step 2: the unit and the step, typed.
        composeRule.onNodeWithTag("measurement_setup_tab_unit").performScrollTo().assertIsSelected()
        // The next step's tab opens exactly when Next does, and a new unit's step starts complete.
        composeRule.onNodeWithTag("measurement_setup_tab_reminders").assertIsEnabled()
        composeRule.onNodeWithTag("measurement_setup_unit").performScrollTo().performTextReplacement("cm")
        composeRule.onNodeWithTag("measurement_setup_step").performScrollTo().performTextReplacement("0.5")
        Espresso.closeSoftKeyboard()
        composeRule.onNodeWithTag("measurement_setup_next").assertIsEnabled().performClick()

        // Step 3: off at 09:00 to start with, previewed under the name just typed.
        composeRule.onNodeWithTag("measurement_setup_tab_reminders").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("measurement_setup_tab_streak").assertIsEnabled()
        composeRule.onNodeWithTag("measurement_setup_reminder_time_fixed").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("measurement_setup_reminder_time").performScrollTo().assertTextEquals("09:00")
        // Switched on, a reminder asks for notifications at once; granted, nothing comes between.
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
            InstrumentationRegistry.getInstrumentation().targetContext.packageName,
            Manifest.permission.POST_NOTIFICATIONS,
        )
        composeRule.onNodeWithTag("measurement_setup_reminder").performScrollTo().assertIsOff().performClick()
        composeRule.onNodeWithTag("measurement_setup_reminder").assertIsOn()
        composeRule.onNodeWithTag("measurement_setup_reminder_preview_title").performScrollTo().assertTextEquals("Waist")
        composeRule.onNodeWithTag("measurement_setup_reminder_preview_text").assertTextEquals(appString(R.string.measurement_reminder_open))
        composeRule.onNodeWithTag("measurement_setup_next").performClick()

        // Step 4 is the last: Create takes Next's place.
        composeRule.onNodeWithTag("measurement_setup_tab_streak").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("measurement_setup_next").assertDoesNotExist()
        composeRule.onNodeWithTag("measurement_setup_rewards").performScrollTo().assertIsOff().performClick()
        composeRule.onNodeWithTag("measurement_setup_rewards").assertIsOn()

        // Back keeps what each step holds, and nothing is stored before Create.
        composeRule.onNodeWithTag("top_back").performClick()
        composeRule.onNodeWithTag("measurement_setup_reminder").performScrollTo().assertIsOn()
        composeRule.onNodeWithTag("measurement_setup_next").performClick()
        composeRule.onNodeWithTag("measurement_setup_rewards").performScrollTo().assertIsOn()
        composeRule.runOnIdle { assertTrue(state.madeMeasurements.isEmpty()) }
        composeRule.onNodeWithTag("measurement_setup_create").assertIsEnabled().performClick()

        val made = composeRule.runOnIdle { state.madeMeasurements.single() }
        composeRule.runOnIdle {
            assertEquals("Waist" to "cm", made.label to made.unit)
            assertEquals(MeasurementStep("0.5"), made.step)
            assertEquals(Checkpoint("day", CheckpointTime.Fixed(LocalTime.of(9, 0)), remind = true), made.checkpoint)
            assertEquals(ChecklistRewards(maxPasses = 3, everyKeptDays = 7, periods = listOf(RewardPeriod(LocalDate.parse(today)))), made.standardRewards)
            assertEquals(MadeMeasurementWhenAway, made.whenAway)
        }

        // Its page has the four parts, each with what was chosen, and nothing to save.
        composeRule.onNodeWithTag("measurement_setup").assertDoesNotExist()
        openPage(made)
        composeRule.onNodeWithTag("measurement_settings_tab_look").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("measurement_settings_tab_items").assertDoesNotExist()
        composeRule.onNodeWithTag("measurement_settings_name").performScrollTo().assertInputEquals("Waist")
        composeRule.onNodeWithTag("measurement_settings_tab_unit").assertTextEquals(appString(R.string.measurement_settings_unit_part))
        openPart("measurement_settings_tab_unit")
        composeRule.onNodeWithTag("measurement_settings_unit").performScrollTo().assertInputEquals("cm")
        composeRule.onNodeWithTag("measurement_settings_step").performScrollTo().assertInputEquals(number(0.5))
        openPart("measurement_settings_tab_reminders")
        composeRule.onNodeWithTag("measurement_settings_reminder").performScrollTo().assertIsOn()
        composeRule.onNodeWithTag("measurement_settings_reminder_time_fixed").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("measurement_settings_reminder_time").performScrollTo().assertTextEquals("09:00")
        openPart("measurement_settings_tab_streak")
        composeRule.onNodeWithTag("measurement_settings_rewards").performScrollTo().assertIsOn()
        composeRule.onNodeWithTag("measurement_settings_max_passes_value").performScrollTo().assertTextEquals("3")
        composeRule.onNodeWithTag("measurement_settings_away_due").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("measurement_settings_save").assertIsNotEnabled()
    }

    /**
     * Counting toward the streak, its tile wears the socket while today is open and the coin once
     * today is logged, in a checklist's words, and the token page has its row.
     */
    @Test
    fun inTheStreakItsTileWearsTheSocketThenTheCoinAndTheTokenPageHasItsRow() {
        val previous = MeasurementLog(waist.id, yesterday, 1L, 91.0)
        val existing = state.measurementLogs
        state = state.copy(
            measurements = state.measurements + waist.copy(mo = TrackerMoPolicy(enabled = true)),
            measurementLogs = existing + previous,
        ).applyingMeasurementEdits(waist.id, listOf(MeasurementEdit.JoinRewards), LocalDate.parse(today))
        setApp()

        assertTileStatus("home_measurement_${waist.id}", appString(R.string.app_home_task_open))
        composeRule.onNodeWithTag("home_streak").performClick()
        composeRule.onNodeWithTag("streak_measurement_${waist.id}").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("top_back").performClick()

        tile("home_measurement_${waist.id}").performClick()
        save("92")
        composeRule.runOnIdle {
            assertEquals(mapOf(yesterday to 91.0, today to 92.0), state.recordsOf(waist.id).associate { it.dateKey to it.value })
            assertEquals(existing, state.measurementLogs.filter { it.measurementId != waist.id })
        }
        composeRule.onNodeWithTag("top_back").performClick()
        assertTileStatus("home_measurement_${waist.id}", appString(R.string.app_home_task_done))
    }

    /**
     * "Send a test" on its Reminders part posts its reminder, with the switch off, under the
     * measurement's name. The notification's tap is the one that opens this measurement on today,
     * and arriving, that request opens its screen on that day.
     */
    @Test
    fun sendATestShowsItsReminderAndATapOpensItOnTheDayItAskedAbout() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        // The suite grants no permission on its own, and the app creates the channel on start; the test host is not the app's activity.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        ReminderNotificationChannel.create(context)
        notificationManager.cancelAll()
        state = state.copy(measurements = state.measurements + waist, measurementLogs = listOf(MeasurementLog(waist.id, yesterday, 1L, 92.0)))
        fun postedTest() = notificationManager.activeNotifications
            .firstOrNull { it.tag == slotKey(ReminderViewKind.Measurement, waist.id, "day") && it.id == ChecklistReminderRequestCode }

        try {
            setApp()
            tile("home_settings").performClick()
            openPage(waist)
            openPart("measurement_settings_tab_reminders")
            composeRule.onNodeWithTag("measurement_settings_reminder").performScrollTo().assertIsOff()
            composeRule.onNodeWithTag("measurement_settings_reminder_test").performScrollTo().assertIsEnabled().performClick()

            composeRule.waitUntil(timeoutMillis = 5_000) { postedTest() != null }
            val posted = requireNotNull(postedTest()).notification
            assertEquals("Waist", posted.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString())
            assertEquals(appString(R.string.measurement_reminder_open), posted.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString())
            assertEquals(loggedViewDestinationPendingIntent(context, LoggedViewRef(LoggedViewKind.Measurement, waist.id), today), posted.contentIntent)

            // The tap's request arrives: the page held nothing unsaved, so the measurement opens, on today.
            composeRule.runOnIdle { request = AppNavigationRequest(AppDestination.Measurement, loggedViewId = waist.id, loggedDateKey = today) }
            composeRule.onNodeWithTag("measurement_selected_day").assertDoesNotExist()
            composeRule.onNodeWithText("Waist").assertIsDisplayed()
            // Today has no record yet: the line under the buttons names yesterday's.
            composeRule.onNodeWithText(appString(R.string.measurement_previous, shown(number(92.0), "cm"), appString(R.string.date_yesterday))).performScrollTo().assertIsDisplayed()
            composeRule.runOnIdle { assertEquals(null, request) }
        } finally {
            notificationManager.cancelAll()
        }
    }

    /**
     * Hidden with the switch on its Settings row, its tile is gone and a reminder's tap finds no
     * screen to open, while its settings stay open and its reminder's row says it is hidden. Shown
     * again, its tile is back and opens its screen.
     */
    @Test
    fun itIsHiddenAndShownAgainAndKeepsItsSettingsWhileHidden() {
        state = state.copy(measurements = state.measurements + waist + mood)
        setApp()
        tile("home_measurement_${waist.id}").assertIsDisplayed()
        tile("home_settings").performClick()

        composeRule.onNodeWithTag("settings_feature_measurement_${waist.id}").performScrollTo().assertIsOn().performClick()
        composeRule.onNodeWithTag("settings_feature_measurement_${waist.id}").assertIsOff()
        composeRule.onNodeWithTag("settings_feature_measurement_${mood.id}").performScrollTo().assertIsOn()
        composeRule.runOnIdle { assertEquals(false, visibility.shows(waist.ref)) }

        // Its tile is gone and the other's is not; a tap on a reminder of it leads Home.
        composeRule.onNodeWithTag("top_back").performClick()
        composeRule.onNodeWithTag("home_measurement_${waist.id}").assertDoesNotExist()
        tile("home_measurement_${mood.id}").assertIsDisplayed()
        composeRule.runOnIdle { request = AppNavigationRequest(AppDestination.Measurement, loggedViewId = waist.id, loggedDateKey = today) }
        composeRule.onNodeWithTag("measurement_value").assertDoesNotExist()
        tile("home_settings").assertIsDisplayed()

        // Its settings open, and its reminder's row says what stands in the way; there is no test to send.
        tile("home_settings").performClick()
        openPage(waist)
        openPart("measurement_settings_tab_reminders")
        composeRule.onNodeWithTag("measurement_settings_reminder_dependency").performScrollTo().assertTextEquals(appString(R.string.settings_turn_feature_on, "Waist"))
        composeRule.onNodeWithTag("measurement_settings_reminder_test").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithTag("top_back").performClick()

        composeRule.onNodeWithTag("settings_feature_measurement_${waist.id}").performScrollTo().performClick()
        composeRule.onNodeWithTag("settings_feature_measurement_${waist.id}").assertIsOn()
        composeRule.runOnIdle { assertEquals(FeatureVisibilitySettings(), visibility) }
        composeRule.onNodeWithTag("top_back").performClick()
        tile("home_measurement_${waist.id}").performClick()
        composeRule.onNodeWithTag("measurement_value").assertIsDisplayed()
    }

    /**
     * A measurement without rewards still shows its recorded completion, with its reminder off
     * at 09:00, outside the streak and still due on
     * holiday, and with nothing waiting to be saved.
     */
    @Test
    fun aMeasurementWithoutRewardsKeepsItsDefaultsAndRecordedCompletion() {
        val records = listOf(MeasurementLog(waist.id, today, 2L, 92.0))
        state = state.copy(measurements = state.measurements + waist, measurementLogs = records)
        setApp()

        tile("home_measurement_${waist.id}").assert(hasContentDescription(appString(R.string.app_home_task_done)))
        tile("home_settings").performClick()
        composeRule.onNodeWithTag("settings_feature_measurement_${waist.id}").performScrollTo().assertIsOn()
        openPage(waist)
        composeRule.onNodeWithTag("measurement_settings_tab_look").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("measurement_settings_name").performScrollTo().assertInputEquals("Waist")
        openPart("measurement_settings_tab_reminders")
        composeRule.onNodeWithTag("measurement_settings_reminder").performScrollTo().assertIsOff()
        composeRule.onNodeWithTag("measurement_settings_reminder_time_fixed").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("measurement_settings_reminder_time").performScrollTo().assertTextEquals("09:00")
        openPart("measurement_settings_tab_streak")
        composeRule.onNodeWithTag("measurement_settings_rewards").performScrollTo().assertIsOff()
        composeRule.onNodeWithTag("measurement_settings_away_due").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("measurement_settings_save").assertIsNotEnabled()
        composeRule.runOnIdle {
            assertEquals(listOf(waist), state.madeMeasurements)
            assertEquals(records, state.measurementLogs)
        }

        // Leaving asks nothing, and the token page has no row for it.
        composeRule.onNodeWithTag("top_back").performClick()
        composeRule.onNodeWithTag("measurement_settings_discard_dialog").assertDoesNotExist()
        composeRule.onNodeWithTag("top_back").performClick()
        composeRule.onNodeWithTag("home_streak").performClick()
        composeRule.onNodeWithTag("streak_measurement_${waist.id}").assertDoesNotExist()
    }

    @Test
    fun severalMeasurementsBesideTheChecklistsAreAllOnHome() = everyTileIsOnHome(override = null)

    @Test
    fun severalMeasurementsBesideTheChecklistsAreAllOnHomeInGerman() =
        everyTileIsOnHome(DeviceConfigurationOverride.Locales(LocaleList("de")))

    @Test
    fun severalMeasurementsBesideTheChecklistsAreAllOnHomeWithALargeFont() = everyTileIsOnHome(DeviceConfigurationOverride.FontScale(2f))

    @Test
    fun severalMeasurementsBesideTheChecklistsAreAllOnHomeInAShortWindow() = everyTileIsOnHome(DeviceConfigurationOverride.ForcedSize(ShortWindow))

    /**
     * Four measurements beside Weight, Eating, Reading, Supplements, Care and a checklist of one's
     * own: every tile is on Home, in reach as Home sizes and scrolls its tiles, each made
     * measurement's under the name typed for it, whatever the language, and the last one opens
     * its own screen.
     */
    private fun everyTileIsOnHome(override: DeviceConfigurationOverride?) {
        val made = listOf(
            waist,
            madeMeasurement("msr_pu1s", "Resting pulse", ViewIcon.Pulse, ViewHue.Navy, unit = "bpm", step = "1"),
            mood,
            madeMeasurement("msr_fat1", "Body fat", ViewIcon.Ruler, ViewHue.Olive, unit = "%", step = "0.1"),
        )
        state = AppState(
            checklists = listOf(TestSupplementsChecklist, TestCareChecklist, TestSecondChecklist),
            measurements = listOf(TestWeightMeasurement) + made,
            questions = listOf(TestEatingQuestions),
            time = listOf(TestReadingTime),
        )
        setApp(override)

        val tiles = listOf("home_log_activity", "home_measurement_weight", "home_checklist_supplements", "home_time_reading", "home_questions_eating", "home_checklist_care", "home_checklist_${TestSecondChecklist.id}") +
            made.map { "home_measurement_${it.id}" } +
            listOf("home_substances", "home_settings")
        tiles.forEach { tag -> tile(tag).assertIsDisplayed().assertHeightIsAtLeast(48.dp) }
        made.forEach { measurement -> tileNamed("home_measurement_${measurement.id}", measurement.label).assertExists() }

        val last = made.last()
        tile("home_measurement_${last.id}").performClick()
        composeRule.onNodeWithText(last.label).assertIsDisplayed()
        composeRule.onNodeWithTag("measurement_value").assertExists()
    }

    private companion object {
        /** A window too low for Mo and five rows of square tiles, as a split screen leaves it. */
        val ShortWindow = DpSize(360.dp, 420.dp)
    }
}
