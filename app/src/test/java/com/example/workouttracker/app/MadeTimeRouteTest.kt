package com.example.workouttracker.app

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
import com.example.workouttracker.core.time.currentDateKey
import com.example.workouttracker.core.ui.theme.WorkoutTrackerTheme
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.measurement.testMadeMeasurement
import com.example.workouttracker.domain.measurement.withWeight
import com.example.workouttracker.domain.questions.testMadeQuestions
import com.example.workouttracker.domain.questions.withEating
import com.example.workouttracker.domain.ref
import com.example.workouttracker.domain.settings.FeatureVisibilitySettings
import com.example.workouttracker.domain.time.TimeLog
import com.example.workouttracker.domain.time.deleteTime
import com.example.workouttracker.domain.time.testMadeTime
import com.example.workouttracker.domain.time.withReading
import com.example.workouttracker.domain.activity.withActivity
import com.example.workouttracker.feature.time.testTimeSessions
import com.example.workouttracker.feature.weight.selectedHistoryDay
import com.example.workouttracker.feature.weight.transcript
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A time view a person made has a tile on Home that opens its screen, as a made measurement and a
 * made questions view have: two views are two places, the one selected survives process death,
 * and one that is gone or hidden falls back to Home. A tap on its reminder opens it on today,
 * whichever day the reminder rang on, and minutes added on its screen say which view and which
 * day, so its reminder about that day can be answered.
 */
@RunWith(RobolectricTestRunner::class)
class MadeTimeRouteTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = currentDateKey()
    private val yesterday = LocalDate.parse(today).minusDays(1).toString()
    private val piano = testMadeTime()
    private val guitar = testMadeTime(id = "tim_m7xd", label = "Guitar", goal = 90)
    private val waist = testMadeMeasurement()
    private val sleep = testMadeQuestions(madeOn = LocalDate.parse(today).minusDays(30))
    private val made = AppState().withWeight().withEating().withReading().withActivity()
        .let { it.copy(measurements = it.measurements + waist, questions = it.questions + sleep, time = it.time + piano + guitar) }
    private var state by mutableStateOf(made)
    private var visibility by mutableStateOf(FeatureVisibilitySettings())
    private var request by mutableStateOf<AppNavigationRequest?>(null)
    private var consumed = 0

    /** Each day stored: the view, the day, and that view's minutes of the day before and after. */
    private val logged = mutableListOf<List<Any>>()

    @Composable
    private fun App() {
        WorkoutTrackerTheme {
            MomentumApp(
                state = state,
                onStateChange = { state = it; true },
                onActivitySave = ActivitySaveNotExpected,
                featureVisibility = visibility,
                requestedNavigation = request,
                onDestinationConsumed = {
                    request = null
                    consumed += 1
                },
                onDayLogged = { view, dateKey, before, after ->
                    fun AppState.minutes() = timeLogs.filter { it.viewId == view.id && it.dateKey == dateKey }.map { it.minutes }
                    logged += listOf(view, dateKey, before.minutes(), after.minutes())
                },
                timeSessions = testTimeSessions(hasDoNotDisturbAccess = true),
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

    private fun line(tag: String): String = compose.transcript().single { it.startsWith("[$tag]") }

    private fun said(text: String): Boolean = "text=\"$text\"" in compose.transcript()

    /** Where a tile sits in reading order: its row, then its column. */
    private fun place(tag: String): Pair<Float, Float> = compose.onNodeWithTag(tag).fetchSemanticsNode().positionInRoot.let { it.y to it.x }

    private fun minutesOf(viewId: String) = state.timeLogs.filter { it.viewId == viewId }.map { it.dateKey to it.minutes }

    @Test
    fun timersFollowOtherKindsAndRecordedMinutesWearTheDoneBadge() {
        compose.setContent { App() }

        val tiles = listOf("home_measurement_${waist.id}", "home_questions_${sleep.id}", "home_time_reading", "home_time_${piano.id}", "home_time_${guitar.id}")
        val places = tiles.map(::place)
        assertEquals(places.sortedWith(compareBy({ it.first }, { it.second })), places)
        assertTrue(said("Piano") && said("Guitar"))

        // A factual completion can show without joining rewards.
        val status = SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription)
        compose.onNodeWithTag("home_time_${piano.id}").assert(!status)
        state = made.copy(timeLogs = listOf(TimeLog(piano.id, today, 1, 30)))
        compose.waitForIdle()
        compose.onNodeWithTag("home_time_${piano.id}").assert(hasContentDescription("Done today"))
    }

    /** Each opens on today with its own name, its own goal and its own minutes, and stores under its own ID; Reading's logs are never touched. */
    @Test
    fun twoMadeViewsAreTwoPlacesEachWithItsOwnGoalAndItsOwnMinutes() {
        state = made.copy(timeLogs = listOf(TimeLog("reading", today, 1, 25)))
        compose.setContent { App() }

        click("home_time_${piano.id}")
        assertTrue(compose.transcript().toString(), said("Piano"))
        assertEquals("[time_${piano.id}_week_minutes] text=\"0 / 60 min\"", line("time_${piano.id}_week_minutes"))
        click("time_${piano.id}_type_minutes")
        type("time_${piano.id}_minutes_input", "30")
        click("time_${piano.id}_log_today")
        assertEquals(listOf(today to 30), minutesOf(piano.id))

        click("top_back")
        click("home_time_${guitar.id}")
        assertTrue(compose.transcript().toString(), said("Guitar"))
        assertEquals("[time_${guitar.id}_week_minutes] text=\"0 / 90 min\"", line("time_${guitar.id}_week_minutes"))
        click("time_${guitar.id}_history_$yesterday")
        type("time_${guitar.id}_minutes_input", "45")
        click("time_${guitar.id}_log_today")

        assertEquals(listOf(today to 30), minutesOf(piano.id))
        assertEquals(listOf(yesterday to 45), minutesOf(guitar.id))
        assertEquals(listOf(today to 25), minutesOf("reading"))
        // Each day's minutes said which view and which day they were added to, so its reminder about that day can go.
        assertEquals(
            listOf(listOf(piano.ref, today, emptyList<Int?>(), listOf(30)), listOf(guitar.ref, yesterday, emptyList<Int?>(), listOf(45))),
            logged,
        )
        // A removal answers nothing.
        click("time_${guitar.id}_remove_day")
        click("time_${guitar.id}_remove_day")
        assertEquals(emptyList<Pair<String, Int?>>(), minutesOf(guitar.id))
        assertEquals(2, logged.size)
    }

    @Test
    fun theOpenViewAndWhatIsTypedSurviveProcessDeath() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { App() }
        click("home_time_${guitar.id}")
        click("time_${guitar.id}_history_$yesterday")
        type("time_${guitar.id}_minutes_input", "45")

        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()

        assertTrue(compose.transcript().toString(), said("Guitar"))
        assertEquals(yesterday, compose.transcript().selectedHistoryDay("time_${guitar.id}"))
        assertTrue(line("time_${guitar.id}_minutes_input"), "field=\"45\"" in line("time_${guitar.id}_minutes_input"))
        assertEquals(emptyList<Pair<String, Int?>>(), minutesOf(guitar.id))
    }

    /** A measurement and a time view under one ID are two places: the destination says which kind is open. */
    @Test
    fun aViewThatIsGoneOrHiddenFallsBackToHomeAndAMeasurementOfItsIdIsAnotherPlace() {
        val twin = testMadeMeasurement(id = piano.id, label = "Twin")
        state = made.copy(measurements = made.measurements + twin)
        compose.setContent { App() }

        click("home_measurement_${twin.id}")
        compose.onNodeWithTag("measurement_value").assertExists()
        click("top_back")
        click("home_time_${piano.id}")
        compose.onNodeWithTag("time_${piano.id}_start").assertExists()
        compose.onNodeWithTag("measurement_value").assertDoesNotExist()

        // Hidden, its screen closes and its tile is gone; the measurement of its ID keeps its own.
        visibility = FeatureVisibilitySettings().hiding(piano.ref)
        compose.waitForIdle()
        compose.onNodeWithTag("time_${piano.id}_start").assertDoesNotExist()
        compose.onNodeWithTag("home_time_${piano.id}").assertDoesNotExist()
        compose.onNodeWithTag("home_measurement_${twin.id}").assertExists()
        compose.onNodeWithTag("home_time_${guitar.id}").assertExists()

        // Deleted while its screen is open, it falls back to Home too.
        click("home_time_${guitar.id}")
        compose.onNodeWithTag("time_${guitar.id}_start").assertExists()
        state = state.deleteTime(guitar.id)
        compose.waitForIdle()
        compose.onNodeWithTag("time_${guitar.id}_start").assertDoesNotExist()
        compose.onNodeWithTag("home_time_${guitar.id}").assertDoesNotExist()
        compose.onNodeWithTag("home_settings").assertExists()
    }

    /**
     * A tap on its reminder opens the view on today, whichever day the reminder rang on: it asks
     * about the week, as Reading's does. The day it carried is not kept for the next view opened,
     * and a request for a view that is gone falls back to Home.
     */
    @Test
    fun aTapOnItsReminderOpensTheViewOnTodayWhicheverDayItRangOn() {
        compose.setContent { App() }

        request = AppNavigationRequest(AppDestination.Time, loggedViewId = piano.id, loggedDateKey = yesterday)
        compose.waitForIdle()

        assertEquals(today, compose.transcript().selectedHistoryDay("time_${piano.id}"))
        compose.onNodeWithTag("time_${piano.id}_start").assertExists()
        assertEquals(1, consumed)

        // The measurement opened next is on its own day: nothing of the reminder's day was left waiting.
        click("top_back")
        click("home_measurement_${waist.id}")
        compose.onNodeWithTag("measurement_selected_day").assertDoesNotExist()

        click("top_back")
        request = AppNavigationRequest(AppDestination.Time, loggedViewId = "tim_gone", loggedDateKey = yesterday)
        compose.waitForIdle()
        compose.onNodeWithTag("home_settings").assertExists()
        assertEquals(2, consumed)
        assertFalse(compose.transcript().any { it.startsWith("[time_") })
    }

    /** The view a reminder names is of its kind: a time view's request never opens the measurement or the questions view of that ID. */
    @Test
    fun aRequestNamesItsKind() {
        val twin = testMadeMeasurement(id = piano.id, label = "Twin")
        state = made.copy(measurements = made.measurements + twin)
        compose.setContent { App() }

        request = AppNavigationRequest(AppDestination.Time, loggedViewId = piano.id)
        compose.waitForIdle()
        compose.onNodeWithTag("time_${piano.id}_start").assertExists()
        compose.onNodeWithTag("measurement_value").assertDoesNotExist()

        click("top_back")
        request = AppNavigationRequest(AppDestination.Measurement, loggedViewId = piano.id)
        compose.waitForIdle()
        compose.onNodeWithTag("measurement_value").assertExists()
        compose.onNodeWithTag("time_${piano.id}_start").assertDoesNotExist()
    }
}
