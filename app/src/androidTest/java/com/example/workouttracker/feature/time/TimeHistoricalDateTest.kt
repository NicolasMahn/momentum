package com.example.workouttracker.feature.time

import com.example.workouttracker.domain.time.TimeLog
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.workouttracker.core.ui.theme.WorkoutTrackerTheme
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.testfixtures.ReadingTimeId
import com.example.workouttracker.domain.time.addTimeMinutes
import com.example.workouttracker.feature.time.TimeSessionNote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TimeHistoricalDateTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun partialSessionsOnDifferentDaysReachWeeklyGoal() {
        val records = mutableStateOf(emptyList<TimeLog>())
        composeRule.setContent {
            WorkoutTrackerTheme {
                TimeScreenFixture(
                    records = records.value,
                    onAddMinutes = { date, minutes ->
                        records.value = AppState(
                            timeLogs = addTimeMinutes(records.value, ReadingTimeId, date, minutes, records.value.size + 1L),
                        ).timeLogs.filter { it.viewId == ReadingTimeId }
                        true
                    },
                    onRemoveDate = { true },
                    todayKey = "2026-09-20",
                )
            }
        }

        composeRule.onNodeWithTag("time_reading_history_2026-09-16").performClick()
        composeRule.onNodeWithTag("time_reading_minutes_input").performTextReplacement("15")
        composeRule.onNodeWithTag("time_reading_log_today").performClick()
        composeRule.onNodeWithTag("time_reading_week_minutes").assertTextContains("15 / 60 min", substring = true)

        composeRule.onNodeWithTag("time_reading_history_2026-09-19").performClick()
        composeRule.onNodeWithTag("time_reading_minutes_input").performTextReplacement("45")
        composeRule.onNodeWithTag("time_reading_log_today").performClick()
        composeRule.onNodeWithTag("time_reading_week_minutes").assertTextContains("60 / 60 min", substring = true)
    }

    @Test
    fun emptyHistoricalDayBecomesTheCompletionTarget() {
        val todayKey = "2026-09-17"
        var completedDateKey: String? = null
        composeRule.setContent {
            WorkoutTrackerTheme {
                TimeScreenFixture(
                    records = emptyList(),
                    onAddMinutes = { dateKey, _ ->
                        completedDateKey = dateKey
                        true
                    },
                    onRemoveDate = { true },
                    todayKey = todayKey,
                )
            }
        }

        selectPastDay("2026-09-15")
        composeRule.onNodeWithTag("time_reading_selected_day").assertTextEquals("Tue, Sep 15")
        composeRule.onNodeWithTag("time_reading_log_today").performClick()

        composeRule.runOnIdle { assertEquals("2026-09-15", completedDateKey) }
        composeRule.onNodeWithText("Added").assertIsDisplayed()
        composeRule.onNodeWithTag("time_reading_log_today").assertIsNotEnabled()
    }

    @Test
    fun successfulSaveLatchesImmediatelyAndRejectsRapidDuplicateTaps() {
        val todayKey = "2026-09-17"
        var saveCalls = 0
        composeRule.setContent {
            WorkoutTrackerTheme {
                TimeScreenFixture(
                    records = emptyList(),
                    onAddMinutes = { _, _ ->
                        saveCalls += 1
                        true
                    },
                    onRemoveDate = { true },
                    todayKey = todayKey,
                )
            }
        }

        selectPastDay("2026-09-16")
        composeRule.onNodeWithTag("time_reading_log_today").performClick()
        composeRule.onNodeWithTag("time_reading_log_today").performClick()

        composeRule.runOnIdle { assertEquals(1, saveCalls) }
        composeRule.onNodeWithText("Added").assertIsDisplayed()
        composeRule.onNodeWithTag("time_reading_log_today").assertIsNotEnabled()
    }

    @Test
    fun failedSaveStaysRetryableAndNeverShowsCompletedState() {
        val todayKey = "2026-09-17"
        var saveCalls = 0
        composeRule.setContent {
            WorkoutTrackerTheme {
                TimeScreenFixture(
                    records = emptyList(),
                    onAddMinutes = { _, _ ->
                        saveCalls += 1
                        false
                    },
                    onRemoveDate = { true },
                    todayKey = todayKey,
                )
            }
        }
        selectPastDay("2026-09-16")
        freezeClockOnTheFakeDate()

        composeRule.onNodeWithTag("time_reading_log_today").performClick()

        composeRule.mainClock.advanceTimeBy(80)
        composeRule.runOnIdle { assertEquals(1, saveCalls) }
        composeRule.onNodeWithTag("time_reading_save_error").assertIsDisplayed()
        composeRule.onNodeWithText("Add").assertIsDisplayed()
        composeRule.onNodeWithTag("time_reading_log_today_check", useUnmergedTree = true)
            .assertDoesNotExist()
        composeRule.onNodeWithTag("time_reading_log_today_celebration", useUnmergedTree = true)
            .assertDoesNotExist()
    }

    @Test
    fun olderReadingShowsUnknownTimeAndCanReceiveMinutes() {
        val todayKey = "2026-09-17"
        composeRule.setContent {
            WorkoutTrackerTheme {
                TimeScreenFixture(
                    records = listOf(TimeLog(ReadingTimeId, "2026-09-16", 100L)),
                    onAddMinutes = { _, _ -> true },
                    onRemoveDate = { true },
                    todayKey = todayKey,
                )
            }
        }

        selectPastDay("2026-09-16")
        composeRule.onNodeWithTag("time_reading_week_minutes").assertTextContains("0 / 60 min")
        composeRule.onNodeWithTag("time_reading_log_today").assertIsEnabled()
        composeRule.onNodeWithTag("time_reading_log_today_check", useUnmergedTree = true)
            .assertDoesNotExist()
        composeRule.onNodeWithTag("time_reading_log_today_celebration", useUnmergedTree = true)
            .assertDoesNotExist()
    }

    @Test
    fun populatedHistoricalDaySelectsItsDateAndKeepsExactCorrection() {
        val log = TimeLog(ReadingTimeId, "2026-09-15", 100L)
        val todayKey = "2026-09-17"
        composeRule.setContent {
            WorkoutTrackerTheme {
                TimeScreenFixture(
                    records = listOf(log),
                    onAddMinutes = { _, _ -> true },
                    onRemoveDate = { true },
                    todayKey = todayKey,
                )
            }
        }

        composeRule.onNodeWithTag("time_reading_history_2026-09-15").performClick()

        composeRule.onNodeWithTag("time_reading_selected_day").assertTextEquals("Tue, Sep 15")
        composeRule.onNodeWithTag("time_reading_remove_day").assertIsEnabled()
        composeRule.onNodeWithTag("time_reading_remove_day").assertIsDisplayed()
    }

    /** The button never leaves: on a day with nothing to remove it is simply greyed out. */
    @Test
    fun removeButtonKeepsItsPlaceAndOnlyEnablesForDaysThatHaveAReading() {
        composeRule.setContent {
            WorkoutTrackerTheme {
                TimeScreenFixture(
                    records = listOf(TimeLog(ReadingTimeId, "2026-09-15", 100L)),
                    onAddMinutes = { _, _ -> true },
                    onRemoveDate = { true },
                    todayKey = "2026-09-17",
                )
            }
        }

        composeRule.onNodeWithTag("time_reading_remove_day").assertIsNotEnabled()
        composeRule.onNodeWithTag("time_reading_history_2026-09-15").performClick()
        composeRule.onNodeWithTag("time_reading_remove_day").assertIsEnabled()
            .assertContentDescriptionEquals("Remove the entry for Tue, Sep 15")
    }

    @Test
    fun failedRemovalKeepsCompletionAndSuccessfulRemovalAllowsAnotherSave() {
        val log = TimeLog(ReadingTimeId, "2026-09-16", 100L)
        val records = mutableStateOf(listOf(log))
        var allowDelete = false
        var saves = 0
        composeRule.setContent {
            WorkoutTrackerTheme {
                TimeScreenFixture(records.value, { _, _ ->
                    saves += 1
                    records.value = listOf(log)
                    true
                }, {
                    if (allowDelete) records.value = emptyList()
                    allowDelete
                }, "2026-09-17")
            }
        }
        // Removal is reached the same way as everywhere else: on the day the form has selected.
        selectPastDay("2026-09-16")
        // Two taps: the first arms the button, the second removes.
        composeRule.onNodeWithTag("time_reading_remove_day").performClick()
        composeRule.onNodeWithTag("time_reading_remove_day").performClick()
        composeRule.onNodeWithTag("time_reading_delete_error").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(listOf(log), records.value); allowDelete = true }
        composeRule.onNodeWithTag("time_reading_remove_day").performClick()
        composeRule.onNodeWithTag("time_reading_remove_day").performClick()
        composeRule.onNodeWithTag("time_reading_remove_day").assertIsNotEnabled()
        composeRule.onNodeWithTag("time_reading_log_today").assertIsEnabled().performClick()
        composeRule.runOnIdle { assertEquals(1, saves) }
        composeRule.onNodeWithTag("time_reading_log_today").assertIsNotEnabled()
    }

    @Test
    fun delayedRecordRefreshDoesNotBlockAnotherDateOrAllowDuplicateSave() {
        val savedDates = mutableListOf<String>()
        composeRule.setContent {
            WorkoutTrackerTheme {
                TimeScreenFixture(emptyList(), { dateKey, _ -> savedDates += dateKey; true }, { true }, "2026-09-17")
            }
        }
        selectPastDay("2026-09-16")
        composeRule.onNodeWithTag("time_reading_log_today").performClick()
        // Minutes belong to the date they were typed for, so the next date gets its own.
        selectPastDay("2026-09-15")
        composeRule.onNodeWithTag("time_reading_log_today").assertIsEnabled().performClick()
        composeRule.onNodeWithTag("time_reading_history_2026-09-16").performClick()
        composeRule.onNodeWithTag("time_reading_log_today").assertIsNotEnabled()
        composeRule.runOnIdle { assertEquals(listOf("2026-09-16", "2026-09-15"), savedDates) }
    }

    @Test
    fun persistedSaveCelebratesButSelectingExistingDateDoesNotReplayIt() {
        val todayKey = "2026-09-17"
        composeRule.setContent {
            WorkoutTrackerTheme {
                TimeScreenFixture(listOf(TimeLog(ReadingTimeId, "2026-09-15", 100)), { _, _ -> true }, { true }, todayKey)
            }
        }
        selectPastDay("2026-09-16")
        freezeClockOnTheFakeDate()
        composeRule.onNodeWithTag("time_reading_log_today").performClick()
        composeRule.mainClock.advanceTimeBy(80)
        composeRule.onNodeWithTag("time_reading_log_today_celebration", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("time_reading_history_2026-09-15").performClick()
        composeRule.mainClock.advanceTimeBy(32)
        composeRule.onNodeWithTag("time_reading_remove_day").assertIsEnabled()
        composeRule.onNodeWithTag("time_reading_log_today_check", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("time_reading_log_today_celebration", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun synchronousParentRecordUpdateKeepsSuccessfulCelebrationRunning() {
        val todayKey = "2026-09-17"
        val records = mutableStateOf(emptyList<TimeLog>())
        var persisted = false
        composeRule.setContent {
            WorkoutTrackerTheme {
                TimeScreenFixture(
                    records = records.value,
                    onAddMinutes = { dateKey, _ ->
                        // MainActivity publishes the persisted records before its callback returns.
                        persisted = true
                        records.value = listOf(TimeLog(ReadingTimeId, dateKey, 100L))
                        true
                    },
                    onRemoveDate = { true },
                    todayKey = todayKey,
                )
            }
        }
        selectPastDay("2026-09-16")
        freezeClockOnTheFakeDate()
        composeRule.onNodeWithTag("time_reading_log_today").performClick()
        composeRule.mainClock.advanceTimeBy(80)
        composeRule.runOnIdle { assertTrue(persisted) }
        composeRule.onNodeWithTag("time_reading_log_today_celebration", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithTag("time_reading_log_today_check", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.mainClock.advanceTimeBy(1_500)
        composeRule.onNodeWithTag("time_reading_log_today_celebration", useUnmergedTree = true)
            .assertDoesNotExist()
        composeRule.onNodeWithTag("time_reading_log_today_check", useUnmergedTree = true)
            .assertDoesNotExist()
    }

    @Test
    fun todayIsTimedAndOnlyADayAlreadyGoneTakesTypedMinutes() {
        var starts = 0
        val saves = mutableListOf<Int>()
        composeRule.setContent {
            WorkoutTrackerTheme {
                TimeScreenFixture(
                    records = emptyList(),
                    onAddMinutes = { _, minutes -> saves += minutes; true },
                    onRemoveDate = { true },
                    todayKey = "2026-09-17",
                    onStartSession = { starts += 1 },
                )
            }
        }

        composeRule.onNodeWithTag("time_reading_start").performClick()
        composeRule.runOnIdle { assertEquals(1, starts) }

        // Forgetting the timer must never lose reading: today takes typed minutes too, behind a
        // small button, and the way back to the timer is right there.
        composeRule.onNodeWithTag("time_reading_minutes_input").assertDoesNotExist()
        composeRule.onNodeWithTag("time_reading_type_minutes").performClick()
        composeRule.onNodeWithTag("time_reading_start").assertDoesNotExist()
        composeRule.onNodeWithTag("time_reading_minutes_input").performTextReplacement("20")
        composeRule.onNodeWithTag("time_reading_log_today").performClick()
        composeRule.runOnIdle { assertEquals(listOf(20), saves) }
        composeRule.onNodeWithTag("time_reading_back_to_timer").performClick()
        composeRule.onNodeWithTag("time_reading_start").assertIsDisplayed()

        // A past day can only be typed in, and its field starts empty, so Add cannot save minutes
        // nobody typed.
        composeRule.onNodeWithTag("time_reading_history_2026-09-16").performClick()
        composeRule.onNodeWithTag("time_reading_start").assertDoesNotExist()
        composeRule.onNodeWithTag("time_reading_minutes_input")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        composeRule.onNodeWithTag("time_reading_log_today").performClick()
        composeRule.runOnIdle { assertEquals(listOf(20), saves) }
    }

    @Test
    fun aSessionThatEndedByLeavingLeavesOneQuietLine() {
        composeRule.setContent {
            WorkoutTrackerTheme {
                TimeScreenFixture(
                    records = emptyList(),
                    onAddMinutes = { _, _ -> true },
                    onRemoveDate = { true },
                    todayKey = "2026-09-17",
                    sessionNote = TimeSessionNote(ReadingTimeId, "2026-09-17", minutes = 23, leftApp = true),
                )
            }
        }
        composeRule.onNodeWithTag("time_reading_session_note")
            .assertTextContains("The timer ended when you left the app · 23 min saved")
    }

    private fun selectPastDay(dateKey: String, minutes: String = "15") {
        composeRule.onNodeWithTag("time_reading_history_$dateKey").performClick()
        composeRule.onNodeWithTag("time_reading_minutes_input").performTextReplacement(minutes)
    }

    /** The clock may only stop once the first composition has settled on the fake date. */
    private fun freezeClockOnTheFakeDate() {
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
    }
}
