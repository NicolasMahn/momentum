package com.example.workouttracker.feature.supplements

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.workouttracker.core.ui.theme.WorkoutTrackerTheme
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.testfixtures.StandardSupplementIds
import com.example.workouttracker.AllSupplementIds
import com.example.workouttracker.TestSupplementsChecklist
import com.example.workouttracker.YearRoundSupplementIds
import com.example.workouttracker.feature.checklist.ChecklistScreen
import com.example.workouttracker.domain.checklist.ChecklistDay
import com.example.workouttracker.supplementDay
import com.example.workouttracker.supplementTicks
import com.example.workouttracker.supplementsAppState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import com.example.workouttracker.domain.holiday.HolidaySchedule

class SupplementHistoricalDateTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyCalendarDayBecomesTheSaveTarget() {
        var persistedState = supplementsAppState()
        composeRule.setContent {
            WorkoutTrackerTheme {
                var state by remember { mutableStateOf(persistedState) }
                ChecklistScreen(
                    checklist = TestSupplementsChecklist,
                    state = state,
                    onStateChange = { updated ->
                        state = updated
                        persistedState = updated
                        true
                    },
                    todayKey = "2026-09-17",
                    daysOff = HolidaySchedule(),
                )
            }
        }

        composeRule.onNodeWithTag("checklist_supplements_history_2026-09-15")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("checklist_supplements_history_2026-09-15").assertIsSelected()
        composeRule.onNodeWithTag("checklist_supplements_selected_day").assertTextEquals("Tue, Sep 15")
        // Nothing starts ticked, so there is nothing to save until the day's supplements are pressed.
        composeRule.onNodeWithTag("checklist_supplements_status_multivitamin").performScrollTo().performClick()
        composeRule.onNodeWithTag("checklist_supplements_status_creatine").performScrollTo().performClick()
        composeRule.onNodeWithTag("checklist_supplements_save").performScrollTo().performClick()

        composeRule.runOnIdle {
            assertEquals(
                supplementDay(
                    dateKey = "2026-09-15",
                    timestampMillis = persistedState.checklistDays.single().timestampMillis,
                    takenIds = YearRoundSupplementIds,
                ),
                persistedState.checklistDays.single(),
            )
        }
    }

    @Test
    fun populatedCalendarDaySelectsItsDateAndKeepsExactCorrection() {
        val historical = supplementDay(
            dateKey = "2026-09-15",
            timestampMillis = 1L,
            takenIds = setOf(StandardSupplementIds.CREATINE),
        )
        composeRule.setContent {
            WorkoutTrackerTheme {
                ChecklistScreen(
                    checklist = TestSupplementsChecklist,
                    state = supplementsAppState(historical),
                    onStateChange = { true },
                    todayKey = "2026-09-17",
                    daysOff = HolidaySchedule(),
                )
            }
        }

        composeRule.onNodeWithTag("checklist_supplements_history_2026-09-15")
            .performScrollTo()
            .performClick()

        composeRule.onNodeWithTag("checklist_supplements_history_2026-09-15").assertIsSelected()
        composeRule.onNodeWithTag("checklist_supplements_selected_day").assertTextEquals("Tue, Sep 15")
        composeRule.onNodeWithTag("checklist_supplements_status_creatine").assertIsOn()
        composeRule.onNodeWithTag("checklist_supplements_status_multivitamin").assertIsOff()
        composeRule.onNodeWithTag("checklist_supplements_remove_day").assertIsDisplayed()
    }

    /** The button never leaves: on a day with no record it is simply greyed out. */
    @Test
    fun removeButtonKeepsItsPlaceAndOnlyEnablesForDaysWithASavedRecord() {
        composeRule.setContent {
            WorkoutTrackerTheme {
                ChecklistScreen(
                    checklist = TestSupplementsChecklist,
                    state = supplementsAppState(supplementDay("2026-09-15", 1L, AllSupplementIds)),
                    onStateChange = { true },
                    todayKey = "2026-09-17",
                    daysOff = HolidaySchedule(),
                )
            }
        }

        composeRule.onNodeWithTag("checklist_supplements_remove_day").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithTag("checklist_supplements_history_2026-09-15")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("checklist_supplements_remove_day")
            .performScrollTo()
            .assertIsEnabled()
            .assertContentDescriptionEquals("Remove the record for Tue, Sep 15")
    }

    @Test
    fun twoTapsOnTheRemoveButtonDeleteTheSelectedDay() {
        var persistedState = supplementsAppState(supplementDay("2026-09-15", 1L, AllSupplementIds))
        composeRule.setContent {
            WorkoutTrackerTheme {
                var state by remember { mutableStateOf(persistedState) }
                ChecklistScreen(
                    checklist = TestSupplementsChecklist,
                    state = state,
                    onStateChange = { updated ->
                        state = updated
                        persistedState = updated
                        true
                    },
                    todayKey = "2026-09-17",
                    daysOff = HolidaySchedule(),
                )
            }
        }

        composeRule.onNodeWithTag("checklist_supplements_history_2026-09-15")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("checklist_supplements_remove_day").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(1, persistedState.checklistDays.size) }
        composeRule.onNodeWithTag("checklist_supplements_remove_day")
            .assertContentDescriptionEquals("Confirm: Remove the record for Tue, Sep 15")
        composeRule.onNodeWithTag("checklist_supplements_remove_day").performClick()

        composeRule.runOnIdle { assertEquals(emptyList<ChecklistDay>(), persistedState.checklistDays) }
        composeRule.onNodeWithTag("checklist_supplements_remove_day").assertIsNotEnabled()
    }

    @Test
    fun clearingEverySupplementAndSavingRemovesTheDay() {
        var persistedState = supplementsAppState(supplementDay("2026-09-17", 1L, YearRoundSupplementIds))
        composeRule.setContent {
            WorkoutTrackerTheme {
                var state by remember { mutableStateOf(persistedState) }
                ChecklistScreen(
                    checklist = TestSupplementsChecklist,
                    state = state,
                    onStateChange = { updated ->
                        state = updated
                        persistedState = updated
                        true
                    },
                    todayKey = "2026-09-17",
                    daysOff = HolidaySchedule(),
                )
            }
        }

        composeRule.onNodeWithTag("checklist_supplements_status_multivitamin").performScrollTo().performClick()
        composeRule.onNodeWithTag("checklist_supplements_status_creatine").performScrollTo().performClick()
        composeRule.onNodeWithTag("checklist_supplements_save").performScrollTo().performClick()

        composeRule.runOnIdle { assertEquals(emptyList<ChecklistDay>(), persistedState.checklistDays) }
    }

    /** Every other day here is in September, outside Vitamin D's season; this one is inside it. */
    @Test
    fun aWinterDayAsksForVitaminDAndSavesItWithTheOthers() {
        var persistedState = supplementsAppState()
        composeRule.setContent {
            WorkoutTrackerTheme {
                var state by remember { mutableStateOf(persistedState) }
                ChecklistScreen(
                    checklist = TestSupplementsChecklist,
                    state = state,
                    onStateChange = { updated ->
                        state = updated
                        persistedState = updated
                        true
                    },
                    todayKey = "2026-12-16",
                    daysOff = HolidaySchedule(),
                )
            }
        }

        composeRule.onNodeWithTag("checklist_supplements_status_vitamin_d").performScrollTo().assertIsDisplayed()
        AllSupplementIds.forEach { id -> composeRule.onNodeWithTag("checklist_supplements_status_$id").performScrollTo().performClick() }
        // The day is only all done with Vitamin D ticked as well: it is due, not merely offered.
        composeRule.onNodeWithTag("checklist_supplements_save")
            .assertTextContains("Mark all done")
            .performScrollTo()
            .performClick()

        composeRule.runOnIdle {
            assertEquals(
                supplementDay(
                    dateKey = "2026-12-16",
                    timestampMillis = persistedState.checklistDays.single().timestampMillis,
                    takenIds = AllSupplementIds,
                ),
                persistedState.checklistDays.single(),
            )
        }
    }
}
