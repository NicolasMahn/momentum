package de.nicolasmahn.momentum.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.Espresso
import de.nicolasmahn.momentum.R
import de.nicolasmahn.momentum.core.time.currentDateKey
import de.nicolasmahn.momentum.core.ui.theme.WorkoutTrackerTheme
import de.nicolasmahn.momentum.domain.mo.TrackerMoPolicy
import de.nicolasmahn.momentum.domain.AppState
import de.nicolasmahn.momentum.domain.substance.SubstanceGoal
import de.nicolasmahn.momentum.domain.substance.SubstanceKind
import de.nicolasmahn.momentum.domain.substance.SubstanceSettings
import de.nicolasmahn.momentum.testing.appString
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Staying away is chosen in Settings, a substance is marked as one to stay away from, the bank is
 * set, and Save stores all of it at once. The stored state is the proof. On Home the day is kept
 * from the start, and a use of that substance opens it. Whether a day without a use needs a
 * check-in is the goal's "Only log uses".
 */
class SubstanceStayingAwayFlowTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var state by mutableStateOf(AppState(substances = SubstanceSettings(tracked = SubstanceSettings.Legacy.tracked)))

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
    fun stayingAwayIsChosenASubstanceIsMarkedAndTheSaveStoresIt() {
        state = state.copy(substances = state.substances.copy(mo = TrackerMoPolicy(enabled = true)))
        setApp()
        val today = LocalDate.parse(currentDateKey())
        composeRule.onNodeWithTag("home_settings").performClick()
        composeRule.onNodeWithTag("settings_group_substances").performScrollTo().performClick()

        composeRule.onNodeWithTag("substance_settings_tab_streak").performScrollTo().performClick()
        composeRule.onNodeWithTag("substance_settings_goal_staying_away").performScrollTo().performClick()
        composeRule.onNodeWithTag("substance_settings_goal_staying_away").assertIsSelected()
        // Nothing is stayed away from yet, so Streak says so; marking cannabis among the
        // substances takes the line away, and the goal chosen is still chosen on the way back.
        composeRule.onNodeWithTag("substance_settings_nothing_stayed_away").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("substance_settings_tab_substances").performScrollTo().performClick()
        composeRule.onNodeWithTag("substance_settings_staying_away_cannabis").performScrollTo().performClick()
        composeRule.onNodeWithTag("substance_settings_tab_streak").performScrollTo().performClick()
        composeRule.onNodeWithTag("substance_settings_goal_staying_away").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("substance_settings_nothing_stayed_away").assertDoesNotExist()
        composeRule.onNodeWithTag("substance_settings_max_passes_more").performScrollTo().performClick()
        composeRule.runOnIdle { assertNull(state.substances.rewards) }
        composeRule.onNodeWithTag("substance_settings_save").performClick()
        composeRule.runOnIdle {
            assertEquals(SubstanceGoal.StayingAway, state.substances.goalOn(today))
            assertEquals(setOf(SubstanceKind.CANNABIS.id), state.substances.stayingAway.on(today))
            assertEquals(4, state.substances.rewards?.maxPasses?.on(today))
            assertTrue(state.substances.exceptionsOnly.on(today))
        }

        // Saved, the page returned to the Settings list, one step from Home, where the day is kept
        // from the start of the day; a use of cannabis makes it a gap.
        Espresso.pressBack()
        assertTileStatus("home_substances", appString(R.string.app_home_task_kept))
        composeRule.onNodeWithTag("home_substances").performClick()
        // The bank is in the header as on any view with one. The day is kept, so there is nothing
        // to set a token aside for, and the tokens are no switch.
        composeRule.onNodeWithTag("top_tokens").assertIsDisplayed().assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
        composeRule.onNodeWithTag("substance_choice_cannabis").performScrollTo().performClick()
        composeRule.onNodeWithTag("substance_save").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals(listOf(SubstanceKind.CANNABIS.id), state.substanceLogs.map { it.substanceId })
            // The screen opens on yesterday, the day today judges.
            assertEquals(listOf(today.minusDays(1).toString()), state.substanceLogs.map { it.dateKey })
            assertTrue(state.substanceNoneDays.isEmpty())
        }
        composeRule.onNodeWithTag("top_back").performClick()
        assertTileStatus("home_substances", appString(R.string.app_home_task_open))
        composeRule.onNode(
            hasContentDescription(appString(R.string.app_home_task_kept)) and hasAnyAncestor(hasTestTag("home_substances")),
            useUnmergedTree = true,
        ).assertDoesNotExist()
    }

    /**
     * "Only log uses" is exceptions only as it is stored, in a checklist's direction: the box
     * starts checked, the explanation above it follows a press, and Save stores the state the box
     * shows, which is what a reopened page shows again. Its label, role and state are one element.
     */
    @Test
    fun onlyLogUsesStartsCheckedAndStoresTheStateItShows() {
        setApp()
        val today = LocalDate.parse(currentDateKey())
        val box = "substance_settings_only_uses"
        val explanation = "substance_settings_streak_note"
        val noCheckInNeeded = appString(R.string.substance_settings_staying_away_note)
        val checkInNeeded = appString(R.string.substance_settings_staying_away_check_in_note)
        fun openStreak() {
            composeRule.onNodeWithTag("settings_group_substances").performScrollTo().performClick()
            composeRule.onNodeWithTag("substance_settings_tab_streak").performScrollTo().performClick()
        }
        composeRule.onNodeWithTag("home_settings").performClick()
        openStreak()

        // The choice belongs to the staying-away goal alone.
        composeRule.onNodeWithTag(box).assertDoesNotExist()
        composeRule.onNodeWithTag("substance_settings_goal_staying_away").performScrollTo().performClick()
        composeRule.onNodeWithTag(box).performScrollTo().assertIsOn()
            .assertTextEquals(appString(R.string.substance_settings_only_uses))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
        composeRule.onNodeWithTag(explanation).performScrollTo().assertTextEquals(noCheckInNeeded)

        // Unchecked, saved and reopened: a day needs its check-in, and that is what is stored.
        composeRule.onNodeWithTag(box).performScrollTo().performClick()
        composeRule.onNodeWithTag(box).assertIsOff()
        composeRule.onNodeWithTag(explanation).performScrollTo().assertTextEquals(checkInNeeded)
        composeRule.onNodeWithTag("substance_settings_save").performClick()
        composeRule.runOnIdle { assertFalse(state.substances.exceptionsOnly.on(today)) }
        openStreak()
        composeRule.onNodeWithTag(box).performScrollTo().assertIsOff()
        composeRule.onNodeWithTag(explanation).performScrollTo().assertTextEquals(checkInNeeded)

        // Checked, saved and reopened: the reverse.
        composeRule.onNodeWithTag(box).performScrollTo().performClick()
        composeRule.onNodeWithTag(box).assertIsOn()
        composeRule.onNodeWithTag(explanation).performScrollTo().assertTextEquals(noCheckInNeeded)
        composeRule.onNodeWithTag("substance_settings_save").performClick()
        composeRule.runOnIdle { assertTrue(state.substances.exceptionsOnly.on(today)) }
        openStreak()
        composeRule.onNodeWithTag(box).performScrollTo().assertIsOn()
        composeRule.onNodeWithTag(explanation).performScrollTo().assertTextEquals(noCheckInNeeded)
    }

    /** What the badge on the Home tile [tag] says to TalkBack. */
    private fun assertTileStatus(tag: String, description: String) {
        composeRule.onNode(
            hasContentDescription(description) and hasAnyAncestor(hasTestTag(tag)),
            useUnmergedTree = true,
        ).assertExists()
    }
}
