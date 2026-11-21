package com.example.workouttracker.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.Espresso
import com.example.workouttracker.R
import com.example.workouttracker.core.time.currentDateKey
import com.example.workouttracker.core.ui.theme.WorkoutTrackerTheme
import com.example.workouttracker.domain.AppState
import com.example.workouttracker.domain.checklist.WhenAway
import com.example.workouttracker.domain.substance.SubstanceGoal
import com.example.workouttracker.domain.substance.SubstanceSettings
import com.example.workouttracker.testing.appString
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The check-in is switched on and Logging chosen in Settings, "None yesterday" logs the day, and the
 * Substances tile wears the badge a joined checklist's does. The stored state is the proof: the
 * choices change only when Save says so, and the day is kept by being logged, not by what it holds.
 */
class SubstanceCheckInFlowTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var state by mutableStateOf(AppState(substances = SubstanceSettings.Legacy))

    private fun setApp() {
        composeRule.setContent {
            WorkoutTrackerTheme {
                MomentumApp(
                    state = state,
                    onStateChange = { state = it; true },
                    onActivitySave = ActivitySaveNotExpected,
                )
            }
        }
    }

    @Test
    fun theCheckInAndTheStreakAreSwitchedOnAndNoneTodayEarnsTheBadge() {
        setApp()
        val today = LocalDate.parse(currentDateKey())

        // Until the switches are on, the tile is plainly outside the streak.
        assertNoTileStatus("home_substances")
        composeRule.onNodeWithTag("home_settings").performClick()
        composeRule.onNodeWithTag("settings_group_substances").performScrollTo().performClick()
        composeRule.onNodeWithTag("substance_settings").assertIsDisplayed()

        // The switch, the goal and what an away day asks stay on the page until Save.
        composeRule.onNodeWithTag("substance_settings_tab_reminders").performScrollTo().performClick()
        composeRule.onNodeWithTag("substance_settings_reminder").performScrollTo().performClick()
        composeRule.onNodeWithTag("substance_settings_tab_streak").performScrollTo().performClick()
        composeRule.onNodeWithTag("substance_settings_goal_logging").performScrollTo().performClick()
        composeRule.onNodeWithTag("substance_settings_goal_logging").assertIsSelected()
        composeRule.onNodeWithTag("substance_settings_away_excused").performScrollTo().performClick()
        composeRule.onNodeWithTag("substance_settings_away_excused").assertIsSelected()
        composeRule.runOnIdle { assertEquals(SubstanceSettings.Legacy, state.substances) }
        composeRule.onNodeWithTag("substance_settings_save").performClick()
        composeRule.runOnIdle {
            assertTrue(state.substances.checkIn.remind)
            assertEquals(SubstanceGoal.Logging, state.substances.goalOn(today))
            assertEquals(WhenAway.Excused, state.substances.whenAway.on(today))
        }

        // Before the first logging record, reminder and reward settings create no completion.
        Espresso.pressBack()
        assertNoTileStatus("home_substances")

        // The screen opens on yesterday, and "None yesterday" logs it; the day logged is the coin: kept,
        // as Substances' day is never "done".
        val yesterday = today.minusDays(1)
        composeRule.onNodeWithTag("home_substances").performClick()
        composeRule.onNodeWithTag("substance_none_today").performScrollTo().performClick()
        composeRule.onNodeWithTag("substance_none_today").assertIsOn()
        composeRule.onNodeWithTag("substance_save").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals(listOf(yesterday.toString()), state.substanceNoneDays.map { it.dateKey })
            assertTrue(state.substanceLogs.isEmpty())
        }
        composeRule.onNodeWithTag("top_back").performClick()
        assertTileStatus("home_substances", appString(R.string.app_home_task_kept))

        // Logging a use instead of none keeps the day exactly the same way.
        composeRule.onNodeWithTag("home_substances").performClick()
        composeRule.onNodeWithTag("substance_choice_cannabis").performScrollTo().performClick()
        composeRule.onNodeWithTag("substance_none_today").assertIsOff()
        composeRule.onNodeWithTag("substance_save").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertTrue(state.substanceNoneDays.isEmpty())
            assertEquals(listOf(yesterday.toString()), state.substanceLogs.map { it.dateKey })
        }
        composeRule.onNodeWithTag("top_back").performClick()
        assertTileStatus("home_substances", appString(R.string.app_home_task_kept))
    }

    /** What the badge on the Home tile [tag] says to TalkBack. */
    private fun assertTileStatus(tag: String, description: String) {
        composeRule.onNode(
            hasContentDescription(description) and hasAnyAncestor(hasTestTag(tag)),
            useUnmergedTree = true,
        ).assertExists()
    }

    private fun assertNoTileStatus(tag: String) {
        listOf(R.string.app_home_task_open, R.string.app_home_task_kept).forEach { status ->
            composeRule.onNode(
                hasContentDescription(appString(status)) and hasAnyAncestor(hasTestTag(tag)),
                useUnmergedTree = true,
            ).assertDoesNotExist()
        }
    }
}
