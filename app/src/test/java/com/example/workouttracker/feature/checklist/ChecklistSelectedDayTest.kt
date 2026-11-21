package com.example.workouttracker.feature.checklist

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import com.example.workouttracker.core.ui.theme.WorkoutTrackerTheme
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.checklist.AboutDay
import com.example.workouttracker.domain.checklist.ChecklistConfiguration
import com.example.workouttracker.domain.checklist.ChecklistItem
import com.example.workouttracker.domain.checklist.Checkpoint
import com.example.workouttracker.domain.checklist.CheckpointTime
import com.example.workouttracker.domain.checklist.Dated
import com.example.workouttracker.domain.checklist.ViewHue
import com.example.workouttracker.domain.checklist.ViewIcon
import com.example.workouttracker.domain.holiday.HolidaySchedule
import com.example.workouttracker.feature.weight.selectedHistoryDay
import com.example.workouttracker.feature.weight.transcript
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A checklist's day is picked in its calendar or by a reminder, and the screen names it only while
 * it is not the day the screen opens on, which for a checklist about yesterday is yesterday.
 */
@RunWith(RobolectricTestRunner::class)
class ChecklistSelectedDayTest {
    @get:Rule
    val compose = createComposeRule()

    private val lastNight = ChecklistConfiguration(
        id = "chk_night",
        label = "Yesterday",
        icon = ViewIcon.Journal,
        hue = ViewHue.Plum,
        checkpoints = listOf(Checkpoint("cpt_morning", CheckpointTime.Fixed(LocalTime.of(8, 0)), remind = true)),
        items = listOf(ChecklistItem("itm_violin", "Practiced violin")),
        about = Dated.always(AboutDay.Yesterday),
    )
    private val tag = "checklist_chk_night"
    private var todayKey by mutableStateOf("2026-10-13")
    private var requested by mutableStateOf<String?>(null)
    private val restoration by lazy { StateRestorationTester(compose) }

    private fun show() {
        restoration.setContent {
            WorkoutTrackerTheme {
                ChecklistScreen(
                    state = AppState(checklists = listOf(lastNight)),
                    checklist = lastNight,
                    onStateChange = { true },
                    todayKey = todayKey,
                    daysOff = HolidaySchedule(),
                    initialDateKey = requested,
                    onInitialDateConsumed = { requested = null },
                )
            }
        }
        compose.waitForIdle()
    }

    private fun click(tag: String) {
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private fun selectedDay(): String = compose.transcript().selectedHistoryDay(tag)
    private fun dayLine(): String? = compose.transcript().singleOrNull { it.startsWith("[${tag}_selected_day]") }

    @Test
    fun aCalendarDayIsNamedUntilTheOpeningDayIsPickedAgain() {
        show()
        assertEquals("2026-10-12", selectedDay())
        compose.onNodeWithTag("${tag}_selected_day").assertDoesNotExist()

        // Today is not the day this checklist asks about, so it is named like any other day.
        click("${tag}_history_2026-10-13")
        assertEquals("2026-10-13", selectedDay())
        assertEquals("[${tag}_selected_day] text=\"Tue, Oct 13\"", dayLine())

        // A correction stays on its day, and stays named, when the screen is brought back.
        click("${tag}_history_2026-10-08")
        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        assertEquals("2026-10-08", selectedDay())
        assertEquals("[${tag}_selected_day] text=\"Thu, Oct 8\"", dayLine())

        click("${tag}_history_2026-10-12")
        compose.onNodeWithTag("${tag}_selected_day").assertDoesNotExist()
    }

    /**
     * The line takes no room while it says nothing: on the opening day the first item sits where
     * the line stands on another day, and comes back there when the opening day is picked again.
     */
    @Test
    fun theLineTakesNoRoomOnTheOpeningDay() {
        fun top(tag: String) = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot().top
        show()
        val firstItem = top("${tag}_status_itm_violin")

        click("${tag}_history_2026-10-08")
        assertEquals(firstItem, top("${tag}_selected_day"))
        assertTrue(top("${tag}_status_itm_violin") > firstItem)

        click("${tag}_history_2026-10-12")
        assertEquals(firstItem, top("${tag}_status_itm_violin"))
    }

    @Test
    @Config(qualifiers = "de")
    fun theDayIsNamedInTheScreensLanguage() {
        show()
        click("${tag}_history_2026-10-08")
        assertEquals("[${tag}_selected_day] text=\"Do., 8. Okt.\"", dayLine())
    }

    @Test
    fun aRemindersDayIsNamedAndUnsavedTicksKeepTheirDay() {
        show()
        requested = "2026-10-11"
        compose.waitForIdle()
        assertEquals("2026-10-11", selectedDay())
        assertEquals("[${tag}_selected_day] text=\"Sun, Oct 11\"", dayLine())
        assertEquals(null, requested)

        // A screen holding an unsaved tick stays on its day; the request is answered all the same.
        click("${tag}_status_itm_violin")
        requested = "2026-10-09"
        compose.waitForIdle()
        assertEquals("2026-10-11", selectedDay())
        assertEquals(null, requested)
    }

    /** An untouched screen follows the day the checklist asks about and stays unnamed; a picked day stays, named. */
    @Test
    fun anUntouchedScreenFollowsTheNewDayAndAPickedDayStays() {
        show()
        todayKey = "2026-10-14"
        compose.waitForIdle()
        assertEquals("2026-10-13", selectedDay())
        compose.onNodeWithTag("${tag}_selected_day").assertDoesNotExist()

        click("${tag}_history_2026-10-08")
        todayKey = "2026-10-15"
        compose.waitForIdle()
        assertEquals("2026-10-08", selectedDay())
        assertEquals("[${tag}_selected_day] text=\"Thu, Oct 8\"", dayLine())
    }
}
