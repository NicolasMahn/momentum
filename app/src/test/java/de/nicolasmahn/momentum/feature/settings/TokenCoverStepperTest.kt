package de.nicolasmahn.momentum.feature.settings

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import de.nicolasmahn.momentum.core.ui.theme.WorkoutTrackerTheme
import de.nicolasmahn.momentum.domain.checklist.WhenAway
import de.nicolasmahn.momentum.domain.time.TimeEdit
import de.nicolasmahn.momentum.domain.time.TokenCover
import de.nicolasmahn.momentum.domain.time.applyingEdits
import de.nicolasmahn.momentum.domain.time.testMadeTime
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h1600dp")
class TokenCoverStepperTest {
    @get:Rule val compose = createComposeRule()
    private val today = LocalDate.parse("2026-10-26")

    private fun show(cover: TokenCover? = null, goal: Int = 60): TimeSettingsDraft {
        val joined = testMadeTime(goal = 120).applyingEdits(listOf(TimeEdit.JoinRewards), today.minusWeeks(1))
        val view = joined.applyingEdits(listOfNotNull(cover?.let(TimeEdit::SetTokenCover), TimeEdit.SetGoal(goal)), today)
        val draft = TimeSettingsDraft(view, today)
        compose.setContent {
            WorkoutTrackerTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) { TimeStreak(view = draft.edited, draft = draft, today = today, testTag = "time") }
            }
        }
        return draft
    }

    /** The line about a quarter of the goal goes once the cover is set, and its room stays, so the holiday choice below does not move. */
    @Test
    fun settingTheCoverMovesNothingBelowIt() {
        show()
        compose.onNodeWithTag("time_token_cover_quarter").assertExists()
        val before = compose.onNodeWithTag("time_away_${WhenAway.Due.storageId}").getUnclippedBoundsInRoot()
        compose.onNodeWithTag("time_token_cover_more").performClick()
        compose.onNodeWithText("20 min").assertExists()
        compose.onNodeWithTag("time_token_cover_quarter").assertDoesNotExist()
        assertEquals(before, compose.onNodeWithTag("time_away_${WhenAway.Due.storageId}").getUnclippedBoundsInRoot())
    }

    /**
     * The cover is not tied to the goal: a cover above a goal lowered since, or one raised before
     * the goal is lowered on the same page, can be stepped down and back up.
     */
    @Test
    fun aGoalLoweredOnThePageNeverStrandsTheCover() {
        val draft = show()
        compose.onNodeWithTag("time_token_cover_more").performClick()
        compose.onNodeWithText("20 min").assertExists()
        draft.typeGoal("12")
        compose.onNodeWithTag("time_token_cover_less").performClick()
        compose.onNodeWithText("15 min").assertExists()
        compose.onNodeWithTag("time_token_cover_more").assertIsEnabled().performClick()
        compose.onNodeWithText("20 min").assertExists()
    }

    /** A cover stored above a goal lowered since can be stepped down and back up while the page is open. */
    @Test
    fun aCoverAboveTheGoalStaysReachable() {
        show(cover = TokenCover.Minutes(100), goal = 60)
        compose.onNodeWithText("100 min").assertExists()
        compose.onNodeWithTag("time_token_cover_less").performClick()
        compose.onNodeWithText("95 min").assertExists()
        compose.onNodeWithTag("time_token_cover_more").assertIsEnabled().performClick()
        compose.onNodeWithText("100 min").assertExists()
    }
}
