package com.example.workouttracker.core.ui

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.example.workouttracker.core.ui.theme.WorkoutTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class MomentumTabsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val selected = mutableStateOf(ShortLabel)

    @Test
    fun eachTabSaysItsLabelAndWhetherItIsTheChosenTab() {
        setTabs()

        composeRule.onNodeWithTag("tab_$ShortLabel")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
            .assertTextEquals(ShortLabel)
            .assertIsSelected()
        composeRule.onNodeWithTag("tab_$LongLabel").assertIsNotSelected().performClick()

        composeRule.runOnIdle { assertEquals(LongLabel, selected.value) }
        composeRule.onNodeWithTag("tab_$LongLabel").assertIsSelected()
        composeRule.onNodeWithTag("tab_$ShortLabel").assertIsNotSelected()
    }

    @Test
    fun aLongLabelKeepsOneLineAndTheRowScrollsToTheChosenTab() {
        setTabs()

        val long = composeRule.onNodeWithText(LongLabel, useUnmergedTree = true).textLayout()
        assertEquals(1, long.lineCount)
        assertFalse(long.hasVisualOverflow)
        val short = composeRule.onNodeWithTag("tab_$ShortLabel").assertHeightIsAtLeast(48.dp).getUnclippedBoundsInRoot()
        assertEquals(short.height, composeRule.onNodeWithTag("tab_$LongLabel").getUnclippedBoundsInRoot().height)

        // Choosing the tab that runs past the row's end scrolls it into sight; the row keeps its height.
        composeRule.onNodeWithTag("tab_$LongLabel").performScrollTo().performClick()
        composeRule.onNodeWithTag("tab_$LongLabel").assertIsDisplayed()
        assertEquals(short.height, composeRule.onNodeWithTag("tab_$ShortLabel").getUnclippedBoundsInRoot().height)
    }

    /** German editor tabs fit a phone's width together, so each is as wide as its label and none breaks. */
    @Test
    fun labelsThatFitTogetherKeepOneLineEach() {
        val german = listOf("Punkte", "Erinnerungen", "Aussehen", "Streak")
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1f)) {
                WorkoutTrackerTheme {
                    MomentumTabs(
                        tabs = german,
                        selected = german.first(),
                        onSelect = {},
                        label = { it },
                        modifier = Modifier.width(411.dp),
                    )
                }
            }
        }

        german.forEach { label ->
            assertEquals(label, 1, composeRule.onNodeWithText(label, useUnmergedTree = true).textLayout().lineCount)
        }
    }

    /** On a narrow phone with the largest font, every label keeps its one line whole, and the row scrolls. */
    @Test
    fun aLongLabelInALargeFontIsNeverCut() {
        val german = listOf("Punkte", "Erinnerungen", "Aussehen", "Streak")
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                WorkoutTrackerTheme {
                    MomentumTabs(
                        tabs = german,
                        selected = german.first(),
                        onSelect = {},
                        label = { it },
                        modifier = Modifier.width(320.dp),
                    )
                }
            }
        }

        german.forEach { label ->
            val layout = composeRule.onNodeWithText(label, useUnmergedTree = true).textLayout()
            assertFalse(label, layout.hasVisualOverflow)
            assertEquals(label, 1, layout.lineCount)
        }
    }

    private fun setTabs() {
        composeRule.setContent {
            // A fixed font scale keeps the row too narrow for both labels on any test device.
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1f)) {
                WorkoutTrackerTheme {
                    MomentumTabs(
                        tabs = listOf(ShortLabel, LongLabel),
                        selected = selected.value,
                        onSelect = { selected.value = it },
                        label = { it },
                        // Too narrow for both labels, so the row scrolls.
                        modifier = Modifier.width(150.dp),
                        testTag = { "tab_$it" },
                        motionEnabled = true,
                    )
                }
            }
        }
    }

    private fun SemanticsNodeInteraction.textLayout(): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        return layouts.single()
    }

    private companion object {
        const val ShortLabel = "Items"
        const val LongLabel = "Reminders today"
    }
}
