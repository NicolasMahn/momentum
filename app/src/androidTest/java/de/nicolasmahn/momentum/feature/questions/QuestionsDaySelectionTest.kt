package de.nicolasmahn.momentum.feature.questions

import androidx.compose.ui.test.assertTextEquals
import de.nicolasmahn.momentum.testfixtures.EatingQualityQuestionId
import de.nicolasmahn.momentum.testfixtures.EatingAmountQuestionId
import de.nicolasmahn.momentum.testfixtures.EatingQuestionsId
import de.nicolasmahn.momentum.domain.questions.eatingEntry
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import de.nicolasmahn.momentum.R
import de.nicolasmahn.momentum.TestEatingQuestions
import de.nicolasmahn.momentum.core.ui.theme.WorkoutTrackerTheme
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.changingDay
import de.nicolasmahn.momentum.testing.appString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The questions screen as Eating's destination shows it: Eating's configuration, its words and its tags. */
class QuestionsDaySelectionTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val eating = TestEatingQuestions

    private val saved = eatingEntry(
        id = "saved",
        dateKey = "2026-09-15",
        timestampMillis = 1L,
        quality = "good",
    )

    @Test
    fun selectingAnotherDayOpensItAndAnEmptyDayLeavesTheEdit() {
        val state = mutableStateOf(AppState(questions = listOf(eating), questionEntries = listOf(saved)))
        composeRule.setContent {
            WorkoutTrackerTheme {
                QuestionsScreen(
                    state = state.value,
                    view = eating,
                    words = eating.words(),
                    onDayChange = { change -> state.value.changingDay(change, timestampMillis = 2L) { updated -> state.value = updated; true } },
                    todayKey = "2026-09-17",
                    testTagPrefix = eating.testTagPrefix,
                )
            }
        }
        composeRule.onNodeWithTag("questions_eating_delete").assertIsNotEnabled()

        // The day the screen opens on is not named and its line takes no room; a calendar tap
        // opens a saved day and names it.
        composeRule.onNodeWithTag("questions_eating_selected_day").assertDoesNotExist()
        composeRule.onNodeWithTag("questions_eating_history_2026-09-15").performScrollTo().performClick()
        composeRule.onNodeWithTag("questions_eating_selected_day").assertTextEquals("Tue, Sep 15")
        composeRule.onNodeWithTag("questions_eating_delete").assertIsEnabled()
        composeRule.onNodeWithTag("questions_eating_quality_2").assertIsSelected()

        // An empty day is the way out of the edit: nothing can be removed from it.
        composeRule.onNodeWithTag("questions_eating_history_2026-09-14").performScrollTo().performClick()
        composeRule.onNodeWithTag("questions_eating_delete").assertIsNotEnabled()

        composeRule.onNodeWithTag("questions_eating_history_2026-09-15").performScrollTo().performClick()
        composeRule.onNodeWithTag("questions_eating_delete").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(listOf(saved), state.value.questionEntries.filter { it.viewId == EatingQuestionsId }) }
        composeRule.onNodeWithTag("questions_eating_delete").performClick()

        composeRule.runOnIdle { assertTrue(state.value.questionEntries.filter { it.viewId == EatingQuestionsId }.isEmpty()) }
        composeRule.onNodeWithTag("questions_eating_delete").assertIsNotEnabled()
    }

    /**
     * A day that has a reflection shows it: as the screen opens, and after a save, which leaves the
     * form on the saved day with the lasting check. Only a save celebrates; another answered day
     * shows its check without one.
     */
    @Test
    fun aDayThatHasAReflectionShowsItAndASaveLeavesItOnShow() {
        val yesterdays = eatingEntry(id = "yesterday", dateKey = "2026-09-16", timestampMillis = 1L, quality = "fair")
        val state = mutableStateOf(AppState(questions = listOf(eating), questionEntries = listOf(yesterdays, saved)))
        composeRule.setContent {
            WorkoutTrackerTheme {
                QuestionsScreen(
                    state = state.value,
                    view = eating,
                    words = eating.words(),
                    onDayChange = { change -> state.value.changingDay(change, timestampMillis = 2L) { updated -> state.value = updated; true } },
                    todayKey = "2026-09-17",
                    testTagPrefix = eating.testTagPrefix,
                )
            }
        }
        composeRule.onNodeWithTag("questions_eating_history_2026-09-16").assertIsSelected()
        composeRule.onNodeWithTag("questions_eating_quality_1").assertIsSelected()
        composeRule.onNodeWithTag("questions_eating_amount_not_sure").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("questions_eating_save").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithTag("questions_eating_save_check", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("questions_eating_delete").assertIsEnabled()

        // A change is the action again, in the words of a change, and its save celebrates.
        composeRule.onNodeWithTag("questions_eating_amount_2").performScrollTo().performClick()
        composeRule.onNodeWithTag("questions_eating_save").performScrollTo().assertIsEnabled()
        composeRule.onNodeWithText(appString(R.string.questions_save_changes)).assertIsDisplayed()
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag("questions_eating_save").performClick()
        composeRule.mainClock.advanceTimeBy(80)
        composeRule.onNodeWithTag("questions_eating_save_celebration", useUnmergedTree = true).assertIsDisplayed()
        composeRule.mainClock.autoAdvance = true
        composeRule.runOnIdle {
            assertEquals(listOf(yesterdays.copy(timestampMillis = 2L, answers = yesterdays.answers + (EatingAmountQuestionId to "about_the_same")), saved), state.value.questionEntries.filter { it.viewId == EatingQuestionsId })
        }

        // The form stays on the saved day with what was saved.
        composeRule.onNodeWithTag("questions_eating_history_2026-09-16").assertIsSelected()
        composeRule.onNodeWithTag("questions_eating_quality_1").assertIsSelected()
        composeRule.onNodeWithTag("questions_eating_amount_2").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("questions_eating_save").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithTag("questions_eating_save_check", useUnmergedTree = true).assertIsDisplayed()

        composeRule.onNodeWithTag("questions_eating_history_2026-09-15").performScrollTo().performClick()
        composeRule.onNodeWithTag("questions_eating_quality_2").assertIsSelected()
        composeRule.onNodeWithTag("questions_eating_save_check", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("questions_eating_save_celebration", useUnmergedTree = true).assertDoesNotExist()
    }

    /**
     * Answers picked and not saved survive process death with the day they were picked for: on a
     * day without a reflection, and on one that has one, where they come back as a change of it.
     */
    @Test
    fun aStartedReflectionSurvivesProcessDeathWithItsDay() {
        val state = mutableStateOf(AppState(questions = listOf(eating), questionEntries = listOf(saved)))
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent {
            WorkoutTrackerTheme {
                QuestionsScreen(
                    state = state.value,
                    view = eating,
                    words = eating.words(),
                    onDayChange = { change -> state.value.changingDay(change, timestampMillis = 2L) { updated -> state.value = updated; true } },
                    todayKey = "2026-09-17",
                    testTagPrefix = eating.testTagPrefix,
                )
            }
        }
        composeRule.onNodeWithTag("questions_eating_history_2026-09-14").performScrollTo().performClick()
        composeRule.onNodeWithTag("questions_eating_quality_3").performScrollTo().performClick()
        composeRule.onNodeWithTag("questions_eating_amount_not_sure").performScrollTo().performClick()

        restoration.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithTag("questions_eating_history_2026-09-14").assertIsSelected()
        composeRule.onNodeWithTag("questions_eating_quality_3").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("questions_eating_amount_not_sure").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("questions_eating_save").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals(eatingEntry(id = state.value.questionEntries.filter { it.viewId == EatingQuestionsId }.last().id, dateKey = "2026-09-14", timestampMillis = 2L, quality = "very_good"), state.value.questionEntries.filter { it.viewId == EatingQuestionsId }.last())
        }

        composeRule.onNodeWithTag("questions_eating_history_2026-09-15").performScrollTo().performClick()
        composeRule.onNodeWithTag("questions_eating_quality_4").performScrollTo().performClick()

        restoration.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithTag("questions_eating_history_2026-09-15").assertIsSelected()
        composeRule.onNodeWithTag("questions_eating_quality_4").performScrollTo().assertIsSelected()
        composeRule.onNodeWithText(appString(R.string.questions_save_changes)).assertExists()
        // The reflection it opened on came back with it, so the save goes into that reflection.
        composeRule.onNodeWithTag("questions_eating_save").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals(saved.copy(timestampMillis = 2L, answers = saved.answers + (EatingQualityQuestionId to "excellent")), state.value.questionEntries.filter { it.viewId == EatingQuestionsId }.first())
        }
    }

    @Test
    fun aStartedReflectionSurvivesMidnightButAnUntouchedOneMovesOn() {
        val todayKey = mutableStateOf("2026-09-17")
        val state = AppState(questions = listOf(eating))
        composeRule.setContent {
            WorkoutTrackerTheme {
                QuestionsScreen(
                    state = state,
                    view = eating,
                    words = eating.words(),
                    onDayChange = { change -> state.changingDay(change, timestampMillis = 2L) { true } },
                    todayKey = todayKey.value,
                    testTagPrefix = eating.testTagPrefix,
                )
            }
        }
        // Eating looks back, so the untouched form follows "yesterday" as the day changes.
        composeRule.onNodeWithTag("questions_eating_history_2026-09-16").assertIsSelected()
        composeRule.runOnIdle { todayKey.value = "2026-09-18" }
        composeRule.onNodeWithTag("questions_eating_history_2026-09-17").assertIsSelected()

        composeRule.onNodeWithTag("questions_eating_quality_2").performClick()
        composeRule.runOnIdle { todayKey.value = "2026-09-19" }

        composeRule.onNodeWithTag("questions_eating_history_2026-09-17").assertIsSelected()
        composeRule.onNodeWithTag("questions_eating_quality_2").assertIsSelected()
        // Yesterday has moved on without the started form, which now names the day it is on.
        composeRule.onNodeWithTag("questions_eating_selected_day").assertTextEquals("Thu, Sep 17")
    }
}
