package com.example.workouttracker.gallery

import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import com.example.workouttracker.app.AppDestination
import com.example.workouttracker.core.ui.theme.WorkoutTrackerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Timer specimens use real screens and sessions over isolated configurations and in-memory logs. */
@RunWith(RobolectricTestRunner::class)
class TimeScreenSpecimensTest {
    @get:Rule
    val compose = createComposeRule()

    private val specimens = viewSpecimens(AppDestination.Time).associateBy { it.name }
    private var shown by mutableStateOf<GallerySpecimen?>(null)

    private fun show(name: String) {
        shown = specimens.getValue(name)
        compose.waitForIdle()
    }

    private fun click(tag: String) {
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    /** The minutes of the selected day's week, read from the count beside the bar ("60 / 90 min"). */
    private fun weekMinutes(prefix: String): Int =
        compose.onNodeWithTag("${prefix}_week_minutes").fetchSemanticsNode().config[SemanticsProperties.Text].single().text.substringBefore(" /").toInt()

    @Test
    fun theScreenAndSessionUseEachTimersConfiguration() {
        compose.setContent { WorkoutTrackerTheme { shown?.let { specimen -> key(specimen.name) { specimen.content() } } } }

        show("TimeScreen, Reading")
        compose.onNodeWithTag("gallery_reading_screen_start").assertExists()
        compose.onNodeWithTag("gallery_reading_screen_history").assertExists()

        show("TimeScreen, a made view")
        val made = "gallery_${DemoPiano.id}"
        compose.onNodeWithTag("${made}_start").assertExists()
        compose.onNodeWithTag("${made}_session_note").assertDoesNotExist()

        // Minutes are added into the specimen's own memory and stay on show, against the view's own goal.
        // The day is picked in the calendar: four days ago, which the fixture leaves empty.
        click("${made}_history_${fixtureDateKey(4)}")
        val before = weekMinutes(made)
        compose.onNodeWithTag("${made}_minutes_input").performTextReplacement("35")
        click("${made}_log_today")
        compose.onNodeWithTag("${made}_week_minutes").assertTextEquals("${before + 35} / 90 min")

        show("TimeScreen, after a session under a minute")
        compose.onNodeWithTag("${made}_session_note").assertExists()

        show("TimeScreen, nothing logged")
        compose.onNodeWithTag("${made}_remove_day").assertExists()

        show("TimeSessionScreen, Reading")
        compose.onNodeWithTag("time_${DemoReadingTime.id}_session_minutes").assertTextEquals("⁨Reading⁩ · 23 min")
        show("TimeSessionScreen, Reading, save failed")
        compose.onNodeWithTag("time_${DemoReadingTime.id}_session_error").assertExists()

        show("TimeSessionScreen, a made view")
        compose.onNodeWithTag("time_${DemoPiano.id}_session_minutes").assertTextEquals("⁨Piano⁩ · 23 min")
        show("TimeSessionScreen, a long name")
        compose.onNodeWithTag("time_tim_l0ng_session_error").assertExists()

        // A flavor changes Mo and not a word: each session still speaks as its view does.
        show("TimeSessionScreen, a made view on the flavor Reading")
        compose.onNodeWithTag("time_${DemoPiano.id}_session_minutes").assertTextEquals("⁨Piano⁩ · 23 min")
        show("TimeSessionScreen, Reading on the flavor Plain")
        compose.onNodeWithTag("time_${DemoReadingTime.id}_session_minutes").assertTextEquals("⁨Reading⁩ · 23 min")
    }
}
