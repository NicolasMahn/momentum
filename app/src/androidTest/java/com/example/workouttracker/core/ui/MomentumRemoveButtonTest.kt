package com.example.workouttracker.core.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.example.workouttracker.R
import com.example.workouttracker.core.ui.theme.WorkoutTrackerTheme
import com.example.workouttracker.testing.appString
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MomentumRemoveButtonTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun onlyTheSecondTapRemovesAndArmingNeverResizesTheButton() {
        var removals = 0
        composeRule.setContent {
            WorkoutTrackerTheme {
                MomentumRemoveButton(
                    description = "Remove day",
                    onRemove = { removals += 1 },
                    testTag = "remove",
                )
            }
        }
        val button = composeRule.onNodeWithTag("remove")
        val restingBounds = button.getUnclippedBoundsInRoot()

        button.performClick()

        composeRule.runOnIdle { assertEquals(0, removals) }
        button.assertContentDescriptionEquals(appString(R.string.core_remove_confirm, "Remove day"))
        assertEquals(restingBounds, button.getUnclippedBoundsInRoot())

        button.performClick()

        composeRule.runOnIdle { assertEquals(1, removals) }
        button.assertContentDescriptionEquals("Remove day")
    }

    @Test
    fun anArmedButtonDisarmsAfterAMomentAndWhenItsSubjectChanges() {
        var removals = 0
        var subject by mutableStateOf("2026-09-15")
        composeRule.setContent {
            WorkoutTrackerTheme {
                MomentumRemoveButton(
                    description = "Remove day",
                    onRemove = { removals += 1 },
                    testTag = "remove",
                    resetKey = subject,
                )
            }
        }
        val button = composeRule.onNodeWithTag("remove")
        composeRule.mainClock.autoAdvance = false

        button.performClick()
        composeRule.mainClock.advanceTimeBy(3_500)
        button.assertContentDescriptionEquals("Remove day")

        button.performClick()
        button.assertContentDescriptionEquals(appString(R.string.core_remove_confirm, "Remove day"))
        subject = "2026-09-16"
        composeRule.mainClock.advanceTimeByFrame()
        button.assertContentDescriptionEquals("Remove day")

        button.performClick()
        composeRule.runOnIdle { assertEquals(0, removals) }
    }

    /**
     * The button beside a main action is a square the same height as that action, and disabling
     * it greys it out without moving anything: that is what lets a day with nothing to remove
     * look identical to one that has something.
     */
    @Test
    fun aDisabledButtonKeepsItsSquareSlotAndCannotBePressed() {
        var removals = 0
        var hasSomethingToRemove by mutableStateOf(true)
        composeRule.setContent {
            WorkoutTrackerTheme {
                Row {
                    MomentumButton(
                        text = "Neighbour",
                        onClick = {},
                        modifier = Modifier.weight(1f),
                        testTag = "neighbour",
                    )
                    MomentumRemoveButton(
                        description = "Remove day",
                        onRemove = { removals += 1 },
                        testTag = "remove",
                        enabled = hasSomethingToRemove,
                    )
                }
            }
        }
        val enabledBounds = composeRule.onNodeWithTag("remove").getUnclippedBoundsInRoot()
        val neighbourBounds = composeRule.onNodeWithTag("neighbour").getUnclippedBoundsInRoot()
        assertEquals(enabledBounds.height.value, enabledBounds.width.value, 1f)
        assertEquals(neighbourBounds.height.value, enabledBounds.height.value, 1f)

        hasSomethingToRemove = false

        composeRule.onNodeWithTag("remove").assertIsNotEnabled()
        assertEquals(enabledBounds, composeRule.onNodeWithTag("remove").getUnclippedBoundsInRoot())
        composeRule.onNodeWithTag("neighbour")
            .assertLeftPositionInRootIsEqualTo(neighbourBounds.left)
        composeRule.runOnIdle { assertEquals(0, removals) }
    }
}
