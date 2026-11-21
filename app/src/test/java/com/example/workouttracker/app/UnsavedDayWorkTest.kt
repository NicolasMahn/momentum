package com.example.workouttracker.app

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import com.example.workouttracker.core.time.currentDateKey
import com.example.workouttracker.core.ui.theme.WorkoutTrackerTheme
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.activity.ActivityEdit
import com.example.workouttracker.domain.activity.ActivityEntry
import com.example.workouttracker.domain.activity.ListedActivity
import com.example.workouttracker.domain.activity.applyingEdits
import com.example.workouttracker.domain.activity.routine.ExerciseLog
import com.example.workouttracker.domain.activity.routine.LoggedSet
import com.example.workouttracker.domain.activity.routine.Routine
import com.example.workouttracker.domain.activity.routine.RoutineDay
import com.example.workouttracker.domain.activity.routine.RoutineExercise
import com.example.workouttracker.domain.activity.routine.WorkoutSession
import com.example.workouttracker.domain.activity.testActivityConfiguration
import com.example.workouttracker.domain.checklist.ChecklistConfiguration
import com.example.workouttracker.domain.checklist.ChecklistItem
import com.example.workouttracker.domain.checklist.Checkpoint
import com.example.workouttracker.domain.checklist.CheckpointTime
import com.example.workouttracker.domain.checklist.ViewHue
import com.example.workouttracker.domain.checklist.ViewIcon
import com.example.workouttracker.domain.checklist.recordOn
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * What a day screen holds and has not saved is not thrown away in passing: Back, the arrow and,
 * on a checklist, another day of its calendar ask first, and Keep editing keeps it. A screen with
 * nothing unsaved is left at once, and an activity picked from Other's list is no typing.
 */
@RunWith(RobolectricTestRunner::class)
class UnsavedDayWorkTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val today = currentDateKey()
    private val yesterday = LocalDate.parse(today).minusDays(1).toString()
    private val plants = ChecklistConfiguration(
        id = "chk_plants",
        label = "Plants",
        icon = ViewIcon.Checklist,
        hue = ViewHue.Pine,
        checkpoints = listOf(Checkpoint("cpt_morning", CheckpointTime.Fixed(LocalTime.of(8, 0)), remind = false)),
        items = listOf(ChecklistItem("itm_water", "Water")),
    )
    private val flow = RoutineDay("day_flow", "Flow", listOf(RoutineExercise("ex_sun", "Sun salutation", 2, 5..10, weight = null, startReps = 5)))
    private val activity = testActivityConfiguration().applyingEdits(
        listOf(ActivityEdit.AddActivity(ListedActivity("act_yoga", "Yoga", ViewIcon.Bolt, routine = Routine(listOf(flow))))),
        LocalDate.parse(today),
    ).copy(logsDuration = true)
    private val flowToday = WorkoutSession(
        id = "$today-day_flow", activityId = "act_yoga", day = "day_flow", dateKey = today, timestampMillis = 200L,
        exerciseLogs = listOf(ExerciseLog("ex_sun", "Sun salutation", listOf(LoggedSet(null, 6.0)))),
        durationMinutes = 20,
    )
    private val yogaEntry = ActivityEntry("entry-y", "act_yoga", "Yoga", today, 100L)
    private val stored = AppState(checklists = listOf(plants), activity = activity, activityEntries = listOf(yogaEntry), sessions = listOf(flowToday))
    private var state by mutableStateOf(stored)

    private val tag = "checklist_chk_plants"
    private val water = "${tag}_status_itm_water"
    private val discard = "${tag}_discard"

    private fun setApp() = compose.setContent {
        WorkoutTrackerTheme { MomentumApp(state = state, onStateChange = { state = it; true }, onActivitySave = ActivitySaveNotExpected) }
    }

    private fun click(tag: String) {
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    /** A day of the calendar, which sits below the form. */
    private fun clickDay(dateKey: String) {
        compose.onNodeWithTag("${tag}_history_$dateKey").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private fun systemBack() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private fun onHome() = compose.onNodeWithTag("home_checklist_${plants.id}").assertExists()

    @Test
    fun backAsksBeforeTicksAreLostAndKeepEditingKeepsThem() {
        setApp()
        click("home_checklist_${plants.id}")
        click(water)

        systemBack()
        compose.onNodeWithTag(discard).assertExists()
        click("${discard}_stay")
        compose.onNodeWithTag(discard).assertDoesNotExist()
        compose.onNodeWithTag(water).assertIsOn()

        click("top_back")
        click("${discard}_confirm")
        onHome()
        assertTrue(state.recordOn(plants.id, today).isEmpty)
    }

    @Test
    fun anotherDayAsksBeforeTicksAreLost() {
        setApp()
        click("home_checklist_${plants.id}")
        click(water)

        clickDay(yesterday)
        click("${discard}_stay")
        compose.onNodeWithTag(water).assertIsOn()
        compose.onNodeWithTag("${tag}_selected_day").assertDoesNotExist()

        clickDay(yesterday)
        click("${discard}_confirm")
        compose.onNodeWithTag("${tag}_selected_day").assertExists()
        compose.onNodeWithTag(water).assertIsOff()
        assertEquals(stored, state)
    }

    @Test
    fun anUntouchedScreenIsLeftWithoutAQuestion() {
        setApp()
        click("home_checklist_${plants.id}")
        clickDay(yesterday)
        compose.onNodeWithTag(discard).assertDoesNotExist()
        systemBack()
        onHome()
    }

    @Test
    fun activityAsksForATypedNameAndNotForAPickedOne() {
        setApp()
        click("home_log_activity")
        click("activity_choice_custom")
        click("activity_other_act_yoga")
        systemBack()
        onHome()

        click("home_log_activity")
        click("activity_choice_custom")
        compose.onNodeWithTag("activity_name").performTextReplacement("Rowing")
        compose.waitForIdle()
        systemBack()
        compose.onNodeWithTag("activity_discard").assertExists()
        click("activity_discard_confirm")
        onHome()
        assertEquals(stored, state)
    }

    /** An open routine day's minutes are stored only by Finish, so Back asks before it closes the day. */
    @Test
    fun backAsksBeforeAnOpenRoutineDaysChangedMinutesAreLost() {
        setApp()
        click("home_log_activity")
        click("activity_choice_custom")
        click("activity_other_act_yoga")
        click("gym_plan_day_flow")
        compose.onNodeWithTag("activity_duration").performTextReplacement("35")
        compose.waitForIdle()
        systemBack()
        click("activity_discard_stay")
        compose.onNodeWithTag("gym_change_plan").assertExists()
        systemBack()
        click("activity_discard_confirm")
        compose.onNodeWithTag("gym_plan_day_flow").assertExists()
        assertEquals(stored, state)
    }

    /** An entry opened in Edit and cancelled leaves nothing typed behind. */
    @Test
    fun aCancelledEditLeavesNothingToAskAbout() {
        setApp()
        click("home_log_activity")
        click("activity_entry_edit_activity:entry-y")
        click("activity_edit_cancel")
        systemBack()
        compose.onNodeWithTag("activity_discard").assertDoesNotExist()
        onHome()
    }
}
