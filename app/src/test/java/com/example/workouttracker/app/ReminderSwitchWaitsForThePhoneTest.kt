package com.example.workouttracker.app

import android.Manifest
import android.app.Activity
import android.app.Application
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import com.example.workouttracker.R
import com.example.workouttracker.core.ui.theme.WorkoutTrackerTheme
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.LoggedView
import com.example.workouttracker.domain.LoggedViewRef
import com.example.workouttracker.domain.loggedView
import com.example.workouttracker.domain.measurement.testMadeMeasurement
import com.example.workouttracker.domain.questions.testMadeQuestions
import com.example.workouttracker.domain.ref
import com.example.workouttracker.domain.time.testMadeTime
import com.example.workouttracker.feature.checklist.tagName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager

/**
 * A logged view's Save that switches its reminder on while the phone does not allow exact alarms
 * stores the rest of the Save at once and asks the phone; the reminder is stored only once the
 * phone has given what it needs. Refused, or never answered, the reminder stays off, and the
 * page holds the switch alone. A measurement, a questions view and a time view alike.
 */
@RunWith(RobolectricTestRunner::class)
class ReminderSwitchWaitsForThePhoneTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var state by mutableStateOf(AppState(measurements = listOf(testMadeMeasurement()), questions = listOf(testMadeQuestions()), time = listOf(testMadeTime())))

    @Before fun noExactAlarms() {
        shadowOf(compose.activity.application as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
    }

    private fun setApp(restoration: StateRestorationTester? = null) {
        val app = @androidx.compose.runtime.Composable { WorkoutTrackerTheme { MomentumApp(state = state, onStateChange = { state = it; true }, onActivitySave = ActivitySaveNotExpected) } }
        if (restoration == null) compose.setContent(app) else restoration.setContent(app)
    }

    private fun click(tag: String) {
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private val LoggedView.page: String get() = "${kind.tagName}_settings"

    private fun stored(view: LoggedViewRef) = requireNotNull(state.loggedView(view))

    /** Renames [view] and switches its reminder on, then presses Save, as a person does on its page. */
    private fun renameSwitchOnAndSave(view: LoggedView) {
        click("home_settings")
        click("settings_group_${view.kind.tagName}_${view.id}")
        compose.onNodeWithTag("${view.page}_name").performTextReplacement("Renamed")
        click("${view.page}_tab_reminders")
        click("${view.page}_reminder")
        click("${view.page}_save")
    }

    /** The phone's screen for exact alarms, asked for once, is left with exact alarms [granted] or not. */
    private fun answerExactAlarms(granted: Boolean) {
        val asked = requireNotNull(shadowOf(compose.activity).nextStartedActivityForResult) { "The phone was not asked." }
        assertEquals(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, asked.intent.action)
        ShadowAlarmManager.setCanScheduleExactAlarms(granted)
        shadowOf(compose.activity).receiveResult(asked.intent, Activity.RESULT_CANCELED, null)
        compose.waitForIdle()
    }

    /** The name is stored at once, the reminder waits, and the page holds the switch alone. */
    private fun assertWaiting(view: LoggedView) {
        assertEquals("Renamed", stored(view.ref).label)
        assertFalse(stored(view.ref).checkpoint.remind)
        compose.onNodeWithTag("${view.page}_reminder").assertIsOn()
        compose.onNodeWithTag("${view.page}_save").assertIsEnabled()
    }

    @Test fun aMeasurementRefusedStaysOff() = refused(testMadeMeasurement())

    @Test fun aQuestionsViewRefusedStaysOff() = refused(testMadeQuestions())

    @Test fun aTimeViewRefusedStaysOff() = refused(testMadeTime())

    @Test fun aMeasurementGivenRemindsAndItsPageCloses() = given(testMadeMeasurement())

    @Test fun aQuestionsViewGivenRemindsAndItsPageCloses() = given(testMadeQuestions())

    @Test fun aTimeViewGivenRemindsAndItsPageCloses() = given(testMadeTime())

    @Test fun aMeasurementInterruptedStaysOff() = interrupted(testMadeMeasurement())

    @Test fun aQuestionsViewInterruptedStaysOff() = interrupted(testMadeQuestions())

    @Test fun aTimeViewInterruptedStaysOff() = interrupted(testMadeTime())

    /** Refused, the reminder stays off, and the page says so with the switch still waiting on it. */
    private fun refused(view: LoggedView) {
        setApp()
        renameSwitchOnAndSave(view)
        assertWaiting(view)

        answerExactAlarms(granted = false)

        assertFalse(stored(view.ref).checkpoint.remind)
        compose.onNodeWithTag("${view.page}_error").assertTextEquals(compose.activity.getString(R.string.measurement_settings_alarm_not_set))
        compose.onNodeWithTag("${view.page}_reminder").assertIsOn()
    }

    /** Given, the reminder is stored and the page returns to the list, its Save finished. */
    private fun given(view: LoggedView) {
        setApp()
        renameSwitchOnAndSave(view)
        assertWaiting(view)

        answerExactAlarms(granted = true)

        assertTrue(stored(view.ref).checkpoint.remind)
        compose.onNodeWithTag(view.page).assertDoesNotExist()
        compose.onNodeWithTag("settings_new").assertExists()
    }

    /** The process dies while the phone is asked and no answer comes: the reminder stays off, and the switch still waits on the page. */
    private fun interrupted(view: LoggedView) {
        val restoration = StateRestorationTester(compose)
        setApp(restoration)
        renameSwitchOnAndSave(view)

        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()

        assertWaiting(view)
    }
}
