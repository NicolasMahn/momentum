package de.nicolasmahn.momentum.feature.logactivity

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import de.nicolasmahn.momentum.core.ui.theme.WorkoutTrackerTheme
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.activity.ActivityEntry
import de.nicolasmahn.momentum.domain.activity.BoulderingActivityId
import de.nicolasmahn.momentum.domain.activity.CyclingActivityId
import de.nicolasmahn.momentum.domain.activity.GymActivityId
import de.nicolasmahn.momentum.domain.activity.routine.ExerciseLog
import de.nicolasmahn.momentum.domain.activity.routine.LoggedSet
import de.nicolasmahn.momentum.domain.activity.routine.WorkoutSession
import de.nicolasmahn.momentum.domain.activity.testActivityConfiguration
import de.nicolasmahn.momentum.domain.activity.withActivity
import de.nicolasmahn.momentum.feature.weight.transcript
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * How long an activity took is asked for only where Settings switched it on. Off, as every
 * configuration starts, the screen has no duration field and a save stores none; a duration
 * already recorded is a fact, so it stays shown and survives an edit that cannot change it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h1600dp-xhdpi")
class ActivityDurationSettingTest {
    @get:Rule
    val compose = createComposeRule()

    private val recordedDay = "2026-10-19"
    private val bouldering = ActivityEntry("entry-b", BoulderingActivityId, "Bouldering", recordedDay, 100L, durationMinutes = 90)
    private val gymDayA = WorkoutSession(
        id = "$recordedDay-A", activityId = GymActivityId, day = "A", dateKey = recordedDay, timestampMillis = 200L,
        exerciseLogs = listOf(ExerciseLog("bench_press", "Bench Press", listOf(LoggedSet(22.5, 10.0)))),
        durationMinutes = 50,
    )
    private var state by mutableStateOf(AppState(activityEntries = listOf(bouldering), sessions = listOf(gymDayA)).withActivity())

    private fun show() {
        compose.setContent {
            WorkoutTrackerTheme {
                ActivityLogScreen(state = state, onStateChange = { state = it; true }, onWorkoutFinished = {}, todayKey = "2026-10-21")
            }
        }
        compose.waitForIdle()
    }

    /** Settings stores the switch, as its page does on Save; the records stay as they are. */
    private fun logDurations(on: Boolean) {
        state = state.withActivity(state.activity!!.copy(logsDuration = on))
        compose.waitForIdle()
    }

    private fun click(tag: String) {
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private fun top(tag: String): Float = compose.onNodeWithTag(tag).fetchSemanticsNode().positionInRoot.y

    private fun assertNoField() = compose.onNodeWithTag("activity_duration").assertDoesNotExist()

    private fun shows(text: String): Boolean = compose.transcript().any { text in it }

    @Test
    fun theFieldStandsNowhereUntilSettingsAsksForIt() {
        show()
        // The gym is chosen on arrival: its days, with no field above them or inside a workout.
        compose.onNodeWithTag("gym_plan_a").assertExists()
        assertNoField()
        click("gym_plan_a")
        compose.onNodeWithTag("finish_workout").assertExists()
        assertNoField()
        click("gym_change_plan")
        click("activity_choice_cycling")
        compose.onNodeWithTag("activity_save").assertExists()
        assertNoField()

        logDurations(true)
        assertTrue(top("activity_duration") < top("activity_save"))
        click("activity_choice_gym")
        assertTrue(top("activity_duration") < top("gym_plan_a"))
        click("gym_plan_a")
        assertTrue(top("activity_duration") < top("finish_workout"))

        logDurations(false)
        assertNoField()
    }

    @Test
    fun withTheSettingOffASaveStoresNoDuration() {
        show()
        click("activity_choice_cycling")
        click("activity_save")
        assertEquals(listOf(null), state.activityEntries.filter { it.activityId == CyclingActivityId }.map { it.durationMinutes })
        assertEquals(90, state.activityEntries.single { it.id == bouldering.id }.durationMinutes)
    }

    /**
     * What the field held when it was switched off is not read: text that is no duration holds no
     * save back, and minutes typed for it reach none.
     */
    @Test
    fun whatTheFieldHeldWhenItWasSwitchedOffIsNotRead() {
        logDurations(true)
        show()
        click("activity_choice_cycling")
        compose.onNodeWithTag("activity_duration").performTextReplacement("0")
        compose.onNodeWithTag("activity_save").assertIsNotEnabled()
        logDurations(false)
        compose.onNodeWithTag("activity_save").assertIsEnabled()

        logDurations(true)
        compose.onNodeWithTag("activity_duration").performTextReplacement("45")
        logDurations(false)
        click("activity_save")
        assertEquals(listOf(null), state.activityEntries.filter { it.activityId == CyclingActivityId }.map { it.durationMinutes })
    }

    @Test
    fun anEntrysRecordedDurationStaysShownAndSurvivesAnEditWithTheSettingOff() {
        show()
        click("activity_history_$recordedDay")
        assertTrue(shows("90 min"))
        assertTrue(shows("50 min · 1 set"))

        // The edit moves the entry to another activity; its minutes are not the form's to change.
        click("activity_entry_edit_activity:entry-b")
        assertNoField()
        click("activity_choice_cycling")
        click("activity_save")
        assertEquals(listOf(bouldering.copy(activityId = CyclingActivityId, name = "Cycling")), state.activityEntries)
        assertTrue(shows("90 min"))
    }

    /**
     * A session finished again keeps its minutes, opened from the day's records or continued
     * through its activity's days, where the form never read what the session holds.
     */
    @Test
    fun aSessionsRecordedDurationSurvivesBeingFinishedAgainWithTheSettingOff() {
        show()
        click("activity_history_$recordedDay")
        click("activity_entry_edit_workout:$recordedDay-A")
        assertNoField()
        compose.onNodeWithTag("workout_bench_press_reps_0").performTextReplacement("11")
        click("finish_workout")
        assertEquals(50, state.sessions.single().durationMinutes)
        assertEquals(11.0, state.sessions.single().exerciseLogs.single { it.exerciseId == "bench_press" }.sets.first().reps, 0.0)

        click("gym_plan_a")
        compose.onNodeWithTag("workout_bench_press_reps_0").performTextReplacement("12")
        click("finish_workout")
        assertEquals(50, state.sessions.single().durationMinutes)
        assertEquals(12.0, state.sessions.single().exerciseLogs.single { it.exerciseId == "bench_press" }.sets.first().reps, 0.0)
        assertTrue(shows("50 min"))
    }
}
