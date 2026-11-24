package de.nicolasmahn.momentum.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import de.nicolasmahn.momentum.core.time.currentDateKey
import de.nicolasmahn.momentum.core.ui.theme.WorkoutTrackerTheme
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.measurement.MeasurementLog
import de.nicolasmahn.momentum.domain.compatibility.WeightMeasurementId
import de.nicolasmahn.momentum.domain.measurement.testMadeMeasurement
import de.nicolasmahn.momentum.domain.measurement.withWeight
import de.nicolasmahn.momentum.feature.weight.transcript
import de.nicolasmahn.momentum.reminder.ReminderCompletionNotification
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A tap on a measurement's reminder arrives as a request for that measurement and the day it
 * asked about. The day moves a screen nobody touched, once; a number being typed stays.
 * A day saved on that screen tells whoever answers reminders which measurement and which day.
 */
@RunWith(RobolectricTestRunner::class)
class MeasurementReminderArrivalTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = currentDateKey()
    private val yesterday = LocalDate.parse(today).minusDays(1).toString()
    private val waist = testMadeMeasurement()
    private val mood = testMadeMeasurement(id = "msr_m7xd", label = "Mood", unit = "", step = "1")
    private var state by mutableStateOf(
        AppState(
            measurementLogs = listOf(
                MeasurementLog(waist.id, yesterday, 1L, 91.5),
                MeasurementLog(WeightMeasurementId, yesterday, 2L, 81.4),
                MeasurementLog(mood.id, yesterday, 3L, 7.0),
            ),
        ).withWeight().let { it.copy(measurements = it.measurements + waist + mood) },
    )
    private var request by mutableStateOf<AppNavigationRequest?>(null)
    private var consumed = 0

    /** Each day saved: the measurement, the day, and the records of that measurement before and after. */
    private val saved = mutableListOf<List<Any>>()
    private val answered = mutableListOf<ReminderCompletionNotification>()

    @Composable
    private fun App() {
        WorkoutTrackerTheme {
            MomentumApp(
                state = state,
                onStateChange = { state = it; true },
                onActivitySave = ActivitySaveNotExpected,
                requestedNavigation = request,
                onDestinationConsumed = {
                    request = null
                    consumed += 1
                },
                onDayLogged = { view, dateKey, before, after ->
                    saved += listOf(view.id, dateKey, before.measurementLogs.filter { it.measurementId == view.id }.map { it.value }, after.measurementLogs.filter { it.measurementId == view.id }.map { it.value })
                },
                onReminderCompletion = { answered += it },
            )
        }
    }

    private fun click(tag: String) {
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private fun tapReminder(measurementId: String, dateKey: String?) {
        request = AppNavigationRequest(AppDestination.Measurement, loggedViewId = measurementId, loggedDateKey = dateKey)
        compose.waitForIdle()
    }

    private fun field(tag: String): String = compose.transcript().single { it.startsWith("[$tag]") }

    /** The screen names its day only while that is not today, which is how it tells the two apart without a date field. */
    private fun assertOnToday() = compose.onNodeWithTag("measurement_selected_day").assertDoesNotExist()
    private fun assertOnAnotherDay() = compose.onNodeWithTag("measurement_selected_day").assertExists()

    @Test
    fun aTapOpensTheMeasurementOnTheDayItsReminderAskedAboutOnce() {
        compose.setContent { App() }

        tapReminder(waist.id, yesterday)

        assertOnAnotherDay()
        assertEquals("[measurement_value] field=\"91.5\"", field("measurement_value"))
        assertEquals(1, consumed)

        // The day is used up: opened again from its tile, the measurement is on today.
        click("top_back")
        click("home_measurement_${waist.id}")
        assertOnToday()

        // A request that names no day opens today, and another measurement's request opens that one.
        click("top_back")
        tapReminder(mood.id, null)
        assertOnToday()
        tapReminder(mood.id, yesterday)
        assertEquals("[measurement_value] field=\"7\"", field("measurement_value"))
    }

    @Test
    fun theDayNeverReplacesANumberBeingTyped() {
        compose.setContent { App() }
        click("home_measurement_${waist.id}")

        compose.onNodeWithTag("measurement_value").performTextReplacement("93")
        tapReminder(waist.id, yesterday)
        assertOnToday()
        assertEquals("[measurement_value] field=\"93\"", field("measurement_value"))

        // The request is used up all the same: with the field back as the day opened, nothing typed, the day still does not move.
        compose.onNodeWithTag("measurement_value").performTextReplacement("91.5")
        compose.waitForIdle()
        assertOnToday()
        assertEquals(1, consumed)
    }

    /** A day opened by a tap survives process death, as the place does. */
    @Test
    fun theOpenedDaySurvivesProcessDeath() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { App() }
        tapReminder(waist.id, yesterday)

        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()

        assertOnAnotherDay()
        assertEquals("[measurement_value] field=\"91.5\"", field("measurement_value"))
    }

    @Test
    fun aRequestForAMeasurementThatIsGoneFallsBackAsAMissingOneDoes() {
        compose.setContent { App() }

        tapReminder("msr_gone", yesterday)

        compose.onNodeWithTag("measurement_value").assertDoesNotExist()
        compose.onNodeWithTag("home_settings").assertExists()
        assertEquals(1, consumed)
        // Its day is forgotten with it: the next measurement opened is on today.
        click("home_measurement_${waist.id}")
        assertOnToday()
    }

    /** A saved day tells which measurement and which day, Weight's as a made measurement's. */
    @Test
    fun aSavedDayTellsWhichMeasurementAndWhichDayWeightsToo() {
        compose.setContent { App() }

        tapReminder(waist.id, yesterday)
        compose.onNodeWithTag("measurement_value").performTextReplacement("92")
        click("measurement_save")
        assertEquals(listOf(listOf(waist.id, yesterday, listOf(91.5), listOf(92.0))), saved)

        click("measurement_delete")
        click("measurement_delete")
        assertEquals(listOf(waist.id, yesterday, listOf(92.0), emptyList<Double>()), saved.last())
        assertEquals(2, saved.size)
        assertEquals(emptyList<ReminderCompletionNotification>(), answered)

        click("top_back")
        click("home_measurement_weight")
        compose.onNodeWithTag("measurement_value").performTextReplacement("81")
        click("measurement_save")
        assertEquals(listOf(WeightMeasurementId, today, listOf(81.4), listOf(81.0, 81.4)), saved.last())
        assertEquals(emptyList<ReminderCompletionNotification>(), answered)
        assertTrue(state.measurementLogs.any { it.measurementId == WeightMeasurementId && it.dateKey == today && it.value == 81.0 })
    }
}
