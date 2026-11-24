package de.nicolasmahn.momentum.core.ui

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import de.nicolasmahn.momentum.core.ui.completionButtonColors
import de.nicolasmahn.momentum.core.ui.theme.WorkoutTrackerTheme
import de.nicolasmahn.momentum.testfixtures.defaultReadingTime
import de.nicolasmahn.momentum.feature.checklist.presentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CompletionButtonBehaviorTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun successfulSignalSpringsCheckAndBrieflyBurstsThenKeepsCheck() {
        composeRule.mainClock.autoAdvance = false
        val completed = mutableStateOf(false)
        val signal = mutableStateOf(0)
        composeRule.setContent {
            WorkoutTrackerTheme {
                val presentation = defaultReadingTime(label = "reading").presentation()
                CompletionButton(
                    actionLabel = "Read today",
                    completedLabel = "Read today",
                    completed = completed.value,
                    onClick = {
                        completed.value = true
                        signal.value += 1
                    },
                    icon = presentation.icon,
                    colors = presentation.colors.completionButtonColors(),
                    testTag = "completion",
                    celebrationSignal = signal.value,
                    intensity = CompletionCelebrationIntensity.Strong,
                    motionEnabled = true,
                )
            }
        }
        composeRule.onNodeWithTag("completion").performClick()
        composeRule.mainClock.advanceTimeBy(80)
        composeRule.onNodeWithTag("completion_celebration", useUnmergedTree = true).assertIsDisplayed()
        val animatedWidth = composeRule
            .onNodeWithTag("completion_check", useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot.width
        composeRule.mainClock.advanceTimeBy(1_500)
        composeRule.onNodeWithTag("completion_celebration", useUnmergedTree = true).assertDoesNotExist()
        val settledWidth = composeRule
            .onNodeWithTag("completion_check", useUnmergedTree = true)
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot.width
        assertTrue(
            "Check must visibly change scale during its spring",
            kotlin.math.abs(animatedWidth - settledWidth) > 0.5f,
        )
    }

    @Test
    fun cancellingCompletionRemovesBurstAndRemountDoesNotReplayOldSignal() {
        composeRule.mainClock.autoAdvance = false
        val completed = mutableStateOf(false)
        val signal = mutableStateOf(0)
        val mounted = mutableStateOf(true)
        composeRule.setContent {
            WorkoutTrackerTheme {
                if (mounted.value) {
                    val presentation = defaultReadingTime(label = "reading").presentation()
                    CompletionButton(
                        actionLabel = "Read",
                        completedLabel = "Read",
                        completed = completed.value,
                        onClick = {
                            completed.value = true
                            signal.value += 1
                        },
                        icon = presentation.icon,
                        colors = presentation.colors.completionButtonColors(),
                        testTag = "completion",
                        celebrationSignal = signal.value,
                        intensity = CompletionCelebrationIntensity.Strong,
                        motionEnabled = true,
                    )
                }
            }
        }
        composeRule.onNodeWithTag("completion").performClick()
        composeRule.mainClock.advanceTimeBy(80)
        composeRule.onNodeWithTag("completion_celebration", useUnmergedTree = true).assertIsDisplayed()
        composeRule.runOnIdle { completed.value = false }
        composeRule.mainClock.advanceTimeBy(32)
        composeRule.onNodeWithTag("completion_celebration", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("completion_check", useUnmergedTree = true).assertDoesNotExist()
        composeRule.runOnIdle { mounted.value = false }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.runOnIdle {
            completed.value = true
            mounted.value = true
        }
        composeRule.mainClock.advanceTimeBy(80)
        composeRule.onNodeWithTag("completion_check", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("completion_celebration", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun reducedMotionKeepsPressStationaryAndCompletesWithoutBurst() {
        composeRule.mainClock.autoAdvance = false
        val completed = mutableStateOf(false)
        val signal = mutableStateOf(0)
        composeRule.setContent {
            WorkoutTrackerTheme {
                val presentation = defaultReadingTime(label = "reading").presentation()
                CompletionButton(
                    actionLabel = "Read",
                    completedLabel = "Read",
                    completed = completed.value,
                    onClick = {
                        completed.value = true
                        signal.value += 1
                    },
                    icon = presentation.icon,
                    colors = presentation.colors.completionButtonColors(),
                    testTag = "completion",
                    celebrationSignal = signal.value,
                    intensity = CompletionCelebrationIntensity.Strong,
                    motionEnabled = false,
                )
            }
        }
        val normalBounds = composeRule
            .onNodeWithTag("completion")
            .fetchSemanticsNode()
            .boundsInRoot
        composeRule.onNodeWithTag("completion").performTouchInput { down(center) }
        composeRule.mainClock.advanceTimeBy(150)
        assertEquals(
            normalBounds,
            composeRule.onNodeWithTag("completion").fetchSemanticsNode().boundsInRoot,
        )
        composeRule.onNodeWithTag("completion").performTouchInput { up() }
        composeRule.mainClock.advanceTimeBy(80)
        composeRule.onNodeWithTag("completion_check", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("completion_celebration", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun strongPressCompressesBeforeRelease() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            WorkoutTrackerTheme {
                val presentation = defaultReadingTime(label = "reading").presentation()
                CompletionButton(
                    actionLabel = "Read",
                    completedLabel = "Read",
                    completed = false,
                    onClick = {},
                    icon = presentation.icon,
                    colors = presentation.colors.completionButtonColors(),
                    testTag = "completion",
                    intensity = CompletionCelebrationIntensity.Strong,
                    motionEnabled = true,
                )
            }
        }
        val normalBounds = composeRule
            .onNodeWithTag("completion")
            .fetchSemanticsNode()
            .boundsInRoot
        composeRule.onNodeWithTag("completion").performTouchInput { down(center) }
        composeRule.mainClock.advanceTimeBy(150)
        val pressedBounds = composeRule
            .onNodeWithTag("completion")
            .fetchSemanticsNode()
            .boundsInRoot
        assertTrue(pressedBounds.width < normalBounds.width)
        assertTrue(pressedBounds.top > normalBounds.top)
        composeRule.onNodeWithTag("completion").performTouchInput { up() }
    }

    @Test
    fun historicalLabelFitsAtLargeAccessibleFontSize() {
        composeRule.setContent {
            WorkoutTrackerTheme {
                val density = LocalDensity.current.density
                CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 2f)) {
                    val presentation = defaultReadingTime(label = "reading").presentation()
                    CompletionButton(
                        actionLabel = "Read on Thu, Sep 17",
                        completedLabel = "Read on Thu, Sep 17",
                        completed = true,
                        onClick = {},
                        icon = presentation.icon,
                        colors = presentation.colors.completionButtonColors(),
                        testTag = "completion",
                    )
                }
            }
        }
        val button = composeRule
            .onNodeWithTag("completion")
            .fetchSemanticsNode()
            .boundsInRoot
        val label = composeRule
            .onNodeWithText("Read on Thu, Sep 17", useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
        assertTrue(
            "Enlarged label must fit inside the button",
            button.contains(label.topLeft) && button.contains(label.bottomRight),
        )
    }

    @Test
    fun completedButtonLosesRaisedEdgeWithoutChangingItsFootprint() {
        val completed = mutableStateOf(false)
        var background = Color.Unspecified
        var depth = Color.Unspecified
        composeRule.setContent {
            WorkoutTrackerTheme {
                val presentation = defaultReadingTime(label = "reading").presentation()
                background = MaterialTheme.colorScheme.background
                depth = presentation.colors.completionButtonColors().depth
                CompletionButton(
                    actionLabel = "Read today",
                    completedLabel = "Read today",
                    completed = completed.value,
                    onClick = { completed.value = true },
                    icon = presentation.icon,
                    colors = presentation.colors.completionButtonColors(),
                    testTag = "completion",
                    modifier = Modifier
                        .testTag("completion_footprint")
                        .background(background),
                )
            }
        }
        val footprint = composeRule.onNodeWithTag("completion_footprint")
        val boundsBefore = footprint.fetchSemanticsNode().boundsInRoot
        val raised = footprint.captureToImage().toPixelMap()
        assertEquals(depth, raised[raised.width / 2, raised.height - 2])

        composeRule.onNodeWithTag("completion").performClick()

        val boundsAfter = footprint.fetchSemanticsNode().boundsInRoot
        val settled = footprint.captureToImage().toPixelMap()
        assertEquals(boundsBefore, boundsAfter)
        assertEquals(background, settled[settled.width / 2, settled.height - 2])
        composeRule.onNodeWithTag("completion_check", useUnmergedTree = true).assertIsDisplayed()
    }

}
