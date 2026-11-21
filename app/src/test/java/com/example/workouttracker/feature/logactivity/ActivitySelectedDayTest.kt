package com.example.workouttracker.feature.logactivity

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import com.example.workouttracker.core.ui.theme.WorkoutTrackerTheme
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.activity.ActivityEntry
import com.example.workouttracker.domain.activity.BoulderingActivityId
import com.example.workouttracker.domain.activity.testActivityConfiguration
import com.example.workouttracker.domain.activity.withActivity
import com.example.workouttracker.feature.weight.selectedHistoryDay
import com.example.workouttracker.feature.weight.transcript
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The day an activity is logged for is picked in Activity's calendar, never typed: the screen
 * opens on today, names any other day it is on, saves there, and comes back to today by a tap
 * on it, a reminder, or the day turning over an untouched form.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h1600dp-xhdpi")
class ActivitySelectedDayTest {
    @get:Rule
    val compose = createComposeRule()

    private val bouldering = ActivityEntry("entry-b", BoulderingActivityId, "Bouldering", "2026-10-19", 100L, durationMinutes = 90)
    private var state by mutableStateOf(AppState(activityEntries = listOf(bouldering)).withActivity())
    private var todayKey by mutableStateOf("2026-10-21")
    private var requested by mutableStateOf<String?>(null)
    private val restoration by lazy { StateRestorationTester(compose) }

    private fun show() {
        restoration.setContent {
            WorkoutTrackerTheme {
                ActivityLogScreen(
                    state = state,
                    onStateChange = { state = it; true },
                    onWorkoutFinished = {},
                    todayKey = todayKey,
                    requestedActivityId = requested,
                    onActivityRequestConsumed = { requested = null },
                )
            }
        }
        compose.waitForIdle()
    }

    private fun click(tag: String) {
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private fun selectedDay(): String = compose.transcript().selectedHistoryDay("activity")
    private fun dayLine(): String? = compose.transcript().singleOrNull { it.startsWith("[activity_selected_day]") }
    private fun newEntry(): ActivityEntry = state.activityEntries.single { it.id != bouldering.id }

    @Test
    fun aCalendarDayIsNamedAndTakesTheEntryUntilTodayIsPickedAgain() {
        show()
        assertEquals("2026-10-21", selectedDay())
        compose.onNodeWithTag("activity_selected_day").assertDoesNotExist()
        // The day is no field to type into, and no fold stands where one used to hide it.
        compose.onNodeWithTag("activity_date").assertDoesNotExist()
        compose.onNodeWithTag("activity_more_options").assertDoesNotExist()

        click("activity_history_2026-10-15")
        assertEquals("[activity_selected_day] text=\"Thu, Oct 15\"", dayLine())

        // A correction stays on its day, and stays named, when the screen is brought back.
        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        assertEquals("2026-10-15", selectedDay())
        assertEquals("[activity_selected_day] text=\"Thu, Oct 15\"", dayLine())

        click("activity_choice_cycling")
        click("activity_save")
        assertEquals("2026-10-15", newEntry().dateKey)
        // Saving leaves the form on the day it was picked for.
        assertEquals("[activity_selected_day] text=\"Thu, Oct 15\"", dayLine())

        click("activity_history_2026-10-21")
        compose.onNodeWithTag("activity_selected_day").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "de-w411dp-h1600dp-xhdpi")
    fun theDayIsNamedInTheScreensLanguage() {
        show()
        click("activity_history_2026-10-15")
        assertEquals("[activity_selected_day] text=\"Do., 15. Okt.\"", dayLine())
    }

    @Test
    fun anEntryOfAnotherDayIsEditedOnItsDay() {
        // The entry opens with its minutes in the field, which stands there once Settings asks for it.
        state = state.withActivity(testActivityConfiguration().copy(logsDuration = true))
        show()
        click("activity_history_2026-10-19")
        click("activity_entry_edit_activity:entry-b")
        assertEquals("[activity_selected_day] text=\"Mon, Oct 19\"", dayLine())
        assertEquals("[activity_duration] field=\"90\"", compose.transcript().single { it.startsWith("[activity_duration]") })

        compose.onNodeWithTag("activity_duration").performTextReplacement("60")
        click("activity_save")
        assertEquals(listOf(bouldering.copy(durationMinutes = 60)), state.activityEntries)
    }

    @Test
    fun aRemindersTapComesBackToToday() {
        show()
        click("activity_history_2026-10-15")
        requested = BoulderingActivityId
        compose.waitForIdle()
        assertEquals("2026-10-21", selectedDay())
        compose.onNodeWithTag("activity_selected_day").assertDoesNotExist()
        assertEquals(null, requested)
    }

    /** An untouched screen follows today and stays unnamed; a picked day stays, named. */
    @Test
    fun anUntouchedScreenFollowsTheNewDayAndAPickedDayStays() {
        show()
        todayKey = "2026-10-22"
        compose.waitForIdle()
        assertEquals("2026-10-22", selectedDay())
        compose.onNodeWithTag("activity_selected_day").assertDoesNotExist()

        click("activity_history_2026-10-15")
        todayKey = "2026-10-23"
        compose.waitForIdle()
        assertEquals("2026-10-15", selectedDay())
        assertEquals("[activity_selected_day] text=\"Thu, Oct 15\"", dayLine())
    }
}
