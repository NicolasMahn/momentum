package com.example.workouttracker.feature.checklist

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The day a notification asked about moves an untouched screen, which then names that day, and never one holding unsaved ticks. */
class ChecklistNotificationDayTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val lastNight = ChecklistConfiguration(
        id = "chk_night",
        label = "Yesterday",
        icon = ViewIcon.Journal,
        hue = ViewHue.Plum,
        checkpoints = listOf(Checkpoint("cpt_morning", CheckpointTime.Fixed(LocalTime.of(8, 0)), remind = true)),
        items = listOf(ChecklistItem("itm_phone", "No phone in bed"), ChecklistItem("itm_violin", "Practiced violin")),
        about = Dated.always(AboutDay.Yesterday),
    )
    private var requested by mutableStateOf<String?>(null)
    private var consumed = 0

    private fun setScreen() {
        composeRule.setContent {
            MaterialTheme {
                ChecklistScreen(
                    state = AppState(checklists = listOf(lastNight)),
                    checklist = lastNight,
                    onStateChange = { true },
                    todayKey = "2026-10-13",
                    daysOff = HolidaySchedule(),
                    initialDateKey = requested,
                    onInitialDateConsumed = { requested = null; consumed++ },
                )
            }
        }
    }

    @Test
    fun aNotificationsDayMovesAnUntouchedScreen() {
        setScreen()
        composeRule.onNodeWithTag("checklist_chk_night_history_2026-10-12").assertIsSelected()
        // The day the screen opens on is not named.
        composeRule.onNodeWithTag("checklist_chk_night_selected_day").assertDoesNotExist()

        composeRule.runOnIdle { requested = "2026-10-11" }

        composeRule.onNodeWithTag("checklist_chk_night_history_2026-10-11").assertIsSelected()
        // Another day is, so what is ticked here is not taken for the day the checklist asks about now.
        composeRule.onNodeWithTag("checklist_chk_night_selected_day").assertTextEquals("Sun, Oct 11")
        composeRule.runOnIdle { assertEquals(1, consumed) }
    }

    @Test
    fun unsavedTicksKeepTheirDayWhenANotificationsDayArrives() {
        setScreen()
        composeRule.onNodeWithTag("checklist_chk_night_status_itm_violin").performClick()
        composeRule.onNodeWithTag("checklist_chk_night_status_itm_violin").assertIsOn()

        composeRule.runOnIdle { requested = "2026-10-11" }

        composeRule.onNodeWithTag("checklist_chk_night_history_2026-10-12").assertIsSelected()
        composeRule.onNodeWithTag("checklist_chk_night_selected_day").assertDoesNotExist()
        composeRule.onNodeWithTag("checklist_chk_night_status_itm_violin").assertIsOn()
        composeRule.runOnIdle { assertEquals(1, consumed) }
    }
}
