package com.example.workouttracker.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import com.example.workouttracker.core.time.currentDateKey
import com.example.workouttracker.core.ui.theme.WorkoutTrackerTheme
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.activity.ActivityConfiguration
import com.example.workouttracker.domain.activity.ActivityEdit
import com.example.workouttracker.domain.activity.ActivityEntry
import com.example.workouttracker.domain.activity.BoulderingActivityId
import com.example.workouttracker.domain.activity.GymActivityId
import com.example.workouttracker.domain.activity.ListedActivity
import com.example.workouttracker.domain.activity.applyingEdits
import com.example.workouttracker.domain.activity.routine.ExerciseLog
import com.example.workouttracker.domain.activity.routine.LoggedSet
import com.example.workouttracker.domain.activity.routine.Routine
import com.example.workouttracker.domain.activity.routine.RoutineDay
import com.example.workouttracker.domain.activity.routine.RoutineExercise
import com.example.workouttracker.domain.activity.routine.WorkoutSession
import com.example.workouttracker.domain.activity.testActivityConfiguration
import com.example.workouttracker.domain.checklist.ViewIcon
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Editing a record comes before the routine, and a routine comes with its activity wherever it
 * is picked: an entry opens as an entry though its activity has a routine since, a session opens
 * on its own day though the routine dropped it, Other shows the picker of an activity that has a
 * routine, and the reminder's tap chooses the activity it named. Walked through the app on the
 * real habit day, as the screen reads it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h1600dp-xhdpi")
class ActivityEditingTest {
    @get:Rule
    val compose = createComposeRule()

    private val todayKey = currentDateKey()
    private var state by mutableStateOf(AppState())
    private var request by mutableStateOf<AppNavigationRequest?>(null)

    /**
     * Bouldering gained a routine with a weightless exercise after an entry of it was logged, the
     * gym lost day C after a session on it, and Yoga, listed fifth and so under Other, has a
     * routine too.
     */
    private val configuration = testActivityConfiguration().applyingEdits(
        listOf(
            ActivityEdit.AddRoutineDay(BoulderingActivityId, RoutineDay("day_boulder", "Session", listOf(RoutineExercise("ex_campus", "Campus board", 3, 5..8, weight = null, startReps = 6)))),
            ActivityEdit.RemoveRoutineDay("C"),
            ActivityEdit.AddActivity(ListedActivity("act_yoga", "Yoga", ViewIcon.Bolt, routine = Routine(listOf(RoutineDay("day_flow", "Flow", listOf(RoutineExercise("ex_sun", "Sun salutation", 2, 5..10, weight = null, startReps = 5))))))),
        ),
        LocalDate.parse(todayKey),
    )
    private val boulderingEntry = ActivityEntry("entry-b", BoulderingActivityId, "Bouldering", todayKey, 100L, durationMinutes = 90)
    private val dayCSession = WorkoutSession(
        id = "$todayKey-C", activityId = GymActivityId, day = "C", dateKey = todayKey, timestampMillis = 200L,
        exerciseLogs = listOf(ExerciseLog("barbell_deadlift", "Barbell Deadlift", listOf(LoggedSet(70.0, 8.0), LoggedSet(72.5, 6.0)))),
    )

    private fun show(configuration: ActivityConfiguration = this.configuration) {
        state = AppState(activity = configuration, activityEntries = listOf(boulderingEntry), sessions = listOf(dayCSession))
        compose.setContent {
            WorkoutTrackerTheme {
                MomentumApp(state = state, onStateChange = { state = it; true }, onActivitySave = ActivitySaveNotExpected, requestedNavigation = request, onDestinationConsumed = { request = null })
            }
        }
        // Home fits the window, so there is nothing to scroll it by; the Activity screen always scrolls.
        compose.onNodeWithTag("home_log_activity").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private fun click(tag: String) {
        compose.onNodeWithTag(tag).performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private fun input(tag: String): String = compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    @Test
    fun anEntryOpensAsAnEntryThoughItsActivityHasARoutineSince() {
        show()
        click("activity_choice_bouldering")
        compose.onNodeWithTag("gym_plan_day_boulder").assertExists()
        compose.onNodeWithTag("activity_save").assertDoesNotExist()

        click("activity_entry_edit_activity:entry-b")
        compose.onNodeWithTag("gym_plan_day_boulder").assertDoesNotExist()
        compose.onNodeWithTag("activity_choice_bouldering").assertIsSelected()
        compose.onNodeWithTag("activity_name").assertDoesNotExist()
        click("activity_save")
        assertEquals(listOf(boulderingEntry), state.activityEntries)
        assertEquals(configuration, state.activity)

        // With the entry saved the tile shows its routine again.
        compose.onNodeWithTag("gym_plan_day_boulder").assertExists()
    }

    @Test
    fun aSessionOpensOnItsOwnDayThoughTheRoutineDroppedIt() {
        show()
        click("activity_choice_gym")
        compose.onNodeWithTag("gym_plan_c").assertDoesNotExist()

        click("activity_entry_edit_workout:$todayKey-C")
        assertEquals("70", input("workout_barbell_deadlift_weight_0"))
        assertEquals("8", input("workout_barbell_deadlift_reps_0"))
        assertEquals("6", input("workout_barbell_deadlift_reps_1"))
        compose.onNodeWithTag("activity_choice_gym").assertIsSelected()

        // Leaving the session goes back to the days still offered, where the removed one is not.
        click("gym_change_plan")
        compose.onNodeWithTag("gym_plan_a").assertExists()
        compose.onNodeWithTag("gym_plan_c").assertDoesNotExist()
        compose.onNodeWithTag("workout_barbell_deadlift_reps_0").assertDoesNotExist()
    }

    @Test
    fun otherShowsThePickerOfAnActivityWithARoutineAndATypedNameHasNone() {
        show()
        click("activity_choice_custom")
        compose.onNodeWithTag("activity_save").assertExists()

        click("activity_other_act_yoga")
        compose.onNodeWithTag("activity_other_act_yoga").assertIsSelected()
        compose.onNodeWithTag("gym_plan_day_flow").assertExists()
        compose.onNodeWithTag("activity_save").assertDoesNotExist()
        compose.onNodeWithTag("activity_name").assertExists()

        click("gym_plan_day_flow")
        compose.onNodeWithTag("workout_ex_sun_reps_0").assertExists()
        compose.onNodeWithTag("activity_name").assertDoesNotExist()
        click("gym_change_plan")
        compose.onNodeWithTag("gym_plan_day_flow").assertExists()
        compose.onNodeWithTag("activity_other_act_yoga").assertIsSelected()

        // Jogging has no routine, so the plain entry is back.
        click("activity_other_jogging")
        compose.onNodeWithTag("gym_plan_day_flow").assertDoesNotExist()
        compose.onNodeWithTag("activity_save").assertExists()
    }

    @Test
    fun theRemindersTapChoosesTheActivityItNamedWhereverItIs() {
        show()
        compose.onNodeWithTag("activity_choice_gym").assertIsSelected()

        compose.runOnIdle { request = AppNavigationRequest(AppDestination.Activity, activityTarget = ActivityLaunchTarget.Activity("act_yoga")) }
        compose.waitForIdle()
        compose.onNodeWithTag("activity_choice_custom").assertIsSelected()
        compose.onNodeWithTag("activity_other_act_yoga").assertIsSelected()
        compose.onNodeWithTag("gym_plan_day_flow").assertExists()

        compose.runOnIdle { request = AppNavigationRequest(AppDestination.Activity, activityTarget = ActivityLaunchTarget.Activity(BoulderingActivityId)) }
        compose.waitForIdle()
        compose.onNodeWithTag("activity_choice_bouldering").assertIsSelected()
        compose.onNodeWithTag("gym_plan_day_boulder").assertExists()

        // An activity nothing offers any more leaves the screen as it is.
        compose.runOnIdle { request = AppNavigationRequest(AppDestination.Activity, activityTarget = ActivityLaunchTarget.Activity("act_gone")) }
        compose.waitForIdle()
        compose.onNodeWithTag("activity_choice_bouldering").assertIsSelected()
        compose.onNodeWithTag("gym_plan_day_boulder").assertExists()
    }

    /**
     * A reminder's tap while an entry is being edited lets the edit go instead of carrying it to
     * the activity the reminder named: the gym shows its routine, not "Save changes" over the
     * entry, the entry's duration is gone from the form, and the entry itself is where it was.
     */
    @Test
    fun theRemindersTapLetsGoOfAnEntryBeingEditedInsteadOfMovingIt() {
        // The form holds a duration only where Settings asks for one.
        show(configuration.copy(logsDuration = true))
        click("activity_entry_edit_activity:entry-b")
        compose.onNodeWithTag("activity_save").assertTextContains("Save changes")
        assertEquals("90", input("activity_duration"))

        compose.runOnIdle { request = AppNavigationRequest(AppDestination.Activity, activityTarget = ActivityLaunchTarget.Activity(GymActivityId)) }
        compose.waitForIdle()
        compose.onNodeWithTag("activity_choice_gym").assertIsSelected()
        compose.onNodeWithTag("gym_plan_a").assertExists()
        compose.onNodeWithTag("activity_save").assertDoesNotExist()
        assertEquals("", input("activity_duration"))
        assertEquals(listOf(boulderingEntry), state.activityEntries)

        // Chosen by hand afterwards, an activity without a routine starts a new entry, not the old one.
        click("activity_choice_cycling")
        compose.onNodeWithTag("activity_save").assertTextContains("Save activity")
        click("activity_save")
        val entries = state.activityEntries.associateBy { it.id }
        assertEquals(boulderingEntry, entries.getValue("entry-b"))
        assertEquals("cycling", entries.values.single { it.id != "entry-b" }.activityId)
    }
}
