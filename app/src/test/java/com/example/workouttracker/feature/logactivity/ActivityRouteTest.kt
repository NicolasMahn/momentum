package com.example.workouttracker.feature.logactivity

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import com.example.workouttracker.core.ui.theme.WorkoutTrackerTheme
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.activity.ActivityEdit
import com.example.workouttracker.domain.activity.CyclingActivityId
import com.example.workouttracker.domain.activity.GymActivityId
import com.example.workouttracker.domain.activity.activityDaysForWeek
import com.example.workouttracker.domain.activity.applyingEdits
import com.example.workouttracker.domain.activity.route.Route
import com.example.workouttracker.domain.activity.route.RoutePoint
import com.example.workouttracker.domain.activity.route.RouteRecording
import com.example.workouttracker.domain.activity.testActivityConfiguration
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h1600dp-xhdpi")
class ActivityRouteTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = "2026-11-17"
    private val configuration = testActivityConfiguration()
        .applyingEdits(listOf(ActivityEdit.SetActivityRecordsRoute(CyclingActivityId, true)), LocalDate.parse(today))
    private val started = 1_763_366_400_000L

    /** Two kilometres north and back in 8 minutes, with a pause in between. */
    private val ride = Route(listOf(
        (0..10).map { RoutePoint.of(48.7758 + it * 0.0009, 9.1829, it * 24L) },
        (0..10).map { RoutePoint.of(48.7848 - it * 0.0009, 9.1829, 600 + it * 24L) },
    ))

    @Test
    fun aFinishedRecordingIsAnEntryOfItsDayWithItsRouteAndMovingMinutes() {
        val recording = RouteRecording(CyclingActivityId, started, ride, paused = true)
        val result = recordRoute(AppState(activity = configuration), recording, today) as RecordActivityResult.Success
        val entry = result.entry
        assertEquals(CyclingActivityId, entry.activityId)
        assertEquals(today, entry.dateKey)
        assertEquals(started, entry.timestampMillis)
        assertEquals(ride, entry.route)
        // 480 seconds moving; the 360 s pause is not part of it.
        assertEquals(8, entry.durationMinutes)
        // A ride is an active day as any entry is: its distance makes it no more of one.
        assertEquals(1, activityDaysForWeek(result.state.activityEntries, emptyList(), LocalDate.parse("2026-11-17").minusDays(1)))
    }

    @Test
    fun aRecordingThatFoundNoGpsIsStillTheActivityDone() {
        val result = recordRoute(AppState(activity = configuration), RouteRecording(CyclingActivityId, started), today) as RecordActivityResult.Success
        assertNull(result.entry.route)
        assertNull(result.entry.durationMinutes)
    }

    @Test
    fun onlyTodaysLogOfAnActivityRecordedWithGpsOffersARecording() {
        var state by mutableStateOf(AppState(activity = configuration))
        compose.setContent {
            WorkoutTrackerTheme {
                ActivityLogScreen(state = state, onStateChange = { state = it; true }, onWorkoutFinished = {}, todayKey = today)
            }
        }
        click("activity_choice_$CyclingActivityId")
        compose.onNodeWithTag("activity_route_start").assertIsDisplayed()
        // The button under it still logs the ride without a recording.
        compose.onNodeWithTag("activity_save").assertIsDisplayed()

        click("activity_history_2026-11-16")
        compose.onNodeWithTag("activity_route_start").assertDoesNotExist()

        click("activity_history_$today")
        // Another activity, here the gym with its routine, offers none.
        click("activity_choice_$GymActivityId")
        compose.onNodeWithTag("activity_route_start").assertDoesNotExist()
    }

    @Test
    fun aRecordedRideShowsHowFarAndHowLongInTheDaysList() {
        val recording = RouteRecording(CyclingActivityId, started, ride, paused = true)
        val state = (recordRoute(AppState(activity = configuration), recording, today) as RecordActivityResult.Success).state
        compose.setContent {
            WorkoutTrackerTheme {
                ActivityLogScreen(state = state, onStateChange = { true }, onWorkoutFinished = {}, todayKey = today)
            }
        }
        compose.onNodeWithText("2.00 km · 8:00", substring = true).assertIsDisplayed()
    }

    private fun click(tag: String) {
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }
}
