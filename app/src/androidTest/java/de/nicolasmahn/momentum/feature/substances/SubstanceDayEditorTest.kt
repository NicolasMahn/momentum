package de.nicolasmahn.momentum.feature.substances

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import de.nicolasmahn.momentum.testing.assertInputEquals
import de.nicolasmahn.momentum.core.ui.theme.WorkoutTrackerTheme
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.substance.SubstanceKind
import de.nicolasmahn.momentum.domain.substance.SubstanceLog
import de.nicolasmahn.momentum.domain.substance.SubstanceSettings
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * The day's editor on fixed days: on [Today] the screen opens on [Asked], the day before, which is
 * the one Substances asks about, and another day is picked in the calendar under the editor.
 */
class SubstanceDayEditorTest {
    @get:Rule
    val composeRule = createComposeRule()

    /** Picks a day as a person does: by its cell in the calendar. */
    private fun selectDay(dateKey: String) {
        composeRule.onNodeWithTag("substance_history_$dateKey").performScrollTo().performClick()
    }

    @Test
    fun failedDaySaveKeepsTheFullMultiKindDraftRetryable() {
        var saveCalls = 0
        composeRule.setContent {
            WorkoutTrackerTheme {
                SubstanceScreen(
                    onOpenSettings = {},
                    todayKey = Today,
                    state = AppState(substances = SubstanceSettings.Legacy),
                    onStateChange = {
                        saveCalls += 1
                        false
                    },
                )
            }
        }

        selectDay(Earlier)
        composeRule.onNodeWithTag("substance_choice_cannabis").performScrollTo().performClick()
        composeRule.onNodeWithTag("substance_amount_new-cannabis-1").assertDoesNotExist()
        composeRule.onNodeWithTag("substance_optional_details").performClick()
        composeRule.onNodeWithTag("substance_amount_new-cannabis-1")
            .performTextReplacement("0.5")
        composeRule.onNodeWithTag("substance_choice_alcohol").performClick()
        composeRule.onNodeWithTag("substance_amount_new-alcohol-1")
            .performTextReplacement("2")
        composeRule.onNodeWithTag("substance_save").performScrollTo().performClick()

        composeRule.onNodeWithTag("substance_save_error").assertIsDisplayed()
        composeRule.onNodeWithTag("substance_selected_day").assertTextEquals(EarlierNamed)
        composeRule.onNodeWithTag("substance_choice_cannabis").assertIsSelected()
        composeRule.onNodeWithTag("substance_choice_alcohol").assertIsSelected()
        composeRule.onNodeWithTag("substance_amount_new-cannabis-1").assertInputEquals("0.5")
        composeRule.onNodeWithTag("substance_amount_new-alcohol-1").assertInputEquals("2")

        composeRule.onNodeWithTag("substance_save").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(2, saveCalls) }
    }

    @Test
    fun historicalDaySavesReopensAndEditsOneKindWithoutLosingTheOther() {
        var persistedState = AppState(substances = SubstanceSettings.Legacy)
        composeRule.setContent {
            var state by remember { mutableStateOf(AppState(substances = SubstanceSettings.Legacy)) }
            WorkoutTrackerTheme {
                SubstanceScreen(
                    onOpenSettings = {},
                    todayKey = Today,
                    state = state,
                    onStateChange = { updated ->
                        persistedState = updated
                        state = updated
                        true
                    },
                )
            }
        }

        selectDay(Earlier)
        composeRule.onNodeWithTag("substance_choice_cannabis").performScrollTo().performClick()
        composeRule.onNodeWithTag("substance_optional_details").performClick()
        composeRule.onNodeWithTag("substance_amount_new-cannabis-1")
            .performTextReplacement("0.5")
        composeRule.onNodeWithTag("substance_choice_alcohol").performClick()
        composeRule.onNodeWithTag("substance_amount_new-alcohol-1")
            .performTextReplacement("2")
        composeRule.onNodeWithTag("substance_save").performScrollTo().performClick()

        var alcoholId = ""
        var cannabisId = ""
        composeRule.runOnIdle {
            assertEquals(2, persistedState.substanceLogs.size)
            alcoholId = persistedState.substanceLogs.single {
                it.substanceId == SubstanceKind.ALCOHOL.id
            }.id
            cannabisId = persistedState.substanceLogs.single {
                it.substanceId == SubstanceKind.CANNABIS.id
            }.id
        }

        // Away to the opening day and back: the saved day reopens as it was stored.
        selectDay(Asked)
        composeRule.onNodeWithTag("substance_selected_day").assertDoesNotExist()
        selectDay(Earlier)
        composeRule.onNodeWithTag("substance_optional_details").performScrollTo().performClick()
        composeRule.onNodeWithTag("substance_amount_$alcoholId")
            .performTextReplacement("3")
        composeRule.onNodeWithTag("substance_save").performScrollTo().performClick()

        composeRule.runOnIdle {
            assertEquals(2, persistedState.substanceLogs.size)
            assertEquals(0.5, persistedState.substanceLogs.single { it.id == cannabisId }.amount)
            assertEquals(3.0, persistedState.substanceLogs.single { it.id == alcoholId }.amount)
            assertEquals(
                setOf(Earlier),
                persistedState.substanceLogs.mapTo(mutableSetOf()) { it.dateKey },
            )
        }
    }

    @Test
    fun trashRemovesEveryRecordOfTheDayInTwoTapsAndLeavesOtherDaysAlone() {
        // The day the screen opens on holds two records; the day before it, one.
        val asked = SubstanceLog("asked-alcohol", SubstanceKind.ALCOHOL.id, Asked, 10L)
        val alsoAsked = SubstanceLog("asked-tobacco", SubstanceKind.TOBACCO.id, Asked, 11L)
        val other = SubstanceLog("other-alcohol", SubstanceKind.ALCOHOL.id, Earlier, 12L)
        var persistedState = AppState(substances = SubstanceSettings.Legacy, substanceLogs = listOf(asked, alsoAsked, other))
        composeRule.setContent {
            var state by remember {
                mutableStateOf(AppState(substances = SubstanceSettings.Legacy, substanceLogs = listOf(asked, alsoAsked, other)))
            }
            WorkoutTrackerTheme {
                SubstanceScreen(
                    onOpenSettings = {},
                    todayKey = Today,
                    state = state,
                    onStateChange = { updated ->
                        persistedState = updated
                        state = updated
                        true
                    },
                )
            }
        }

        // The first tap only arms the button.
        composeRule.onNodeWithTag("substance_remove_day").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals(3, persistedState.substanceLogs.size)
        }

        composeRule.onNodeWithTag("substance_remove_day").performClick()
        composeRule.runOnIdle { assertEquals(listOf(other), persistedState.substanceLogs) }
        composeRule.onNodeWithTag("substance_choice_alcohol").assertIsNotSelected()
        composeRule.onNodeWithTag("substance_choice_tobacco").assertIsNotSelected()

        // A day with nothing saved keeps the button in place, greyed out.
        composeRule.onNodeWithTag("substance_remove_day").assertIsNotEnabled()
    }

    @Test
    fun dirtyDateChangeRequiresAnExplicitStayOrDiscardDecision() {
        composeRule.setContent {
            WorkoutTrackerTheme {
                SubstanceScreen(state = AppState(substances = SubstanceSettings.Legacy), onStateChange = { true }, todayKey = Today, onOpenSettings = {})
            }
        }

        composeRule.onNodeWithTag("substance_choice_cannabis").performClick()
        composeRule.onNodeWithTag("substance_optional_details").performClick()
        composeRule.onNodeWithTag("substance_amount_new-cannabis-1")
            .performTextReplacement("0.5")
        selectDay(Earlier)

        composeRule.onNodeWithTag("substance_discard").assertIsDisplayed()
        composeRule.onNodeWithTag("substance_discard_stay").performClick()
        composeRule.onNodeWithTag("substance_editor_$Asked").assertExists()
        composeRule.onNodeWithTag("substance_selected_day").assertDoesNotExist()
        composeRule.onNodeWithTag("substance_choice_cannabis").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("substance_amount_new-cannabis-1").assertInputEquals("0.5")

        selectDay(Earlier)
        composeRule.onNodeWithTag("substance_discard_confirm").performClick()
        composeRule.onNodeWithTag("substance_selected_day").assertTextEquals(EarlierNamed)
        composeRule.onNodeWithTag("substance_choice_cannabis").assertIsNotSelected()
    }

    /** A started day stays where it is when the day changes, and is named once it is no longer the day asked about. */
    @Test
    fun anUnsavedDraftSurvivesTheDayChangingAndStaysOnItsOwnDate() {
        var todayKey by mutableStateOf(Asked)
        composeRule.setContent {
            WorkoutTrackerTheme {
                SubstanceScreen(state = AppState(substances = SubstanceSettings.Legacy), onStateChange = { true }, todayKey = todayKey, onOpenSettings = {})
            }
        }

        composeRule.onNodeWithTag("substance_selected_day").assertDoesNotExist()
        composeRule.onNodeWithTag("substance_choice_cannabis").performClick()
        composeRule.runOnIdle { todayKey = Today }

        composeRule.onNodeWithTag("substance_editor_$Earlier").assertExists()
        composeRule.onNodeWithTag("substance_selected_day").assertTextEquals(EarlierNamed)
        composeRule.onNodeWithTag("substance_choice_cannabis").assertIsSelected()
        composeRule.onNodeWithTag("substance_history_$Today").performScrollTo().assertIsDisplayed()
    }

    /** An untouched screen moves to the day now asked about, which stays unnamed. */
    @Test
    fun anUntouchedScreenFollowsTheNewDay() {
        var todayKey by mutableStateOf(Asked)
        composeRule.setContent {
            WorkoutTrackerTheme {
                SubstanceScreen(state = AppState(substances = SubstanceSettings.Legacy), onStateChange = { true }, todayKey = todayKey, onOpenSettings = {})
            }
        }

        composeRule.onNodeWithTag("substance_editor_$Earlier").assertExists()
        composeRule.runOnIdle { todayKey = Today }

        composeRule.onNodeWithTag("substance_editor_$Asked").assertExists()
        composeRule.onNodeWithTag("substance_selected_day").assertDoesNotExist()
    }
}

private const val Today = "2026-09-17"

/** The day the screen opens on while it is [Today]. */
private const val Asked = "2026-09-16"
private const val Earlier = "2026-09-15"
private const val EarlierNamed = "Tue, Sep 15"
